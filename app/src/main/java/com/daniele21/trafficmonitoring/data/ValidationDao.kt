package com.daniele21.trafficmonitoring.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ValidationDao {
    @Query("SELECT * FROM validation_runs WHERE endedAtMs IS NULL ORDER BY startedAtMs DESC LIMIT 1")
    suspend fun activeRun(): ValidationRunEntity?

    @Query("SELECT * FROM validation_runs WHERE id = :runId LIMIT 1")
    suspend fun runById(runId: String): ValidationRunEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRun(run: ValidationRunEntity)

    @Update
    suspend fun updateRun(run: ValidationRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetworkEvent(event: NetworkEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCounterSnapshot(snapshot: CounterSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLifecycleEvent(event: LifecycleEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManualMarker(marker: ManualTestMarkerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttributionInterval(interval: AttributionIntervalEntity)

    @Query("SELECT * FROM network_events WHERE runId = :runId ORDER BY receivedAtWallClockMs DESC LIMIT :limit")
    suspend fun recentNetworkEvents(runId: String, limit: Int = 50): List<NetworkEventEntity>

    @Query("SELECT * FROM manual_test_markers WHERE runId = :runId ORDER BY timestampWallClockMs DESC LIMIT :limit")
    suspend fun recentMarkers(runId: String, limit: Int = 50): List<ManualTestMarkerEntity>

    @Query("SELECT * FROM counter_snapshots WHERE runId = :runId ORDER BY observedAtWallClockMs DESC LIMIT :limit")
    suspend fun recentCounterSnapshots(runId: String, limit: Int = 20): List<CounterSnapshotEntity>

    @Query("SELECT * FROM attribution_intervals WHERE runId = :runId ORDER BY endedAtMs DESC LIMIT :limit")
    suspend fun recentAttributionIntervals(runId: String, limit: Int = 20): List<AttributionIntervalEntity>

    @Query("SELECT * FROM counter_snapshots WHERE runId = :runId ORDER BY observedAtWallClockMs DESC LIMIT 1")
    suspend fun latestCounterSnapshot(runId: String): CounterSnapshotEntity?

    @Query("SELECT * FROM network_events WHERE id = :eventId LIMIT 1")
    suspend fun networkEventById(eventId: String): NetworkEventEntity?

    @Query("SELECT * FROM network_events WHERE runId = :runId ORDER BY receivedAtWallClockMs ASC")
    suspend fun networkEventsForRun(runId: String): List<NetworkEventEntity>

    @Query("SELECT * FROM counter_snapshots WHERE runId = :runId ORDER BY observedAtWallClockMs ASC")
    suspend fun counterSnapshotsForRun(runId: String): List<CounterSnapshotEntity>

    @Query("SELECT * FROM lifecycle_events WHERE runId = :runId ORDER BY timestampWallClockMs ASC")
    suspend fun lifecycleEventsForRun(runId: String): List<LifecycleEventEntity>

    @Query("SELECT * FROM manual_test_markers WHERE runId = :runId ORDER BY timestampWallClockMs ASC")
    suspend fun manualMarkersForRun(runId: String): List<ManualTestMarkerEntity>

    @Query("SELECT * FROM attribution_intervals WHERE runId = :runId ORDER BY startedAtMs ASC")
    suspend fun attributionIntervalsForRun(runId: String): List<AttributionIntervalEntity>

    @Query("SELECT * FROM attribution_intervals ORDER BY startedAtMs ASC")
    suspend fun allAttributionIntervals(): List<AttributionIntervalEntity>

    @Query(
        "SELECT * FROM attribution_intervals " +
            "WHERE endedAtMs > :startMs AND startedAtMs < :endMs " +
            "ORDER BY startedAtMs ASC"
    )
    suspend fun attributionIntervalsBetween(startMs: Long, endMs: Long): List<AttributionIntervalEntity>

    @Query("DELETE FROM network_events")
    suspend fun deleteNetworkEvents()

    @Query("DELETE FROM counter_snapshots")
    suspend fun deleteCounterSnapshots()

    @Query("DELETE FROM lifecycle_events")
    suspend fun deleteLifecycleEvents()

    @Query("DELETE FROM manual_test_markers")
    suspend fun deleteManualMarkers()

    @Query("DELETE FROM attribution_intervals")
    suspend fun deleteAttributionIntervals()

    @Query("DELETE FROM validation_runs")
    suspend fun deleteRuns()
}
