# Data and export

## Goal

Preserve enough local evidence to determine after a long field test whether the Android background strategy actually worked.

For M1, **debuggability matters more than storage minimization**. Network events are rare, so storing raw observations is cheap.

## Storage technology

Use Room / SQLite.

All data remains local unless the user explicitly chooses **Export validation run**.

## ValidationRun

One bounded experiment.

Suggested fields:

```text
id: UUID
startedAt: Instant
endedAt: Instant?
label: String?
appVersion: String
schemaVersion: Int
attributionAlgorithmVersion: Int
deviceManufacturer: String
deviceModel: String
androidVersion: String
sdkInt: Int
batteryModeDeclaredByTester: String?
notes: String?
```

A fresh run should not delete previous runs unless the user explicitly asks to clear history.

## NetworkEvent

Raw evidence that Android/network code delivered an event.

```text
id: UUID
runId: UUID
receivedAtWallClockMs: Long
receivedAtElapsedRealtimeMs: Long?
source: pending_intent | callback | recovery | boot | startup
kind: available | lost | capabilities_changed | link_changed | snapshot | other
androidNetworkHandle: String?
transportSet: String
isValidated: Boolean?
isMetered: Boolean?
isNotVpn: Boolean?
vpnPresent: Boolean?
ssid: String?
ssidAvailability: known | unknown | permission_missing | unavailable
interfaceNames: String?
rawSummaryJson: String?
```

Do not persist BSSID by default.

`androidNetworkHandle` is diagnostic only and must never be used as a durable identity across reboot.

## CounterSnapshot

Persist every boundary/recovery counter read.

```text
id: UUID
runId: UUID
eventId: UUID?
observedAtWallClockMs: Long
observedAtElapsedRealtimeMs: Long
bootGeneration: String
source: traffic_stats_total | traffic_stats_mobile | interface | network_stats_reference
rxBytes: Long?
txBytes: Long?
interfaceName: String?
status: valid | unsupported | error
errorCode: String?
```

If multiple counter sources are read for one event, store separate rows rather than flattening away source provenance.

## LifecycleEvent

Used to diagnose background/process gaps.

```text
id: UUID
runId: UUID
timestampWallClockMs: Long
timestampElapsedRealtimeMs: Long?
kind:
  process_start |
  process_foreground |
  process_background |
  receiver_started |
  receiver_finished |
  recovery_worker_started |
  recovery_worker_finished |
  boot_received |
  clean_stop |
  crash_detected |
  historical_exit_reason |
  permission_changed |
  other
detailsJson: String?
```

Avoid generating high-frequency lifecycle noise. Persist events that explain measurement continuity.

## ManualTestMarker

Human ground truth entered during testing.

```text
id: UUID
runId: UUID
timestampWallClockMs: Long
timestampElapsedRealtimeMs: Long?
markerType: String
label: String
currentObservedNetwork: String?
notes: String?
```

The UI should provide quick presets plus free text.

Suggested presets:

```text
SWITCHING_NETWORK
NETWORK_SWITCH_COMPLETE
START_KNOWN_DOWNLOAD
END_KNOWN_DOWNLOAD
START_KNOWN_UPLOAD
END_KNOWN_UPLOAD
SCREEN_OFF
SCREEN_ON
BATTERY_SAVER_ON
BATTERY_SAVER_OFF
REBOOTING
OTHER
```

## AttributionInterval

Derived data, not raw evidence.

```text
id: UUID
runId: UUID
startedAtMs: Long
endedAtMs: Long
networkIdentity: String?
networkDisplayName: String?
transport: wifi | cellular | ethernet | other | offline | unknown
rxBytes: Long?
txBytes: Long?
confidence: confirmed | inferred | unattributed | discarded
startEvidenceEventId: UUID?
endEvidenceEventId: UUID?
reason: String
algorithmVersion: Int
```

The interval must retain references to the evidence that produced it.

## CurrentState / durable baseline

The app needs a tiny durable state record so process death does not erase the last known boundary.

Suggested shape:

```text
runId
lastEventId
lastCounterSnapshotId
activeNetworkIdentity
bootGeneration
lastSuccessfulPersistenceAt
continuityState
pendingIntentRegistrationVersion
```

