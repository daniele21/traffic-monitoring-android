# M1B validation protocol

## Purpose

M1B answers a narrower question than the final product:

> While the Android process exists, do device-wide cumulative counters plus `ConnectivityManager.NetworkCallback` create coherent, auditable usage intervals?

M1B does **not** test survival after process death. That is M1C.

## Evidence produced

Every network observation now produces:

```text
NetworkEvent
    +
CounterSnapshot (TrafficStats total RX/TX since boot)
    ↓
compare with previous evidence
    ↓
AttributionInterval
```

The existing validation ZIP remains the source of truth.

## Confidence policy

M1B deliberately never emits `confirmed`.

```text
same observed identity, valid continuity  -> inferred
network changed / identity ambiguous      -> unattributed
VPN active                                -> unattributed
process restart                           -> discarded
boot changed                              -> discarded
counter regressed                         -> discarded
wall clock vs elapsedRealtime discontinuity > 30 s -> discarded
```

Discarded intervals have no RX/TX delta so they cannot create synthetic usage.

## A. Emulator smoke test

Use a fresh run:

```bash
bash scripts/run-emulator-debug.sh --clear-data --logs
```

`--clear-data` must print `App data clear confirmed: Success`. The helper exits with an error if Android does not confirm the clear, so a supposedly fresh run can no longer silently retain prior validation data.

In the app:

1. confirm the screen says `M1B`;
2. verify `Device counters since boot` shows numeric RX/TX values;
3. press **Capture evidence** twice, a few seconds apart;
4. confirm at least one recent attribution interval appears;
5. export the run.

Expected ZIP:

- `counter-snapshots.csv` has rows with non-null monotonically increasing RX/TX;
- `attribution-intervals.csv` has at least one interval;
- same-network intervals are `inferred`;
- `summary.json.counterSnapshotCount > 0`;
- `summary.json.attributionIntervalCount > 0`.

## B. Controlled download on emulator

1. press **Start known download**;
2. generate a known amount of network traffic **inside the emulator** rather than on the host Mac;
3. press **End known download**;
4. press **Capture evidence** once more;
5. export.

Evaluate the counter delta between the start/end marker evidence. RX should increase by a plausible amount relative to the transfer; exact equality is not expected because these are device-wide network counters.

A start/end marker pair with identical counters is an inconclusive transfer test, not a counter failure: it proves that no counted device traffic occurred between those two evidence points.

## C. In-process transition signal

While the app process remains alive, cause a network state change or disable/re-enable the emulator network where practical.

Expected:

- `network-events.csv` includes rows with `source=callback`;
- callback kinds can include `available`, `capabilities_changed`, `link_properties_changed`, or `lost`;
- each callback observation has a linked counter snapshot through `eventId`;
- a changed network identity produces an `unattributed` boundary rather than assigning all bytes to either side.

The emulator is only a smoke test for callback wiring. Real Wi-Fi/cellular transitions require a physical phone.

## D. Process restart guard

1. create at least two pieces of evidence;
2. leave some time/traffic between them;
3. terminate the debug app process without clearing its data;
4. relaunch it;
5. capture/export.

Expected:

- the previous validation run persists;
- a new `process_start` exists;
- the first interval crossing the old/new process boundary is `discarded` with reason `process_restart_boundary`;
- no bytes from that unobserved period are assigned to a named network.

## E. Clock discontinuity guard

The M1A emulator export from 2026-08-08 demonstrated a roughly 52-minute wall-clock jump while `elapsedRealtime` advanced by only roughly 22 seconds. This is treated as lost continuity.

If a similar condition occurs in M1B, expected output is:

```text
confidence = discarded
reason = clock_discontinuity
rxBytes = null
txBytes = null
```

## First M1B emulator evidence — 2026-08-08

Export `traffic-monitoring-validation-2026-08-08T17-25-35.564Z.zip` demonstrated the core M1B invariants:

- 44 network events, including 26 in-process callback events;
- 36 counter snapshots and 35 attribution intervals;
- one boot generation (`boot:8`) with monotonically increasing RX/TX and no counter reset;
- deliberate Wi-Fi → offline → cellular → Wi-Fi callback sequence was observed;
- network-change deltas stayed `unattributed`;
- a process restart produced one `discarded/process_restart_boundary` interval with no bytes assigned;
- all evidence IDs were unique and all counter/attribution evidence references resolved to existing network events;
- first-to-last cumulative counter movement was RX +50,614 bytes and TX +30,498 bytes = 81,112 bytes total;
- attributed accounting reconciled exactly: 7,943 inferred + 71,287 unattributed + 1,882 hidden behind the intentionally discarded restart boundary = 81,112 bytes.

Two formal gate items remained open in that export:

1. the active validation run had originated under M1A, so the ZIP included earlier M1A lifecycle/network evidence instead of representing a fresh M1B-only run;
2. the `START_KNOWN_DOWNLOAD` and `END_KNOWN_DOWNLOAD` marker snapshots had identical counters (RX 15,474,025; TX 1,774,817), so the controlled-transfer accuracy check was not exercised.

The debug launcher was subsequently hardened so `--clear-data` can no longer ignore an unsuccessful `pm clear`. Export creation also writes an `export_created` lifecycle event containing the current build version, while the run keeps its original start-version metadata.

## F. First physical-device run

Once A-D pass, install the same debug APK on a real Android phone and run:

```text
Wi-Fi A
  -> known download
  -> cellular
  -> known transfer
  -> Wi-Fi A
```

Use manual markers immediately around each deliberate transition and export the ZIP at the end.

Success criteria:

- TrafficStats counters remain monotonic within one boot;
- callback events occur near deliberate transitions while the process remains alive;
- stable-network deltas are plausible;
- transition deltas stay unattributed when the exact boundary is ambiguous;
- no reboot/process/timing gap creates synthetic usage.

SSID/name availability is evaluated separately because Android permission state can hide Wi-Fi identity even when transport/counter evidence is valid.

## What comes next

Only after M1B evidence is coherent should M1C add the `PendingIntent` network callback path and test whether transition evidence still arrives after the application process is absent.
