# Implementation plan

## Rule

Do not skip the feasibility gate.

The final Android analytics app is straightforward compared with the unresolved question: **can we observe network attribution boundaries reliably enough in the background without a permanent Foreground Service?**

The repository should answer that first.

# M0 — Documentation and experiment design

**Status: complete.**

Deliverables:

- product scope;
- architecture boundaries;
- measurement/counter hypotheses;
- background-strategy ladder;
- local evidence/export schema;
- scripted test matrix;
- decision log.

Gate:

- an implementation agent can build the spike without inventing product semantics or background strategy.

# M1A — Minimal Android validation app

**Status: complete; emulator persistence/export gate passed on 2026-08-08.**

Delivered and validated:

- Kotlin/Compose Android project;
- Room validation database;
- `ValidationRun` lifecycle;
- current network/context snapshots;
- manual test markers;
- raw-event lists;
- export ZIP contract;
- reset/start-new-run flow;
- export serialization tests.

The M1A export also exposed an emulator freeze/suspension case where wall clock and `elapsedRealtime` diverged materially. Such gaps are now explicitly discarded rather than assigned to a network.

# M1B — Counter and in-process network spike

**Status: complete; emulator gate passed on 2026-08-08.**

Delivered:

- `TrafficStats.getTotalRxBytes()` / `getTotalTxBytes()` cumulative device counters;
- boot-generation evidence;
- `ConnectivityManager` / `NetworkCapabilities` network context;
- process-wide default `NetworkCallback` diagnostics;
- `NetworkEvent` + `CounterSnapshot` persistence;
- deterministic adjacent-evidence attribution;
- wall-clock vs `elapsedRealtime` continuity checks;
- counter-regression / boot-change rejection;
- VPN ambiguity kept unattributed;
- counter/attribution evidence in UI and export.

Final clean emulator evidence:

```text
appVersion: 0.1.0-m1b-debug
network events: 12
in-process callback events: 8
counter snapshots: 12
attribution intervals: 11
controlled payload: 10 MiB
controlled RX delta: 11,869,535 B
first-to-last total counter movement: 12,397,857 B
exported interval reconciliation: 12,397,857 B
result: PASS
```

Earlier M1B evidence separately validated conservative handling of Wi-Fi → offline → cellular → Wi-Fi boundaries and ordinary process restart gaps.

M1B confidence policy:

```text
same observed network + valid continuity -> inferred
network boundary / unknown identity       -> unattributed
VPN ambiguity                             -> unattributed
clock discontinuity / reboot / reset      -> discarded
process restart                           -> discarded
confirmed                                 -> not emitted by M1B
```

Physical-device behavior remains part of the broader M1C/M1E field work.

# M1C — PendingIntent background event spike

**Status: implementation complete; process-absent validation is the active gate.**

Implemented:

- manifest `BackgroundNetworkReceiver`;
- stable explicit mutable PendingIntent used only where ConnectivityManager needs to fill documented extras;
- `registerNetworkCallback(NetworkRequest, PendingIntent)` registration;
- durable registration/wake status in SharedPreferences;
- UI-start registration plus boot/package-replacement re-registration;
- targeted `EXTRA_NETWORK` context capture;
- `source=pending_intent` network events;
- bounded receiver execution using `goAsync()` + IO coroutine;
- registration/wake lifecycle evidence;
- M1C metadata and counts in validation export;
- branded M1C validation UI;
- repeatable process-absent test protocol in `m1c-validation.md`.

Important API constraint:

> The PendingIntent delivery corresponds to matching network **availability**. M1C does not assume every loss event wakes the app.

Critical test:

1. arm a fresh validation run;
2. move the UI to background;
3. terminate the ordinary background process without force-stop;
4. create a new matching network availability;
5. wait before reopening the UI;
6. export;
7. verify `source=pending_intent` evidence exists at the transition time rather than only at UI reopen.

Gate:

- prove whether availability wakes after ordinary process death are delivered reliably enough and close enough to the real boundary to support the product;
- quantify which loss-only boundaries remain invisible;
- confirm registration survives ordinary process death and can be restored after reboot/update;
- no process gap may silently create confident network usage.

# M1D — Recovery and lifecycle hardening

**Status: blocked on M1C evidence.**

