# AGENTS.md

## Purpose

Build a lightweight, privacy-first Android app that attributes device network usage to the network context in which it occurred, while minimizing background execution and battery impact.

The product positioning is now:

> **Evidence-first network observability for Android.**

Primary promise:

> **Know your network usage — and the evidence behind it.**

The implementation includes product analytics through M5 instrumentation, but feasibility claims still depend on real-device evidence. Do not mark M1C/M1E/M5 as validated merely because their code or protocols exist.

The evidence-product roadmap is tracked separately as E0–E6. Do not confuse implemented evidence UX with proven Android measurement reliability.

This file is intentionally short. Do not load the entire documentation tree by default.

## Progressive disclosure

Read only the documents needed for the task:

| If you are working on… | Read |
|---|---|
| Product positioning / scope / truthfulness | `docs/product-spec.md` |
| Evidence Coverage / experiments / assertions / Evidence Pack | `docs/evidence-observability-roadmap.md` |
| Product UX / information architecture | `docs/product-ux.md` |
| Brand / visual identity / product wording | `docs/brand-kit.md` |
| Overall architecture / boundaries | `docs/architecture.md` |
| Counters, identities, attribution | `docs/measurement-engine.md` |
| Background survival / events / FGS | `docs/background-strategy.md` |
| M1C PendingIntent process-absent validation | `docs/m1c-validation.md` |
| M1E 48–72h standard-mode field validation | `docs/m1e-field-validation.md` |
| M5 multi-device/OEM validation | `docs/m5-device-matrix.md` |
| M1B baseline / counter validation | `docs/m1b-validation.md` |
| Local schema / export bundle | `docs/data-and-export.md` |
| Combined M/E implementation order | `docs/implementation-plan.md` |
| Broader real-device validation | `docs/testing.md` |
| Local emulator debugging / Play release signing | `docs/local-development-and-release.md` |
| Why key choices were made | `docs/decisions.md` |
| Documentation map only | `docs/README.md` |

For most changes, `AGENTS.md` + one or two targeted documents should be enough.

## Core measurement invariants

1. **Event-driven before polling-driven.** Network changes should define attribution boundaries whenever Android can deliver them.
2. Do not poll every few seconds/minutes in the background just to keep the app alive.
3. A traffic delta may be attributed to a network only when the interval's network context is sufficiently known.
4. If an app/process gap makes the network path ambiguous, prefer `unattributed` over guessing.
5. Counter reset, reboot, unsupported values, interface recreation or decreasing counters must never create negative or giant synthetic usage.
6. Wi-Fi SSID enriches identity. If permission or platform behavior hides it, tracking must degrade explicitly rather than silently pretending networks are distinguishable.
7. Do not store packet contents, DNS queries, destinations, URLs, browsing history, payloads or application-level traffic metadata by default.
8. Do not use a local VPN/VpnService merely to inspect or intercept user traffic for v1.
9. All measurement, history, experiments and evidence remain local unless the user explicitly exports or enables an integration.
10. Exportability is part of the measurement/evidence system, not a later convenience feature.
11. Record enough lifecycle evidence to determine whether a missed network change was caused by process death, reboot, permission state, recovery delay, OEM restriction or measurement logic.
12. A Foreground Service is an escalation path, not the default architecture, until the standard approach is proven insufficient.
13. Treat PendingIntent network delivery as **availability evidence**, not proof that every network loss is observable.
14. Never use package force-stop as the positive M1C process-death test; it exercises Android's user-stopped package semantics rather than ordinary process absence.
15. Product analytics must never silently redistribute `unattributed` bytes to named networks.
16. Discarded intervals never enter consumer usage totals.
17. Validation evidence and long-lived consumer usage history stay separable so attribution logic remains auditable.

## Evidence-first invariants

1. **A metric without provenance is incomplete.** Evidence summaries must reconcile with the underlying attribution data.
2. **Evidence Coverage and Measurement Health are separate.** Do not invent one opaque trust score.
3. User-facing Evidence is not the raw Monitor console. Translate technical provenance into deterministic, understandable concepts.
4. Every evidence metric definition is versioned when semantics change.
5. Experiments reference bounded normal measurement evidence; do not build a second collection pipeline.
6. Assertions return only `PASS`, `FAIL` or `INCONCLUSIVE`.
7. Insufficient evidence must produce `INCONCLUSIVE`, never PASS by absence of evidence.
8. Evidence Packs are explicit exports and must state methodology/limitations.
9. Integrity hashes prove exported-file integrity only; never call them device attestation.
10. Optional app-level context must clearly reflect Android API permission/granularity limits and must not imply packet-level precision.
11. External observability export is optional, disabled by default and downstream of the local evidence model.
12. Do not couple domain entities directly to OpenTelemetry/vendor SDK classes; use a neutral evidence model first.
13. AI-generated narrative must never replace deterministic evidence, assertion reasons or source references.

## Product / brand invariants

- The shield is the primary symbol; do not distort or repeatedly decorate it inside product panels.
- Product UI stays flat, restrained and highly legible even when the brand mark itself is dimensional.
- Royal Blue is primary selection/action; Signal Cyan is live/current/peak emphasis.
- Green, amber and red remain semantic status colors rather than decorative branding.
- Prefer direct product language: `Current network`, `Downloaded`, `Uploaded`, `Total used`, `Usage by network`, `Usage trend`, `Peak usage`, `Evidence coverage`, `Measurement health`.
- Keep raw RX/TX, interface names, callback sources, counter status and raw attribution reasons inside **Monitor** diagnostics.
- Current primary consumer navigation stays intentionally small: **Overview** and **Networks**. Experiments is added only when E3 is implemented.
- Evidence is progressively disclosed from Usage/Experiment surfaces rather than exposed as a dense technical dashboard by default.

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
- Evidence summaries: pure deterministic domain/application logic over persisted attribution/provenance.
- Foreground Service: only if M1E evidence shows Standard mode is not reliable enough.
- No root requirement.
- No cloud backend requirement.

## Architecture boundaries

Keep platform APIs behind small interfaces/components so attribution and evidence rules can be tested without a real phone:

- `TrafficCounterReader`
- `NetworkContextReader`
- `PendingIntentNetworkMonitor`
- `AttributionEngine`
- `UsageBucketAllocator`
- `UsageRepository`
- future `EvidenceSummaryCalculator`
- future `EvidenceTimelineBuilder`
- future `ExperimentCoordinator`
- future `AssertionEngine`
- `ValidationExporter`
- future `EvidencePackExporter`

UI must consume application/domain models. Compose screens must not directly perform counter reads, register callbacks, write Room entities or call external telemetry SDKs.

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
- Evidence calculations and assertions must have deterministic unit fixtures.
- Update the nearest source-of-truth doc whenever behavior changes.

## Definition of done for implementation changes

A change is complete when:

- behavior is covered by unit/integration tests where feasible;
- evidence/usage totals reconcile deterministically when relevant;
- relevant lifecycle/network events are visible in validation evidence;
- failure, uncertainty and permission states are represented explicitly;
- no core/evidence invariant above is violated;
- docs remain synchronized without duplicating detailed content;
- Android build/tests pass on the supported toolchain.

A field milestone is **validated** only when the corresponding physical-device protocol passes; CI success alone does not close M1E or M5.

## Start here

Read `docs/implementation-plan.md` next.

For reliability work, continue M1C/M1E real-device behavior followed by the M5 multi-OEM matrix.

For differentiated product work, the next recommended milestone is **E1 — Evidence Coverage + Measurement Health** in `docs/evidence-observability-roadmap.md`.
