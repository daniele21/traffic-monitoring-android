package com.daniele21.trafficmonitoring.evidence

import kotlin.math.max
import kotlin.math.min

/**
 * Product-facing evidence semantics.
 *
 * Evidence Coverage answers how much accountable usage can be assigned to a specific network.
 * Measurement Health remains separate and explains invalid/continuity evidence. A lack of data is
 * never represented as 100% coverage.
 */
data class EvidenceIntervalInput(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val confidence: String,
    val reason: String
)

enum class MeasurementHealthState {
    GOOD,
    LIMITED,
    DEGRADED
}

data class EvidenceCoverageSummary(
    val startMs: Long,
    val endMs: Long,
    val attributedRxBytes: Long,
    val attributedTxBytes: Long,
    val unattributedRxBytes: Long,
    val unattributedTxBytes: Long,
    val discardedIntervalCount: Int,
    val continuityGapCount: Int,
    val longestGapMs: Long,
    val evidenceCoveragePercent: Double?,
    val healthState: MeasurementHealthState,
    val healthReasons: List<String>,
    val metricDefinitionVersion: Int = EvidenceSummaryCalculator.METRIC_DEFINITION_VERSION
) {
    val attributedBytes: Long get() = attributedRxBytes + attributedTxBytes
    val unattributedBytes: Long get() = unattributedRxBytes + unattributedTxBytes
    val accountedBytes: Long get() = attributedBytes + unattributedBytes
}

class EvidenceSummaryCalculator {
    fun calculate(
        startMs: Long,
        endMs: Long,
        totalRxBytes: Long,
        totalTxBytes: Long,
        unattributedRxBytes: Long,
        unattributedTxBytes: Long,
        intervals: List<EvidenceIntervalInput>
    ): EvidenceCoverageSummary {
        require(endMs > startMs) { "Evidence timeframe must have positive duration" }

        val safeTotalRx = totalRxBytes.coerceAtLeast(0L)
        val safeTotalTx = totalTxBytes.coerceAtLeast(0L)
        val safeUnattributedRx = unattributedRxBytes.coerceIn(0L, safeTotalRx)
        val safeUnattributedTx = unattributedTxBytes.coerceIn(0L, safeTotalTx)
        val attributedRx = safeTotalRx - safeUnattributedRx
        val attributedTx = safeTotalTx - safeUnattributedTx
        val accounted = safeTotalRx + safeTotalTx
        val attributed = attributedRx + attributedTx

        val overlapping = intervals.filter { it.endedAtMs > startMs && it.startedAtMs < endMs }
        val discarded = overlapping.filter { it.confidence == "discarded" }
        val continuityGaps = discarded.filter { it.reason in CONTINUITY_GAP_REASONS }
        val longestGapMs = continuityGaps.maxOfOrNull { interval ->
            val clippedStart = max(startMs, interval.startedAtMs)
            val clippedEnd = min(endMs, interval.endedAtMs)
            (clippedEnd - clippedStart).coerceAtLeast(0L)
        } ?: 0L

        val coverage = if (accounted > 0L) {
            attributed.toDouble() * 100.0 / accounted.toDouble()
        } else {
            null
        }

        val reasons = buildList {
            if (coverage == null) add("not_enough_usage_evidence")
            if (coverage != null && coverage < DEGRADED_COVERAGE_PERCENT) {
                add("evidence_coverage_below_80")
            } else if (coverage != null && coverage < GOOD_COVERAGE_PERCENT) {
                add("evidence_coverage_below_95")
            }
            if (continuityGaps.isNotEmpty()) add("continuity_gaps_detected")
            if (longestGapMs >= LONG_GAP_MS) add("long_continuity_gap")
            if (discarded.size >= MULTIPLE_DISCARDED_THRESHOLD) {
                add("multiple_discarded_intervals")
            } else if (discarded.isNotEmpty()) {
                add("discarded_evidence_detected")
            }
        }

        val healthState = when {
            coverage == null -> MeasurementHealthState.LIMITED
            coverage < DEGRADED_COVERAGE_PERCENT -> MeasurementHealthState.DEGRADED
            longestGapMs >= LONG_GAP_MS -> MeasurementHealthState.DEGRADED
            discarded.size >= MULTIPLE_DISCARDED_THRESHOLD -> MeasurementHealthState.DEGRADED
            coverage < GOOD_COVERAGE_PERCENT -> MeasurementHealthState.LIMITED
            discarded.isNotEmpty() -> MeasurementHealthState.LIMITED
            else -> MeasurementHealthState.GOOD
        }

        return EvidenceCoverageSummary(
            startMs = startMs,
            endMs = endMs,
            attributedRxBytes = attributedRx,
            attributedTxBytes = attributedTx,
            unattributedRxBytes = safeUnattributedRx,
            unattributedTxBytes = safeUnattributedTx,
            discardedIntervalCount = discarded.size,
            continuityGapCount = continuityGaps.size,
            longestGapMs = longestGapMs,
            evidenceCoveragePercent = coverage,
            healthState = healthState,
            healthReasons = reasons
        )
    }

    companion object {
        const val METRIC_DEFINITION_VERSION = 1
        const val GOOD_COVERAGE_PERCENT = 95.0
        const val DEGRADED_COVERAGE_PERCENT = 80.0
        const val LONG_GAP_MS = 30L * 60L * 1000L
        const val MULTIPLE_DISCARDED_THRESHOLD = 3

        val CONTINUITY_GAP_REASONS = setOf(
            "process_restart_boundary",
            "clock_discontinuity",
            "boot_changed",
            "non_monotonic_elapsed_realtime"
        )
    }
}
