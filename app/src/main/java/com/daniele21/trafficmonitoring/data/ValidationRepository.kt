package com.daniele21.trafficmonitoring.data

import android.os.Build
import android.os.SystemClock
import com.daniele21.trafficmonitoring.BuildConfig
import com.daniele21.trafficmonitoring.platform.NetworkContextReader
import com.daniele21.trafficmonitoring.platform.NetworkContextSnapshot
import java.util.UUID

class ValidationRepository(
    private val database: ValidationDatabase,
    private val networkContextReader: NetworkContextReader
) {
    private val dao = database.validationDao()

    suspend fun ensureActiveRun(): ValidationRunEntity {
        dao.activeRun()?.let { return it }
        val now = System.currentTimeMillis()
        val run = ValidationRunEntity(
            id = UUID.randomUUID().toString(),
            startedAtMs = now,
            endedAtMs = null,
            label = null,
            appVersion = BuildConfig.VERSION_NAME,
            schemaVersion = 1,
            attributionAlgorithmVersion = 1,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            batteryModeDeclaredByTester = null,
            notes = null
        )
        dao.insertRun(run)
        return run
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

    suspend fun captureNetworkSnapshot(source: String = "manual"): Pair<NetworkEventEntity, NetworkContextSnapshot> {
        val run = ensureActiveRun()
        val snapshot = networkContextReader.readCurrent()
        val event = NetworkEventEntity(
            id = UUID.randomUUID().toString(),
            runId = run.id,
            receivedAtWallClockMs = System.currentTimeMillis(),
            receivedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            source = source,
            kind = "snapshot",
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
        dao.insertNetworkEvent(event)
        return event to snapshot
    }

    suspend fun addMarker(markerType: String, label: String, notes: String? = null): ManualTestMarkerEntity {
        val run = ensureActiveRun()
        val (_, snapshot) = captureNetworkSnapshot(source = "marker")
        val marker = ManualTestMarkerEntity(
            id = UUID.randomUUID().toString(),
            runId = run.id,
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

    suspend fun startNewRun(): ValidationRunEntity {
        dao.activeRun()?.let { active ->
            dao.updateRun(active.copy(endedAtMs = System.currentTimeMillis()))
        }
        return ensureActiveRun().also {
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
            recentMarkers = dao.recentMarkers(run.id, limit)
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

    suspend fun clearAllAndStartFresh(): ValidationRunEntity {
        database.clearAllTables()
        return ensureActiveRun()
    }
}

data class ValidationDashboardData(
    val run: ValidationRunEntity,
    val recentEvents: List<NetworkEventEntity>,
    val recentMarkers: List<ManualTestMarkerEntity>
)

data class ValidationExportBundle(
    val run: ValidationRunEntity,
    val networkEvents: List<NetworkEventEntity>,
    val counterSnapshots: List<CounterSnapshotEntity>,
    val attributionIntervals: List<AttributionIntervalEntity>,
    val lifecycleEvents: List<LifecycleEventEntity>,
    val manualMarkers: List<ManualTestMarkerEntity>
)
