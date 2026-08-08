# M1C validation protocol — PendingIntent background network wake

## Purpose

M1C answers the next feasibility question:

> Can Android deliver useful network-availability evidence after the Traffic Monitoring UI process is absent, without a permanent Foreground Service?

The candidate is:

```text
ConnectivityManager.registerNetworkCallback(
    NetworkRequest,
    PendingIntent
)
```

Android documents that this request may outlive the calling application. The PendingIntent delivery corresponds to network **availability** (`onAvailable`) and includes `EXTRA_NETWORK` plus the original `EXTRA_NETWORK_REQUEST`.

M1C therefore does **not** assume that every network loss produces a wake. The experiment measures what Android actually delivers.

## Implementation under test

```text
UI start / boot / package replace
        ↓
register stable explicit PendingIntent
        ↓
process may disappear
        ↓
matching network becomes available
        ↓
manifest BroadcastReceiver
        ↓
source=pending_intent NetworkEvent
+ TrafficStats CounterSnapshot
+ lifecycle evidence
        ↓
Room → validation ZIP
```

The receiver is non-exported. The PendingIntent is explicit and mutable because ConnectivityManager must add its documented extras; setting an explicit receiver component bounds where that mutable PendingIntent can resolve.

Registration uses a stable Intent identity. Re-registering the same PendingIntent replaces the previous request instead of accumulating callbacks.

## Important: do not use force-stop for the positive test

Do **not** use:

```bash
adb shell am force-stop com.daniele21.trafficmonitoring.debug
```

Force-stop puts the package into Android's explicitly stopped state and can suppress background delivery until the user launches the app again. That tests user force-stop semantics, not ordinary process death.

Use `am kill` after putting the app in the background.

## A. Fresh M1C smoke test

```bash
git checkout agent/m1a-validation-app
git pull origin agent/m1a-validation-app
bash scripts/run-emulator-debug.sh --clear-data
```

Expected UI:

- header says `Android validation · M1C`;
- **Background capture = Armed**;
- registration version is `v1`;
- `Downloaded` / `Uploaded` counters are numeric;
- the shield/blue/cyan brand theme is visible in light or dark mode.

Capture evidence twice and export once before the kill test if you want a quick baseline.

## B. Emulator process-absent wake test

### 1. Put the app in the background

Press Home in the emulator, then confirm the package process exists:

```bash
adb shell pidof com.daniele21.trafficmonitoring.debug
```

### 2. Kill only the background process

```bash
adb shell am kill com.daniele21.trafficmonitoring.debug
sleep 2
adb shell pidof com.daniele21.trafficmonitoring.debug || true
```

The second command should normally return no PID.

### 3. Create a new network-availability event

For the emulator, disabling Wi-Fi may only represent a **loss**, which the PendingIntent API is not guaranteed to deliver. The useful positive test is the subsequent Wi-Fi availability.

```bash
adb shell svc wifi disable
sleep 8

# Record the host-side test time for later comparison.
date -u '+%Y-%m-%dT%H:%M:%SZ'

adb shell svc wifi enable
sleep 12
```

Check whether Android recreated the app process:

```bash
adb shell pidof com.daniele21.trafficmonitoring.debug || true
```

A recreated PID is useful evidence but is not itself the gate. The exported event timeline is the source of truth.

### 4. Reopen and export

```bash
bash scripts/run-emulator-debug.sh
```

Do **not** use `--clear-data`.

Open the app and verify `PendingIntent wakes` increased. Export the validation ZIP.

## C. ZIP success criteria

A successful process-absent wake should show:

```text
lifecycle-events.csv
  process_start                 around receiver process creation
  pending_intent_received       around actual network availability

network-events.csv
  source = pending_intent
  kind   = available
  network handle / transport from EXTRA_NETWORK

counter-snapshots.csv
  eventId references that pending_intent event
```

The key timing question is:

> Is the `pending_intent` event timestamp near the deliberate Wi-Fi enable / real network availability time, rather than only when the UI is reopened later?

The interval crossing ordinary process death should remain conservative. If the previous evidence belongs to the old process, the first new interval is expected to be:

```text
confidence = discarded
reason = process_restart_boundary
rxBytes = null
txBytes = null
```

That is intentional: background delivery proves a boundary signal, not complete knowledge of bytes consumed during an unobserved process gap.

## D. Negative/control cases

After the positive test is understood, optionally test:

- Wi-Fi disable only — determine whether no wake is produced for pure loss;
- Wi-Fi enable again — availability should be the stronger PendingIntent candidate;
- package force-stop — treat suppression as expected Android user-stop behavior, not M1C failure;
- repeated app opens/re-arms — verify there is no callback multiplication;
- reboot — verify `boot_received` plus re-registration evidence;
- app update/install — verify package-replaced re-registration where the platform delivers it.

## E. Physical-device gate

The emulator only validates wiring. The product decision needs a real phone.

Suggested sequence with the app armed and then process-absent:

```text
Wi-Fi A connected
→ app to background
→ ordinary process kill / long idle
→ leave Wi-Fi A
→ connect Wi-Fi B or re-enable Wi-Fi
→ wait before reopening app
→ reopen and export
```

Repeat with cellular/Wi-Fi transitions where practical.

Success criteria:

- a deliberate new network availability produces `source=pending_intent` evidence while the UI process was absent;
- timestamps are close enough to the actual transition to be useful for attribution boundaries;
- registration survives ordinary process death;
- reboot/app replacement can re-arm the registration;
- no process/reboot/clock gap is converted into confident synthetic usage;
- missing loss-only events are explicitly understood rather than assumed away.

## M1C decision gate

Choose based on evidence:

1. PendingIntent availability events are sufficient to reconstruct the required boundaries → proceed to recovery/lifecycle hardening.
2. They help but leave material gaps → combine with coarse recovery and quantify uncertainty in M1D/M1E.
3. They miss required boundaries too often → only then evaluate the Foreground Service fallback experiment.

Do not introduce a permanent Foreground Service before this evidence exists.
