# Testing and validation

## Purpose

The Android spike succeeds only if real-device evidence shows that network transitions and traffic deltas remain trustworthy while the app UI is not open.

Automated tests validate our logic. Field tests validate Android/OEM behavior.

Both are required.

# 1. Automated tests

## Delta calculation

Cover at least:

- monotonic RX/TX increase;
- zero traffic;
- counter decrease/reset;
- reboot generation change;
- unsupported counter values;
- very large `Long` values without overflow;
- elapsed-time validation;
- physically implausible rate rejection.

## Attribution engine

Deterministic event sequences should cover:

```text
Wi-Fi A → Wi-Fi B
Wi-Fi → cellular
cellular → Wi-Fi
Wi-Fi → offline → Wi-Fi
same network repeated event
process continuity gap
reboot
missing SSID
VPN appears/disappears
```

Assertions:

- confirmed intervals only when continuity is proven;
- uncertain gaps are not assigned to a named network;
- total bytes reconcile with accepted counter deltas;
- reset intervals never create usage;
- evidence IDs on derived intervals point to the expected observations.

## Persistence

Room integration tests should verify:

- event + snapshot atomicity where designed;
- process restart preserves active baseline;
- fresh validation run does not corrupt earlier runs;
- export reads a coherent snapshot;
- schema version is included.

## Export

Generate a fixture run and assert ZIP contents:

```text
manifest.json
network-events.csv
counter-snapshots.csv
attribution-intervals.csv
lifecycle-events.csv
manual-markers.csv
summary.json
README.txt
```

Verify:

- CSV quoting;
- Unicode SSIDs;
- timestamps;
- null values;
- stable headers/order;
- summary reconciliation;
- no forbidden identifiers (IMEI, IMSI, Android ID, advertising ID, account data).

# 2. Real-device validation protocol

## Before each run

Record in the run metadata/notes:

- phone model;
- Android version;
- app version;
- battery setting: Optimized / Unrestricted;
- Battery Saver state;
- Usage Access granted/denied;
- Wi-Fi identity permission state;
- foreground-service mode if later tested;
- expected recovery cadence.

Start a fresh validation run.

## Manual markers

Before and after each deliberate action, use a marker.

Example:

```text
14:00 MARKER: SWITCHING_TO_HOME_WIFI
14:01 MARKER: HOME_WIFI_CONNECTED
14:10 MARKER: START_500MB_DOWNLOAD
14:14 MARKER: END_500MB_DOWNLOAD
14:30 MARKER: SWITCHING_TO_PHONE_HOTSPOT
14:31 MARKER: PHONE_HOTSPOT_CONNECTED
```

The marker timestamp is human ground truth. Android event timestamps should be close to it.

# 3. M1B foreground/in-process tests

These tests establish the basic counter and context model before background complexity.

## A. Known Wi-Fi download

1. Connect to Wi-Fi A.
2. Mark start.
3. Download a known-size file (for example 500 MB–1 GB).
4. Avoid unrelated heavy traffic during the test.
5. Mark end.
6. Export.

Evaluate:

- total RX delta is plausible relative to payload + protocol/background overhead;
- TX remains much smaller than RX for a pure download;
- no reset/spike event occurred;
- network identity remains Wi-Fi A.

Initial tolerance is empirical, not a billing guarantee. Investigate differences >5% in an otherwise isolated test before accepting the counter source.

## B. Known upload

Same procedure with a controlled upload. Validate TX primarily.

## C. Wi-Fi A → Wi-Fi B

1. Stay on Wi-Fi A for several minutes.
2. Mark transition.
3. Connect to Wi-Fi B.
4. Perform known transfers on both sides.
5. Export.

Expected:

- two distinct network identities when SSID permission is available;
- boundary occurs near manual marker;
- no traffic interval bridges the switch incorrectly.

## D. Wi-Fi → cellular → Wi-Fi

Validate transport transitions and mobile counters.

## E. SSID permission denied

Expected:

- traffic counters still recorded;
- Wi-Fi identity explicitly reports unavailable/generic;
- no crash/security exception;
- export explains permission state.

## F. VPN on/off

Run a controlled transfer:

1. Wi-Fi, VPN off.
2. VPN on, same physical Wi-Fi.
3. VPN off.

Compare:

- total counters;
- per-interface diagnostic counters;
- NetworkStats reference if enabled;
- physical network context.

Goal: discover whether total `TrafficStats` creates VPN double counting or other artifacts on the test device. Do not assume the answer.

## G. Reboot

