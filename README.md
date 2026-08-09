<p align="center">
  <img src="docs/assets/shield_wordmark.png" alt="Traffic Monitoring" width="600" />
</p>

<p align="center"><strong>Evidence-first network observability for Android.</strong><br/>Know your network usage — and the evidence behind it.</p>

# Traffic Monitoring Android

Traffic Monitoring is a privacy-first Android observability project that measures network usage while preserving the evidence quality behind every attribution.

It answers two questions:

> **What happened?** How much data did this Android device use, when, and on which Wi-Fi / hotspot / mobile network?

> **How do we know?** How much of that usage is actually supported by trustworthy attribution evidence, and where did measurement continuity break down?

When attribution is uncertain, Traffic Monitoring keeps those bytes **Unattributed** rather than assigning them to the wrong network.

The engineering philosophy is simple:

> **Measure before claiming. Preserve uncertainty. Keep the user in control. Make results auditable.**

## Why this is different

Traffic Monitoring is not positioned as a generic data-usage meter and it is not a packet analyzer.

```text
Usage
What happened?
        ↓
Evidence
How trustworthy is the attribution?
        ↓
Experiments
Can a network-behavior claim be evaluated?
        ↓
Monitor
How did Android actually measure it?
```

The core product does not require packet contents, browsing history, destinations, DNS logging, a local VPN or a cloud backend.

## Two development tracks

The project has two explicit roadmaps:

```text
M roadmap
Android measurement reliability / feasibility

E roadmap
Evidence-first observability product
```

### Measurement state

```text
M1A  persistence / export                         PASS on emulator
M1B  counters + in-process network evidence       PASS on emulator
M1C  PendingIntent background availability        implemented · field validation pending
M1D  recovery + lifecycle hardening               implemented · field validation pending
M1E  48–72h Standard-mode protocol                ready · physical-device run pending
M2   deterministic attribution engine             implemented · physical replay acceptance pending
M3   persistent product usage history             implemented
M4   minimal Android analytics UX                 implemented
M5   OEM/device reliability instrumentation        implemented · multi-device matrix pending
```

A green CI build does not turn M1E/M5 into validated milestones. Those require real phones and exported evidence.

### Evidence-product roadmap

```text
E0  positioning / evidence semantics              documented
E1  Evidence Coverage + Measurement Health        implemented · product validation pending
E2  human-readable Evidence Timeline + Pack       next
E3  Experiment Mode                               planned
E4  deterministic Assertions                      planned
E5  optional app-level historical context         exploratory
E6  optional open observability / OTLP adapter    planned later
```

Full plan: [`docs/evidence-observability-roadmap.md`](docs/evidence-observability-roadmap.md).

## Minimal product UX

The default application surface is intentionally simple.

### Overview

- **Current network** + simple monitoring state;
- **Total used**;
- **Downloaded** / **Uploaded**;
- Today / 7 days / 30 days / This month / **Custom**;
- **Evidence Coverage** + **Measurement Health**;
- restrained usage trend and peak;
- top networks;
- explicit Unattributed usage when present.

E1 now exposes a compact product-level summary such as:

```text
Evidence
92% Evidence coverage
Measurement health · Good
View evidence →
```

### Networks

- ranked network usage;
- human-readable network name;
- friendly transport label;
- percentage and total for the selected timeframe.

### Evidence

Evidence is the human-readable provenance layer. E1 currently shows:

- Evidence Coverage;
- Measurement Health with explicit reasons;
- attributed vs unattributed usage;
- continuity gaps;
- discarded intervals;
- longest continuity gap;
- Coverage v1 methodology.

It is deliberately different from Monitor.

### Experiments

After E3, users can create bounded evaluation runs such as a local-inference privacy test, inspect observed traffic/evidence and export a self-contained Evidence Pack.

Assertions in E4 use three truthful outcomes:

```text
PASS
FAIL
INCONCLUSIVE
```

Insufficient evidence never becomes PASS by absence of evidence.

### Monitor

Advanced measurement detail stays behind **Monitor**:

