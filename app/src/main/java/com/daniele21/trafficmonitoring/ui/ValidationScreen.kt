package com.daniele21.trafficmonitoring.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniele21.trafficmonitoring.data.ManualTestMarkerEntity
import com.daniele21.trafficmonitoring.data.NetworkEventEntity
import java.time.Instant

@Composable
fun ValidationScreen(
    state: ValidationUiState,
    onRefreshNetwork: () -> Unit,
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Traffic Monitoring",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Android validation · M1A",
                style = MaterialTheme.typography.bodyMedium
            )

            StatusCard(state)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRefreshNetwork, enabled = !state.isLoading) {
                    Text("Refresh network")
                }
                Button(onClick = onExport, enabled = !state.isLoading && state.runId != null) {
                    Text("Export validation run")
                }
            }

            Text("Manual test markers", style = MaterialTheme.typography.titleMedium)
            Text(
                "Use these immediately before or after a deliberate test action. They become human ground truth in the exported ZIP.",
                style = MaterialTheme.typography.bodySmall
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
            OutlinedButton(onClick = { customMarkerOpen = true }, enabled = !state.isLoading) {
                Text("Add custom marker")
            }

            HorizontalDivider()

            Text("Recent network observations", style = MaterialTheme.typography.titleMedium)
            if (state.recentEvents.isEmpty()) {
                Text("No observations yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                state.recentEvents.take(12).forEach { EventRow(it) }
            }

            HorizontalDivider()

            Text("Recent markers", style = MaterialTheme.typography.titleMedium)
            if (state.recentMarkers.isEmpty()) {
                Text("No markers yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                state.recentMarkers.take(12).forEach { MarkerRow(it) }
            }

            HorizontalDivider()

            Text("Validation run", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onStartNewRun, enabled = !state.isLoading) {
                    Text("Start new run")
                }
                TextButton(onClick = { clearConfirmationOpen = true }, enabled = !state.isLoading) {
                    Text("Clear all local data")
                }
            }

            Text(
                "M1A records snapshots and tester markers only. Traffic counters and background network callbacks are intentionally added in the next milestones.",
                style = MaterialTheme.typography.bodySmall
            )

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
            text = { Text("This permanently deletes all local runs, events and markers on this device.") },
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
private fun StatusCard(state: ValidationUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Current network", style = MaterialTheme.typography.labelLarge)
            Text(
                state.currentNetwork,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )

            val runText = state.runId?.let { "Run ${it.take(8)}…" } ?: "Preparing validation run…"
            Text(runText, style = MaterialTheme.typography.bodySmall)
            state.runStartedAtMs?.let {
                Text("Started ${formatTime(it)}", style = MaterialTheme.typography.bodySmall)
            }

            if (state.isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp))
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
private fun EventRow(event: NetworkEventEntity) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "${formatTime(event.receivedAtWallClockMs)} · ${event.transportSet}",
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
        val network = event.ssid ?: if (event.transportSet.contains("wifi")) "Wi-Fi name unavailable" else event.transportSet
        Text(
            "$network · ${event.source}/${event.kind}",
            style = MaterialTheme.typography.bodySmall
        )
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
        Text(
            marker.currentObservedNetwork ?: "Network unavailable",
            style = MaterialTheme.typography.bodySmall
        )
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
