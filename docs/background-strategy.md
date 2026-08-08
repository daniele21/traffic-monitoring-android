# Background strategy

## Goal

Capture network-boundary events with the lowest practical battery/background cost, without assuming a permanent Foreground Service is necessary.

This is the central feasibility question of M1.

## Why normal in-process callbacks are not enough

`ConnectivityManager.registerDefaultNetworkCallback(NetworkCallback)` and callback-based `registerNetworkCallback(...)` are useful while the app process is alive, but Android documents that callbacks stop when the application exits.

Therefore a normal `NetworkCallback` alone cannot prove 24/7 attribution reliability.

## Primary candidate: PendingIntent network callback

Android provides:

```kotlin
ConnectivityManager.registerNetworkCallback(
    networkRequest,
    pendingIntent
)
```

The Android API documentation explicitly states that this request **may outlive the calling application** and can deliver an Intent to a `BroadcastReceiver` registered dynamically or in the manifest.

Reference:

- https://developer.android.com/reference/android/net/ConnectivityManager#registerNetworkCallback(android.net.NetworkRequest,%20android.app.PendingIntent)

This is the first mechanism to validate because it potentially gives us exactly what the product needs:

```text
no active UI/process
      ↓
network becomes available
      ↓
Android sends PendingIntent
      ↓
BroadcastReceiver starts process briefly
      ↓
snapshot context + counters
      ↓
persist boundary
      ↓
process can become idle again
```

### What it does not prove yet

The documentation guarantees an availability-style callback for matching networks; it does not automatically prove that every product-relevant transition gives us a complete pair of start/end boundaries.

M1 must specifically test:

- Wi-Fi A → Wi-Fi B;
- Wi-Fi → cellular;
- cellular → Wi-Fi;
- Wi-Fi → offline → Wi-Fi;
- hotspot → normal Wi-Fi;
- same SSID reconnect;
- VPN on/off while physical network stays the same;
- process absent;
- screen off / Doze;
- Battery Saver;
- reboot.

We must not design the production tracker around this mechanism until the exported evidence confirms those transitions.

## In-process callback path

While the process is already running, use a regular `NetworkCallback` as richer diagnostic evidence because it provides ordered events such as:

- `onAvailable`;
- `onCapabilitiesChanged`;
- `onLinkPropertiesChanged`;
- `onLost`.

Reference:

- https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback

The in-process callback and PendingIntent path should write into the same normalized event recorder so they can be compared in exports.

## NetworkRequest shape

The spike should observe internet-capable networks rather than actively request/bring up networks.

Use `registerNetworkCallback`, not `requestNetwork`, unless a later experiment explicitly needs active network acquisition.

Record transport/capability details needed to distinguish:

- Wi-Fi;
- cellular;
- Ethernet;
- VPN;
- validated/unvalidated;
- metered/unmetered.

Do not rely only on the app's default network because a VPN may become the default while the underlying physical network remains Wi-Fi/cellular.

## Battery setting: Unrestricted

During M1, provide a validation checklist/in-app status that asks the tester to set:

```text
Settings → Apps → Traffic Monitoring → Battery → Unrestricted
```

Android documents these broad background modes:

- Unrestricted;
- Optimized;
- Restricted.

Reference:

- https://developer.android.com/topic/performance/background-optimization

`Unrestricted` is useful evidence and should reduce background restrictions, but it must **not** be treated as a guarantee that the app process remains permanently alive.

Export whether the app can detect relevant power/background state and always include a manual run metadata field indicating the intended test mode.

## Recovery worker

Schedule a coarse WorkManager job only as a recovery/liveness mechanism.

Initial experiment cadence:

```text
2–4 hours
```

This is intentionally not minute-level polling.

The worker records:

- actual execution timestamp;
- current network context;
- current traffic counters;
- time since last network-boundary event;
- time since last durable observation;
- whether continuity can still be proven.

If the worker wakes after a gap and continuity is not provable, the resulting bytes are `unattributed`, not assigned to the current network.

WorkManager execution is not exact-time scheduling; delayed execution is expected and must be exported as evidence rather than treated as a bug by itself.

## Process and reboot recovery

At every process start:

1. persist a `PROCESS_START` lifecycle event;
2. record boot-session identity;
3. inspect the last durable baseline;
4. capture current context/counters;
5. determine whether continuity can be proven;
6. never silently bridge an uncertain gap.

Where available, collect historical process exit reasons using public Android APIs to help classify deaths such as crash, low memory, user stop or system behavior.

After reboot:

- create a new boot generation;
- reset the counter baseline;
- re-register any PendingIntent/network observers that do not survive reboot;
- ensure boot handling obeys current Android background restrictions;
- record all recovery actions in the validation log.

## Foreground Service fallback

A Foreground Service is candidate **Mode B**, introduced only if Mode A misses transitions in field tests.

Expected behavior:

```text
Foreground Service
      ↓
regular NetworkCallback remains registered
      ↓
process receives higher execution priority
      ↓
network boundaries recorded immediately
```

Trade-offs:

- persistent user-visible notification;
- more lifecycle complexity;
- Play policy / foreground-service declaration requirements;
- more risk of unnecessary battery use if implemented poorly.

Android requires an appropriate foreground-service type. A network-monitoring use case may need to be evaluated under `specialUse` if no standard type applies; Play submission reviews the declared use case.

Reference:

- https://developer.android.com/develop/background-work/services/fgs
- https://developer.android.com/develop/background-work/services/fgs/service-types

Do not select the final FGS type until the implementation phase verifies current Android/Play requirements.

## Decision ladder

Use this order:

```text
1. PendingIntent network callback
        +
   in-process callback diagnostics
        +
   coarse recovery worker
        +
   Unrestricted battery setting during validation

                 ↓ field test

2. If 100% deliberate transitions are observed:
      keep Standard mode; no permanent notification

3. If transitions are missed due background/process behavior:
      test Foreground Service fallback

4. Compare reliability + battery + UX
      choose product default honestly
```

## What would count as failure of Standard mode

Any of the following during a controlled run is enough to keep the gate open:

- a deliberate Wi-Fi/hotspot/mobile transition has no corresponding event evidence;
- a process-death interval gets silently assigned to the wrong network;
- PendingIntent registrations disappear unexpectedly and are not recovered;
- OEM behavior prevents event delivery despite the documented configuration;
- battery impact is disproportionate to the passive workload.

A missed event that is correctly marked as `unattributed` is better than a false attribution, but repeated gaps would still mean the product precision goal is not met.
