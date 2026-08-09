<p align="center">
  <img src="docs/assets/traffic-monitoring-lockup.svg" alt="Traffic Monitoring" width="720" />
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

The project now has two explicit roadmaps:

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
E1  Evidence Coverage + Measurement Health        next implementation
E2  human-readable Evidence Timeline + Pack       planned
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
- restrained usage trend and peak;
- top networks;
- explicit Unattributed usage when present.

E1 adds a compact product-level summary such as:

```text
Evidence coverage
92%

11.4 GB attributed
0.8 GB unattributed
Measurement health · Good
```

### Networks

- ranked network usage;
- human-readable network name;
- friendly transport label;
- percentage and total for the selected timeframe.

### Evidence

Evidence is the human-readable provenance layer: attribution coverage, continuity gaps, measurement health and a normalized timeline.

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

See [`docs/product-ux.md`](docs/product-ux.md), [`docs/evidence-observability-roadmap.md`](docs/evidence-observability-roadmap.md) and [`docs/brand-kit.md`](docs/brand-kit.md).

## Measurement architecture

```text
ConnectivityManager evidence
  ├─ in-process NetworkCallback
  └─ PendingIntent availability callback
              +
TrafficStats cumulative counters
              ↓
       AttributionEngine
              ↓
 inferred / unattributed / discarded
              ↓
   validation evidence (auditable)
              +
 five-minute product UsageBuckets
              ↓
     Overview / Networks
              +
 future EvidenceSummaryCalculator
              ↓
 Evidence Coverage / Health / Experiments
```

The product history is stored separately from raw validation evidence. Accepted intervals are allocated into fixed five-minute buckets; integer byte totals are preserved exactly, discarded intervals never enter product totals, and unattributed usage remains explicit.

## Evidence-first direction

The next product implementation is **E1 — Evidence Coverage + Measurement Health**.

The planned evolution is:

```text
Attribution evidence
        ↓
Evidence Coverage + Health
        ↓
Human-readable Evidence Timeline
        ↓
Evidence Pack
        ↓
Experiment Mode
        ↓
Assertions
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
0.2.0-m5-dev
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
