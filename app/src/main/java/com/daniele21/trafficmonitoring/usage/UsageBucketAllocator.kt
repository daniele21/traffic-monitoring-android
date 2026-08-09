package com.daniele21.trafficmonitoring.usage

import com.daniele21.trafficmonitoring.data.AttributionIntervalEntity
import kotlin.math.floor

/** Splits accepted attribution intervals across fixed five-minute buckets while preserving byte totals exactly. */
object UsageBucketAllocator {
    const val BUCKET_MS = 5 * 60 * 1000L
    const val UNATTRIBUTED_ID = "__unattributed__"

    data class Allocation(
        val networkIdentity: String,
        val displayName: String,
        val transport: String,
        val confidence: String,
        val bucketStartMs: Long,
        val bucketEndMs: Long,
        val rxBytes: Long,
        val txBytes: Long
    )

    fun allocate(interval: AttributionIntervalEntity): List<Allocation> {
        if (interval.confidence == "discarded") return emptyList()
        val rx = interval.rxBytes ?: return emptyList()
        val tx = interval.txBytes ?: return emptyList()
        val identity = interval.networkIdentity ?: UNATTRIBUTED_ID
        val displayName = interval.networkDisplayName ?: if (identity == UNATTRIBUTED_ID) "Unattributed" else identity
        val start = interval.startedAtMs
        val end = interval.endedAtMs

        if (end <= start) {
            val bucketStart = floorToBucket(start)
            return listOf(
                Allocation(
                    networkIdentity = identity,
                    displayName = displayName,
                    transport = interval.transport,
                    confidence = interval.confidence,
                    bucketStartMs = bucketStart,
                    bucketEndMs = bucketStart + BUCKET_MS,
                    rxBytes = rx,
                    txBytes = tx
                )
            )
        }

        val spans = buildList {
            var cursor = start
            while (cursor < end) {
                val bucketStart = floorToBucket(cursor)
                val bucketEnd = bucketStart + BUCKET_MS
                val spanEnd = minOf(end, bucketEnd)
                add(Triple(bucketStart, bucketEnd, spanEnd - cursor))
                cursor = spanEnd
            }
        }

        val duration = (end - start).toDouble()
        var assignedRx = 0L
        var assignedTx = 0L
        return spans.mapIndexed { index, (bucketStart, bucketEnd, overlapMs) ->
            val last = index == spans.lastIndex
            val allocatedRx = if (last) rx - assignedRx else floor(rx.toDouble() * overlapMs / duration).toLong()
            val allocatedTx = if (last) tx - assignedTx else floor(tx.toDouble() * overlapMs / duration).toLong()
            assignedRx += allocatedRx
            assignedTx += allocatedTx
            Allocation(
                networkIdentity = identity,
                displayName = displayName,
                transport = interval.transport,
                confidence = interval.confidence,
                bucketStartMs = bucketStart,
                bucketEndMs = bucketEnd,
                rxBytes = allocatedRx,
                txBytes = allocatedTx
            )
        }
    }

    private fun floorToBucket(timestampMs: Long): Long = (timestampMs / BUCKET_MS) * BUCKET_MS
}
