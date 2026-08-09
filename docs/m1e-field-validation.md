# M1E — Standard-mode 48–72 hour field validation

## Status

Protocol implemented. Field gate remains pending until a physical-device run is completed.

## Standard mode under test

```text
PendingIntent network availability events
+ in-process NetworkCallback diagnostics while alive
+ TrafficStats cumulative counters
+ 4-hour WorkManager recovery checkpoint
+ process/boot/exit evidence
+ no permanent Foreground Service
```

The recovery worker is a safety net, not a high-frequency measurement source.

## Primary run

Run 48–72 hours on a physical phone with Battery set to **Unrestricted** for the primary feasibility test. Do not force-stop the app during the normal run.

At the start:

1. install/update the current debug or internal-test build;
2. open Traffic Monitoring and verify `Current network` plus `Monitoring`;
3. open **Monitor** and verify background capture is armed;
4. start a fresh validation run;
5. capture `bash scripts/device-validation-status.sh --device SERIAL` and save the output next to the final ZIP.

## Deliberate transition matrix

Perform and note wall-clock times for at least:

- Wi-Fi A → Wi-Fi B;
- Wi-Fi → mobile;
- mobile → Wi-Fi;
- Wi-Fi → offline → Wi-Fi;
- hotspot → Wi-Fi;
- VPN off → on → off;
- screen-off transition after at least 30 minutes idle;
- one overnight idle period;
- one Battery Saver period;
- one app process recreation without force-stop.

Use Monitor markers immediately before/after deliberate transitions when practical.

## Known transfers

Perform at least three controlled transfers:

- stable Wi-Fi download;
- stable mobile/hotspot transfer;
- transfer close to a deliberate network boundary.

The stable-network transfer should produce a plausible counter delta. The boundary transfer is expected to become unattributed unless evidence brackets it strongly enough.

## Recovery evidence

Over a 48–72 hour run, the export should contain `recovery_worker_started` / `recovery_worker_completed` entries at an inexact coarse cadence. Exact four-hour timing is not required.

Each recovery execution should:

- re-arm the PendingIntent registration;
- capture current network + counters;
- record process-exit evidence where available;
- avoid creating a confident interval across an unknown process gap.

## Pass criteria

Primary feasibility target:

- 100% of deliberate scripted network transitions have corresponding evidence sufficient to explain the boundary;
- no deliberate transition is silently assigned to the wrong network;
- controlled stable-network transfers are plausible;
- zero negative/giant reset artifacts;
- reboot/process/clock discontinuities are explicit;
- unattributed usage is visible rather than guessed;
- export independently explains the run;
- no permanent Foreground Service was required;
- passive battery impact is acceptable for the product.

## Failure classification

Classify every miss before changing architecture:

- PendingIntent availability not delivered;
- loss-only boundary not observable;
- process killed / package stopped;
- recovery delay;
- SSID permission/redaction;
- VPN ambiguity;
- counter reset/reboot;
- OEM background restriction;
- measurement bug.

Do not introduce a Foreground Service just because one export contains unattributed usage. M1F is justified only if field evidence shows the standard strategy systematically misses product-critical boundaries.

## Artifacts to keep

For every field run keep:

```text
validation ZIP
+ device-validation-status output
+ phone/OEM/Android version
+ battery mode
+ deliberate transition timestamps
+ known transfer sizes
+ qualitative battery observation
```
