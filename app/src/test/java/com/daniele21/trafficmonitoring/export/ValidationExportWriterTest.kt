package com.daniele21.trafficmonitoring.export

import com.daniele21.trafficmonitoring.data.AttributionIntervalEntity
import com.daniele21.trafficmonitoring.data.CounterSnapshotEntity
import com.daniele21.trafficmonitoring.data.LifecycleEventEntity
import com.daniele21.trafficmonitoring.data.ManualTestMarkerEntity
import com.daniele21.trafficmonitoring.data.NetworkEventEntity
import com.daniele21.trafficmonitoring.data.ValidationExportBundle
import com.daniele21.trafficmonitoring.data.ValidationRunEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ValidationExportWriterTest {
    private val writer = ValidationExportWriter()

    @Test
    fun `export contains every required validation file`() {
        val output = ByteArrayOutputStream()
        writer.write(sampleBundle(), output)

        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names += entry.name
                entry = zip.nextEntry
            }
        }

        assertEquals(
            setOf(
                "manifest.json",
                "network-events.csv",
                "counter-snapshots.csv",
                "attribution-intervals.csv",
                "lifecycle-events.csv",
                "manual-markers.csv",
                "summary.json",
                "README.txt"
            ),
            names
        )
    }

    @Test
    fun `csv escapes commas quotes and new lines`() {
        assertEquals("plain", writer.csvCell("plain"))
        assertEquals("\"hello,world\"", writer.csvCell("hello,world"))
        assertEquals("\"say \"\"hello\"\"\"", writer.csvCell("say \"hello\""))
        assertEquals("\"a\nb\"", writer.csvCell("a\nb"))
    }

    @Test
    fun `empty future datasets still export headers`() {
        val output = ByteArrayOutputStream()
        writer.write(sampleBundle(), output)

        val contents = unzip(output.toByteArray())
        assertTrue(contents.getValue("counter-snapshots.csv").startsWith("id,runId,eventId"))
        assertTrue(contents.getValue("attribution-intervals.csv").startsWith("id,runId,startedAtIso"))
    }

    private fun sampleBundle(): ValidationExportBundle = ValidationExportBundle(
        run = ValidationRunEntity(
            id = "run-1",
            startedAtMs = 1_700_000_000_000,
            endedAtMs = null,
            label = null,
            appVersion = "0.1.0-test",
            schemaVersion = 1,
            attributionAlgorithmVersion = 1,
            deviceManufacturer = "Test",
            deviceModel = "Phone",
            androidVersion = "15",
            sdkInt = 35,
            batteryModeDeclaredByTester = null,
            notes = null
        ),
        networkEvents = listOf(
            NetworkEventEntity(
                id = "event-1",
                runId = "run-1",
                receivedAtWallClockMs = 1_700_000_000_100,
                receivedAtElapsedRealtimeMs = 10L,
                source = "manual",
                kind = "snapshot",
                androidNetworkHandle = "42",
                transportSet = "wifi",
                isValidated = true,
                isMetered = false,
                isNotVpn = true,
                vpnPresent = false,
                ssid = "Home, \"Lab\"",
                ssidAvailability = "known",
                interfaceNames = "wlan0",
                rawSummaryJson = "{}"
            )
        ),
        counterSnapshots = emptyList<CounterSnapshotEntity>(),
        attributionIntervals = emptyList<AttributionIntervalEntity>(),
        lifecycleEvents = listOf(
            LifecycleEventEntity(
                id = "life-1",
                runId = "run-1",
                timestampWallClockMs = 1_700_000_000_050,
                timestampElapsedRealtimeMs = 5L,
                kind = "process_start",
                detailsJson = null
            )
        ),
        manualMarkers = listOf(
            ManualTestMarkerEntity(
                id = "marker-1",
                runId = "run-1",
                timestampWallClockMs = 1_700_000_000_200,
                timestampElapsedRealtimeMs = 20L,
                markerType = "OTHER",
                label = "Test marker",
                currentObservedNetwork = "Home",
                notes = "note"
            )
        )
    )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return result
    }
}
