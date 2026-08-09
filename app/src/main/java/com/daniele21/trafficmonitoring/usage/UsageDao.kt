package com.daniele21.trafficmonitoring.usage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UsageDao {
    @Query("SELECT * FROM network_profiles WHERE identity = :identity LIMIT 1")
    suspend fun profile(identity: String): NetworkProfileEntity?

    @Query("SELECT * FROM network_profiles ORDER BY lastSeenAtMs DESC")
    suspend fun profiles(): List<NetworkProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProfile(profile: NetworkProfileEntity)

    @Query("SELECT * FROM usage_buckets WHERE networkIdentity = :networkIdentity AND bucketStartMs = :bucketStartMs LIMIT 1")
    suspend fun bucket(networkIdentity: String, bucketStartMs: Long): UsageBucketEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBucket(bucket: UsageBucketEntity)

    @Query("SELECT * FROM usage_buckets WHERE bucketStartMs >= :startMs AND bucketStartMs < :endMs ORDER BY bucketStartMs ASC")
    suspend fun bucketsBetween(startMs: Long, endMs: Long): List<UsageBucketEntity>

    @Query("SELECT networkIdentity, SUM(rxBytes) AS rxBytes, SUM(txBytes) AS txBytes FROM usage_buckets WHERE bucketStartMs >= :startMs AND bucketStartMs < :endMs GROUP BY networkIdentity ORDER BY (SUM(rxBytes) + SUM(txBytes)) DESC")
    suspend fun totalsByNetwork(startMs: Long, endMs: Long): List<UsageNetworkTotalRow>

    @Query("SELECT * FROM processed_intervals WHERE intervalId = :intervalId LIMIT 1")
    suspend fun processedInterval(intervalId: String): ProcessedIntervalEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markProcessed(interval: ProcessedIntervalEntity)

    @Query("DELETE FROM usage_buckets")
    suspend fun deleteBuckets()

    @Query("DELETE FROM network_profiles")
    suspend fun deleteProfiles()

    @Query("DELETE FROM processed_intervals")
    suspend fun deleteProcessedIntervals()
}
