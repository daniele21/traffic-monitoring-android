# Implementation plan

## Rule

The feasibility gate still governs product claims: code may be implemented ahead of field validation, but a milestone that requires real Android/OEM behavior is not marked **validated** until evidence exists.

The central unresolved product question remains:

> Can Android provide sufficiently reliable network-boundary evidence in the background without a permanent Foreground Service?

# M0 — Documentation and experiment design

**Status: complete.**

Product scope, architecture, measurement hypotheses, export schema, test matrix and decision log are defined.

# M1A — Minimal Android validation app

**Status: complete; emulator gate passed 2026-08-08.**

Validated Room persistence, manual markers, lifecycle evidence and ZIP export. The first run also exposed wall-clock vs `elapsedRealtime` discontinuity, now treated as lost continuity rather than synthetic traffic.

# M1B — Counter and in-process network spike

**Status: complete; emulator gate passed 2026-08-08.**

Delivered `TrafficStats` cumulative counters, live `NetworkCallback`, counter snapshots and conservative adjacent-evidence attribution.

Final clean emulator evidence included a controlled 10 MiB payload with RX +11,869,535 B and exact first-to-last accounting reconciliation of 12,397,857 B.

Policy:

```text
same observed network + valid continuity -> inferred
network boundary / unknown identity       -> unattributed
VPN ambiguity                             -> unattributed
clock / reboot / counter reset            -> discarded
process restart                           -> discarded
```

# M1C — PendingIntent background event spike

**Status: implementation complete; process-absent field validation pending.**

Implemented:

- `registerNetworkCallback(NetworkRequest, PendingIntent)`;
- explicit non-exported receiver;
- durable registration/wake state;
- targeted `EXTRA_NETWORK` capture;
- boot/package-update re-registration;
- `source=pending_intent` evidence;
- bounded receiver work;
- Monitor status and export metadata;
- `m1c-validation.md` protocol.

The API is treated as network **availability** evidence, not assumed to represent every loss boundary.

# M1D — Recovery and lifecycle hardening

**Status: implementation complete; field behavior pending M1E.**

Implemented:

- unique four-hour WorkManager recovery checkpoint;
- battery-not-low constraint, with no connected-network requirement;
- PendingIntent re-arm from recovery;
- recovery network/counter snapshot;
- `recovery_worker_started/completed/failed` evidence;
- process-start evidence;
- historical process-exit capture on API 30+;
- reboot/app-update recovery re-scheduling;
- no wake lock and no permanent service.

Gate remains empirical: process death/recovery must never create a confident wrong attribution on real devices.

# M1E — Standard-mode 48–72 hour field validation

**Status: protocol ready; physical-device gate pending.**

Standard mode:

```text
PendingIntent availability evidence
+ in-process callback while alive
+ TrafficStats counters
+ 4h WorkManager recovery
+ process/boot/exit evidence
+ no permanent Foreground Service
```

Use `m1e-field-validation.md` and `scripts/device-validation-status.sh`.

Primary success target remains 100% explainability of deliberate scripted transitions: a transition may be confidently represented or explicitly uncertain, but must never be silently assigned to the wrong network.

M1F remains conditional and is not implemented unless M1E evidence justifies a Foreground Service A/B test.

# M2 — Production attribution engine

**Status: implementation foundation complete; acceptance awaits field replay fixtures.**

Implemented:

- dedicated deterministic `AttributionEngine`;
- explicit `AttributionPolicy`;
- inferred / unattributed / discarded accounting;
- boot, counter, clock, VPN and process-gap invariants;
- compatibility facade for M1B fixtures;
- attribution algorithm version advanced for new runs.

Remaining acceptance gate:

- replay exported physical-device evidence and confirm deterministic expected totals before calling the engine production-validated.

# M3 — Efficient persistent usage history

**Status: implementation complete.**

Implemented a separate product analytics database so consumer history is independent of raw validation evidence:

```text
AttributionInterval
      ↓
UsageBucketAllocator
      ↓
5-minute UsageBucket
+ NetworkProfile
+ ProcessedInterval idempotency
      ↓
product analytics queries
```

Properties:

- accepted intervals are split proportionally across fixed five-minute buckets;
- integer byte totals reconcile exactly after splitting;
- discarded intervals never enter product totals;
- unattributed bytes remain explicit;
- ingestion is transactional and idempotent;
- existing validation intervals are backfilled safely at process start;
- validation data and long-lived product history remain separate databases.

# M4 — Minimal Android analytics MVP

**Status: core product UX implemented; refinement/field usability pending.**

The validation harness is no longer the default home.

Primary product surface:

- Overview;
- Networks;
- Today / 7 days / 30 days / This month / Custom;
- custom date-range picker;
- Total used;
- Downloaded / Uploaded;
- current network + simple monitoring status;
- restrained usage trend + peak;
- per-network ranking;
- explicit unattributed usage.

Advanced technical state is moved behind **Monitor**.

UX source of truth: `product-ux.md` and `brand-kit.md`.

Remaining M4 refinement before release hardening:

- accessibility/device-size polish from real-phone testing;
- empty/error-state copy refinement;
- usability validation on physical devices.

# M5 — Reliability across devices/OEMs

**Status: instrumentation + protocol implemented; multi-device validation pending.**

Implemented:

- `device_environment` evidence containing OEM/model/Android/power/background state;
- historical process-exit evidence;
- `scripts/device-validation-status.sh` for repeatable adb context capture;
- `m5-device-matrix.md` covering Pixel/AOSP, Samsung and an aggressive-background OEM;
- tests for Doze/idle, Battery Saver, Optimized/Unrestricted, reboot, update, force-stop negative control, low-memory/process death, VPN and Wi-Fi/mobile/hotspot transitions.

M5 cannot be marked passed until representative physical hardware has completed the matrix.

# M6 — Product/release hardening

**Status: not started.**

Only after technical viability is established:

- onboarding / permission education;
- background-mode guidance;
- reset/export product UX;
- privacy copy;
- Play policy review;
- release signing/internal testing;
- performance/battery benchmark documentation.

## Current execution order

```text
M1C/M1E real-device evidence
        ↓
M2 replay acceptance
        ↓
M4 usability refinement
        ↓
M5 multi-OEM matrix
        ↓
M6 release hardening
```

Code through M5 may exist before these gates close; product claims must continue to follow the evidence.
