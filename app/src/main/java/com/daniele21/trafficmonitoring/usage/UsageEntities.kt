package com.daniele21.trafficmonitoring.usage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_profiles")
data class NetworkProfileEntity(
    @PrimaryKey val identity: String,
    val displayName: String,
    val transport: String,
    val firstSeenAtMs: Long,
    val lastSeenAtMs: Long
)

@Entity(
    tableName = "usage_buckets",
    primaryKeys = ["networkIdentity", "bucketStartMs"]
)
data class UsageBucketEntity(
    val networkIdentity: String,
    val bucketStartMs: Long,
    val bucketEndMs: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val confidence: String,
    val updatedAtMs: Long
)

@Entity(tableName = "processed_intervals")
data class ProcessedIntervalEntity(
    @PrimaryKey val intervalId: String,
    val processedAtMs: Long
)

data class UsageNetworkTotalRow(
    val networkIdentity: String,
    val rxBytes: Long,
    val txBytes: Long
)
