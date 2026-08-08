# Architecture decision log

Keep entries compact. Detailed behavior belongs in the authoritative technical document linked by each decision.

## D001 — Use a separate Android repository

**Status:** accepted

The Android implementation lives in `traffic-monitoring-android` rather than inside the macOS repository.

Reason:

- native platform APIs are completely different;
- Kotlin/Gradle/Compose/Room and Swift/Xcode/SwiftUI/SwiftData have different build and release workflows;
- shared value is primarily product semantics, not source-code reuse;
- separate repos keep agent context and CI smaller.

The two projects should still use compatible user-facing concepts such as network identity, usage interval, download/upload totals, time ranges and uncertainty.

## D002 — Build natively with Kotlin

**Status:** accepted

Use Kotlin + Jetpack Compose + Room.

Do not introduce Kotlin Multiplatform merely to share models with the existing Swift app. Shared semantics can be documented and tested independently without adding cross-platform build complexity.

## D003 — No live 2-second background monitor

**Status:** accepted

The Android product does not need live throughput while in background.

Background measurement should be event-driven with only coarse recovery checks. A fast refresh can be added later only while a diagnostic screen is visibly open.

## D004 — Test PendingIntent connectivity events before a Foreground Service

**Status:** accepted as M1 hypothesis, not yet proven production architecture

First candidate:

```text
ConnectivityManager.registerNetworkCallback(NetworkRequest, PendingIntent)
```

Android documents that the request may outlive the calling application. This could provide network-availability boundary events without keeping the process alive.

The 48–72 hour field test decides whether it is reliable enough.

See `background-strategy.md`.

## D005 — Battery Unrestricted is test configuration, not a guarantee

**Status:** accepted

Primary M1 tests should use Battery → Unrestricted to remove avoidable background restrictions.

However, the design must not equate Unrestricted with an immortal process. Process/lifecycle gaps remain explicit measurement evidence.

## D006 — WorkManager is recovery, not boundary detection

**Status:** accepted

Use a coarse 2–4 hour periodic worker to capture liveness/recovery evidence.

Do not attribute multi-hour traffic solely because the same SSID appears at both worker executions; an intermediate network change may have occurred.

## D007 — Foreground Service is conditional fallback

**Status:** accepted

Do not pay the persistent-notification and Play-policy cost unless exported M1 evidence shows Standard mode misses required transitions.

If needed, test Foreground Service mode using exactly the same validation schema and scripted transitions for an A/B comparison.

## D008 — Exportability precedes advanced tracking

**Status:** accepted

Implement durable logging and validation export in M1A before sophisticated background experiments.

Reason: an unexportable multi-day test cannot be independently debugged and encourages anecdotal conclusions.

See `data-and-export.md`.

## D009 — Preserve raw observations separately from derived attribution

**Status:** accepted

Do not persist only final totals.

Store network events, counters and lifecycle observations separately from derived intervals so attribution logic can be replayed/revised without losing evidence.

## D010 — Validate multiple counter sources before choosing canonical measurement

**Status:** accepted

Primary low-cost candidate is `TrafficStats` total RX/TX.

Capture mobile/per-interface diagnostics where inexpensive and compare against controlled transfers and optional `NetworkStatsManager` reference data.

Do not assume per-interface counters are canonical: Android documents them as partial statistics without all correctness adjustments.

## D011 — NetworkStatsManager is reference/reconciliation, not SSID attribution

**Status:** accepted

`NetworkStatsManager` provides valuable device historical totals when Usage Access is granted, but its historical buckets are not fine-grained enough to define arbitrary Wi-Fi SSID boundaries by themselves.

Use it to cross-check totals, not to replace network-change evidence.

## D012 — Prefer unattributed bytes over wrong per-network totals

**Status:** accepted

A valid byte delta with ambiguous network ownership is stored as `unattributed`.

A reset/invalid counter interval is `discarded`.

Do not assign a gap to the network visible after recovery merely because it is the current network.

## D013 — SSID is allowed; BSSID is not stored by default

**Status:** accepted

SSID is required for the user-facing product question and can be stored locally after appropriate permission/access.

BSSID is not required for v1 identity and adds sensitivity/instability, so do not persist it by default.

## D014 — No packet capture or VpnService interception

**Status:** accepted

The product measures counters and connectivity context only.

Do not implement packet inspection, DNS logging, destination logging, accessibility-based monitoring, root or VpnService interception to improve attribution.

This keeps the utility privacy-first and avoids creating a much more invasive network-observation product.

## D015 — Start with a modern Android support floor for the spike

**Status:** provisional

The implementation spike should prioritize modern public APIs and fast learning over maximum device coverage.

A practical initial floor is Android 12 / API 31 if per-interface `TrafficStats` diagnostics are used directly. The production minimum SDK must be revisited after M1; total counters and much of ConnectivityManager exist on older releases, so broader support may be possible.

Do not freeze the long-term minimum SDK before the measurement architecture is proven.
