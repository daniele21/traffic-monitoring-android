package com.daniele21.trafficmonitoring.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniele21.trafficmonitoring.R
import com.daniele21.trafficmonitoring.data.AttributionIntervalEntity
import com.daniele21.trafficmonitoring.data.ManualTestMarkerEntity
import com.daniele21.trafficmonitoring.data.NetworkEventEntity
import com.daniele21.trafficmonitoring.ui.theme.SignalCyan
import java.time.Instant
import java.util.Locale

@Composable
fun ValidationScreen(
    state: ValidationUiState,
    onRefreshNetwork: () -> Unit,
    onArmBackground: () -> Unit,
    onAddMarker: (type: String, label: String, notes: String?) -> Unit,
    onExport: () -> Unit,
    onStartNewRun: () -> Unit,
    onClearAll: () -> Unit
) {
    var customMarkerOpen by remember { mutableStateOf(false) }
    var clearConfirmationOpen by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BrandHeader()
            StatusCard(state)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRefreshNetwork, enabled = !state.isLoading) {
                    Text("Capture evidence")
                }
                OutlinedButton(onClick = onArmBackground, enabled = !state.isLoading) {
                    Text("Re-arm background")
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExport,
                enabled = !state.isLoading && state.runId != null
            ) {
                Text("Export validation run")
            }

            SectionTitle(
                title = "Manual test markers",
                subtitle = "Mark deliberate actions. Each marker also captures current network context and cumulative device usage."
            )
            MarkerButton(
                text = "Switching network",
                enabled = !state.isLoading,
                onClick = { onAddMarker("SWITCHING_NETWORK", "Switching network", null) }
            )
            MarkerButton(
                text = "Network switch complete",
                enabled = !state.isLoading,
                onClick = { onAddMarker("NETWORK_SWITCH_COMPLETE", "Network switch complete", null) }
            )
            MarkerButton(
                text = "Start known download",
                enabled = !state.isLoading,
                onClick = { onAddMarker("START_KNOWN_DOWNLOAD", "Start known download", null) }
            )
            MarkerButton(
                text = "End known download",
                enabled = !state.isLoading,
                onClick = { onAddMarker("END_KNOWN_DOWNLOAD", "End known download", null) }
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { customMarkerOpen = true },
                enabled = !state.isLoading
            ) {
                Text("Add custom marker")
            }

            HorizontalDivider()
            SectionTitle("Recent attribution intervals")
            if (state.recentIntervals.isEmpty()) {
                MutedText("Capture at least two pieces of evidence to create an interval.")
            } else {
                state.recentIntervals.take(8).forEach { AttributionRow(it) }
            }

            HorizontalDivider()
            SectionTitle("Recent network observations")
            if (state.recentEvents.isEmpty()) {
                MutedText("No observations yet.")
            } else {
                state.recentEvents.take(12).forEach { EventRow(it) }
            }

            HorizontalDivider()
            SectionTitle("Recent markers")
            if (state.recentMarkers.isEmpty()) {
                MutedText("No markers yet.")
            } else {
                state.recentMarkers.take(12).forEach { MarkerRow(it) }
            }

            HorizontalDivider()
            SectionTitle(
                title = "Validation run",
                subtitle = "M1C tests whether Android can deliver network-availability evidence after the UI process is absent, without a permanent foreground service."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onStartNewRun, enabled = !state.isLoading) {
                    Text("Start new run")
                }
                TextButton(onClick = { clearConfirmationOpen = true }, enabled = !state.isLoading) {
                    Text("Clear local data")
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (customMarkerOpen) {
        CustomMarkerDialog(
            onDismiss = { customMarkerOpen = false },
            onSave = { label, notes ->
                onAddMarker("OTHER", label, notes)
                customMarkerOpen = false
            }
        )
    }

    if (clearConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { clearConfirmationOpen = false },
            title = { Text("Clear all validation data?") },
            text = { Text("This permanently deletes all local runs, events, counters, intervals and markers on this device.") },
            confirmButton = {
                Button(onClick = {
                    onClearAll()
                    clearConfirmationOpen = false
                }) { Text("Clear data") }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmationOpen = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_shield),
            contentDescription = "Traffic Monitoring shield",
            modifier = Modifier.size(54.dp)
        )
        Column {
            Text(
                text = "Traffic Monitoring",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Android validation · M1C",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusCard(state: ValidationUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Current network", style = MaterialTheme.typography.labelLarge)
            Text(
                state.currentNetwork,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            val counter = state.latestCounter
            if (counter != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    Metric("Downloaded", formatBytes(counter.rxBytes))
                    Metric("Uploaded", formatBytes(counter.txBytes))
                }
                MutedText("Device totals since boot · ${counter.bootGeneration}")
            }

            HorizontalDivider()

            Text("Background capture", style = MaterialTheme.typography.labelLarge)
            Text(
                if (state.backgroundRegistered) "Armed" else "Not armed",
                color = if (state.backgroundRegistered) SignalCyan else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
            MutedText(
                "PendingIntent wakes: ${state.backgroundWakeCount} · recent stored events: ${state.pendingIntentEventCount}"
            )
            state.lastBackgroundWakeAtMs?.let { wakeAt ->
                MutedText(
                    "Last wake ${formatTime(wakeAt)}${state.lastBackgroundWakeNetworkHandle?.let { " · network $it" }.orEmpty()}"
                )
            }
            state.backgroundRegisteredAtMs?.let { registeredAt ->
                MutedText("Registration v${state.backgroundRegistrationVersion} · armed ${formatTime(registeredAt)}")
            }
            state.backgroundError?.let { Text("Registration error: $it", color = MaterialTheme.colorScheme.error) }

            MutedText("In-process callback events in recent evidence: ${state.callbackEventCount}")

            val runText = state.runId?.let { "Run ${it.take(8)}…" } ?: "Preparing validation run…"
            MutedText(runText)
            state.runStartedAtMs?.let { MutedText("Started ${formatTime(it)}") }

            if (state.isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text("  Saving locally…", style = MaterialTheme.typography.bodySmall)
                }
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.error?.let {
                Text("Error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        subtitle?.let { MutedText(it) }
    }
}

@Composable
private fun MutedText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MarkerButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled
    ) {
        Text(text)
    }
}

@Composable
private fun AttributionRow(interval: AttributionIntervalEntity) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val bytes = if (interval.rxBytes != null || interval.txBytes != null) {
            "Downloaded ${formatBytes(interval.rxBytes)} · Uploaded ${formatBytes(interval.txBytes)}"
        } else {
            "bytes discarded"
        }
        Text(
            "${formatTime(interval.endedAtMs)} · ${interval.confidence}",
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
        MutedText("${interval.networkDisplayName ?: interval.transport} · $bytes · ${interval.reason}")
    }
}

@Composable
private fun EventRow(event: NetworkEventEntity) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "${formatTime(event.receivedAtWallClockMs)} · ${event.transportSet}",
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
        val network = event.ssid ?: if (event.transportSet.contains("wifi")) "Wi-Fi name unavailable" else event.transportSet
        MutedText("$network · ${event.source}/${event.kind}")
    }
}

@Composable
private fun MarkerRow(marker: ManualTestMarkerEntity) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "${formatTime(marker.timestampWallClockMs)} · ${marker.label}",
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
        MutedText(marker.currentObservedNetwork ?: "Network unavailable")
    }
}

@Composable
private fun CustomMarkerDialog(onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var label by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom marker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("What are you testing?") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(label.trim(), notes.trim().ifBlank { null }) },
                enabled = label.isNotBlank()
            ) { Text("Save marker") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatTime(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

private fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "n/a"
    if (bytes < 1_000L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1_000.0 && unitIndex < units.lastIndex) {
        value /= 1_000.0
        unitIndex++
    }
    return String.format(Locale.US, "%.2f %s", value, units[unitIndex])
}
