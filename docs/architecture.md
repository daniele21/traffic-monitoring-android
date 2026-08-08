# Architecture

## Goal

Separate Android platform behavior from attribution logic so the hardest part of the product can be validated with deterministic tests and exported evidence.

## Dependency direction

```text
UI → Application → Domain ← Platform
                  ↓
             Persistence
```

The domain layer must not depend on Android SDK classes.

## Proposed modules / packages

A single Android app module is sufficient for the spike, but code should respect these logical boundaries:

```text
ui/
application/
domain/
platform/network/
platform/counters/
persistence/
export/
```

Do not create a multi-module Gradle architecture until the codebase is large enough to justify it.

## Core abstractions

### TrafficCounterReader

Returns a counter snapshot with explicit source metadata.

```kotlin
interface TrafficCounterReader {
    suspend fun read(): CounterSnapshot
}
```

A snapshot should include RX/TX plus enough metadata to detect unsupported/reset generations.

### NetworkContextReader

Converts Android network state into a stable domain snapshot.

```kotlin
interface NetworkContextReader {
    suspend fun current(): NetworkContext
}
```

### NetworkEventSource

Represents externally delivered connectivity boundaries independent of whether they came from a callback, PendingIntent receiver, boot/recovery path, or a foreground-service callback.

### MeasurementRecorder

Persists raw observations first-class:

- network event;
- counter snapshot;
- lifecycle event;
- permission/background state;
- manual marker.

### AttributionEngine

Consumes ordered observations and derives intervals with explicit confidence.

It must be pure/testable where possible.

### ValidationExporter

Reads a frozen validation run and creates a shareable ZIP bundle containing machine-readable CSV/JSON plus a concise manifest.

## Runtime flow — event boundary

```text
Android delivers network event
            ↓
BroadcastReceiver / callback adapter
            ↓
record lifecycle + raw network event
            ↓
read network context
            ↓
read counter snapshot
            ↓
transactionally persist observation
            ↓
AttributionEngine closes prior interval
            ↓
new baseline/context becomes active
```

The receiver should perform only bounded work. If Android execution limits require deferring heavier work, persist the event trigger immediately and hand off through an appropriate constrained mechanism without losing the event timestamp.

## Runtime flow — recovery check

```text
coarse WorkManager execution
            ↓
record recovery event
            ↓
read current context + counters
            ↓
compare with last durable observation
            ↓
if lifecycle continuity is known:
    reconcile normally
else:
    create explicit uncertain/unattributed gap
```

A recovery worker must never pretend it observed a transition that actually occurred hours earlier.

## Raw vs derived data

Raw evidence is authoritative for debugging.

Derived attribution is reproducible from raw observations and should therefore be versioned by attribution algorithm version.

```text
RawObservation tables
        ↓
AttributionEngine vN
        ↓
DerivedInterval rows
```

This lets a validation export prove whether an error came from Android event delivery, counter behavior, or our attribution algorithm.

## Network context model

Suggested domain shape:

```text
NetworkContext
- observedAtWallClock
- observedAtElapsedRealtime
- networkHandle/session token (ephemeral)
- transport: wifi | cellular | ethernet | other | offline
- ssid: String?
- isValidated
- isMetered
- vpnPresent
- interfaceNames: Set<String>
- identityQuality: named | generic | unavailable
```

Do not use Android `Network` handles as durable identities across reboot/process history.

## Counter snapshot model

Suggested shape:

```text
CounterSnapshot
- observedAtWallClock
- observedAtElapsedRealtime
- bootSessionId / boot marker
- totalRxBytes
- totalTxBytes
- mobileRxBytes?
- mobileTxBytes?
- perInterface counters? (diagnostic)
- source
- validity
```

The spike should capture more than one counter candidate when inexpensive so the export can compare them.

## Persistence

Room is the default local store.

Writes on network events are expected to be rare, so optimizing away a few inserts is less important than preserving evidence atomically.

Use a transaction for the observation set associated with one event when practical.

## UI

The validation UI is deliberately small:

```text
Status
Current network
Background mode / permissions
Last observed event
Last counter snapshot

[ Add test marker ]
[ Export validation run ]
[ Start fresh run ]

Recent events
```

Do not build the final analytics IA until M1 passes.

## Future production analytics

If the feasibility gate succeeds, the architecture can later add:

```text
confirmed attribution intervals
        ↓
usage buckets
        ↓
Room aggregates
        ↓
Overview / Trend / Networks
```

The macOS product semantics can be mirrored, but Android measurement implementation remains native and platform-specific.