1. Record baseline/marker.
2. Reboot.
3. Reopen only if required by the experiment design.
4. Export after recovery.

Expected:

- new boot generation;
- no delta across pre/post reboot counters;
- registration/recovery evidence visible.

# 4. M1C background PendingIntent tests

Critical rule: after arming the run, do **not** keep opening the app to check it. That changes the process lifecycle we are trying to validate.

## Test sequence — same day

With Battery = Unrestricted:

```text
Wi-Fi Home
  ↓
phone hotspot
  ↓
cellular
  ↓
Wi-Fi Office/other AP
  ↓
Wi-Fi Home
```

At each deliberate switch:

- create marker immediately before leaving the current network if practical;
- create marker immediately after the new network is visibly connected;
- then leave the app alone again.

If creating the marker itself keeps the process alive and biases the experiment, run a second test where ground truth is written externally (notes/paper) and the app is never opened during transitions.

Evaluate export:

- did a `pending_intent` event appear for each new relevant network?
- did its timestamp occur at the switch rather than at next app launch?
- did process-start/receiver events show Android waking the app?
- was each byte interval attributed correctly?

## Process-death variant

Use developer tools/test conditions where appropriate to encourage process removal without force-stopping the app.

Do **not** use force-stop as the only background test: Android intentionally treats force-stopped apps differently.

Record process-start and historical exit-reason evidence.

# 5. Doze / idle tests

## Screen-off long idle

1. Arm run on Wi-Fi A.
2. Turn screen off for 2–4 hours.
3. Change to hotspot or Wi-Fi B during/after idle as the script requires.
4. Keep UI closed.
5. Later export.

Evaluate event timestamps and recovery worker delay.

## Battery Saver

Repeat with Battery Saver enabled.

## Optimized battery mode

After the primary Unrestricted run, repeat a shorter matrix with default Optimized mode to quantify the real UX requirement.

Do not treat Unrestricted as permanent process guarantee; the export should show actual behavior.

# 6. 48–72 hour Standard-mode field run

Target configuration:

```text
PendingIntent network registration
+ diagnostic callback whenever process happens to be alive
+ recovery worker every 2–4h
+ no Foreground Service
+ Battery = Unrestricted
```

During the run deliberately generate at least:

- 5 Wi-Fi ↔ Wi-Fi transitions;
- 5 Wi-Fi ↔ cellular transitions;
- 3 hotspot sessions;
- 1 VPN on/off sequence;
- 1 long screen-off idle period (>4h);
- 1 Battery Saver period;
- 1 reboot if the registration strategy is intended to recover after boot.

Use controlled transfers on at least three different network contexts.

# 7. Pass/fail metrics

## Transition capture rate

```text
observed deliberate transitions / deliberate transitions
```

Preferred M1 acceptance:

```text
100%
```

A transition counts as observed only if event evidence is timestamped near the real transition, not reconstructed later from the next app launch.

## Unattributed bytes

Report:

```text
unattributed bytes / all valid measured bytes
```

For a deliberate controlled run, target as close to zero as possible. Any non-zero value must be explainable from exported lifecycle evidence.

## False attribution

A confidently assigned interval known to belong to another network is a critical failure even if total bytes are otherwise accurate.

## Counter artifacts

Acceptance:

- zero giant synthetic deltas;
- zero negative/wrapped deltas;
- reboot/reset correctly discarded.

## Export auditability

A reviewer who did not run the experiment should be able to answer:

- what transitions were intended;
- what Android delivered;
- whether the process was alive;
- what counters were read;
- how each interval was classified.

If that cannot be answered from the ZIP, improve instrumentation before running more field tests.

# 8. Battery evaluation

Do not promise a fixed battery percentage before measurement.

For M1 capture:

- Android battery usage screen result for the app after a representative run;
- whether app appears among meaningful battery consumers;
- recovery worker count;
- receiver wake count;
- foreground-service duration if Mode B is tested;
- any wake-lock usage (target: none for Standard mode).

Optional development benchmark with ADB/batterystats can be added once the core spike works.

The important architectural target is qualitative and structural first:

- no high-frequency background polling;
- no permanent wake lock;
- event-triggered work is short;
- recovery wakes only a few times per day.

# 9. If Standard mode fails

Do not immediately hide failures with heuristics.

Export the failing run, identify whether the cause was:

- PendingIntent event semantics;
- process/background restriction;
- OEM behavior;
- permission loss;
- counter behavior;
- our attribution state machine.

Only if background event delivery is the blocker should M1F add the Foreground Service and rerun the same matrix for a fair A/B comparison.