- raw network observations;
- TrafficStats counter evidence;
- attribution intervals/confidence;
- PendingIntent and in-process callbacks;
- recovery/lifecycle evidence;
- manual markers;
- validation ZIP export.

Rule:

> If it explains **what happened**, it belongs in Usage.  
> If it explains **how trustworthy the result is**, it belongs in Evidence.  
> If it explains **how Android measured it**, it belongs in Monitor.

See [`docs/product-ux.md`](docs/product-ux.md), [`docs/e1-evidence-coverage.md`](docs/e1-evidence-coverage.md), [`docs/evidence-observability-roadmap.md`](docs/evidence-observability-roadmap.md) and [`docs/brand-kit.md`](docs/brand-kit.md).

<p align="center">
  <img src="docs/assets/architecture-diagram.jpg" alt="Traffic Monitoring Android Architecture" width="100%" />
</p>

The system follows a strict 4-tier local-first architecture that separates low-level Android platform signals from attribution logic, derived product analytics, and user-facing observability surfaces:

### Tier 1 — Android platform signals
- **`ConnectivityManager`**: Listens for network interface transitions using in-process [`NetworkCallback`](app/src/main/java/com/daniele21/trafficmonitoring/platform/InProcessNetworkMonitor.kt) when active, and manifest [`BroadcastReceiver` + `PendingIntent`](app/src/main/java/com/daniele21/trafficmonitoring/platform/PendingIntentNetworkMonitor.kt) for background availability events.
- **`TrafficStats`**: Reads cumulative device RX/TX counters ([`TrafficCounterReader`](app/src/main/java/com/daniele21/trafficmonitoring/platform/TrafficCounterReader.kt)) across system interfaces while tracking source metadata to handle counter resets and boot shifts safely.
- **`Lifecycle & recovery`**: Tracks process start/exit ([`ProcessExitRecorder`](app/src/main/java/com/daniele21/trafficmonitoring/background/ProcessExitRecorder.kt)), boot cycles, and schedules coarse WorkManager safety-net checks ([`RecoveryWorker`](app/src/main/java/com/daniele21/trafficmonitoring/background/RecoveryWorker.kt)) to catch missed boundaries.
- **`Android constraints`**: Handles system-level execution rules explicitly—Doze mode, permissions (SSID visibility), VPN context, and aggressive OEM background kill behaviors.

### Tier 2 — Raw evidence and attribution
- **`Platform adapters`**: Normalizes platform callbacks ([`CurrentNetworkReader`](app/src/main/java/com/daniele21/trafficmonitoring/platform/CurrentNetworkReader.kt)) and sanitizes network identity (e.g. Wi-Fi SSID enrichment vs. transport fallback).
- **`ValidationRepository`**: Persists raw, un-summarized network observations, counter snapshots, lifecycle events, and manual markers into Room/SQLite ([`ValidationDatabase`](app/src/main/java/com/daniele21/trafficmonitoring/data/ValidationDatabase.kt)) for auditable replay.
- **`AttributionEngine`**: Pure domain logic ([`AttributionEngine`](app/src/main/java/com/daniele21/trafficmonitoring/domain/AttributionEngine.kt)) that computes usage intervals with explicit confidence classifications: **`attributed`**, **`unattributed`** (uncertain boundaries), or **`discarded`** (counter resets or invalid states).

### Tier 3 — Derived product state
- **`Usage analytics`**: Allocates accepted attribution intervals into fixed 5-minute buckets within a separate product database ([`UsageDatabase`](app/src/main/java/com/daniele21/trafficmonitoring/usage/UsageDatabase.kt), [`UsageBucketAllocator`](app/src/main/java/com/daniele21/trafficmonitoring/usage/UsageBucketAllocator.kt)) for trend, peak, and per-network totals.
- **`Evidence model`**: Deterministically evaluates top-line metrics ([`EvidenceSummary`](app/src/main/java/com/daniele21/trafficmonitoring/evidence/EvidenceSummary.kt))—computing **Evidence Coverage %**, **Measurement Health** status (Good, Limited, Degraded), and continuity gaps.
- **`Experiments / evaluation`**: Planned E3/E4 layer to evaluate bounded network hypothesis runs returning explicit outcomes (`PASS`, `FAIL`, `INCONCLUSIVE`).

