package com.daniele21.trafficmonitoring.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CounterAttributionTest {
    @Test
    fun `same network produces inferred monotonic delta`() {
        val result = CounterAttribution.between(
            evidence(wall = 1_000, elapsed = 1_000, rx = 10_000, tx = 5_000),
            evidence(wall = 6_000, elapsed = 6_000, rx = 12_500, tx = 5_700)
        )!!

        assertEquals("inferred", result.confidence)
        assertEquals("same_network_between_evidence", result.reason)
        assertEquals(2_500L, result.rxBytes)
        assertEquals(700L, result.txBytes)
        assertEquals("wifi:42", result.networkIdentity)
    }

    @Test
    fun `network change keeps bytes unattributed`() {
        val result = CounterAttribution.between(
            evidence(wall = 1_000, elapsed = 1_000, rx = 10_000, tx = 5_000, identity = "wifi:42"),
            evidence(wall = 6_000, elapsed = 6_000, rx = 12_500, tx = 5_700, identity = "cellular:43", transport = "cellular")
        )!!

        assertEquals("unattributed", result.confidence)
        assertEquals("network_changed_between_evidence", result.reason)
        assertEquals(null, result.networkIdentity)
        assertEquals(2_500L, result.rxBytes)
    }

    @Test
    fun `emulator style wall clock discontinuity is discarded`() {
        val result = CounterAttribution.between(
            evidence(wall = 1_000, elapsed = 1_000, rx = 10_000, tx = 5_000),
            evidence(wall = 3_121_000, elapsed = 23_000, rx = 12_500, tx = 5_700)
        )!!

        assertEquals("discarded", result.confidence)
        assertEquals("clock_discontinuity", result.reason)
        assertNull(result.rxBytes)
        assertNull(result.txBytes)
    }

    @Test
    fun `counter regression is discarded`() {
        val result = CounterAttribution.between(
            evidence(wall = 1_000, elapsed = 1_000, rx = 10_000, tx = 5_000),
            evidence(wall = 2_000, elapsed = 2_000, rx = 9_000, tx = 5_100)
        )!!

        assertEquals("discarded", result.confidence)
        assertEquals("counter_reset", result.reason)
    }

    @Test
    fun `boot generation change is discarded`() {
        val result = CounterAttribution.between(
            evidence(wall = 1_000, elapsed = 1_000, rx = 10_000, tx = 5_000, boot = "boot:1"),
            evidence(wall = 2_000, elapsed = 2_000, rx = 300, tx = 100, boot = "boot:2")
        )!!

        assertEquals("discarded", result.confidence)
        assertEquals("boot_changed", result.reason)
    }

    @Test
    fun `vpn interval remains unattributed`() {
        val result = CounterAttribution.between(
            evidence(wall = 1_000, elapsed = 1_000, rx = 10_000, tx = 5_000, identity = "vpn:42", transport = "wifi|vpn"),
            evidence(wall = 2_000, elapsed = 2_000, rx = 10_500, tx = 5_300, identity = "vpn:42", transport = "wifi|vpn")
        )!!

        assertEquals("unattributed", result.confidence)
        assertEquals("vpn_active_total_counter_ambiguous", result.reason)
    }

    @Test
    fun `unsupported counter does not invent interval`() {
        val previous = evidence(wall = 1_000, elapsed = 1_000, rx = null, tx = null)
        val current = evidence(wall = 2_000, elapsed = 2_000, rx = null, tx = null)

        assertNull(CounterAttribution.between(previous, current))
    }

    private fun evidence(
        wall: Long,
        elapsed: Long,
        rx: Long?,
        tx: Long?,
        boot: String = "boot:1",
        identity: String = "wifi:42",
        transport: String = "wifi"
    ) = AttributionEvidence(
        wallClockMs = wall,
        elapsedRealtimeMs = elapsed,
        bootGeneration = boot,
        rxBytes = rx,
        txBytes = tx,
        eventId = "event-$wall",
        networkIdentity = identity,
        networkDisplayName = "Test network",
        transport = transport
    )
}
