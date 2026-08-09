package com.daniele21.trafficmonitoring.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniele21.trafficmonitoring.R
import com.daniele21.trafficmonitoring.usage.UsageNetworkTotal
import com.daniele21.trafficmonitoring.usage.UsageTrendPoint
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class AdaptiveProductSection { OVERVIEW, NETWORKS, EVIDENCE }

@Composable
fun AdaptiveProductScreen(
    state: ProductUiState,
    onSelectTimeframe: (ProductTimeframe) -> Unit,
    onSelectCustomRange: (startMs: Long, endExclusiveMs: Long) -> Unit,
    onRefresh: () -> Unit,
    onRequestWifiIdentity: () -> Unit,
    onOpenMonitor: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(AdaptiveProductSection.OVERVIEW) }
    var customRangeOpen by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AdaptiveHeader(
                state = state,
                onRefresh = onRefresh,
                onOpenMonitor = onOpenMonitor
            )

            PrimarySectionSelector(
                section = section,
                onSelect = { section = it }
            )

            AdaptiveTimeframeSelector(
                selected = state.timeframe,
                onSelect = { timeframe ->
                    if (timeframe == ProductTimeframe.CUSTOM) {
                        customRangeOpen = true
                    } else {
                        onSelectTimeframe(timeframe)
                    }
                }
            )

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (section) {
                AdaptiveProductSection.OVERVIEW -> AdaptiveOverviewContent(
                    state = state,
                    onRequestWifiIdentity = onRequestWifiIdentity,
                    onOpenEvidence = { section = AdaptiveProductSection.EVIDENCE }
                )

                AdaptiveProductSection.NETWORKS -> AdaptiveNetworksContent(state)

                AdaptiveProductSection.EVIDENCE -> Column(modifier = Modifier.fillMaxSize()) {
                    TextButton(
                        onClick = { section = AdaptiveProductSection.OVERVIEW },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Text("← Back to overview")
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        EvidenceContent(state)
                    }
                }
            }
        }
    }

    if (customRangeOpen) {
        AdaptiveCustomRangeDialog(
            onDismiss = { customRangeOpen = false },
            onApply = { startMs, endMs ->
                onSelectCustomRange(startMs, endMs)
                customRangeOpen = false
            }
        )
    }
}

