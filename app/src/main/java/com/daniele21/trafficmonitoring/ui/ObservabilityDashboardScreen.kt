package com.daniele21.trafficmonitoring.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniele21.trafficmonitoring.R
import com.daniele21.trafficmonitoring.evidence.EvidenceCoverageSummary
import com.daniele21.trafficmonitoring.evidence.MeasurementHealthState
import com.daniele21.trafficmonitoring.usage.UsageBucketAllocator
import com.daniele21.trafficmonitoring.usage.UsageNetworkTotal
import com.daniele21.trafficmonitoring.usage.UsageTrendPoint
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt

private enum class DashboardSection { OVERVIEW, NETWORKS, EVIDENCE }

/**
 * Consumer hierarchy follows the evidence-first mission:
 * what happened -> when -> where -> how trustworthy -> raw Monitor only when requested.
 */
@Composable
fun ObservabilityDashboardScreen(
    state: ProductUiState,
    onSelectTimeframe: (ProductTimeframe) -> Unit,
    onSelectCustomRange: (startMs: Long, endExclusiveMs: Long) -> Unit,
    onRefresh: () -> Unit,
    onRequestWifiIdentity: () -> Unit,
    onOpenMonitor: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(DashboardSection.OVERVIEW) }
    var customRangeOpen by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            DashboardHeader(onRefresh = onRefresh, onOpenMonitor = onOpenMonitor)
            PrimaryNavigation(section = section, onSelect = { section = it })
            TimeframeBar(
                selected = state.timeframe,
                onSelect = { timeframe ->
                    if (timeframe == ProductTimeframe.CUSTOM) customRangeOpen = true
                    else onSelectTimeframe(timeframe)
                }
            )
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            when (section) {
                DashboardSection.OVERVIEW -> DashboardOverview(
                    state = state,
                    onOpenNetworks = { section = DashboardSection.NETWORKS },
                    onOpenEvidence = { section = DashboardSection.EVIDENCE }
                )
                DashboardSection.NETWORKS -> DashboardNetworks(state, onRequestWifiIdentity)
                DashboardSection.EVIDENCE -> DashboardEvidence(state)
            }
        }
    }

    if (customRangeOpen) {
        DashboardRangeDialog(
            onDismiss = { customRangeOpen = false },
            onApply = { startMs, endMs ->
                onSelectCustomRange(startMs, endMs)
                customRangeOpen = false
            }
        )
    }
}

@Composable
private fun DashboardHeader(onRefresh: () -> Unit, onOpenMonitor: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_shield),
            contentDescription = "Traffic Monitoring",
            modifier = Modifier.size(38.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                "Traffic Monitoring",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Evidence-first network observability",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HeaderAction("↻", onRefresh)
        HeaderAction("⋮", onOpenMonitor)
    }
}

@Composable
private fun HeaderAction(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            fontSize = 26.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = if (symbol == "⋮") 5.dp else 0.dp)
        )
    }
}

@Composable
private fun PrimaryNavigation(section: DashboardSection, onSelect: (DashboardSection) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DashboardTab(
            label = "Overview",
            selected = section == DashboardSection.OVERVIEW,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(DashboardSection.OVERVIEW) }
        )
        DashboardTab(
            label = "Networks",
            selected = section == DashboardSection.NETWORKS,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(DashboardSection.NETWORKS) }
        )
    }
}

