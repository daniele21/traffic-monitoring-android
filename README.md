# Traffic Monitoring Android

Android feasibility and validation project for low-power, privacy-first network-usage attribution.

The goal is to answer a deceptively simple question:

> How much data did this Android device use on each Wi-Fi / hotspot / mobile network?

The project starts deliberately as a **measurement spike**, not as a polished consumer app. Before building dashboards, we must prove that Android can reliably detect network boundaries in the background and associate device traffic deltas with the correct network without requiring high-frequency polling.

## Current phase

**M1 — Background measurement feasibility.**

The first implementation will validate:

- whether network changes can wake the app reliably while its UI/process is not active;
- whether Android traffic counters are suitable for monotonic delta measurement;
- whether Wi-Fi identities can be read with acceptable permissions;
- whether VPN, reboot, Doze, Battery Saver and OEM process management create attribution gaps;
- how much battery/background activity the approach causes;
- whether a Foreground Service is actually necessary.

No production analytics UI should be built until this feasibility gate is passed.

## Local debug and Play internal testing

The repository includes the same local-first Android workflow used by `android-local-llm-harness`:

```bash
# list configured Android emulators
bash scripts/run-emulator-debug.sh --list-avds

# build/install/launch the debug app on a running emulator
bash scripts/run-emulator-debug.sh

# or start a named AVD and launch the app
bash scripts/run-emulator-debug.sh --avd Pixel_8_API_35 --logs
```

Debug builds use `com.daniele21.trafficmonitoring.debug`, so they can coexist with the release/Play application ID.

Release signing is local-only. Create the private Play upload key once, store its password in macOS Keychain, then generate the signed AAB:

```bash
bash scripts/build-play-release.sh create-key
bash scripts/build-play-release.sh setup
bash scripts/build-play-release.sh build
```

For a later Play upload, increment `versionCode` and build in one command:

```bash
bash scripts/build-play-release.sh build-next
```

CI also publishes an intentionally unsigned release AAB that can be downloaded and signed only on the developer Mac. See [`docs/local-development-and-release.md`](docs/local-development-and-release.md) for the complete workflow and security rules.

## Preferred experiment direction

The primary hypothesis is an **event-driven** architecture rather than continuous polling:

```text
Android network change
        ↓
ConnectivityManager network event
        ↓
read traffic counters + current network context
        ↓
close previous attribution interval
        ↓
open new network baseline
        ↓
local Room database
```

The first candidate to validate is `ConnectivityManager.registerNetworkCallback(NetworkRequest, PendingIntent)`. Android documents that this request may outlive the calling application, making it potentially suitable for waking a broadcast receiver on relevant network availability changes without a permanent foreground service.

A low-frequency recovery worker (hours, not seconds/minutes) will be evaluated as a safety net, not as the primary attribution mechanism.

## Validation export is a first-class feature

The spike is not useful unless its behavior can be inspected after hours or days with the screen off.

The app therefore must support exporting a local validation bundle containing, at minimum:

- observed network events;
- traffic counter snapshots;
- derived attribution intervals;
- app/process lifecycle events;
- recovery-worker executions;
- manual test markers entered by the tester;
- permission/background-mode state;
- device/app metadata required to interpret the run.

The export must make it possible to compare **what the tester intentionally did** with **what the app actually observed**.

See [`docs/data-and-export.md`](docs/data-and-export.md) and [`docs/testing.md`](docs/testing.md).

## Documentation

Start with [`AGENTS.md`](AGENTS.md), then read only the document relevant to the current task.

- [`docs/README.md`](docs/README.md) — documentation map.
- [`docs/product-spec.md`](docs/product-spec.md) — problem, scope and success criteria.
- [`docs/architecture.md`](docs/architecture.md) — components and runtime data flow.
- [`docs/measurement-engine.md`](docs/measurement-engine.md) — counters, network identity and attribution boundaries.
- [`docs/background-strategy.md`](docs/background-strategy.md) — background execution candidates and escalation path.
- [`docs/data-and-export.md`](docs/data-and-export.md) — local schema and validation export format.
- [`docs/implementation-plan.md`](docs/implementation-plan.md) — milestone order and acceptance gates.
- [`docs/testing.md`](docs/testing.md) — scripted real-device validation matrix.
- [`docs/local-development-and-release.md`](docs/local-development-and-release.md) — emulator debug, upload-key management and signed AAB generation.
- [`docs/decisions.md`](docs/decisions.md) — architectural decision log.

## Initial technical direction

- Kotlin.
- Jetpack Compose for the minimal validation UI.
- Room / SQLite for durable local logs.
- `ConnectivityManager` / `NetworkCapabilities` for network context.
- `WifiInfo` for connected Wi-Fi metadata when permission allows it.
- `TrafficStats` as a low-cost counter source to validate.
- `NetworkStatsManager` as a reference/reconciliation source where Usage Access is granted; not assumed to provide per-SSID history.
- WorkManager only for coarse recovery/liveness checks.
- No packet capture, VPN-based interception, accessibility service, root, or cloud backend.

## Product principle

When attribution is uncertain, record the bytes as **unattributed / uncertain** rather than assigning them to the wrong network.

A missing interval is visible and debuggable. A confidently wrong Wi-Fi total is not.
