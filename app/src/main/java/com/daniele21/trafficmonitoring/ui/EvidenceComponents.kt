package com.daniele21.trafficmonitoring.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniele21.trafficmonitoring.evidence.EvidenceCoverageSummary
import com.daniele21.trafficmonitoring.evidence.MeasurementHealthState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EvidenceOverviewCard(
    summary: EvidenceCoverageSummary?,
    onOpenEvidence: () -> Unit
) {
    Card(
        onClick = onOpenEvidence,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Evidence", style = MaterialTheme.typography.labelLarge)
            if (summary == null) {
                Text(
                    "Collecting enough information to evaluate this timeframe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            coverageLabel(summary),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Evidence coverage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HealthLabel(summary.healthState)
                }
                Text(
                    overviewExplanation(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "View evidence →",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun EvidenceContent(state: ProductUiState) {
    val summary = state.evidence
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 28.dp)
    ) {
        item {
            Text(
                "Evidence",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "How strongly the selected usage is supported by the observations collected on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (summary == null) {
            item {
                EvidenceSectionCard("Not enough evidence yet") {
                    Text(
                        "Traffic Monitoring is still collecting the information needed to evaluate this timeframe.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            item {
                EvidenceSectionCard("Evidence coverage") {
                    Text(
                        coverageLabel(summary),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (summary.evidenceCoveragePercent == null) {
                            "No accountable usage is available in this timeframe yet."
                        } else {
                            "Share of measured usage that can be assigned to a specific network."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                EvidenceSectionCard("Measurement health") {
                    HealthLabel(summary.healthState)
                    Text(
                        healthExplanation(summary),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (summary.healthReasons.isNotEmpty()) {
                        summary.healthReasons.forEach { reason ->
                            Text(
                                "• ${friendlyReason(reason)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                EvidenceSectionCard("Accounted usage") {
                    EvidenceMetricRow("Attributed", formatEvidenceBytes(summary.attributedBytes))
                    EvidenceMetricRow("Unattributed", formatEvidenceBytes(summary.unattributedBytes))
                    Text(
                        "Unattributed usage is measured traffic whose network owner cannot be supported strongly enough. It is never redistributed to a named network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                EvidenceSectionCard("Continuity") {
                    EvidenceMetricRow("Continuity gaps", summary.continuityGapCount.toString())
                    EvidenceMetricRow("Discarded intervals", summary.discardedIntervalCount.toString())
                    EvidenceMetricRow(
                        "Longest gap",
                        if (summary.longestGapMs > 0L) formatDuration(summary.longestGapMs) else "None observed"
                    )
                    Text(
                        "Discarded intervals never enter usage totals. They affect measurement health because their counter or continuity evidence cannot be trusted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                EvidenceSectionCard("How this is calculated") {
                    Text(
                        "Coverage v${summary.metricDefinitionVersion} = attributed usage ÷ (attributed + unattributed usage). Measurement health is evaluated separately from coverage so invalid evidence cannot be hidden by a high percentage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EvidenceSectionCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
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
private fun EvidenceMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HealthLabel(state: MeasurementHealthState) {
    val color = healthColor(state)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape)
        )
        Text(
            "  ${healthLabel(state)}",
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

@Composable
private fun healthColor(state: MeasurementHealthState): Color = when (state) {
    MeasurementHealthState.GOOD -> MaterialTheme.colorScheme.tertiary
    MeasurementHealthState.LIMITED -> MaterialTheme.colorScheme.secondary
    MeasurementHealthState.DEGRADED -> MaterialTheme.colorScheme.error
}

private fun coverageLabel(summary: EvidenceCoverageSummary): String =
    summary.evidenceCoveragePercent?.let { "${it.roundToInt()}%" } ?: "Not enough data"

private fun healthLabel(state: MeasurementHealthState): String = when (state) {
    MeasurementHealthState.GOOD -> "Good"
    MeasurementHealthState.LIMITED -> "Limited"
    MeasurementHealthState.DEGRADED -> "Degraded"
}

private fun overviewExplanation(summary: EvidenceCoverageSummary): String = when {
    summary.evidenceCoveragePercent == null -> "Not enough accountable usage yet to calculate coverage."
    summary.healthState == MeasurementHealthState.GOOD -> "Usage is well supported and no material continuity issue is visible."
    summary.healthState == MeasurementHealthState.LIMITED -> "Some usage or continuity evidence is incomplete. Open Evidence to see why."
    else -> "The selected timeframe contains material uncertainty or continuity issues."
}

private fun healthExplanation(summary: EvidenceCoverageSummary): String = when (summary.healthState) {
    MeasurementHealthState.GOOD -> "No material evidence-quality issue is visible for this timeframe."
    MeasurementHealthState.LIMITED -> "The result is usable with caveats. The reasons below explain what reduced confidence."
    MeasurementHealthState.DEGRADED -> "Material uncertainty or continuity loss is present. Treat per-network totals cautiously."
}

private fun friendlyReason(reason: String): String = when (reason) {
    "not_enough_usage_evidence" -> "Not enough accountable usage to calculate coverage"
    "evidence_coverage_below_95" -> "Some measured usage could not be attributed to a network"
    "evidence_coverage_below_80" -> "A substantial share of measured usage could not be attributed"
    "continuity_gaps_detected" -> "One or more measurement continuity gaps were detected"
    "long_continuity_gap" -> "At least one continuity gap lasted 30 minutes or more"
    "discarded_evidence_detected" -> "Some evidence was invalid and excluded from usage totals"
    "multiple_discarded_intervals" -> "Multiple invalid evidence intervals were excluded"
    else -> reason.replace('_', ' ')
}

private fun formatEvidenceBytes(bytes: Long): String {
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

private fun formatDuration(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}
