package com.daniele21.trafficmonitoring.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TrafficMonitoringRoot(
    productState: ProductUiState,
    validationState: ValidationUiState,
    onSelectTimeframe: (ProductTimeframe) -> Unit,
    onSelectCustomRange: (Long, Long) -> Unit,
    onRefreshProduct: () -> Unit,
    onRequestWifiIdentity: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onArmBackground: () -> Unit,
    onAddMarker: (String, String, String?) -> Unit,
    onExport: () -> Unit,
    onStartNewRun: () -> Unit,
    onClearAll: () -> Unit
) {
    var monitorOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = monitorOpen) { monitorOpen = false }

    if (!monitorOpen) {
        AdaptiveProductScreen(
            state = productState,
            onSelectTimeframe = onSelectTimeframe,
            onSelectCustomRange = onSelectCustomRange,
            onRefresh = onRefreshProduct,
            onRequestWifiIdentity = onRequestWifiIdentity,
            onOpenMonitor = { monitorOpen = true }
        )
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { monitorOpen = false }) { Text("Back") }
                    Text(
                        "Monitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Advanced diagnostics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    ValidationScreen(
                        state = validationState,
                        onRefreshNetwork = onRefreshNetwork,
                        onArmBackground = onArmBackground,
                        onAddMarker = onAddMarker,
                        onExport = onExport,
                        onStartNewRun = onStartNewRun,
                        onClearAll = onClearAll
                    )
                }
            }
        }
    }
}