### Tier 4 — User experience and export
- **`Overview + Networks`**: Primary analytics dashboard ([`ProductScreen`](app/src/main/java/com/daniele21/trafficmonitoring/ui/ProductScreen.kt)) presenting total usage, top networks, usage trends, and high-level health.
- **`Evidence`**: Human-readable provenance screen ([`EvidenceComponents`](app/src/main/java/com/daniele21/trafficmonitoring/ui/EvidenceComponents.kt)) detailing coverage, health reasons, continuity gaps, and trust factors.
- **`Monitor`**: Technical diagnostics console ([`ObservabilityDashboardScreen`](app/src/main/java/com/daniele21/trafficmonitoring/ui/ObservabilityDashboardScreen.kt)) for raw events, counter deltas, callback logs, and manual marker injection.
- **`Export`**: Packaging pipeline ([`ValidationExporter`](app/src/main/java/com/daniele21/trafficmonitoring/export/ValidationExporter.kt)) that exports engineering diagnostic ZIPs and future Evidence Packs.

> **Privacy boundary**: The architecture operates entirely on device. It records **no packet contents**, **no DNS queries or URLs**, **no browsing history**, requires **no local VPN interception**, and needs **no cloud backend**.

The product history is stored separately from raw validation evidence. Accepted intervals are allocated into fixed five-minute buckets; integer byte totals are preserved exactly, discarded intervals never enter product totals, and unattributed usage remains explicit.

E1 deliberately reads discarded/continuity evidence from the validation store because those intervals must affect Measurement Health without contaminating consumer usage totals.

## Evidence Coverage v1

```text
accountedBytes = attributedBytes + unattributedBytes
Evidence Coverage = attributedBytes / accountedBytes
```

If there is no accountable usage, the app shows **Not enough data** rather than manufacturing 100% coverage.

Measurement Health is evaluated separately:

```text
Good      coverage >= 95% and no discarded evidence
Limited   incomplete coverage or some discarded evidence
Degraded  coverage < 80%, a gap >= 30m, or >= 3 discarded intervals
```

The exact semantics and tests are documented in [`docs/e1-evidence-coverage.md`](docs/e1-evidence-coverage.md).

## Evidence-first direction

The next product milestone is **E2 — Evidence Timeline + Evidence Pack**.

The evolution is:

```text
Attribution evidence
        ↓
Evidence Coverage + Health        E1 ✓
        ↓
Human-readable Evidence Timeline  E2
        ↓
Evidence Pack                     E2
        ↓
Experiment Mode                   E3
        ↓
Assertions                       E4
        ↓
optional app context / open observability export
```

The Evidence Pack is separate from the engineering validation ZIP. It is designed to communicate a result, methodology, uncertainty and machine-readable provenance to someone who did not run the test.

## Background strategy

Standard mode is intentionally event-driven:

```text
PendingIntent network availability
+ in-process callback while alive
+ TrafficStats counters
+ unique 4-hour WorkManager recovery safety net
+ process / boot / exit evidence
+ no permanent Foreground Service
```

The WorkManager job is recovery/liveness insurance, not high-frequency measurement. A Foreground Service remains a conditional fallback only if M1E evidence shows Standard mode systematically misses product-critical boundaries.

## Local emulator debug

```bash
# list configured AVDs
bash scripts/run-emulator-debug.sh --list-avds

# build/install/launch
bash scripts/run-emulator-debug.sh

# clean local run
bash scripts/run-emulator-debug.sh --avd Pixel_8_API_35 --clear-data --logs
```

Debug application ID:

```text
com.daniele21.trafficmonitoring.debug
```

Current development version:

```text
0.3.0-e1-dev
```

## Physical-device field validation

Capture the device/OEM context first:

