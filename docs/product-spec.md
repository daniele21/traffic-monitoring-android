# Product specification

## Problem

Android can tell users how much data the device used overall, but the product goal is more specific:

> Attribute device traffic to the Wi-Fi / hotspot / mobile network in use when that traffic occurred, while consuming very little battery and without continuously polling.

The motivating case is hotspot usage: a user wants to know how much data the Android device consumed while connected to a particular phone hotspot, distinct from home/office Wi-Fi and mobile data.

## Immediate objective

The first release of this repository is a **technical validation app**, not the final consumer product.

It must answer four feasibility questions:

1. Can Android deliver enough background network-change events to define reliable attribution boundaries without a permanent Foreground Service?
2. Can available traffic counters provide stable device-level byte deltas across those boundaries?
3. Can connected Wi-Fi networks be identified consistently enough for per-network grouping?
4. Can the result be validated after 48–72 hours through a self-contained export bundle?

Only after these questions are answered should the project invest in polished analytics.

## Measurement scope

The desired semantic model is:

```text
network A active
counter baseline A
    ↓ traffic occurs
network boundary observed
counter final A
    ↓
usage attributed to A
    ↓
network B baseline
```

The app tracks **device network-interface/accounting usage**, not carrier billing records.

It should preserve download and upload separately whenever the selected counter source supports both.

## Network categories

The experiment should distinguish at least:

- Wi-Fi with known SSID;
- Wi-Fi with unavailable/unknown SSID;
- cellular/mobile;
- Ethernet/USB network where Android reports it;
- VPN-present context;
- offline/no validated network;
- ambiguous/unattributed interval.

For Wi-Fi, SSID is the preferred user-facing recurring identity. BSSID is not required and should not be persisted by default.

## Background modes under evaluation

### Standard candidate

Goal: no permanent notification and nearly no CPU activity between network events.

Candidate components:

- `ConnectivityManager.registerNetworkCallback(NetworkRequest, PendingIntent)`;
- manifest `BroadcastReceiver`;
- coarse WorkManager recovery/liveness check;
- user may be instructed to use Battery → Unrestricted during validation.

### Precise fallback

If the Standard candidate misses transitions in real tests, evaluate an opt-in Foreground Service with a persistent notification.

The Foreground Service must not be implemented merely because it is easier; evidence from M1 should justify it.

## User-facing validation flow

The minimal app should allow the tester to:

1. see whether measurement is armed/running;
2. see current network identity and permission/background status;
3. create a manual **test marker** before/after deliberate transitions;
4. inspect a concise recent-event timeline;
5. export the complete validation bundle;
6. clear/start a fresh validation run.

A polished traffic analytics dashboard is explicitly not required for M1.

## Export requirement

A test run is incomplete unless it can be exported and inspected independently.

The export must make these questions answerable:

- Which network switches did Android deliver to the app?
- At what timestamp did each event arrive?
- Was the process already alive, newly started by an event, or recovering later?
- What were RX/TX counters at each boundary?
- Which intervals were attributed, uncertain, or dropped?
- Did a reboot/counter reset occur?
- Did the recovery worker run while no network event was observed?
- What manual transition markers did the tester record?
- What permission/battery/background configuration was active?

See `data-and-export.md` for the concrete format.

## Privacy

v1 is local-first.

The app must not collect or persist:

- packet contents;
- domains or URLs;
- DNS queries;
- destination IP history;
- browsing history;
- app-by-app activity unless a future explicit product requirement is introduced;
- physical location coordinates.

SSID is stored only because it is directly required for network attribution. The exported bundle can therefore contain sensitive network names and must be created only through an explicit user action.

## Non-goals for the feasibility spike

Do not build yet:

- polished Overview/Trend/Networks analytics;
- cloud sync/accounts;
- remote telemetry;
- cross-device synchronization;
- mobile-plan billing reconciliation;
- packet capture;
- VpnService-based interception;
- root/device-owner-only approaches;
- exact per-application network usage;
- Play Store release hardening.

## Success criteria for M1

The Standard candidate is considered viable only if controlled field tests show:

- deliberate network transitions are captured reliably while the app UI is not open;
- no synthetic giant/negative usage is produced across reboot or counter reset;
- known-size transfers produce plausible deltas in isolated tests;
- Wi-Fi identities are distinguishable when required permission is granted;
- process death/recovery gaps are visible rather than silently misattributed;
- exports contain enough raw evidence to independently audit derived totals;
- battery/background impact is low enough for a passive utility.

The preferred criterion for deliberate scripted network switches is **100% observed transitions** during the validation run. If that cannot be achieved without a Foreground Service, the product must either adopt the fallback or clearly downgrade its precision claim.

## Product truthfulness

The product must never display a per-network number as exact when the underlying attribution interval is ambiguous.

Preferred states:

- `confirmed` — both interval boundaries are trustworthy;
- `inferred` — evidence is strong but not complete;
- `unattributed` — bytes are known but the network owner cannot be proven;
- `discarded` — counter data itself is invalid/reset/unsupported.

The final analytics product can decide how to present those categories, but the measurement layer must preserve the distinction.
