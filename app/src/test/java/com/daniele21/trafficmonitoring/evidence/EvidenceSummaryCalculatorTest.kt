package com.daniele21.trafficmonitoring.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceSummaryCalculatorTest {
    private val calculator = EvidenceSummaryCalculator()

    @Test
    fun fullyAttributedUsageIsGoodWithFullCoverage() {
        val summary = calculator.calculate(
            startMs = 0L,
            endMs = 60_000L,
            totalRxBytes = 900L,
            totalTxBytes = 100L,
            unattributedRxBytes = 0L,
            unattributedTxBytes = 0L,
            intervals = emptyList()
        )

        assertEquals(1_000L, summary.attributedBytes)
        assertEquals(0L, summary.unattributedBytes)
        assertEquals(100.0, summary.evidenceCoveragePercent!!, 0.0001)
        assertEquals(MeasurementHealthState.GOOD, summary.healthState)
        assertTrue(summary.healthReasons.isEmpty())
    }

    @Test
    fun unattributedUsageReducesCoverage() {
        val summary = calculator.calculate(
            startMs = 0L,
            endMs = 60_000L,
            totalRxBytes = 800L,
            totalTxBytes = 200L,
            unattributedRxBytes = 200L,
            unattributedTxBytes = 50L,
            intervals = emptyList()
        )

        assertEquals(750L, summary.attributedBytes)
        assertEquals(250L, summary.unattributedBytes)
        assertEquals(75.0, summary.evidenceCoveragePercent!!, 0.0001)
        assertEquals(MeasurementHealthState.DEGRADED, summary.healthState)
        assertTrue("evidence_coverage_below_80" in summary.healthReasons)
    }

    @Test
    fun noUsageDoesNotPretendToHavePerfectCoverage() {
        val summary = calculator.calculate(
            startMs = 0L,
            endMs = 60_000L,
            totalRxBytes = 0L,
            totalTxBytes = 0L,
            unattributedRxBytes = 0L,
            unattributedTxBytes = 0L,
            intervals = emptyList()
        )

        assertNull(summary.evidenceCoveragePercent)
        assertEquals(MeasurementHealthState.LIMITED, summary.healthState)
        assertTrue("not_enough_usage_evidence" in summary.healthReasons)
    }

    @Test
    fun processRestartCreatesVisibleContinuityGap() {
        val summary = calculator.calculate(
            startMs = 0L,
            endMs = 120_000L,
            totalRxBytes = 1_000L,
            totalTxBytes = 0L,
            unattributedRxBytes = 0L,
            unattributedTxBytes = 0L,
            intervals = listOf(
                EvidenceIntervalInput(
                    startedAtMs = 20_000L,
                    endedAtMs = 50_000L,
                    confidence = "discarded",
                    reason = "process_restart_boundary"
                )
            )
        )

        assertEquals(1, summary.discardedIntervalCount)
        assertEquals(1, summary.continuityGapCount)
        assertEquals(30_000L, summary.longestGapMs)
        assertEquals(MeasurementHealthState.LIMITED, summary.healthState)
        assertTrue("continuity_gaps_detected" in summary.healthReasons)
    }

    @Test
    fun longGapDegradesHealthEvenWithFullCoverage() {
        val summary = calculator.calculate(
            startMs = 0L,
            endMs = 3_600_000L,
            totalRxBytes = 1_000L,
            totalTxBytes = 0L,
            unattributedRxBytes = 0L,
            unattributedTxBytes = 0L,
            intervals = listOf(
                EvidenceIntervalInput(
                    startedAtMs = 10_000L,
                    endedAtMs = 2_000_000L,
                    confidence = "discarded",
                    reason = "clock_discontinuity"
                )
            )
        )

        assertEquals(MeasurementHealthState.DEGRADED, summary.healthState)
        assertTrue("long_continuity_gap" in summary.healthReasons)
    }

    @Test
    fun longestGapIsClippedToSelectedTimeframe() {
        val summary = calculator.calculate(
            startMs = 100_000L,
            endMs = 200_000L,
            totalRxBytes = 1_000L,
            totalTxBytes = 0L,
            unattributedRxBytes = 0L,
            unattributedTxBytes = 0L,
            intervals = listOf(
                EvidenceIntervalInput(
                    startedAtMs = 0L,
                    endedAtMs = 150_000L,
                    confidence = "discarded",
                    reason = "process_restart_boundary"
                )
            )
        )

        assertEquals(50_000L, summary.longestGapMs)
    }
}