Update atomically with event observations when practical.

## Data volume

The Standard strategy is event-driven. Expected writes are small:

- a few rows per network transition;
- a few recovery snapshots per day;
- lifecycle evidence;
- manual markers.

Even a verbose M1 run should remain tiny compared with normal app storage. There is no reason to aggressively compact raw validation evidence.

Future production analytics may separately use five-minute/hourly buckets, but the M1 raw log should remain auditable.

# Validation export

## User action

Provide a clear action:

```text
Export validation run
```

Use Android's system share/save flow. The app should not upload the bundle automatically.

Suggested filename:

```text
traffic-monitoring-validation-2026-08-08T142900Z.zip
```

## Required ZIP contents

```text
manifest.json
network-events.csv
counter-snapshots.csv
attribution-intervals.csv
lifecycle-events.csv
manual-markers.csv
summary.json
README.txt
```

Optional when enabled/available:

```text
networkstats-reference.csv
process-exit-reasons.csv
```

## manifest.json

Contains interpretation metadata, not raw measurements only.

Example shape:

```json
{
  "exportFormatVersion": 1,
  "schemaVersion": 1,
  "attributionAlgorithmVersion": 1,
  "runId": "...",
  "startedAt": "...",
  "endedAt": "...",
  "appVersion": "0.1.0-spike",
  "device": {
    "manufacturer": "...",
    "model": "...",
    "androidVersion": "...",
    "sdkInt": 36
  },
  "configuration": {
    "backgroundStrategy": "pending_intent_plus_recovery",
    "recoveryCadenceHours": 3,
    "usageAccessGranted": false,
    "wifiIdentityPermissionState": "granted",
    "batteryModeDeclaredByTester": "unrestricted"
  }
}
```

Do not include hardware serial number, advertising ID, Android ID, IMEI, IMSI, phone number, account IDs or other unnecessary stable identifiers.

## CSV requirements

- UTF-8.
- Header row always present.
- ISO-8601 wall-clock column in addition to raw epoch milliseconds where useful.
- Stable column ordering per export format version.
- One row per stored entity; do not pre-aggregate away evidence.
- Quote SSID/free-text fields safely.

## summary.json

The app should produce an automatically computed validation summary so a tester can understand the run before deeper analysis.

Suggested metrics:

```text
runDuration
networkEventsBySource
pendingIntentWakeCount
inProcessEventCount
recoveryWorkerCount
processStartCount
observedBootCount
manualMarkerCount
confirmedRxBytes
confirmedTxBytes
inferredBytes
unattributedBytes
discardedIntervalCount
counterResetCount
unknownSsidEventCount
longestEvidenceGap
```

Also include derived per-network totals split by confidence.

Example:

```json
{
  "networks": [
    {
      "name": "Home Wi-Fi",
      "confirmedBytes": 1245000000,
      "inferredBytes": 0
    },
    {
      "name": "unattributed",
      "confirmedBytes": 0,
      "unattributedBytes": 12000000
    }
  ]
}
```

## README.txt inside export

Explain in plain language:

- that totals are Android device network counters, not carrier billing;
- that SSIDs may appear in the export;
- what `confirmed`, `inferred`, `unattributed`, and `discarded` mean;
- what each CSV file contains;
- the export/schema version;
- that timestamps should be compared to manual markers.

## Privacy and sharing

The export may contain Wi-Fi SSIDs and tester-written notes.

Before sharing, the UI should warn:

> This validation export can include Wi-Fi network names and test notes. It does not include packet contents, websites, DNS queries, location coordinates, device identifiers or account data.

A future `Redact network names` export option is useful but not required for M1.

## Export consistency

Export from a database snapshot/transaction so files represent one coherent point in time.

The export operation must not mutate measurement history.

If the run is still active, include:

```text
runState = active
exportedAt = ...
```

and flush any pending derived state before producing the bundle.

## Why CSV + JSON

CSV makes field-test evidence easy to inspect in Excel/Python without app-specific tooling.

JSON carries structured metadata and summary data without forcing awkward CSV repetition.

The ZIP should therefore be independently analyzable without the Android app or repository source code.
