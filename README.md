<p align="center">
  <img src="docs/assets/traffic-monitoring-lockup.svg" alt="Traffic Monitoring" width="720" />
</p>

<p align="center"><strong>Know your network usage.</strong><br/>See how much data you use, when you use it, and on which network.</p>

# Traffic Monitoring Android

Privacy-first Android network-usage monitoring with a deliberately simple product surface and a separate auditable diagnostic layer.

The product question is simple:

> How much data did this Android device use, when, and on which Wi-Fi / hotspot / mobile network?

When attribution is uncertain, Traffic Monitoring keeps those bytes **Unattributed** rather than assigning them to the wrong network.

## Current state

Implementation now reaches **M5**, while the real-device feasibility gates remain explicit:

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

## Minimal product UX

The default application surface is now intentionally consumer-oriented rather than a validation console.

### Overview

- **Current network** + simple monitoring state;
- **Total used**;
- **Downloaded** / **Uploaded**;
- Today / 7 days / 30 days / This month / **Custom**;
- restrained usage trend and peak;
- top networks;
- explicit Unattributed usage when present.

### Networks

- ranked network usage;
- human-readable network name;
- friendly transport label;
- percentage and total for the selected timeframe.

### Monitor

Advanced measurement detail stays behind **Monitor**:

- raw network observations;
- TrafficStats counter evidence;
- attribution intervals/confidence;
- PendingIntent and in-process callbacks;
- recovery/lifecycle evidence;
- manual markers;
- validation ZIP export.

Rule: if it explains **network usage**, it belongs in the product UI. If it explains **how Android measured it**, it belongs in Monitor.

See [`docs/product-ux.md`](docs/product-ux.md) and [`docs/brand-kit.md`](docs/brand-kit.md).

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
```

The product history is stored separately from raw validation evidence. Accepted intervals are allocated into fixed five-minute buckets; integer byte totals are preserved exactly, discarded intervals never enter product totals, and unattributed usage remains explicit.

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

The diagnostic ZIP includes:

- `network-events.csv`
- `counter-snapshots.csv`
- `attribution-intervals.csv`
- `lifecycle-events.csv`
- `manual-markers.csv`
- `manifest.json`
- `summary.json`
- `README.txt`

It records enough lifecycle/background evidence to explain uncertainty without collecting packet contents, destinations, DNS queries, browsing history, account identifiers or device hardware identifiers.

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

- [`AGENTS.md`](AGENTS.md) — implementation-agent entry point.
- [`docs/README.md`](docs/README.md) — documentation map.
- [`docs/product-ux.md`](docs/product-ux.md) — minimal consumer UX.
- [`docs/brand-kit.md`](docs/brand-kit.md) — brand and product-language rules.
- [`docs/implementation-plan.md`](docs/implementation-plan.md) — milestone status through M5.
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

The implementation can move ahead; confidence in network attribution must continue to come from exported real-device runs.
