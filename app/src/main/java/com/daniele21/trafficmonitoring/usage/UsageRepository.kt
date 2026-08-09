package com.daniele21.trafficmonitoring.usage

import androidx.room.withTransaction
import com.daniele21.trafficmonitoring.data.AttributionIntervalEntity

class UsageRepository(private val database: UsageDatabase) {
    private val dao = database.usageDao()

    suspend fun ingest(interval: AttributionIntervalEntity) {
        if (interval.confidence == "discarded") return

        database.withTransaction {
            if (dao.processedInterval(interval.id) != null) return@withTransaction

            val allocations = UsageBucketAllocator.allocate(interval)
            allocations.forEach { allocation ->
                val existing = dao.bucket(allocation.networkIdentity, allocation.bucketStartMs)
                dao.putBucket(
                    UsageBucketEntity(
                        networkIdentity = allocation.networkIdentity,
                        bucketStartMs = allocation.bucketStartMs,
                        bucketEndMs = allocation.bucketEndMs,
                        rxBytes = (existing?.rxBytes ?: 0L) + allocation.rxBytes,
                        txBytes = (existing?.txBytes ?: 0L) + allocation.txBytes,
                        confidence = mergeConfidence(existing?.confidence, allocation.confidence),
                        updatedAtMs = System.currentTimeMillis()
                    )
                )

                if (allocation.networkIdentity != UsageBucketAllocator.UNATTRIBUTED_ID) {
                    val profile = dao.profile(allocation.networkIdentity)
                    dao.putProfile(
                        NetworkProfileEntity(
                            identity = allocation.networkIdentity,
                            displayName = allocation.displayName,
                            transport = allocation.transport,
                            firstSeenAtMs = minOf(profile?.firstSeenAtMs ?: interval.startedAtMs, interval.startedAtMs),
                            lastSeenAtMs = maxOf(profile?.lastSeenAtMs ?: interval.endedAtMs, interval.endedAtMs)
                        )
                    )
                }
            }

            dao.markProcessed(
                ProcessedIntervalEntity(
                    intervalId = interval.id,
                    processedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun snapshot(startMs: Long, endMs: Long, trendSlotMs: Long): UsageSnapshot {
        val totals = dao.totalsByNetwork(startMs, endMs)
        val profileById = dao.profiles().associateBy { it.identity }
        val networks = totals.map { row ->
            val profile = profileById[row.networkIdentity]
            UsageNetworkTotal(
                identity = row.networkIdentity,
                displayName = when (row.networkIdentity) {
                    UsageBucketAllocator.UNATTRIBUTED_ID -> "Unattributed"
                    else -> profile?.displayName ?: row.networkIdentity
                },
                transport = profile?.transport ?: "unknown",
                rxBytes = row.rxBytes,
                txBytes = row.txBytes
            )
        }

        val buckets = dao.bucketsBetween(startMs, endMs)
        val safeSlotMs = trendSlotMs.coerceAtLeast(UsageBucketAllocator.BUCKET_MS)
        val trend = buckets
            .groupBy { bucket ->
                startMs + ((bucket.bucketStartMs - startMs).coerceAtLeast(0L) / safeSlotMs) * safeSlotMs
            }
            .toSortedMap()
            .map { (slotStart, rows) ->
                UsageTrendPoint(
                    startMs = slotStart,
                    rxBytes = rows.sumOf { it.rxBytes },
                    txBytes = rows.sumOf { it.txBytes }
                )
            }

        return UsageSnapshot(
            startMs = startMs,
            endMs = endMs,
            rxBytes = networks.sumOf { it.rxBytes },
            txBytes = networks.sumOf { it.txBytes },
            networks = networks,
            trend = trend,
            unattributedBytes = networks
                .firstOrNull { it.identity == UsageBucketAllocator.UNATTRIBUTED_ID }
                ?.totalBytes ?: 0L
        )
    }

    suspend fun clearAll() {
        database.withTransaction {
            dao.deleteBuckets()
            dao.deleteProfiles()
            dao.deleteProcessedIntervals()
        }
    }

    private fun mergeConfidence(existing: String?, incoming: String): String = when {
        existing == null -> incoming
        existing == incoming -> existing
        existing == "unattributed" || incoming == "unattributed" -> "mixed"
        else -> "mixed"
    }
}

data class UsageSnapshot(
    val startMs: Long,
    val endMs: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val networks: List<UsageNetworkTotal>,
    val trend: List<UsageTrendPoint>,
    val unattributedBytes: Long
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

data class UsageNetworkTotal(
    val identity: String,
    val displayName: String,
    val transport: String,
    val rxBytes: Long,
    val txBytes: Long
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

data class UsageTrendPoint(
    val startMs: Long,
    val rxBytes: Long,
    val txBytes: Long
) {
    val totalBytes: Long get() = rxBytes + txBytes
}
