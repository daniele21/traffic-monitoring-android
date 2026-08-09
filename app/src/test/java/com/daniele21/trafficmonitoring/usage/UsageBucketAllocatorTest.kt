package com.daniele21.trafficmonitoring.usage

import com.daniele21.trafficmonitoring.data.AttributionIntervalEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageBucketAllocatorTest {
    @Test
    fun `split preserves exact byte totals across buckets`() {
        val bucket = UsageBucketAllocator.BUCKET_MS
        val interval = interval(
            startedAtMs = bucket - 60_000L,
            endedAtMs = bucket + 6 * 60_000L,
            rxBytes = 12_345L,
            txBytes = 6_789L
        )

        val allocations = UsageBucketAllocator.allocate(interval)

        assertTrue(allocations.size >= 2)
        assertEquals(12_345L, allocations.sumOf { it.rxBytes })
        assertEquals(6_789L, allocations.sumOf { it.txBytes })
        assertTrue(allocations.all { it.bucketEndMs - it.bucketStartMs == bucket })
    }

    @Test
    fun `unattributed interval is stored explicitly`() {
        val allocations = UsageBucketAllocator.allocate(
            interval(networkIdentity = null, networkDisplayName = null, confidence = "unattributed")
        )

        assertEquals(UsageBucketAllocator.UNATTRIBUTED_ID, allocations.single().networkIdentity)
        assertEquals("Unattributed", allocations.single().displayName)
    }

    @Test
    fun `discarded interval never reaches product history`() {
        val allocations = UsageBucketAllocator.allocate(
            interval(confidence = "discarded", rxBytes = null, txBytes = null)
        )

        assertTrue(allocations.isEmpty())
    }

    private fun interval(
        startedAtMs: Long = 1_800_000L,
        endedAtMs: Long = 1_860_000L,
        networkIdentity: String? = "wifi:ssid:Home",
        networkDisplayName: String? = "Home",
        confidence: String = "inferred",
        rxBytes: Long? = 1_000L,
        txBytes: Long? = 200L
    ) = AttributionIntervalEntity(
        id = "interval-1",
        runId = "run-1",
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        networkIdentity = networkIdentity,
        networkDisplayName = networkDisplayName,
        transport = "wifi",
        rxBytes = rxBytes,
        txBytes = txBytes,
        confidence = confidence,
        startEvidenceEventId = "e1",
        endEvidenceEventId = "e2",
        reason = "test",
        algorithmVersion = 2
    )
}
