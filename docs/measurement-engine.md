# Measurement engine

## Purpose

This document defines how raw Android traffic counters and network-context observations become per-network usage intervals.

The core rule is conservative:

> A byte delta is attributed only when the network context covering that interval is sufficiently known.

## Counter candidates to validate

### TrafficStats total counters

Primary low-cost candidate for the spike:

```text
TrafficStats.getTotalRxBytes()
TrafficStats.getTotalTxBytes()
```

Android documents these as cumulative bytes across network interfaces since device boot. They are cheap and monotonic within a boot session on supported devices.

Important limitations:

- counters reset after reboot;
- they aggregate across interfaces;
- VPN and other interface behavior must be empirically validated;
- they are not historical once a boundary was missed;
- unsupported values must be handled explicitly.

### TrafficStats mobile counters

Capture as diagnostic/reference when available:

```text
getMobileRxBytes()
getMobileTxBytes()
```

These may help distinguish cellular traffic from total traffic during controlled tests.

### TrafficStats per-interface counters

On modern Android versions, `getRxBytes(iface)` / `getTxBytes(iface)` can provide interface-local diagnostics.

Android explicitly documents these as **partial statistics** that do not contain all adjustments needed for correctness. Therefore they should be treated as diagnostic evidence, not assumed to be canonical production totals before validation.

### NetworkStatsManager

Use as an optional reference/reconciliation source where the user grants Usage Access.

It provides historical usage buckets and device-wide summaries, but Android documents bucket granularity as potentially on the order of hours, so it is not suitable as the sole fine-grained network-boundary source.

It also does not solve arbitrary SSID attribution by itself.

## Network event observations

The engine should preserve events such as:

```text
NETWORK_AVAILABLE
NETWORK_CAPABILITIES_CHANGED
NETWORK_LOST            (in-process callback path)
PENDING_INTENT_WAKE
RECOVERY_SNAPSHOT
BOOT
PROCESS_START
PROCESS_EXIT_OBSERVED
MANUAL_TEST_MARKER
```

The exact Android delivery mechanism is platform-layer metadata. The domain engine consumes normalized observations.

## Physical vs VPN context

The application's default network can itself be a VPN.

The measurement spike must therefore record enough `NetworkCapabilities` information to know:

- transport types;
- whether `NET_CAPABILITY_NOT_VPN` is present;
- relevant interface names from link properties where available;
- whether a VPN network coexists with an underlying physical Wi-Fi/cellular network.

Do not blindly group usage under `VPN` simply because the default app network is a VPN.

The desired product identity is normally the underlying physical carrier/network context.

Because `TrafficStats` total counters span interfaces, VPN accounting is a dedicated M1 validation scenario. Do not claim VPN-safe totals until the controlled test proves expected behavior.

## Wi-Fi identity

Preferred recurring identity:

```text
wifi:<normalized SSID>
```

If SSID is unavailable:

```text
wifi:ssid-unavailable
```

Never silently merge generic historical Wi-Fi traffic into a later known SSID.

`WifiInfo.getSSID()` can return `UNKNOWN_SSID` when required access is unavailable. Treat this as a supported state and export the permission/context status that explains it.

Avoid BSSID persistence by default. BSSID changes within one logical Wi-Fi network and is more sensitive than needed for the product goal.

## Cellular identity

The first spike does not need to identify carrier/SIM subscriptions precisely.

Use a conservative identity such as:

```text
cellular
```

A future production version may split subscriptions only if public APIs and permissions make that reliable and useful.

## Attribution state

Maintain a durable baseline concept:

```text
ActiveBaseline
- context identity
- context observation timestamp
- counter RX/TX
- boot session
- evidence continuity
```

When a trustworthy network boundary arrives:

1. read current counter snapshot;
2. compare with previous baseline;
3. validate counter generation and monotonicity;
4. close the prior interval;
5. assign the delta to the prior context only if continuity is known;
6. persist a new baseline for the new context.

## Delta calculation

For valid monotonic counters within the same boot generation:

```text
rxDelta = current.totalRx - previous.totalRx
txDelta = current.totalTx - previous.totalTx
```

Reject/discard the interval if:

- current counter is unsupported;
- current counter < previous counter;
- reboot/boot generation changed;
- elapsed time <= 0;
- the resulting rate is physically implausible beyond a conservative sanity threshold;
- evidence indicates the two readings cannot be compared.

Never clamp a negative/reset delta into a positive number.

## Attribution confidence

Every derived interval must carry one of:

### confirmed

Use when:

- the previous context is known;
- continuity between boundary observations is intact;
- no process/reboot gap invalidates the event stream;
- counters are valid and comparable.

### inferred

Use only when there is strong evidence but not a fully observed boundary. Keep this category separate from confirmed totals during validation.

Example candidate: recovery after a short known-safe execution handoff where the network did not change and platform evidence proves continuity.

Do not use `inferred` merely because the network before and after a multi-hour process gap has the same SSID; the phone could have switched away and returned.

### unattributed

Use when byte delta is valid but the owning network cannot be proven.

Typical causes:

- process was absent and no persistent event was delivered;
- recovery finds a different network than the last baseline;
- event ordering is incomplete;
- Wi-Fi identity changed inside an unobserved gap.

### discarded

Use when the counter delta itself is invalid or incomparable.

## Process gaps

Process continuity is part of measurement evidence.

Persist:

- process start timestamp;
- last clean persistence timestamp;
- app version;
- boot generation;
- process-exit reasons where public Android APIs provide them.

If the app restarts and cannot prove that network-boundary events continued to be delivered while it was absent, do not bridge the last pre-death baseline directly into the current network as confirmed usage.

## Reboot

TrafficStats counters restart after reboot.

Detect reboot using a boot-session marker derived from platform state such as elapsed realtime / boot timestamp plus explicit boot receiver evidence.

On a new boot generation:

- close old baseline without creating a delta across reboot;
- persist a reboot/reset observation;
- establish a fresh baseline.

## Recovery checks

A periodic recovery snapshot every few hours is a safety net.

Its jobs are:

- prove the database is still writable;
- record that background work ran;
- capture current counters/context;
- expose missed-event gaps;
- create a fresh safe baseline if necessary.

It is **not** a substitute for boundary events.

## Manual markers

The tester must be able to create a timestamped marker before/after deliberate actions, for example:

```text
ABOUT_TO_SWITCH_TO_HOME_WIFI
CONNECTED_TO_HOME_WIFI
ABOUT_TO_SWITCH_TO_PHONE_HOTSPOT
CONNECTED_TO_PHONE_HOTSPOT
START_500MB_DOWNLOAD
END_500MB_DOWNLOAD
SCREEN_OFF
BATTERY_SAVER_ON
```

Markers must be included in the export so observed Android events can be aligned with human ground truth.

## Measurement truth

The final validation should distinguish three independent questions:

1. **Event delivery:** did Android tell us the network changed?
2. **Counter correctness:** did the byte counters move plausibly?
3. **Attribution logic:** did our engine assign the valid delta to the correct interval?

The export schema must make failures in these layers separable.