Deliverables:

- coarse WorkManager recovery/liveness check, initial target 2–4 hours;
- process-start evidence;
- boot generation;
- historical exit-reason capture where supported;
- explicit continuity state;
- uncertain gaps become `unattributed`;
- permission/background-mode status in validation UI/export;
- no wake lock held merely to keep the process alive.

Gate:

- unexpected process death/restart cannot silently create a confident wrong attribution.

# M1E — Standard-mode 48–72 hour field validation

Run the scripted matrix in `testing.md` with:

```text
Mode A
PendingIntent network events
+ in-process diagnostic callback when alive
+ 2–4h recovery worker
+ Battery = Unrestricted during primary test
+ no Foreground Service
```

Primary success target:

- **100% of deliberate scripted network transitions have corresponding event evidence**;
- known transfer deltas are plausible;
- zero giant/reset artifacts;
- all continuity gaps visible;
- export independently explains the run;
- passive battery cost is acceptably low.

If M1E passes, do not add a Foreground Service by default.

If M1E fails specifically because background transitions are missed, continue to M1F.

# M1F — Foreground Service fallback A/B test

**Conditional milestone — do not implement unless M1E evidence requires it.**

Deliverables:

- opt-in precise-tracking Foreground Service;
- appropriate current foreground-service type/permissions after policy review;
- persistent notification;
- same event/counter/attribution recorder as Standard mode;
- exported `backgroundStrategy` metadata;
- identical scripted field test.

Compare Mode A vs Mode B on:

- transition capture rate;
- unattributed bytes/time;
- process gaps;
- battery impact;
- UX cost;
- implementation/policy complexity.

Decision gate:

Choose one honestly:

1. Standard mode is reliable enough → no FGS.
2. FGS is necessary → make precision mode explicit.
3. Neither is reliable enough → narrow/stop the product rather than shipping misleading per-network totals.

# M2 — Production attribution engine

Only after M1 feasibility passes.

Deliverables:

- final domain state machine;
- stable network identity rules;
- confirmed/inferred/unattributed accounting policy;
- VPN behavior based on M1 evidence;
- test fixtures built from exported real runs;
- migration-safe Room schema;
- crash/reboot recovery invariants.

Gate:

- deterministic replay of field-test evidence produces expected totals.

# M3 — Efficient persistent usage history

Move from validation evidence toward product analytics while retaining diagnostic evidence where useful.

Potential model:

- NetworkProfile;
- NetworkSession;
- bounded UsageBucket;
- five-minute/hourly aggregates as justified by Android behavior;
- retention policy.

Do not copy the macOS schema blindly; preserve shared product semantics but adapt storage to Android event/counter evidence.

# M4 — Android analytics MVP

Build the consumer experience only now.

Desired product semantics can mirror the macOS app:

- Overview;
- Trend;
- Networks;
- Today / 7 days / 30 days / This month / Custom;
- download/upload/total;
- per-network ranking;
- peak periods;
- explicit unattributed usage when present.

Use the approved product language from `brand-kit.md`: direct labels such as **Downloaded**, **Uploaded**, **Current network**, **Usage by network** and **Peak usage**, not counter jargon.

# M5 — Reliability across devices/OEMs

Test at least representative:

- Google Pixel / AOSP-like behavior;
- Samsung;
- one aggressive-background OEM if feasible;
- multiple Android versions within supported range.

Validate:

- Doze;
- Battery Saver;
- Optimized vs Unrestricted;
- reboot;
- app update;
- force-stop behavior;
- low memory;
- long idle periods;
- VPN;
- mobile/hotspot/Wi-Fi transitions.

# M6 — Product/release hardening

Only after technical viability is established:

- onboarding/permission education;
- background-mode guidance;
- launch/recovery behavior;
- export/reset history UX;
- privacy copy;
- Play policy review;
- release signing;
- CI artifacts/releases;
- performance/battery benchmark documentation.

## What not to do opportunistically

Before M1 passes, do not spend time on:

- polished charts;
- broad navigation architecture;
- cloud sync;
- account system;
- subscriptions/payments;
- marketing screens;
- elaborate settings;
- foreground service unless the Standard experiment fails.

The fastest route to a good product is to make the measurement hypothesis falsifiable as early as possible.
