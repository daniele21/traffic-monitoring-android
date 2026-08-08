<p align="center">
  <img src="docs/assets/traffic-monitoring-lockup.svg" alt="Traffic Monitoring" width="720" />
</p>

<p align="center"><strong>Know your network usage.</strong><br/>See how much data you use, when you use it, and on which network.</p>

# Traffic Monitoring Android

Android feasibility and validation project for low-power, privacy-first network-usage attribution.

The product goal is simple to state:

> How much data did this Android device use on each Wi-Fi / hotspot / mobile network?

The repository deliberately proves the measurement model before building the full analytics product. When attribution is uncertain, bytes remain **unattributed / uncertain** instead of being confidently assigned to the wrong network.

## Current phase

**M1C — PendingIntent background event spike.**

Completed gates:

- **M1A:** Room persistence, lifecycle evidence, manual markers and ZIP export validated on emulator;
- **M1B:** device-wide cumulative `TrafficStats` counters + in-process `ConnectivityManager.NetworkCallback` validated on emulator, including a controlled 10 MiB transfer and exact interval/counter reconciliation;
- **M1C:** implementation is now present; process-absent background delivery is the active validation gate.

M1C uses:

```text
registerNetworkCallback(NetworkRequest, PendingIntent)
        ↓
manifest BroadcastReceiver
        ↓
source=pending_intent network evidence
+ TrafficStats counter snapshot
        ↓
Room → validation ZIP
```

Android documents that the PendingIntent request may outlive the calling application and is delivered when a matching network becomes **available**. M1C measures whether those availability events are sufficient for the product; it does not assume loss events are delivered.

See [`docs/m1c-validation.md`](docs/m1c-validation.md) for the exact test protocol.

## Brand system

The Android application now follows the approved Traffic Monitoring shield brand direction:

- Midnight `#020D2C` and Deep Navy `#0E2345` for high-trust framing;
- Royal Blue `#002996` for primary action/selection;
- Network Blue `#207CCE` for supporting network emphasis;
- Signal Cyan `#0DC1F9` for live/background status;
- semantic green/amber/red reserved only for state;
- flat, restrained UI even though the shield identity can feel more dimensional.

The product language also follows the brand kit: **Current network**, **Downloaded**, **Uploaded**, **Total used**, **Usage by network**, **Peak usage**. Raw counter terminology stays in diagnostics.

See [`docs/brand-kit.md`](docs/brand-kit.md).

## Local emulator debug

The repository includes a standard Gradle Wrapper and a local-first emulator workflow:

```bash
# list configured AVDs
bash scripts/run-emulator-debug.sh --list-avds

# build/install/launch on the first online emulator/device
bash scripts/run-emulator-debug.sh

# clean validation run on a named emulator
bash scripts/run-emulator-debug.sh --avd Pixel_8_API_35 --clear-data --logs
```

Debug builds use:

```text
com.daniele21.trafficmonitoring.debug
```

so debug and Play/release builds can coexist.

## M1C quick test

Start fresh:

```bash
git checkout agent/m1a-validation-app
git pull origin agent/m1a-validation-app
bash scripts/run-emulator-debug.sh --clear-data
```

Confirm **Background capture = Armed**, put the app in the background, then kill the ordinary background process — **not force-stop**:

```bash
adb shell am kill com.daniele21.trafficmonitoring.debug
sleep 2
adb shell pidof com.daniele21.trafficmonitoring.debug || true
```

Create a new Wi-Fi availability event:

```bash
adb shell svc wifi disable
sleep 8
date -u '+%Y-%m-%dT%H:%M:%SZ'
adb shell svc wifi enable
sleep 12
```

Reopen without clearing data:

```bash
bash scripts/run-emulator-debug.sh
```

Export the validation ZIP. The decisive evidence is a `network-events.csv` row with:

```text
source=pending_intent
kind=available
```

at a timestamp near the deliberate network availability, not merely when the UI is reopened.

Full protocol: [`docs/m1c-validation.md`](docs/m1c-validation.md).

## Validation evidence

Every experiment is auditable through the same local ZIP bundle:

- `network-events.csv`
- `counter-snapshots.csv`
- `attribution-intervals.csv`
- `lifecycle-events.csv`
- `manual-markers.csv`
- `manifest.json`
- `summary.json`
- `README.txt`

M1C distinguishes event source explicitly:

```text
manual / startup
callback        = in-process NetworkCallback
pending_intent  = background PendingIntent receiver
```

The export never contains packet contents, destinations, DNS queries, account identifiers or hardware identifiers.

## Play internal testing / signed AAB

Release signing stays local-only. Create the upload key once, store its password in macOS Keychain, then build a signed AAB:

```bash
bash scripts/build-play-release.sh create-key
bash scripts/build-play-release.sh setup
bash scripts/build-play-release.sh build
```

For a subsequent Play upload:

```bash
bash scripts/build-play-release.sh build-next
```

CI publishes an intentionally unsigned release AAB that can be signed only on the developer machine. See [`docs/local-development-and-release.md`](docs/local-development-and-release.md).

## Documentation

- [`AGENTS.md`](AGENTS.md) — implementation-agent entry point.
- [`docs/README.md`](docs/README.md) — documentation map.
- [`docs/brand-kit.md`](docs/brand-kit.md) — approved brand rules applied to Android.
- [`docs/product-spec.md`](docs/product-spec.md) — problem, scope and success criteria.
- [`docs/architecture.md`](docs/architecture.md) — components and runtime data flow.
- [`docs/measurement-engine.md`](docs/measurement-engine.md) — counters, identity and attribution boundaries.
- [`docs/background-strategy.md`](docs/background-strategy.md) — execution candidates and escalation path.
- [`docs/data-and-export.md`](docs/data-and-export.md) — evidence schema/export contract.
- [`docs/implementation-plan.md`](docs/implementation-plan.md) — milestone gates.
- [`docs/testing.md`](docs/testing.md) — broader field validation matrix.
- [`docs/m1b-validation.md`](docs/m1b-validation.md) — completed M1B evidence and protocol.
- [`docs/m1c-validation.md`](docs/m1c-validation.md) — active process-absent PendingIntent test.
- [`docs/local-development-and-release.md`](docs/local-development-and-release.md) — emulator and release workflow.
- [`docs/decisions.md`](docs/decisions.md) — architectural decisions.

## Technical direction

- Kotlin + Jetpack Compose.
- Room / SQLite, entirely local.
- `ConnectivityManager` / `NetworkCapabilities` for network context.
- `WifiInfo` when Android exposes Wi-Fi identity under the current permission state.
- `TrafficStats` for low-cost cumulative device counters.
- in-process `NetworkCallback` for diagnostics while alive.
- `registerNetworkCallback(NetworkRequest, PendingIntent)` as the first background candidate.
- WorkManager only later as a coarse recovery/liveness safety net.
- no packet capture, VPN interception, accessibility service, root or cloud backend.

## Feasibility rule

Do not add a permanent Foreground Service just because it is the easiest way to keep code alive.

First prove whether the standard event-driven Android path is reliable enough. If it is not, quantify the gap and only then run the explicit Foreground Service A/B fallback experiment.
