package com.daniele21.trafficmonitoring.data

import android.os.Build
import android.os.SystemClock
import com.daniele21.trafficmonitoring.BuildConfig
import com.daniele21.trafficmonitoring.domain.AttributionEngine
import com.daniele21.trafficmonitoring.domain.AttributionEvidence
import com.daniele21.trafficmonitoring.evidence.EvidenceIntervalInput
import com.daniele21.trafficmonitoring.platform.NetworkContextReader
import com.daniele21.trafficmonitoring.platform.NetworkContextSnapshot
import com.daniele21.trafficmonitoring.platform.TrafficCounterReader
import com.daniele21.trafficmonitoring.usage.UsageRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

class ValidationRepository(
    private val database: ValidationDatabase,
    private val networkContextReader: NetworkContextReader,
    private val trafficCounterReader: TrafficCounterReader,
    private val usageRepository: UsageRepository? = null,
    private val attributionEngine: AttributionEngine = AttributionEngine()
) {
    private val dao = database.validationDao()
    private val runMutex = Mutex()
    private val captureMutex = Mutex()
    private var firstCaptureInProcess = true

    suspend fun ensureActiveRun(): ValidationRunEntity = runMutex.withLock {
        dao.activeRun()?.let { return@withLock it }
        val now = System.currentTimeMillis()
        val run = ValidationRunEntity(
            id = UUID.randomUUID().toString(),
            startedAtMs = now,
            endedAtMs = null,
            label = null,
            appVersion = BuildConfig.VERSION_NAME,
            schemaVersion = 1,
            attributionAlgorithmVersion = 2,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            batteryModeDeclaredByTester = null,
            notes = null
        )
        dao.insertRun(run)
        run
    }

    suspend fun recordProcessStart(): ValidationRunEntity {
        val run = ensureActiveRun()
        dao.insertLifecycleEvent(
            LifecycleEventEntity(
                id = UUID.randomUUID().toString(),
                runId = run.id,
                timestampWallClockMs = System.currentTimeMillis(),
                timestampElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                kind = "process_start",
                detailsJson = "{\"version\":\"${BuildConfig.VERSION_NAME}\"}"
            )
        )
        return run
    }

    suspend fun recordLifecycle(kind: String, detailsJson: String? = null) {
        val run = ensureActiveRun()
        dao.insertLifecycleEvent(
            LifecycleEventEntity(
                id = UUID.randomUUID().toString(),
                runId = run.id,
                timestampWallClockMs = System.currentTimeMillis(),
                timestampElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                kind = kind,
                detailsJson = detailsJson
            )
        )
    }

    fun currentNetworkSnapshot(): NetworkContextSnapshot = networkContextReader.readCurrent()

    suspend fun captureNetworkSnapshot(
        source: String = "manual",
        kind: String = "snapshot",
        snapshotOverride: NetworkContextSnapshot? = null
    ): Pair<NetworkEventEntity, NetworkContextSnapshot> = captureMutex.withLock {
        val run = ensureActiveRun()
        val snapshot = snapshotOverride ?: networkContextReader.readCurrent()
        val wallClockMs = System.currentTimeMillis()
        val elapsedRealtimeMs = SystemClock.elapsedRealtime()
        val event = NetworkEventEntity(
            id = UUID.randomUUID().toString(),
            runId = run.id,
            receivedAtWallClockMs = wallClockMs,
            receivedAtElapsedRealtimeMs = elapsedRealtimeMs,
            source = source,
            kind = kind,
            androidNetworkHandle = snapshot.networkHandle,
            transportSet = snapshot.transportSet,
            isValidated = snapshot.isValidated,
            isMetered = snapshot.isMetered,
            isNotVpn = snapshot.isNotVpn,
            vpnPresent = snapshot.vpnPresent,
            ssid = snapshot.ssid,
            ssidAvailability = snapshot.ssidAvailability,
            interfaceNames = snapshot.interfaceNames,
            rawSummaryJson = snapshot.rawSummaryJson
        )

        val previousCounter = dao.latestCounterSnapshot(run.id)
        val reading = trafficCounterReader.read()
        val counterReset = previousCounter?.let { previous ->
            reading.rxBytes != null && reading.txBytes != null &&
                previous.rxBytes != null && previous.txBytes != null &&
                previous.bootGeneration == reading.bootGeneration &&
                (reading.rxBytes < previous.rxBytes || reading.txBytes < previous.txBytes)
        } ?: false

        val currentCounter = CounterSnapshotEntity(
            id = UUID.randomUUID().toString(),
            runId = run.id,
            eventId = event.id,
            observedAtWallClockMs = wallClockMs,
            observedAtElapsedRealtimeMs = elapsedRealtimeMs,
            bootGeneration = reading.bootGeneration,
            source = "traffic_stats_total",
            rxBytes = reading.rxBytes,
            txBytes = reading.txBytes,
            interfaceName = snapshot.interfaceNames,
            status = if (counterReset) "reset" else reading.status,
            errorCode = if (counterReset) "counter_reset" else reading.errorCode
        )

        dao.insertNetworkEvent(event)

        if (previousCounter != null) {
            val previousEvent = previousCounter.eventId?.let { dao.networkEventById(it) }
            val result = attributionEngine.between(
                previous = previousCounter.toEvidence(previousEvent),
                current = currentCounter.toEvidence(event),
                continuityBrokenReason = if (firstCaptureInProcess) "process_restart_boundary" else null
            )
            if (result != null) {
                val interval = AttributionIntervalEntity(
                    id = UUID.randomUUID().toString(),
                    runId = run.id,
                    startedAtMs = result.startedAtMs,
                    endedAtMs = result.endedAtMs,
                    networkIdentity = result.networkIdentity,
                    networkDisplayName = result.networkDisplayName,
                    transport = result.transport,
                    rxBytes = result.rxBytes,
                    txBytes = result.txBytes,
                    confidence = result.confidence,
                    startEvidenceEventId = previousCounter.eventId,
                    endEvidenceEventId = event.id,
                    reason = result.reason,
                    algorithmVersion = run.attributionAlgorithmVersion
                )
                dao.insertAttributionInterval(interval)

                usageRepository?.let { usage ->
                    runCatching { usage.ingest(interval) }
                        .onFailure { error ->
                            dao.insertLifecycleEvent(
                                LifecycleEventEntity(
                                    id = UUID.randomUUID().toString(),
                                    runId = run.id,
                                    timestampWallClockMs = System.currentTimeMillis(),
                                    timestampElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                                    kind = "usage_history_ingest_failed",
                                    detailsJson = JSONObject()
                                        .put("intervalId", interval.id)
                                        .put("error", error::class.java.simpleName)
                                        .put("message", error.message)
                                        .toString()
                                )
                            )
                        }
                }
            }
        }

        dao.insertCounterSnapshot(currentCounter)
        firstCaptureInProcess = false
        event to snapshot
    }

    suspend fun addMarker(markerType: String, label: String, notes: String? = null): ManualTestMarkerEntity {
        val (event, snapshot) = captureNetworkSnapshot(source = "marker")
        val marker = ManualTestMarkerEntity(
            id = UUID.randomUUID().toString(),
            runId = event.runId,
            timestampWallClockMs = System.currentTimeMillis(),
            timestampElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            markerType = markerType,
            label = label,
            currentObservedNetwork = snapshot.displayName,
            notes = notes
        )
        dao.insertManualMarker(marker)
        return marker
    }

    suspend fun startNewRun(): ValidationRunEntity = captureMutex.withLock {
        dao.activeRun()?.let { active ->
            dao.updateRun(active.copy(endedAtMs = System.currentTimeMillis()))
        }
        firstCaptureInProcess = true
        ensureActiveRun().also {
            dao.insertLifecycleEvent(
                LifecycleEventEntity(
                    id = UUID.randomUUID().toString(),
                    runId = it.id,
                    timestampWallClockMs = System.currentTimeMillis(),
                    timestampElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                    kind = "process_foreground",
                    detailsJson = "{\"reason\":\"new_validation_run\"}"
                )
            )
        }
    }

    suspend fun loadDashboard(limit: Int = 30): ValidationDashboardData {
        val run = ensureActiveRun()
        return ValidationDashboardData(
            run = run,
            recentEvents = dao.recentNetworkEvents(run.id, limit),
            recentMarkers = dao.recentMarkers(run.id, limit),
            recentCounters = dao.recentCounterSnapshots(run.id, limit = 12),
            recentIntervals = dao.recentAttributionIntervals(run.id, limit = 12)
        )
    }

    suspend fun loadEvidenceIntervals(startMs: Long, endMs: Long): List<EvidenceIntervalInput> =
        dao.attributionIntervalsBetween(startMs, endMs).map { interval ->
            EvidenceIntervalInput(
                startedAtMs = interval.startedAtMs,
                endedAtMs = interval.endedAtMs,
                confidence = interval.confidence,
                reason = interval.reason
            )
        }

    suspend fun loadExportBundle(runId: String): ValidationExportBundle {
        val run = requireNotNull(dao.runById(runId)) { "Validation run not found: $runId" }
        return ValidationExportBundle(
            run = run,
            networkEvents = dao.networkEventsForRun(runId),
            counterSnapshots = dao.counterSnapshotsForRun(runId),
            attributionIntervals = dao.attributionIntervalsForRun(runId),
            lifecycleEvents = dao.lifecycleEventsForRun(runId),
            manualMarkers = dao.manualMarkersForRun(runId)
        )
    }

    suspend fun clearAllAndStartFresh(): ValidationRunEntity = captureMutex.withLock {
        database.clearAllTables()
        firstCaptureInProcess = true
        ensureActiveRun()
    }

    private fun CounterSnapshotEntity.toEvidence(event: NetworkEventEntity?): AttributionEvidence =
        AttributionEvidence(
            wallClockMs = observedAtWallClockMs,
            elapsedRealtimeMs = observedAtElapsedRealtimeMs,
            bootGeneration = bootGeneration,
            rxBytes = rxBytes,
            txBytes = txBytes,
            eventId = eventId,
            networkIdentity = event?.networkIdentity(),
            networkDisplayName = event?.networkDisplayName(),
            transport = event?.transportSet ?: "unknown"
        )

    private fun NetworkEventEntity.networkIdentity(): String = when {
        transportSet == "offline" -> "offline"
        vpnPresent == true || transportSet.contains("vpn") -> "vpn:${androidNetworkHandle ?: interfaceNames ?: "unknown"}"
        ssid != null -> "wifi:ssid:$ssid"
        transportSet.contains("wifi") -> "wifi:${androidNetworkHandle ?: interfaceNames ?: "unknown"}"
        transportSet.contains("cellular") -> "cellular:${androidNetworkHandle ?: interfaceNames ?: "unknown"}"
        transportSet.contains("ethernet") -> "ethernet:${interfaceNames ?: androidNetworkHandle ?: "unknown"}"
        else -> "network:${androidNetworkHandle ?: interfaceNames ?: transportSet}"
    }

    private fun NetworkEventEntity.networkDisplayName(): String = when {
        ssid != null -> ssid
        transportSet == "offline" -> "Offline"
        transportSet.contains("wifi") -> "Wi-Fi · name unavailable"
        transportSet.contains("cellular") -> "Cellular"
        transportSet.contains("ethernet") -> "Ethernet"
        transportSet.contains("vpn") -> "VPN"
        else -> transportSet
    }
}

data class ValidationDashboardData(
    val run: ValidationRunEntity,
    val recentEvents: List<NetworkEventEntity>,
    val recentMarkers: List<ManualTestMarkerEntity>,
    val recentCounters: List<CounterSnapshotEntity>,
    val recentIntervals: List<AttributionIntervalEntity>
)

data class ValidationExportBundle(
    val run: ValidationRunEntity,
    val networkEvents: List<NetworkEventEntity>,
    val counterSnapshots: List<CounterSnapshotEntity>,
    val attributionIntervals: List<AttributionIntervalEntity>,
    val lifecycleEvents: List<LifecycleEventEntity>,
    val manualMarkers: List<ManualTestMarkerEntity>
)
