package com.daniele21.trafficmonitoring.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "validation_runs")
data class ValidationRunEntity(
    @PrimaryKey val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val label: String?,
    val appVersion: String,
    val schemaVersion: Int,
    val attributionAlgorithmVersion: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidVersion: String,
    val sdkInt: Int,
    val batteryModeDeclaredByTester: String?,
    val notes: String?
)

@Entity(tableName = "network_events")
data class NetworkEventEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val receivedAtWallClockMs: Long,
    val receivedAtElapsedRealtimeMs: Long?,
    val source: String,
    val kind: String,
    val androidNetworkHandle: String?,
    val transportSet: String,
    val isValidated: Boolean?,
    val isMetered: Boolean?,
    val isNotVpn: Boolean?,
    val vpnPresent: Boolean?,
    val ssid: String?,
    val ssidAvailability: String,
    val interfaceNames: String?,
    val rawSummaryJson: String?
)

@Entity(tableName = "counter_snapshots")
data class CounterSnapshotEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val eventId: String?,
    val observedAtWallClockMs: Long,
    val observedAtElapsedRealtimeMs: Long,
    val bootGeneration: String,
    val source: String,
    val rxBytes: Long?,
    val txBytes: Long?,
    val interfaceName: String?,
    val status: String,
    val errorCode: String?
)

@Entity(tableName = "lifecycle_events")
data class LifecycleEventEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val timestampWallClockMs: Long,
    val timestampElapsedRealtimeMs: Long?,
    val kind: String,
    val detailsJson: String?
)

@Entity(tableName = "manual_test_markers")
data class ManualTestMarkerEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val timestampWallClockMs: Long,
    val timestampElapsedRealtimeMs: Long?,
    val markerType: String,
    val label: String,
    val currentObservedNetwork: String?,
    val notes: String?
)

@Entity(tableName = "attribution_intervals")
data class AttributionIntervalEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val networkIdentity: String?,
    val networkDisplayName: String?,
    val transport: String,
    val rxBytes: Long?,
    val txBytes: Long?,
    val confidence: String,
    val startEvidenceEventId: String?,
    val endEvidenceEventId: String?,
    val reason: String,
    val algorithmVersion: Int
)
