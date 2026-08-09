# Documentation map

The documentation follows progressive disclosure. Start from the smallest document that answers the current task and follow links only when necessary.

## Reading paths

### Implementing measurement/background behavior

1. `../AGENTS.md`
2. `implementation-plan.md`
3. `measurement-engine.md`
4. `background-strategy.md`
5. `m1c-validation.md` for PendingIntent process-absent tests
6. `m1e-field-validation.md` for the 48–72 hour standard-mode gate
7. `m5-device-matrix.md` for cross-OEM validation

### Building or refining the product UX

1. `product-ux.md`
2. `brand-kit.md`
3. `implementation-plan.md` M3/M4

### Debugging locally or preparing a Play internal-test build

1. `../AGENTS.md`
2. `local-development-and-release.md`

### Applying or changing the visual identity

1. `brand-kit.md`
2. `product-ux.md`
3. `../app/src/main/java/com/daniele21/trafficmonitoring/ui/theme/TrafficMonitoringTheme.kt`
4. `../app/src/main/res/drawable/ic_brand_shield.xml`
5. `../docs/assets/traffic-monitoring-lockup.svg`

### Changing network attribution behavior

1. `../AGENTS.md`
2. `measurement-engine.md`
3. `data-and-export.md`
4. `m1b-validation.md` for the validated counter/in-process baseline
5. `m1c-validation.md` / `m1e-field-validation.md` when background evidence is affected
6. `decisions.md` when revisiting a recorded choice

### Changing validation/export behavior

1. `data-and-export.md`
2. `m1c-validation.md`
3. `m1e-field-validation.md`
4. `m5-device-matrix.md`

## Documents

- `brand-kit.md` — approved shield identity, palette, product language and visual rules.
- `product-ux.md` — minimal consumer information architecture and language boundary between product UI and Monitor.
- `product-spec.md` — user problem, measurement scope, non-goals and success criteria.
- `architecture.md` — layers, components and runtime data flow.
- `measurement-engine.md` — traffic counters, network identity, attribution intervals, confidence and reset handling.
- `background-strategy.md` — PendingIntent events, process lifecycle, recovery and Foreground Service escalation.
- `data-and-export.md` — raw evidence, derived intervals and validation export bundle.
- `implementation-plan.md` — phased delivery plan and current status through M5.
- `m1b-validation.md` — completed M1B emulator evidence.
- `m1c-validation.md` — PendingIntent process-absent validation protocol.
- `m1e-field-validation.md` — 48–72 hour standard-mode physical-device protocol.
- `m5-device-matrix.md` — cross-version/OEM reliability matrix.
- `testing.md` — broader scripted acceptance matrix.
- `local-development-and-release.md` — emulator debugging, upload-key management and signed AAB generation.
- `decisions.md` — architectural decision log.

## Documentation rule

Avoid repeating detailed behavior across documents. Link to the authoritative document for a concept instead of copying it. Root-level files should remain concise enough that coding agents can orient with minimal context.
