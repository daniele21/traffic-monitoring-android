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

**Status: implementation complete on the development branch; real-device gate pending.**

The current implementation provides the smallest installable app needed for experiments and passes CI unit tests plus debug APK assembly. Do not mark M1A complete until the real-phone persistence/export checks below pass.

Deliverables:

- Kotlin Android project;
- Jetpack Compose single validation screen;
- Room database;
- `ValidationRun` lifecycle;
- current network/context snapshot;
- manual test markers;
- recent raw-event list;
- **Export validation run** producing the documented ZIP bundle;
- reset/start-new-run flow;
- basic unit tests for the export contract and CSV serialization.

Important ordering choice:

> Export comes before complex background behavior.

Every subsequent experiment must already be observable.

Gate:

- install on a real Android phone;
- create markers/events;
- kill/reopen app;
- data survives;
- export opens on a computer and contains coherent CSV/JSON timestamps.

# M1B — Counter and in-process network spike

Implement platform adapters while keeping the app open/process alive.

Deliverables:

- `TrafficCounterReader` using `TrafficStats` total RX/TX;
- mobile counters captured diagnostically;
- per-interface counters captured diagnostically where supported;
- `NetworkContextReader` using `ConnectivityManager` / `NetworkCapabilities` / `WifiInfo`;
- regular `NetworkCallback` diagnostic stream;
- boot/reset detection;
- normalized `NetworkEvent` + `CounterSnapshot` persistence;
- first deterministic `AttributionEngine` delta tests.

Tests:

- Wi-Fi A → Wi-Fi B;
- Wi-Fi → cellular → Wi-Fi;
- known-size download/upload;
- SSID permission allowed/denied;
- VPN on/off;
- reboot.

Gate:

- counter source is monotonic/plausible on the test device;
- no synthetic deltas across reboot/reset;
- network identity is observable enough for the intended Wi-Fi grouping;
- raw evidence and derived intervals reconcile.

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