```bash
bash scripts/device-validation-status.sh --device SERIAL
```

Then use:

- [`docs/m1c-validation.md`](docs/m1c-validation.md) for process-absent PendingIntent evidence;
- [`docs/m1e-field-validation.md`](docs/m1e-field-validation.md) for the 48–72 hour Standard-mode run;
- [`docs/m5-device-matrix.md`](docs/m5-device-matrix.md) for Pixel/AOSP, Samsung and aggressive-background OEM validation.

Do **not** use package force-stop as the positive M1C test. Force-stop is useful later as an explicit negative-control case in M5.

## Validation export

The engineering diagnostic ZIP includes:

- `network-events.csv`
- `counter-snapshots.csv`
- `attribution-intervals.csv`
- `lifecycle-events.csv`
- `manual-markers.csv`
- `manifest.json`
- `summary.json`
- `README.txt`

It records enough lifecycle/background evidence to explain uncertainty without collecting packet contents, destinations, DNS queries, browsing history, account identifiers or device hardware identifiers.

The planned **Evidence Pack** is a separate product-facing export with a concise report, methodology, environment, metrics and integrity manifest.

## Brand system

The app follows the approved shield direction:

- Midnight `#020D2C`;
- Deep Navy `#0E2345`;
- Royal Blue `#002996` for primary selection/action;
- Network Blue `#207CCE`;
- Signal Cyan `#0DC1F9` for live/peak emphasis;
- semantic green/amber/red only for actual state.

The mark may be dimensional; the UI remains flat, restrained and highly legible.

## Play internal testing / signed AAB

Release signing remains local-only:

```bash
bash scripts/build-play-release.sh create-key
bash scripts/build-play-release.sh setup
bash scripts/build-play-release.sh build
```

For a later upload:

```bash
bash scripts/build-play-release.sh build-next
```

CI publishes an intentionally unsigned release AAB for local signing. See [`docs/local-development-and-release.md`](docs/local-development-and-release.md).

## Documentation

- [`AGENTS.md`](AGENTS.md) — implementation-agent entry point and invariants.
- [`docs/README.md`](docs/README.md) — documentation map.
- [`docs/product-spec.md`](docs/product-spec.md) — evidence-first positioning, scope and truthfulness.
- [`docs/evidence-observability-roadmap.md`](docs/evidence-observability-roadmap.md) — E0–E6 product roadmap.
- [`docs/e1-evidence-coverage.md`](docs/e1-evidence-coverage.md) — implemented Coverage/Health semantics, architecture and tests.
- [`docs/product-ux.md`](docs/product-ux.md) — Usage / Evidence / Experiments / Monitor UX boundary.
- [`docs/brand-kit.md`](docs/brand-kit.md) — brand and product-language rules.
- [`docs/architecture.md`](docs/architecture.md) — measurement, evidence and experiment architecture.
- [`docs/implementation-plan.md`](docs/implementation-plan.md) — combined M/E milestone status.
- [`docs/measurement-engine.md`](docs/measurement-engine.md) — counters/identity/attribution semantics.
- [`docs/background-strategy.md`](docs/background-strategy.md) — background strategy and FGS escalation.
- [`docs/m1b-validation.md`](docs/m1b-validation.md) — validated counter baseline.
- [`docs/m1c-validation.md`](docs/m1c-validation.md) — process-absent PendingIntent protocol.
- [`docs/m1e-field-validation.md`](docs/m1e-field-validation.md) — 48–72 hour field protocol.
- [`docs/m5-device-matrix.md`](docs/m5-device-matrix.md) — cross-OEM reliability matrix.
- [`docs/data-and-export.md`](docs/data-and-export.md) — validation evidence/export contract.
- [`docs/local-development-and-release.md`](docs/local-development-and-release.md) — emulator + signing workflow.

## Feasibility rule

Do not make measurement reliability claims that the field evidence has not earned.

Do not make observability claims that the underlying Android data source cannot support.

The implementation can move ahead; confidence in the result must continue to come from deterministic evidence and exported real-device runs.
