package com.daniele21.trafficmonitoring.export

import com.daniele21.trafficmonitoring.data.AttributionIntervalEntity
import com.daniele21.trafficmonitoring.data.CounterSnapshotEntity
import com.daniele21.trafficmonitoring.data.LifecycleEventEntity
import com.daniele21.trafficmonitoring.data.ManualTestMarkerEntity
import com.daniele21.trafficmonitoring.data.NetworkEventEntity
import com.daniele21.trafficmonitoring.data.ValidationExportBundle
import com.daniele21.trafficmonitoring.platform.BackgroundNetworkRegistrationStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ValidationExportWriter {
    fun write(bundle: ValidationExportBundle, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.writeText("manifest.json", manifest(bundle).toString(2))
            zip.writeText("network-events.csv", networkEventsCsv(bundle.networkEvents))
            zip.writeText("counter-snapshots.csv", counterSnapshotsCsv(bundle.counterSnapshots))
            zip.writeText("attribution-intervals.csv", attributionIntervalsCsv(bundle.attributionIntervals))
            zip.writeText("lifecycle-events.csv", lifecycleEventsCsv(bundle.lifecycleEvents))
            zip.writeText("manual-markers.csv", manualMarkersCsv(bundle.manualMarkers))
            zip.writeText("summary.json", summary(bundle).toString(2))
            zip.writeText("README.txt", readme(bundle))
        }
    }

    private fun manifest(bundle: ValidationExportBundle): JSONObject = JSONObject()
        .put("exportFormatVersion", 1)
        .put("schemaVersion", bundle.run.schemaVersion)
        .put("attributionAlgorithmVersion", bundle.run.attributionAlgorithmVersion)
        .put("runId", bundle.run.id)
        .put("startedAt", iso(bundle.run.startedAtMs))
        .put("endedAt", bundle.run.endedAtMs?.let(::iso) ?: JSONObject.NULL)
        .put("appVersion", bundle.run.appVersion)
        .put(
            "device",
            JSONObject()
                .put("manufacturer", bundle.run.deviceManufacturer)
                .put("model", bundle.run.deviceModel)
                .put("androidVersion", bundle.run.androidVersion)
                .put("sdkInt", bundle.run.sdkInt)
        )
        .put(
            "configuration",
            JSONObject()
                .put("backgroundStrategy", "m1c_pending_intent_plus_in_process")
                .put("pendingIntentRegistrationVersion", BackgroundNetworkRegistrationStore.REGISTRATION_VERSION)
                .put("pendingIntentApi", "ConnectivityManager.registerNetworkCallback(NetworkRequest, PendingIntent)")
                .put("counterSource", "TrafficStats.getTotalRxBytes/getTotalTxBytes")
                .put("counterScope", "device_all_interfaces_since_boot")
                .put("recoveryCadenceHours", JSONObject.NULL)
                .put("usageAccessGranted", false)
                .put("wifiIdentityPermissionState", "not_requested")
                .put("batteryModeDeclaredByTester", bundle.run.batteryModeDeclaredByTester ?: JSONObject.NULL)
        )

    private fun summary(bundle: ValidationExportBundle): JSONObject {
        val eventsBySource = JSONObject()
        bundle.networkEvents.groupingBy { it.source }.eachCount().toSortedMap().forEach { (source, count) ->
            eventsBySource.put(source, count)
        }

        val processStarts = bundle.lifecycleEvents.count { it.kind == "process_start" }
        val unknownSsidEvents = bundle.networkEvents.count { it.ssidAvailability != "known" }
        val longestGapMs = bundle.networkEvents
            .map { it.receivedAtWallClockMs }
            .sorted()
            .zipWithNext { a, b -> b - a }
            .maxOrNull() ?: 0L

        val confirmedRx = bundle.attributionIntervals
            .filter { it.confidence == "confirmed" }
            .sumOf { it.rxBytes ?: 0L }
        val confirmedTx = bundle.attributionIntervals
            .filter { it.confidence == "confirmed" }
            .sumOf { it.txBytes ?: 0L }
        val inferredBytes = bundle.attributionIntervals
            .filter { it.confidence == "inferred" }
            .sumOf { (it.rxBytes ?: 0L) + (it.txBytes ?: 0L) }
        val unattributedBytes = bundle.attributionIntervals
            .filter { it.confidence == "unattributed" }
            .sumOf { (it.rxBytes ?: 0L) + (it.txBytes ?: 0L) }

        return JSONObject()
            .put("runDurationMs", (bundle.run.endedAtMs ?: System.currentTimeMillis()) - bundle.run.startedAtMs)
            .put("networkEventsBySource", eventsBySource)
            .put("counterSnapshotCount", bundle.counterSnapshots.size)
            .put("attributionIntervalCount", bundle.attributionIntervals.size)
            .put("pendingIntentWakeCount", bundle.networkEvents.count { it.source == "pending_intent" })
            .put("pendingIntentRegistrationCount", bundle.lifecycleEvents.count { it.kind == "pending_intent_registered" })
            .put("pendingIntentRegistrationFailureCount", bundle.lifecycleEvents.count { it.kind == "pending_intent_registration_failed" })
            .put("inProcessEventCount", bundle.networkEvents.count { it.source == "callback" })
            .put("recoveryWorkerCount", bundle.lifecycleEvents.count { it.kind == "recovery_worker_started" })
            .put("processStartCount", processStarts)
            .put("observedBootCount", bundle.lifecycleEvents.count { it.kind == "boot_received" })
            .put("manualMarkerCount", bundle.manualMarkers.size)
            .put("confirmedRxBytes", confirmedRx)
            .put("confirmedTxBytes", confirmedTx)
            .put("inferredBytes", inferredBytes)
            .put("unattributedBytes", unattributedBytes)
            .put("discardedIntervalCount", bundle.attributionIntervals.count { it.confidence == "discarded" })
            .put("clockDiscontinuityCount", bundle.attributionIntervals.count { it.reason == "clock_discontinuity" })
            .put("counterResetCount", bundle.counterSnapshots.count { it.errorCode == "counter_reset" })
            .put("unknownSsidEventCount", unknownSsidEvents)
            .put("longestEvidenceGapMs", longestGapMs)
            .put("networks", networkSummary(bundle.attributionIntervals))
    }

    private fun networkSummary(intervals: List<AttributionIntervalEntity>): JSONArray {
        val grouped = intervals
            .filter { it.networkIdentity != null && (it.confidence == "confirmed" || it.confidence == "inferred") }
            .groupBy { it.networkIdentity!! }
            .map { (identity, rows) ->
                val rx = rows.sumOf { it.rxBytes ?: 0L }
                val tx = rows.sumOf { it.txBytes ?: 0L }
                JSONObject()
                    .put("networkIdentity", identity)
                    .put("networkDisplayName", rows.lastOrNull()?.networkDisplayName ?: JSONObject.NULL)
                    .put("rxBytes", rx)
                    .put("txBytes", tx)
                    .put("totalBytes", rx + tx)
                    .put("intervalCount", rows.size)
            }
            .sortedByDescending { it.optLong("totalBytes") }

        return JSONArray().also { array -> grouped.forEach { value -> array.put(value) } }
    }

    private fun networkEventsCsv(rows: List<NetworkEventEntity>): String = csv(
        header = listOf(
            "id", "runId", "receivedAtIso", "receivedAtWallClockMs", "receivedAtElapsedRealtimeMs",
            "source", "kind", "androidNetworkHandle", "transportSet", "isValidated", "isMetered",
            "isNotVpn", "vpnPresent", "ssid", "ssidAvailability", "interfaceNames", "rawSummaryJson"
        ),
        rows = rows.map { row ->
            listOf(
                row.id, row.runId, iso(row.receivedAtWallClockMs), row.receivedAtWallClockMs,
                row.receivedAtElapsedRealtimeMs, row.source, row.kind, row.androidNetworkHandle,
                row.transportSet, row.isValidated, row.isMetered, row.isNotVpn, row.vpnPresent,
                row.ssid, row.ssidAvailability, row.interfaceNames, row.rawSummaryJson
            )
        }
    )

    private fun counterSnapshotsCsv(rows: List<CounterSnapshotEntity>): String = csv(
        header = listOf(
            "id", "runId", "eventId", "observedAtIso", "observedAtWallClockMs",
            "observedAtElapsedRealtimeMs", "bootGeneration", "source", "rxBytes", "txBytes",
            "interfaceName", "status", "errorCode"
        ),
        rows = rows.map { row ->
            listOf(
                row.id, row.runId, row.eventId, iso(row.observedAtWallClockMs), row.observedAtWallClockMs,
                row.observedAtElapsedRealtimeMs, row.bootGeneration, row.source, row.rxBytes, row.txBytes,
                row.interfaceName, row.status, row.errorCode
            )
        }
    )

    private fun attributionIntervalsCsv(rows: List<AttributionIntervalEntity>): String = csv(
        header = listOf(
            "id", "runId", "startedAtIso", "startedAtMs", "endedAtIso", "endedAtMs",
            "networkIdentity", "networkDisplayName", "transport", "rxBytes", "txBytes", "confidence",
            "startEvidenceEventId", "endEvidenceEventId", "reason", "algorithmVersion"
        ),
        rows = rows.map { row ->
            listOf(
                row.id, row.runId, iso(row.startedAtMs), row.startedAtMs, iso(row.endedAtMs), row.endedAtMs,
                row.networkIdentity, row.networkDisplayName, row.transport, row.rxBytes, row.txBytes,
                row.confidence, row.startEvidenceEventId, row.endEvidenceEventId, row.reason, row.algorithmVersion
            )
        }
    )

    private fun lifecycleEventsCsv(rows: List<LifecycleEventEntity>): String = csv(
        header = listOf(
            "id", "runId", "timestampIso", "timestampWallClockMs", "timestampElapsedRealtimeMs",
            "kind", "detailsJson"
        ),
        rows = rows.map { row ->
            listOf(
                row.id, row.runId, iso(row.timestampWallClockMs), row.timestampWallClockMs,
                row.timestampElapsedRealtimeMs, row.kind, row.detailsJson
            )
        }
    )

    private fun manualMarkersCsv(rows: List<ManualTestMarkerEntity>): String = csv(
        header = listOf(
            "id", "runId", "timestampIso", "timestampWallClockMs", "timestampElapsedRealtimeMs",
            "markerType", "label", "currentObservedNetwork", "notes"
        ),
        rows = rows.map { row ->
            listOf(
                row.id, row.runId, iso(row.timestampWallClockMs), row.timestampWallClockMs,
                row.timestampElapsedRealtimeMs, row.markerType, row.label, row.currentObservedNetwork, row.notes
            )
        }
    )

    private fun readme(bundle: ValidationExportBundle): String = """
        Traffic Monitoring Android — validation export
        =================================================

        Run ID: ${bundle.run.id}
        App version at run start: ${bundle.run.appVersion}
        Started: ${iso(bundle.run.startedAtMs)}

        M1C records four evidence paths:
        - network-events.csv: startup/manual observations;
        - source=callback: live default-network callbacks while the process exists;
        - source=pending_intent: ConnectivityManager PendingIntent availability wakes that may outlive the UI process;
        - counter-snapshots.csv / attribution-intervals.csv: cumulative TrafficStats evidence and conservative deltas.

        A PendingIntent wake proves only that Android delivered network-availability evidence at that timestamp. It does
        not by itself prove every loss/boundary is observable. M1C compares deliberate transitions against the exported
        event timeline before deciding whether this background strategy is sufficient.

        Continuity gaps, process restarts, VPN ambiguity, reboots, resets and clock discontinuities are never silently
        converted into confident network usage.

        No packet contents, destinations, DNS queries, account identifiers or device hardware identifiers are collected.
    """.trimIndent() + "\n"

    internal fun csv(header: List<String>, rows: List<List<Any?>>): String = buildString {
        appendLine(header.joinToString(",") { csvCell(it) })
        rows.forEach { row -> appendLine(row.joinToString(",") { csvCell(it?.toString().orEmpty()) }) }
    }

    internal fun csvCell(value: String): String {
        val mustQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!mustQuote) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun iso(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    private fun ZipOutputStream.writeText(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