@Composable
private fun DashboardTab(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val border = if (selected) background else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .height(42.dp)
            .background(background, shape)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimeframeBar(selected: ProductTimeframe, onSelect: (ProductTimeframe) -> Unit) {
    val options = listOf(
        ProductTimeframe.TODAY to "Today",
        ProductTimeframe.DAYS_7 to "7D",
        ProductTimeframe.DAYS_30 to "30D",
        ProductTimeframe.MONTH to "Month",
        ProductTimeframe.CUSTOM to "Custom"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (timeframe, label) ->
            val active = timeframe == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(timeframe) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DashboardOverview(
    state: ProductUiState,
    onOpenNetworks: () -> Unit,
    onOpenEvidence: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { LiveNetworkStrip(state) }
        item { UsageHero(state) }
        item { AnalyticsHighlights(state) }
        item { NetworkMixCard(state, onOpenNetworks) }
        item { EvidenceTrustCard(state.evidence, onOpenEvidence) }
        if (state.error != null) {
            item {
                Text(
                    "Couldn’t refresh usage: ${state.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun LiveNetworkStrip(state: ProductUiState) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(
                    if (state.monitoringHealthy) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    CircleShape
                )
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                state.currentNetwork,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                when (state.wifiIdentityStatus) {
                    "permission_required" -> "Wi-Fi name access is optional"
                    "location_disabled" -> "Exact Wi-Fi name paused while Location is off"
                    "unavailable" -> "Wi-Fi connected · exact name unavailable"
                    else -> if (state.monitoringHealthy) "Monitoring live" else "Measurement needs attention"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "LIVE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun UsageHero(state: ProductUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        timeframeTitle(state).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.70f)
                    )
                    Text(
                        formatDashboardBytes(state.totalBytes),
                        fontSize = 42.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                    Text(
                        "Network usage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.70f)
                    )
                }
                EvidencePill(state.evidence)
            }

            DashboardTrendChart(
                points = state.trend,
                lineColor = MaterialTheme.colorScheme.onPrimaryContainer,
                accentColor = MaterialTheme.colorScheme.tertiary
            )

            DirectionSplit(
                downloaded = state.downloadedBytes,
                uploaded = state.uploadedBytes,
                foreground = MaterialTheme.colorScheme.onPrimaryContainer,
                secondary = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun EvidencePill(summary: EvidenceCoverageSummary?) {
    val label = summary?.evidenceCoveragePercent?.let { "${it.roundToInt()}% evidence" } ?: "Collecting evidence"
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun DashboardTrendChart(points: List<UsageTrendPoint>, lineColor: Color, accentColor: Color) {
    val usable = points.size >= 2 && points.any { it.totalBytes > 0L }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!usable) {
            Text(
                "Trend appears as evidence is collected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.62f)
            )
        } else {
            val maxValue = points.maxOf { it.totalBytes }.coerceAtLeast(1L).toFloat()
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baselineY = size.height - 6.dp.toPx()
                drawLine(
                    color = lineColor.copy(alpha = 0.16f),
                    start = Offset(0f, baselineY),
                    end = Offset(size.width, baselineY),
                    strokeWidth = 1.dp.toPx()
                )
                val xStep = size.width / (points.size - 1)
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = xStep * index
                    val y = baselineY - (point.totalBytes.toFloat() / maxValue) * (size.height - 18.dp.toPx())
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                val peakIndex = points.indices.maxByOrNull { points[it].totalBytes } ?: 0
                val peak = points[peakIndex]
                drawCircle(
                    color = accentColor,
                    radius = 4.dp.toPx(),
                    center = Offset(
                        xStep * peakIndex,
                        baselineY - (peak.totalBytes.toFloat() / maxValue) * (size.height - 18.dp.toPx())
                    )
                )
            }
        }
    }
}

@Composable
private fun DirectionSplit(downloaded: Long, uploaded: Long, foreground: Color, secondary: Color) {
    val total = downloaded + uploaded
    val downloadShare = if (total > 0L) downloaded.toFloat() / total else 0f
    Row(modifier = Modifier.fillMaxWidth()) {
        DirectionMetric("↓", "Downloaded", formatDashboardBytes(downloaded), Modifier.weight(1f), foreground)
        DirectionMetric("↑", "Uploaded", formatDashboardBytes(uploaded), Modifier.weight(1f), foreground)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(foreground.copy(alpha = 0.12f), RoundedCornerShape(50))
    ) {
        if (downloadShare > 0f) {
            Box(
                modifier = Modifier
                    .weight(downloadShare.coerceAtLeast(0.001f))
                    .fillMaxSize()
                    .background(foreground.copy(alpha = 0.82f), RoundedCornerShape(50))
            )
        }
        if (downloadShare < 1f) {
            Box(
                modifier = Modifier
                    .weight((1f - downloadShare).coerceAtLeast(0.001f))
                    .fillMaxSize()
                    .background(secondary, RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun DirectionMetric(symbol: String, label: String, value: String, modifier: Modifier, color: Color) {
    Column(modifier = modifier) {
        Text(
            "$symbol $value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.68f))
    }
}

@Composable
private fun AnalyticsHighlights(state: ProductUiState) {
    val peak = state.trend.maxByOrNull { it.totalBytes }
    val topNetwork = state.networks
        .filter { it.identity != UsageBucketAllocator.UNATTRIBUTED_ID }
        .maxByOrNull { it.totalBytes }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AnalyticsTile(
            eyebrow = "PEAK",
            value = peak?.let { formatDashboardBytes(it.totalBytes) } ?: "—",
            detail = peak?.let { formatHour(it.startMs) } ?: "No peak yet",
            modifier = Modifier.weight(1f)
        )
        AnalyticsTile(
            eyebrow = "TOP NETWORK",
            value = topNetwork?.displayName ?: "—",
            detail = topNetwork?.let { formatDashboardBytes(it.totalBytes) } ?: "No attributed usage",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AnalyticsTile(eyebrow: String, value: String, detail: String, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (detail.isNotBlank()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NetworkMixCard(state: ProductUiState, onOpenNetworks: () -> Unit) {
    DashboardCard(onClick = onOpenNetworks) {
        SectionHeading("Network mix", "Where measured traffic was carried")
        if (state.networks.isEmpty()) {
            EmptyAnalytics("No network usage in this timeframe yet.")
        } else {
            state.networks.sortedByDescending { it.totalBytes }.take(3).forEach { network ->
                NetworkShareRow(network, state.totalBytes)
            }
            Text("View all networks →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EvidenceTrustCard(summary: EvidenceCoverageSummary?, onOpenEvidence: () -> Unit) {
    DashboardCard(onClick = onOpenEvidence) {
        SectionHeading("Evidence", "How strongly the usage can be explained")
        if (summary == null) {
            EmptyAnalytics("Collecting enough observations to evaluate this timeframe.")
        } else {
            val coverage = summary.evidenceCoveragePercent
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        coverage?.let { "${it.roundToInt()}%" } ?: "—",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Evidence coverage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HealthChip(summary.healthState)
            }
            EvidenceCoverageBar(coverage)
            Text(evidenceOneLine(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Understand the evidence →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DashboardNetworks(state: ProductUiState, onRequestWifiIdentity: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeading("Network breakdown", "${timeframeTitle(state)} · ${formatDashboardBytes(state.totalBytes)} total") }
        if (state.networks.isEmpty()) {
            item { DashboardCard { EmptyAnalytics("No network usage in this timeframe yet.") } }
        } else {
            items(state.networks.sortedByDescending { it.totalBytes }, key = { it.identity }) { network ->
                NetworkDetailCard(network, state.totalBytes)
            }
        }
        if (state.wifiIdentityStatus in setOf("opt_in_required", "permission_required", "location_disabled")) {
            item { OptionalWifiIdentityCard(state.wifiIdentityStatus, onRequestWifiIdentity) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun NetworkDetailCard(network: UsageNetworkTotal, totalBytes: Long) {
    DashboardCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    network.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(friendlyTransport(network.transport), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatDashboardBytes(network.totalBytes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        ShareBar(if (totalBytes > 0L) network.totalBytes.toFloat() / totalBytes else 0f)
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "↓ ${formatDashboardBytes(network.rxBytes)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("↑ ${formatDashboardBytes(network.txBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OptionalWifiIdentityCard(status: String, onRequestWifiIdentity: () -> Unit) {
    DashboardCard {
        Text("Exact Wi-Fi names", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            when (status) {
                "location_disabled" -> "Monitoring works without the SSID. Android Location is currently off; turn it on only if you explicitly want the exact Wi-Fi name."
                "permission_required" -> "You opted into exact Wi-Fi names, but Android still needs precise-location permission for the SSID field. Core monitoring does not depend on it."
                else -> "Optional. Android classifies the connected SSID as location-sensitive. Leave this off to monitor usage without touching location-sensitive Wi-Fi identity fields."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onRequestWifiIdentity, contentPadding = PaddingValues(0.dp)) {
            Text(if (status == "location_disabled") "Open Location settings" else "Enable exact names")
        }
    }
}

@Composable
private fun DashboardEvidence(state: ProductUiState) {
    val summary = state.evidence
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionHeading("Evidence behind the number", "Usage is useful only when you can see how strongly it is supported.") }
        if (summary == null) {
            item { DashboardCard { EmptyAnalytics("Not enough evidence yet for this timeframe.") } }
        } else {
            item {
                DashboardCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                summary.evidenceCoveragePercent?.let { "${it.roundToInt()}%" } ?: "—",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "of accountable usage attributed to a network",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HealthChip(summary.healthState)
                    }
                    EvidenceCoverageBar(summary.evidenceCoveragePercent)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnalyticsTile("ATTRIBUTED", formatDashboardBytes(summary.attributedBytes), "", Modifier.weight(1f))
                    AnalyticsTile("UNATTRIBUTED", formatDashboardBytes(summary.unattributedBytes), "", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnalyticsTile("CONTINUITY GAPS", summary.continuityGapCount.toString(), "", Modifier.weight(1f))
                    AnalyticsTile("DISCARDED", summary.discardedIntervalCount.toString(), "", Modifier.weight(1f))
                }
            }
            if (summary.healthReasons.isNotEmpty()) {
                item {
                    DashboardCard {
                        SectionHeading("Why health is ${healthLabel(summary.healthState).lowercase()}", "Transparent by design")
                        summary.healthReasons.forEach { reason ->
                            Text("• ${friendlyEvidenceReason(reason)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(
                    "Traffic Monitoring never reallocates uncertain or discarded bytes just to make totals look complete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DashboardCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val column: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
    if (onClick == null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            content = column
        )
    } else {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            content = column
        )
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NetworkShareRow(network: UsageNetworkTotal, totalBytes: Long) {
    val share = if (totalBytes > 0L) network.totalBytes.toFloat() / totalBytes else 0f
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    network.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${(share * 100).roundToInt()}% · ${friendlyTransport(network.transport)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(formatDashboardBytes(network.totalBytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        ShareBar(share)
    }
}

@Composable
private fun ShareBar(share: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.13f), RoundedCornerShape(50))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(share.coerceIn(0f, 1f))
                .height(5.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
        )
    }
}

@Composable
private fun EvidenceCoverageBar(percent: Double?) {
    ShareBar(((percent ?: 0.0) / 100.0).toFloat())
}

@Composable
private fun HealthChip(state: MeasurementHealthState) {
    val color = when (state) {
        MeasurementHealthState.GOOD -> MaterialTheme.colorScheme.tertiary
        MeasurementHealthState.LIMITED -> MaterialTheme.colorScheme.secondary
        MeasurementHealthState.DEGRADED -> MaterialTheme.colorScheme.error
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text("  ${healthLabel(state)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun EmptyAnalytics(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardRangeDialog(onDismiss: () -> Unit, onApply: (Long, Long) -> Unit) {
    val state = rememberDateRangePickerState()
    val valid = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = valid,
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
                }
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(state = state)
    }
}

private fun timeframeTitle(state: ProductUiState): String = when (state.timeframe) {
    ProductTimeframe.TODAY -> "Today"
    ProductTimeframe.DAYS_7 -> "Last 7 days"
    ProductTimeframe.DAYS_30 -> "Last 30 days"
    ProductTimeframe.MONTH -> "This month"
    ProductTimeframe.CUSTOM -> "Custom range"
}

private fun formatDashboardBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L).toDouble()
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        safe >= gb -> String.format(Locale.US, "%.2f GB", safe / gb)
        safe >= mb -> String.format(Locale.US, "%.2f MB", safe / mb)
        safe >= kb -> String.format(Locale.US, "%.1f KB", safe / kb)
        else -> "${safe.toLong()} B"
    }
}

private fun formatHour(epochMs: Long): String = Instant.ofEpochMilli(epochMs)
    .atZone(ZoneId.systemDefault())
    .toLocalTime()
    .let { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) }

private fun friendlyTransport(value: String): String = when {
    value.contains("wifi", ignoreCase = true) -> "Wi-Fi"
    value.contains("cellular", ignoreCase = true) -> "Cellular"
    value.contains("ethernet", ignoreCase = true) -> "Ethernet"
    value.contains("vpn", ignoreCase = true) -> "VPN"
    value.contains("unattributed", ignoreCase = true) -> "Unattributed"
    else -> value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

private fun healthLabel(state: MeasurementHealthState): String = when (state) {
    MeasurementHealthState.GOOD -> "Good"
    MeasurementHealthState.LIMITED -> "Limited"
    MeasurementHealthState.DEGRADED -> "Degraded"
}

private fun evidenceOneLine(summary: EvidenceCoverageSummary): String = when {
    summary.evidenceCoveragePercent == null -> "Not enough accountable usage to calculate coverage yet."
    summary.unattributedBytes > 0L -> "${formatDashboardBytes(summary.unattributedBytes)} remains explicitly unattributed."
    summary.discardedIntervalCount > 0 -> "Some continuity evidence was discarded rather than guessed."
    else -> "Measured usage in this timeframe is fully attributable with the current evidence."
}

private fun friendlyEvidenceReason(reason: String): String = when (reason) {
    "not_enough_usage_evidence" -> "Not enough accountable usage yet"
    "evidence_coverage_below_80" -> "Less than 80% of accountable usage can be assigned to a network"
    "evidence_coverage_below_95" -> "Some valid usage remains unattributed"
    "continuity_gaps_detected" -> "One or more measurement continuity gaps were observed"
    "long_continuity_gap" -> "A continuity gap lasted at least 30 minutes"
    "multiple_discarded_intervals" -> "Multiple intervals were discarded because their evidence was not trustworthy"
    "discarded_evidence_detected" -> "At least one interval was discarded instead of being guessed"
    else -> reason.replace('_', ' ')
}
