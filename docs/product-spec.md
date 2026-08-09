# Product specification

## Positioning

Traffic Monitoring is an **evidence-first network observability tool for Android**.

Primary promise:

> **Know your network usage — and the evidence behind it.**

The product should answer two different questions without mixing their complexity:

1. **What happened?** — how much data was used, when, and on which network;
2. **How do we know?** — how much evidence supports that attribution, where continuity was lost, and what remains uncertain.

This is intentionally different from both a generic data-usage meter and a packet-inspection tool.

The broader product philosophy is:

> Measure before claiming. Preserve uncertainty. Keep the user in control. Make results auditable.

See `evidence-observability-roadmap.md` for the parallel E0–E6 product roadmap.

## Problem

Android can tell users how much data the device used overall, but the core product goal is more specific:

> Attribute device traffic to the Wi-Fi / hotspot / mobile network in use when that traffic occurred, while consuming very little battery and without continuously polling.

The motivating case is hotspot usage: a user wants to know how much data the Android device consumed while connected to a particular phone hotspot, distinct from home/office Wi-Fi and mobile data.

The evidence-first extension adds a second problem:

> A per-network number is only useful if the product can explain how trustworthy that attribution is.

Therefore uncertainty, continuity gaps and evidence provenance are first-class product data rather than debug-only state.

## Immediate objective

The repository now contains both a minimal consumer analytics surface and the underlying validation system, but measurement claims still depend on physical-device evidence.

The feasibility track must continue answering four questions:

1. Can Android deliver enough background network-change events to define reliable attribution boundaries without a permanent Foreground Service?
2. Can available traffic counters provide stable device-level byte deltas across those boundaries?
3. Can connected Wi-Fi networks be identified consistently enough for per-network grouping?
4. Can the result be validated after 48–72 hours through a self-contained export bundle?

The evidence-product track can develop in parallel, but it must expose the current measurement confidence rather than assuming these questions are already solved.

## Product layers

Traffic Monitoring deliberately separates four layers:

```text
Usage
What happened?
Overview / Networks

Evidence
How well is it supported?
Coverage / continuity / explanations

Experiments
Can a network-behavior claim be evaluated?
Bounded runs / assertions / reports

Monitor
How did Android measure it?
Raw counters / callbacks / lifecycle / validation export
```

The consumer surface must remain simpler than the measurement system underneath it.

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

The system should distinguish at least:

- Wi-Fi with known SSID;
- Wi-Fi with unavailable/unknown SSID;
- cellular/mobile;
- Ethernet/USB network where Android reports it;
- VPN-present context;
- offline/no validated network;
- ambiguous/unattributed interval.

For Wi-Fi, SSID is the preferred user-facing recurring identity. BSSID is not required and should not be persisted by default.

## Evidence semantics

The measurement layer must preserve these states:

- `confirmed` — both interval boundaries are trustworthy;
- `inferred` — evidence is strong but not complete;
- `unattributed` — bytes are known but the network owner cannot be proven;
- `discarded` — counter data itself is invalid/reset/unsupported.

The first evidence-product milestone adds **Evidence Coverage** and **Measurement Health**.

Evidence Coverage answers how much accountable traffic could be attributed to a network. Measurement Health separately explains continuity gaps, discarded intervals and invalid evidence.

Do not combine these concepts into one unexplained score.

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

## Primary user experience

The default application should allow a user to understand within seconds:

- current network;
- whether monitoring is healthy;
- total used;
- downloaded/uploaded split;
- usage trend/peak;
- usage by network;
- explicit unattributed usage when present;
- a compact evidence-quality summary.

Raw RX/TX counters, Android network handles, callback kinds and lifecycle internals do not belong in the primary surface.

See `product-ux.md`.

## Evidence and evaluation experience

The evidence-first roadmap adds progressively:

1. Evidence Coverage and Measurement Health;
2. a human-readable Evidence timeline;
3. a user-facing Evidence Pack;
4. bounded Experiment Mode;
5. deterministic assertions with PASS / FAIL / INCONCLUSIVE;
6. optional app-level historical context when Android data quality is good enough;
7. optional open observability export after the neutral evidence schema is stable.

These are defined in `evidence-observability-roadmap.md`.

## Validation / Monitor flow

Monitor remains the advanced engineering surface and should allow a tester to:

1. see whether measurement is armed/running;
2. inspect current network identity and permission/background status;
3. create manual test markers before/after deliberate transitions;
4. inspect raw event/counter/attribution/lifecycle evidence;
5. export the complete validation bundle;
6. clear/start a fresh validation run.

Monitor is not the user-facing Evidence layer.

## Export requirements

There are two distinct export concepts.

### Validation export

Engineering/debug artifact containing raw observations and derived attribution so measurement behavior can be audited independently.

It must answer:

- Which network switches did Android deliver to the app?
- At what timestamp did each event arrive?
- Was the process already alive, newly started by an event, or recovering later?
- What were RX/TX counters at each boundary?
- Which intervals were attributed, uncertain, or dropped?
- Did a reboot/counter reset occur?
- Did the recovery worker run while no network event was observed?
- What manual transition markers did the tester record?
- What permission/battery/background configuration was active?

### Evidence Pack

Future product-facing artifact that summarizes a timeframe or experiment in human-readable and machine-readable form while retaining traceability to evidence.

See `data-and-export.md` and `evidence-observability-roadmap.md`.

## Privacy

The core product is local-first.

It must not collect or persist by default:

- packet contents;
- domains or URLs;
- DNS queries;
- destination IP history;
- browsing history;
- physical location coordinates;
- advertising/hardware/account identifiers.

SSID is stored only because it is directly required for network attribution. Exports can therefore contain sensitive network names and must be created only through an explicit user action.

App-level usage is not part of the core measurement path. A future optional capability may use platform-supported historical usage APIs only with explicit Usage Access and clear granularity limitations. It must not imply packet-level or real-time per-app precision that Android does not provide.

## Non-goals

Do not build:

- packet capture;
- destination/domain history;
- DNS logging;
- VpnService-based interception merely for observability;
- root/device-owner-only approaches;
- cloud accounts as a prerequisite;
- remote telemetry enabled by default;
- AI-generated explanations that replace deterministic evidence;
- exact per-application network claims unsupported by Android data sources;
- a single opaque trust score;
- mobile-plan billing reconciliation.

## Success criteria for measurement feasibility

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

Traffic Monitoring must never display a per-network number as exact when the underlying attribution interval is ambiguous.

Future assertions follow the same rule: when the evidence cannot support PASS or FAIL, the correct result is **INCONCLUSIVE**.

This truthfulness rule is the core of the evidence-first positioning.
