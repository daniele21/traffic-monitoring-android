# AGENTS.md

## Purpose

Build a lightweight, privacy-first Android app that attributes device network usage to the network context in which it occurred, while minimizing background execution and battery impact.

The implementation now includes product analytics through M5 instrumentation, but feasibility claims still depend on real-device evidence. Do not mark M1C/M1E/M5 as validated merely because their code or protocols exist.

This file is intentionally short. Do not load the entire documentation tree by default.

## Progressive disclosure

Read only the documents needed for the task:

| If you are working on… | Read |
|---|---|
| Product scope / success criteria | `docs/product-spec.md` |
| Product UX / consumer information architecture | `docs/product-ux.md` |
| Brand / visual identity / product wording | `docs/brand-kit.md` |
| Overall architecture / boundaries | `docs/architecture.md` |
| Counters, identities, attribution | `docs/measurement-engine.md` |
| Background survival / events / FGS | `docs/background-strategy.md` |
| M1C PendingIntent process-absent validation | `docs/m1c-validation.md` |
| M1E 48–72h standard-mode field validation | `docs/m1e-field-validation.md` |
| M5 multi-device/OEM validation | `docs/m5-device-matrix.md` |
| M1B baseline / counter validation | `docs/m1b-validation.md` |
| Local schema / export bundle | `docs/data-and-export.md` |
| Implementation order / current milestone status | `docs/implementation-plan.md` |
| Broader real-device validation | `docs/testing.md` |
| Local emulator debugging / Play release signing | `docs/local-development-and-release.md` |
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
9. All measurement, history and validation data remains local unless the user explicitly exports it.
10. Exportability is part of the measurement system, not a later convenience feature.
11. Record enough lifecycle evidence to determine whether a missed network change was caused by process death, reboot, permission state, recovery delay, OEM restriction or measurement logic.
12. A Foreground Service is an escalation path, not the default architecture, until the standard approach is proven insufficient.
13. Treat PendingIntent network delivery as **availability evidence**, not proof that every network loss is observable.
14. Never use package force-stop as the positive M1C process-death test; it exercises Android's user-stopped package semantics rather than ordinary process absence.
15. Product analytics must never silently redistribute `unattributed` bytes to named networks.
16. Discarded intervals never enter consumer usage totals.
17. Validation evidence and long-lived consumer usage history stay separable so attribution logic remains auditable.

## Product / brand invariants

- The shield is the primary symbol; do not distort or repeatedly decorate it inside product panels.
- Product UI stays flat, restrained and highly legible even when the brand mark itself is dimensional.
- Royal Blue is primary selection/action; Signal Cyan is live/current/peak emphasis.
- Green, amber and red remain semantic status colors rather than decorative branding.
- Prefer direct product language: `Current network`, `Downloaded`, `Uploaded`, `Total used`, `Usage by network`, `Usage trend`, `Peak usage`.
- Keep raw RX/TX, interface names, callback sources, counter status and attribution reasons inside **Monitor** diagnostics.
- Primary consumer navigation stays intentionally small: **Overview** and **Networks**. Monitor is advanced diagnostics, not a peer analytics destination.

## Technical direction

- Language: Kotlin.
- UI: Jetpack Compose.
- Validation persistence: Room / SQLite.
- Consumer history: separate Room database using five-minute usage buckets + network profiles.
- Network state: `ConnectivityManager`, `NetworkRequest`, `NetworkCapabilities`.
- Primary background candidate: `registerNetworkCallback(NetworkRequest, PendingIntent)` + manifest `BroadcastReceiver`.
- In-process diagnostics: `NetworkCallback` where useful.
- Traffic counters: `TrafficStats` cumulative device counters validated against controlled transfers.
- Coarse recovery: unique WorkManager job measured in hours, never high-frequency polling.
- Historical process exits: `ApplicationExitInfo` where supported.
- Foreground Service: only if M1E evidence shows Standard mode is not reliable enough.
- No root requirement.
- No cloud backend in v1.

## Architecture boundaries

Keep platform APIs behind small interfaces/components so attribution rules can be tested without a real phone:

- `TrafficCounterReader`
- `NetworkContextReader`
- `PendingIntentNetworkMonitor`
- `AttributionEngine`
- `UsageBucketAllocator`
- `UsageRepository`
- `ValidationExporter`

UI must consume application/domain models. Compose screens must not directly perform counter reads, register callbacks, or write Room entities.

## Development rules

- Every externally delivered network event must be persisted promptly with wall-clock and monotonic timestamps where available.
- Persist raw observation before higher-level attribution when practical.
- Keep raw observations and derived intervals separable so logic can be re-evaluated after a field test.
- Never overwrite evidence needed to diagnose a missed transition.
- Keep SSID and transport semantics explicit; do not treat Android `Network` handles as durable cross-reboot identities.
- Avoid BSSID persistence by default.
- Use conservative attribution confidence states rather than one unqualified total.
- The WorkManager recovery path is a safety net, not the primary boundary detector.
- Product history ingestion must be transactional/idempotent.
- Update the nearest source-of-truth doc whenever behavior changes.

## Definition of done for implementation changes

A change is complete when:

- behavior is covered by unit/integration tests where feasible;
- relevant lifecycle/network events are visible in validation evidence;
- failure and permission states are represented explicitly;
- no core invariant above is violated;
- docs remain synchronized without duplicating detailed content;
- Android build/tests pass on the supported toolchain.

A field milestone is **validated** only when the corresponding physical-device protocol passes; CI success alone does not close M1E or M5.

## Start here

Read `docs/implementation-plan.md` next. The implementation exists through M5; the active evidence work is M1C/M1E real-device behavior followed by the M5 multi-OEM matrix. Use `docs/product-ux.md` for consumer-facing changes and keep measurement diagnostics behind Monitor.
