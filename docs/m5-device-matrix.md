# M5 — Device/OEM reliability matrix

## Status

Reliability instrumentation and test protocol implemented. Multi-device acceptance remains pending real hardware.

## Why this exists

Background process policy differs materially across Android versions and OEM builds. Product totals must therefore be validated across representative devices rather than inferred from one emulator or one phone.

Every M5 run should pair a validation ZIP with:

```bash
bash scripts/device-validation-status.sh --device SERIAL
```

The app also records a `device_environment` lifecycle event containing the environment needed to interpret the run.

## Minimum device set

| Family | Purpose | Target |
|---|---|---|
| Google Pixel / AOSP-like | Android baseline | at least one current supported Android version |
| Samsung Galaxy | major OEM behavior | at least one recent One UI device |
| aggressive-background OEM | stress background survival | Xiaomi/Redmi/POCO, Oppo/Realme, Vivo or equivalent if available |
| older supported Android | version regression | one device/emulator near the lower practical support range |

Do not mark M5 complete from emulators alone.

## Test dimensions per device

### Baseline

- fresh install;
- upgrade over existing data;
- light/dark mode product UI;
- Overview and Networks totals reconcile with stored analytics buckets;
- Monitor export remains available.

### Network transitions

- Wi-Fi A → Wi-Fi B;
- Wi-Fi → mobile → Wi-Fi;
- hotspot → Wi-Fi;
- offline gap;
- VPN on/off;
- unknown/redacted Wi-Fi name state.

### Lifecycle/background

- UI backgrounded for 30 min;
- overnight idle;
- Doze/idle entry where practical;
- Battery Saver on/off;
- Optimized battery mode;
- Unrestricted battery mode;
- ordinary cached-process death;
- low-memory process death where reproducible;
- reboot;
- app update;
- explicit force-stop as a negative-control test.

Force-stop is not a positive background-delivery test. It is used to confirm that stopped-package behavior is visible and does not create misleading continuity.

### Recovery

Verify that the unique four-hour recovery work is scheduled and eventually runs subject to Android scheduling constraints. The test is about recovery and evidence continuity, not exact alarm-like timing.

## Metrics

For each device calculate:

- deliberate transitions attempted;
- transitions with corresponding evidence;
- PendingIntent wake count;
- recovery-worker executions;
- unattributed bytes and percentage;
- discarded intervals by reason;
- longest unexplained evidence gap;
- process exits by reason;
- controlled transfer delta error band;
- qualitative passive battery impact.

## Acceptance

A device profile passes when:

- no known transition is confidently assigned to the wrong network;
- deliberate transitions are represented by event evidence or an explicit uncertain gap;
- stable-network totals remain plausible;
- counter resets/reboots cannot create giant values;
- process death cannot create confident synthetic attribution;
- product UI and stored totals remain consistent after reboot/update;
- battery/background behavior is acceptable without a permanent FGS, or the evidence clearly justifies testing M1F.

## Result table

Keep this table updated as hardware runs arrive:

| Device | Android | Battery mode | 48–72h | Transition capture | Unattributed | Battery | Result |
|---|---:|---|---|---:|---:|---|---|
| Emulator | 36 | n/a | smoke only | M1B/M1C wiring | n/a | n/a | not an M5 device pass |
| Pixel | — | — | pending | — | — | — | pending |
| Samsung | — | — | pending | — | — | — | pending |
| Aggressive OEM | — | — | pending | — | — | — | pending |
