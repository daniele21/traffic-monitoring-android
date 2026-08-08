# AGENTS.md

## Purpose

Build a lightweight, privacy-first Android app that attributes device network usage to the network context in which it occurred, while minimizing background execution and battery impact.

The immediate goal is **not** a finished analytics app. The immediate goal is to prove or disprove that Android can provide sufficiently reliable background network-boundary events and traffic counters for this product.

This file is intentionally short. Do not load the entire documentation tree by default.

## Progressive disclosure

Read only the documents needed for the task:

| If you are working on… | Read |
|---|---|
| Product scope / success criteria | `docs/product-spec.md` |
| Overall architecture / boundaries | `docs/architecture.md` |
| Counters, identities, attribution | `docs/measurement-engine.md` |
| Background survival / events / FGS | `docs/background-strategy.md` |
| Local schema / export bundle | `docs/data-and-export.md` |
| Implementation order / current milestone | `docs/implementation-plan.md` |
| Real-device validation | `docs/testing.md` |
| Why key choices were made | `docs/decisions.md` |
| Documentation map only | `docs/README.md` |

For most changes, `AGENTS.md` + one or two targeted documents should be enough.

## Core invariants

1. **Event-driven before polling-driven.** Network changes should define attribution boundaries whenever Android can deliver them.
2. Do not poll every few seconds/minutes in the background just to keep the app alive.
3. A traffic delta may be attributed to a network only when the interval's network context is sufficiently known.
4. If an app/process gap makes the network path ambiguous, prefer `unattributed` over guessing.
5. Counter reset, reboot, unsupported values, interface recreation or decreasing counters must never create negative or giant synthetic usage.
6. Wi-Fi SSID enriches identity. If permission or platform behavior hides it, tracking must degrade explicitly rather than silently pretending networks are distinguishable.
7. Do not store packet contents, DNS queries, destinations, URLs, browsing history, payloads or application-level traffic metadata.
8. Do not use a local VPN/VpnService merely to inspect or intercept user traffic for v1.
9. All measurement and validation data remains local unless the user explicitly exports it.
10. Exportability is part of the measurement spike, not a later convenience feature.
11. Record enough lifecycle evidence to determine whether a missed network change was caused by process death, reboot, permission state, recovery delay or measurement logic.
12. A Foreground Service is an escalation path, not the default architecture, until the standard approach is proven insufficient.

## Initial technical direction

- Language: Kotlin.
- UI: Jetpack Compose.
- Persistence: Room / SQLite.
- Network state: `ConnectivityManager`, `NetworkRequest`, `NetworkCapabilities`.
- Primary background candidate: `registerNetworkCallback(NetworkRequest, PendingIntent)` + manifest `BroadcastReceiver`.
- In-process diagnostics: `NetworkCallback` where useful.
- Traffic counters: validate `TrafficStats` total/mobile/per-interface readings against controlled transfers.
- Historical/reference totals: `NetworkStatsManager` only where Usage Access is explicitly granted.
- Coarse recovery: WorkManager, measured in hours rather than high-frequency polling.
- Foreground Service: only if M1 evidence shows event delivery is not reliable enough without it.
- No root requirement.
- No cloud backend in v1.

## Architecture boundaries

Keep platform APIs behind small interfaces so attribution rules can be tested without a real phone:

- `TrafficCounterReader`
- `NetworkContextReader`
- `NetworkEventSource`
- `MeasurementRecorder`
- `AttributionEngine`
- `ValidationExporter`

UI must consume application/domain models. Compose screens must not directly perform counter reads, register callbacks, or write Room entities.

## Development rules

- Every externally delivered network event must be persisted promptly with wall-clock and monotonic timestamps where available.
- Persist the raw observation before deriving higher-level attribution when practical.
- Keep raw observations and derived intervals separable so logic can be re-evaluated after a field test.
- Never overwrite evidence needed to diagnose a missed transition.
- Add a manual test-marker action early (`Mark current network` / named marker) so human ground truth can be aligned with observed events.
- Keep SSID and transport semantics explicit; do not treat `Network` handles as durable cross-reboot identities.
- Avoid BSSID persistence by default.
- Use conservative attribution confidence states rather than one unqualified total.
- Keep background strategy configurable in debug builds so Standard vs candidate mechanisms can be compared.
- Update the nearest source-of-truth doc whenever behavior changes.

## Definition of done for implementation changes

A change is complete when:

- behavior is covered by unit/integration tests where feasible;
- relevant lifecycle/network events are visible in the validation export;
- failure and permission states are represented explicitly;
- no core invariant above is violated;
- docs remain synchronized without duplicating detailed content;
- Android build/tests pass on the supported toolchain.

## Start here

For a new implementation agent, read `docs/implementation-plan.md` next and follow the active milestone gate. Do not build the polished analytics product before the background-measurement feasibility gate is passed.
