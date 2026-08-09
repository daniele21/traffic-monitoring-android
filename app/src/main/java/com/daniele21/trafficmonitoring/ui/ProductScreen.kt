package com.daniele21.trafficmonitoring.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.daniele21.trafficmonitoring.R
import com.daniele21.trafficmonitoring.usage.UsageNetworkTotal
import com.daniele21.trafficmonitoring.usage.UsageTrendPoint
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ProductSection { OVERVIEW, NETWORKS, EVIDENCE }

@Composable
fun ProductScreen(
    state: ProductUiState,
    onSelectTimeframe: (ProductTimeframe) -> Unit,
    onSelectCustomRange: (startMs: Long, endExclusiveMs: Long) -> Unit,
    onRefresh: () -> Unit,
    onOpenMonitor: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(ProductSection.OVERVIEW) }
    var customRangeOpen by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ProductHeader(
                state = state,
                onRefresh = onRefresh,
                onOpenMonitor = onOpenMonitor
            )
            SectionSelector(section = section, onSelect = { section = it })
            TimeframeSelector(
                selected = state.timeframe,
                onSelect = { timeframe ->
                    if (timeframe == ProductTimeframe.CUSTOM) customRangeOpen = true else onSelectTimeframe(timeframe)
                }
            )
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (section) {
                ProductSection.OVERVIEW -> OverviewContent(
                    state = state,
                    onOpenEvidence = { section = ProductSection.EVIDENCE }
                )
                ProductSection.NETWORKS -> NetworksContent(state)
                ProductSection.EVIDENCE -> EvidenceContent(state)
            }
        }
    }

    if (customRangeOpen) {
        CustomRangeDialog(
            onDismiss = { customRangeOpen = false },
            onApply = { startMs, endMs ->
                onSelectCustomRange(startMs, endMs)
                customRangeOpen = false
            }
        )
    }
}

@Composable
private fun ProductHeader(
    state: ProductUiState,
    onRefresh: () -> Unit,
    onOpenMonitor: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_shield),
            contentDescription = "Traffic Monitoring",
            modifier = Modifier.size(44.dp)
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                "Traffic Monitoring",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Know your network usage — and the evidence behind it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onRefresh) { Text("Refresh") }
        TextButton(onClick = onOpenMonitor) { Text("Monitor") }
    }

    if (state.error != null) {
        Text(
            text = "Couldn’t refresh usage: ${state.error}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SectionSelector(section: ProductSection, onSelect: (ProductSection) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = section == ProductSection.OVERVIEW,
                onClick = { onSelect(ProductSection.OVERVIEW) },
                label = { Text("Overview") }
            )
        }
        item {
            FilterChip(
                selected = section == ProductSection.NETWORKS,
                onClick = { onSelect(ProductSection.NETWORKS) },
                label = { Text("Networks") }
            )
        }
        item {
            FilterChip(
                selected = section == ProductSection.EVIDENCE,
                onClick = { onSelect(ProductSection.EVIDENCE) },
                label = { Text("Evidence") }
            )
        }
    }
}

@Composable
private fun TimeframeSelector(selected: ProductTimeframe, onSelect: (ProductTimeframe) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ProductTimeframe.values().toList()) { timeframe ->
            FilterChip(
                selected = timeframe == selected,
                onClick = { onSelect(timeframe) },
                label = { Text(timeframe.label) }
            )
        }
    }
}

@Composable
private fun OverviewContent(
    state: ProductUiState,
    onOpenEvidence: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 28.dp)
    ) {
        item { CurrentNetworkCard(state) }
        item { TotalUsageCard(state) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "Downloaded",
                    value = formatBytes(state.downloadedBytes),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Uploaded",
                    value = formatBytes(state.uploadedBytes),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            EvidenceOverviewCard(
                summary = state.evidence,
                onOpenEvidence = onOpenEvidence
            )
        }
        item { TrendCard(state.trend) }
        item {
            SectionCard(title = "Usage by network") {
                if (state.networks.isEmpty()) {
                    EmptyUsageText()
                } else {
                    state.networks.take(3).forEachIndexed { index, network ->
                        NetworkRow(network, state.totalBytes)
                        if (index < minOf(2, state.networks.lastIndex)) HorizontalDivider()
                    }
                }
            }
        }
        if (state.unattributedBytes > 0L) {
            item {
                Text(
                    "${formatBytes(state.unattributedBytes)} couldn’t be assigned confidently to a network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NetworksContent(state: ProductUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 28.dp)
    ) {
        item {
            Text(
                "Usage by network",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${timeframeLabel(state)} · ${formatBytes(state.totalBytes)} total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.networks.isEmpty()) {
            item { SectionCard(title = "No usage yet") { EmptyUsageText() } }
        } else {
            items(state.networks, key = { it.identity }) { network ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    NetworkRow(
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
private fun CurrentNetworkCard(state: ProductUiState) {
    SectionCard(title = "Current network") {
        Text(
            state.currentNetwork,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
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
    }
}

@Composable
private fun TotalUsageCard(state: ProductUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                timeframeLabel(state).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                formatBytes(state.totalBytes),
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
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrendCard(points: List<UsageTrendPoint>) {
    val peak = points.maxOfOrNull { it.totalBytes } ?: 0L
    SectionCard(title = "Usage trend") {
        Text(
            if (peak > 0L) "Peak ${formatBytes(peak)}" else "No measured usage yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        UsageTrendChart(points)
    }
}

@Composable
private fun UsageTrendChart(points: List<UsageTrendPoint>) {
    if (points.size < 2 || points.all { it.totalBytes == 0L }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyUsageText()
        }
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val peakColor = MaterialTheme.colorScheme.tertiary
    val maxValue = points.maxOf { it.totalBytes }.coerceAtLeast(1L).toFloat()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(top = 12.dp)
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
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun NetworkRow(
    network: UsageNetworkTotal,
    totalBytes: Long,
    modifier: Modifier = Modifier.padding(vertical = 10.dp)
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(network.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            val percentage = if (totalBytes > 0L) (network.totalBytes * 100.0 / totalBytes) else 0.0
            Text(
                String.format(Locale.US, "%.0f%% · %s", percentage, friendlyTransport(network.transport)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatBytes(network.totalBytes),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
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
private fun EmptyUsageText() {
    Text(
        "Usage will appear here as Traffic Monitoring collects reliable network evidence.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun timeframeLabel(state: ProductUiState): String {
    if (state.timeframe != ProductTimeframe.CUSTOM) return state.timeframe.label
    val start = state.customStartMs ?: return "Custom"
    val endExclusive = state.customEndExclusiveMs ?: return "Custom"
    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val zone = ZoneId.systemDefault()
    val startLabel = Instant.ofEpochMilli(start).atZone(zone).toLocalDate().format(formatter)
    val endLabel = Instant.ofEpochMilli(endExclusive - 1).atZone(zone).toLocalDate().format(formatter)
    return "$startLabel – $endLabel"
}

private fun friendlyTransport(value: String): String = when {
    value.contains("wifi") -> "Wi-Fi"
    value.contains("cellular") -> "Mobile"
    value.contains("ethernet") -> "Ethernet"
    value.contains("vpn") -> "VPN"
    value.contains("unknown") -> "Network"
    else -> value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

private fun formatBytes(bytes: Long): String {
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