@Composable
private fun AdaptiveHeader(
    state: ProductUiState,
    onRefresh: () -> Unit,
    onOpenMonitor: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 430.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_brand_shield),
                    contentDescription = "Traffic Monitoring",
                    modifier = Modifier.size(if (compact) 40.dp else 44.dp)
                )
                Text(
                    text = "Traffic Monitoring",
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (compact) {
                    IconButton(onClick = onRefresh) {
                        Text("↻", fontSize = 24.sp)
                    }
                    IconButton(onClick = onOpenMonitor) {
                        Text("⋮", fontSize = 26.sp)
                    }
                } else {
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                    TextButton(onClick = onOpenMonitor) { Text("Monitor") }
                }
            }
            Text(
                text = "Know your network usage.",
                modifier = Modifier.padding(start = if (compact) 50.dp else 54.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    state.error?.let { error ->
        Text(
            text = "Couldn’t refresh usage: $error",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PrimarySectionSelector(
    section: AdaptiveProductSection,
    onSelect: (AdaptiveProductSection) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = section == AdaptiveProductSection.OVERVIEW,
            onClick = { onSelect(AdaptiveProductSection.OVERVIEW) },
            label = { Text("Overview") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = section == AdaptiveProductSection.NETWORKS,
            onClick = { onSelect(AdaptiveProductSection.NETWORKS) },
            label = { Text("Networks") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AdaptiveTimeframeSelector(
    selected: ProductTimeframe,
    onSelect: (ProductTimeframe) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(ProductTimeframe.TODAY, ProductTimeframe.DAYS_7, ProductTimeframe.DAYS_30).forEach { timeframe ->
                TimeframeChip(
                    timeframe = timeframe,
                    selected = selected == timeframe,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(ProductTimeframe.MONTH, ProductTimeframe.CUSTOM).forEach { timeframe ->
                TimeframeChip(
                    timeframe = timeframe,
                    selected = selected == timeframe,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TimeframeChip(
    timeframe: ProductTimeframe,
    selected: Boolean,
    onSelect: (ProductTimeframe) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = { onSelect(timeframe) },
        label = {
            Text(
                timeframe.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier
    )
}

@Composable
private fun AdaptiveOverviewContent(
    state: ProductUiState,
    onRequestWifiIdentity: () -> Unit,
    onOpenEvidence: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 28.dp)
    ) {
        item { AdaptiveCurrentNetworkCard(state, onRequestWifiIdentity) }
        item { AdaptiveTotalUsageCard(state) }
        item { AdaptiveMetricCards(state) }
        item {
            EvidenceOverviewCard(
                summary = state.evidence,
                onOpenEvidence = onOpenEvidence
            )
        }
        item { AdaptiveTrendCard(state.trend) }
        item {
            AdaptiveSectionCard(title = "Usage by network") {
                if (state.networks.isEmpty()) {
                    AdaptiveEmptyUsageText()
                } else {
                    state.networks.take(3).forEachIndexed { index, network ->
                        AdaptiveNetworkRow(network, state.totalBytes)
                        if (index < minOf(2, state.networks.lastIndex)) HorizontalDivider()
                    }
                }
            }
        }
        if (state.unattributedBytes > 0L) {
            item {
                Text(
                    "${adaptiveFormatBytes(state.unattributedBytes)} couldn’t be assigned confidently to a network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AdaptiveCurrentNetworkCard(
    state: ProductUiState,
    onRequestWifiIdentity: () -> Unit
) {
    AdaptiveSectionCard(title = "Current network") {
        Text(
            state.currentNetwork,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (state.monitoringHealthy) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        shape = CircleShape
                    )
            )
            Text(
                if (state.monitoringHealthy) "  Monitoring" else "  Monitoring needs attention",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (state.wifiIdentityStatus) {
            "permission_required" -> {
                Text(
                    "Android treats the connected Wi-Fi name as location-sensitive information. Allow precise location access so Traffic Monitoring can distinguish this Wi-Fi from your other networks. Physical coordinates are not collected or stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onRequestWifiIdentity) {
                    Text("Show Wi-Fi name")
                }
            }

            "location_disabled" -> {
                Text(
                    "Wi-Fi identity access is allowed, but Android requires Location to be enabled before it exposes the connected Wi-Fi name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onRequestWifiIdentity) {
                    Text("Open location settings")
                }
            }

            "unavailable" -> Text(
                "The Wi-Fi name is still unavailable for this connection. Usage continues to be measured conservatively.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdaptiveTotalUsageCard(state: ProductUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                adaptiveTimeframeLabel(state).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                adaptiveFormatBytes(state.totalBytes),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "Total used",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun AdaptiveMetricCards(state: ProductUiState) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 330.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdaptiveMetricCard("Downloaded", adaptiveFormatBytes(state.downloadedBytes), Modifier.fillMaxWidth())
                AdaptiveMetricCard("Uploaded", adaptiveFormatBytes(state.uploadedBytes), Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdaptiveMetricCard("Downloaded", adaptiveFormatBytes(state.downloadedBytes), Modifier.weight(1f))
                AdaptiveMetricCard("Uploaded", adaptiveFormatBytes(state.uploadedBytes), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AdaptiveMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdaptiveNetworksContent(state: ProductUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 28.dp)
    ) {
        item {
            Text(
                "Usage by network",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${adaptiveTimeframeLabel(state)} · ${adaptiveFormatBytes(state.totalBytes)} total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.networks.isEmpty()) {
            item { AdaptiveSectionCard(title = "No usage yet") { AdaptiveEmptyUsageText() } }
        } else {
            items(state.networks, key = { it.identity }) { network ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    AdaptiveNetworkRow(
                        network = network,
                        totalBytes = state.totalBytes,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdaptiveTrendCard(points: List<UsageTrendPoint>) {
    val peak = points.maxOfOrNull { it.totalBytes } ?: 0L
    AdaptiveSectionCard(title = "Usage trend") {
        Text(
            if (peak > 0L) "Peak ${adaptiveFormatBytes(peak)}" else "No measured usage yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AdaptiveUsageTrendChart(points)
    }
}

@Composable
private fun AdaptiveUsageTrendChart(points: List<UsageTrendPoint>) {
    if (points.size < 2 || points.all { it.totalBytes == 0L }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp),
            contentAlignment = Alignment.Center
        ) {
            AdaptiveEmptyUsageText()
        }
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val peakColor = MaterialTheme.colorScheme.tertiary
    val maxValue = points.maxOf { it.totalBytes }.coerceAtLeast(1L).toFloat()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .padding(top = 10.dp)
    ) {
        val xStep = if (points.size <= 1) 0f else size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = index * xStep
            val y = size.height - (point.totalBytes.toFloat() / maxValue) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        val peakIndex = points.indices.maxByOrNull { points[it].totalBytes } ?: 0
        val peakPoint = points[peakIndex]
        drawCircle(
            color = peakColor,
            radius = 4.dp.toPx(),
            center = Offset(
                x = peakIndex * xStep,
                y = size.height - (peakPoint.totalBytes.toFloat() / maxValue) * size.height
            )
        )
    }
}

@Composable
private fun AdaptiveSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}

@Composable
private fun AdaptiveNetworkRow(
    network: UsageNetworkTotal,
    totalBytes: Long,
    modifier: Modifier = Modifier.padding(vertical = 10.dp)
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                network.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val percentage = if (totalBytes > 0L) (network.totalBytes * 100.0 / totalBytes) else 0.0
            Text(
                String.format(Locale.US, "%.0f%% · %s", percentage, adaptiveFriendlyTransport(network.transport)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            adaptiveFormatBytes(network.totalBytes),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdaptiveCustomRangeDialog(
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    val state = rememberDateRangePickerState()
    val valid = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val startUtc = state.selectedStartDateMillis ?: return@Button
                    val endUtc = state.selectedEndDateMillis ?: return@Button
                    val startDate = Instant.ofEpochMilli(startUtc).atZone(ZoneOffset.UTC).toLocalDate()
                    val endDate = Instant.ofEpochMilli(endUtc).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
                    val zone = ZoneId.systemDefault()
                    onApply(
                        startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                        endDate.atStartOfDay(zone).toInstant().toEpochMilli()
                    )
                },
                enabled = valid
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.height(460.dp),
            showModeToggle = false
        )
    }
}

@Composable
private fun AdaptiveEmptyUsageText() {
    Text(
        "Usage will appear here as Traffic Monitoring collects reliable network evidence.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun adaptiveTimeframeLabel(state: ProductUiState): String {
    if (state.timeframe != ProductTimeframe.CUSTOM) return state.timeframe.label
    val start = state.customStartMs ?: return "Custom"
    val endExclusive = state.customEndExclusiveMs ?: return "Custom"
    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val zone = ZoneId.systemDefault()
    val startLabel = Instant.ofEpochMilli(start).atZone(zone).toLocalDate().format(formatter)
    val endLabel = Instant.ofEpochMilli(endExclusive - 1).atZone(zone).toLocalDate().format(formatter)
    return "$startLabel – $endLabel"
}

private fun adaptiveFriendlyTransport(value: String): String = when {
    value.contains("wifi") -> "Wi-Fi"
    value.contains("cellular") -> "Mobile"
    value.contains("ethernet") -> "Ethernet"
    value.contains("vpn") -> "VPN"
    value.contains("unknown") -> "Network"
    else -> value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

private fun adaptiveFormatBytes(bytes: Long): String {
    if (bytes < 1_000L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1_000.0 && unitIndex < units.lastIndex) {
        value /= 1_000.0
        unitIndex++
    }
    val decimals = if (value >= 100.0) 0 else if (value >= 10.0) 1 else 2
    return String.format(Locale.US, "%.${decimals}f %s", value, units[unitIndex])
}
