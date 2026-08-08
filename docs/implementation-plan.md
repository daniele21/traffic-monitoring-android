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

**Status: implementation complete; emulator persistence/export gate passed on 2026-08-08.**

The validation app proved the evidence pipeline before measurement complexity was added.

Delivered:

- Kotlin Android project;
- Jetpack Compose single validation screen;
- Room database;
- `ValidationRun` lifecycle;
- current network/context snapshot;
- manual test markers;
- recent raw-event list;
- **Export validation run** producing the documented ZIP bundle;
- reset/start-new-run flow;
- unit tests for the export contract and CSV serialization.

Validated export run:

```text
runId: 065b1000-3bc5-4c64-bfbb-64303e30cf57
appVersion: 0.1.0-m1a-debug
device: Android emulator, SDK 36
process starts: 3
manual markers: 2
network observations: 7
```

The same run survived process relaunches, both manual markers were preserved, exported IDs were coherent, and the future counter/attribution CSVs were present with stable headers.

The run also exposed an important timing case: wall clock advanced by roughly 52 minutes while `elapsedRealtime` advanced by only roughly 22 seconds, consistent with an emulator freeze/suspension. This is now an explicit attribution discontinuity: such gaps must be discarded rather than assigned to a network.

Physical-device behavior is not considered proven by M1A. It is tested together with the actual counters and callbacks in M1B/M1C, where device/OEM behavior matters.

Important ordering choice:

> Export comes before complex background behavior.

Every subsequent experiment is already observable.

# M1B — Counter and in-process network spike

**Status: current implementation milestone.**

Implement platform adapters while keeping the app process alive. M1B is deliberately conservative: it validates counters and event boundaries but does not claim background survival.

Deliverables:

- `TrafficCounterReader` using `TrafficStats.getTotalRxBytes()` / `getTotalTxBytes()`;
- boot-generation capture;
- `NetworkContextReader` using `ConnectivityManager` / `NetworkCapabilities` / `WifiInfo`;
- process-wide regular `NetworkCallback` diagnostic stream;
- serialized `NetworkEvent` + `CounterSnapshot` persistence;
- deterministic attribution between adjacent evidence;
- wall-clock vs `elapsedRealtime` continuity check;
- counter-regression / boot-change rejection;
- VPN ambiguity kept unattributed;
- M1B evidence visible in the validation UI and existing ZIP export.

M1B confidence policy:

```text
same observed network + valid continuity -> inferred
network boundary / unknown identity       -> unattributed
VPN ambiguity                             -> unattributed
clock discontinuity / reboot / reset      -> discarded
confirmed                                 -> not emitted by M1B
```

Tests:

- known-size download/upload;
- Wi-Fi A → Wi-Fi B;
- Wi-Fi → cellular → Wi-Fi;
- SSID permission allowed/denied;
- VPN on/off;
- reboot;
- emulator clock/suspension discontinuity fixture.

Gate:

- counter source is monotonic/plausible on the test device;
- `counter-snapshots.csv` is populated;
- `attribution-intervals.csv` reconciles with accepted counter deltas;
- no synthetic deltas across reboot/reset/clock discontinuity;
- callback events appear while the process exists;
- network identity is observable enough for the intended Wi-Fi grouping.

# M1C — PendingIntent background event spike

Implement the preferred low-power background candidate.

Deliverables:

- manifest `BroadcastReceiver`;
- stable PendingIntent registration;
- `registerNetworkCallback(NetworkRequest, PendingIntent)`;
- re-registration on appropriate lifecycle/boot paths;
- event-source metadata proving whether a boundary came from PendingIntent vs live callback;
- bounded receiver execution;
- durable baseline updates;
- export fields for registration/version/status.

Critical test:

1. arm validation run;
2. leave the app UI;
3. allow process to become absent if Android chooses;
4. deliberately switch networks;
5. reopen only much later;
6. verify the export shows boundary events at the actual switch timestamps, not merely when the app was reopened.

Gate:

- prove whether this API produces sufficient background transition evidence for the product.

# M1D — Recovery and lifecycle hardening

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

Use direct user-facing terminology, not counter jargon.

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
