# Documentation map

The documentation follows progressive disclosure. Start from the smallest document that answers the current task and follow links only when necessary.

## Two roadmap tracks

Traffic Monitoring now has two explicit development tracks:

```text
M roadmap — Android measurement reliability / feasibility
E roadmap — evidence-first observability product
```

Measurement claims continue to depend on physical-device evidence even when product-layer code already exists.

## Reading paths

### Implementing measurement/background behavior

1. `../AGENTS.md`
2. `implementation-plan.md`
3. `measurement-engine.md`
4. `background-strategy.md`
5. `m1c-validation.md` for PendingIntent process-absent tests
6. `m1e-field-validation.md` for the 48–72 hour standard-mode gate
7. `m5-device-matrix.md` for cross-OEM validation

### Building the evidence-first product

1. `evidence-observability-roadmap.md`
2. `e1-evidence-coverage.md` for implemented Coverage/Health semantics
3. `product-spec.md`
4. `product-ux.md`
5. `architecture.md`
6. `data-and-export.md` when Evidence Pack / provenance changes
7. `implementation-plan.md` for combined M/E execution order

### Building or refining the minimal usage UX

1. `product-ux.md`
2. `brand-kit.md`
3. `e1-evidence-coverage.md` for the current Evidence surface
4. `evidence-observability-roadmap.md` when adding Evidence/Experiments

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
6. `e1-evidence-coverage.md` if Coverage/Health semantics are affected
7. `evidence-observability-roadmap.md` for broader Evidence roadmap changes
8. `decisions.md` when revisiting a recorded choice

### Changing Evidence Coverage / experiments / assertions

1. `evidence-observability-roadmap.md`
2. `e1-evidence-coverage.md` for Coverage/Health
3. `architecture.md`
4. `product-ux.md`
5. `measurement-engine.md` only if underlying attribution semantics change
6. `data-and-export.md` if provenance/export contracts change

### Changing validation/export behavior

1. `data-and-export.md`
2. `m1c-validation.md`
3. `m1e-field-validation.md`
4. `m5-device-matrix.md`
5. `evidence-observability-roadmap.md` when changing the product Evidence Pack rather than the engineering validation ZIP

## Documents

- `brand-kit.md` — approved shield identity, palette, product language and visual rules.
- `product-ux.md` — minimal usage UX plus progressive-disclosure boundary between Usage, Evidence and Monitor.
- `product-spec.md` — evidence-first positioning, user problem, measurement scope, privacy and product truthfulness.
- `evidence-observability-roadmap.md` — E0–E6 roadmap: Evidence Coverage, Health, Evidence Pack, Experiments, Assertions, optional app context and open observability export.
- `e1-evidence-coverage.md` — implemented Evidence Coverage v1, Measurement Health thresholds, architecture, UI and acceptance tests.
- `architecture.md` — measurement, usage, evidence, experiment and export layers.
- `measurement-engine.md` — traffic counters, network identity, attribution intervals, confidence and reset handling.
- `background-strategy.md` — PendingIntent events, process lifecycle, recovery and Foreground Service escalation.
- `data-and-export.md` — raw evidence, derived intervals and validation export bundle.
- `implementation-plan.md` — combined M/E delivery plan and current status.
- `m1b-validation.md` — completed M1B emulator evidence.
- `m1c-validation.md` — PendingIntent process-absent validation protocol.
- `m1e-field-validation.md` — 48–72 hour standard-mode physical-device protocol.
- `m5-device-matrix.md` — cross-version/OEM reliability matrix.
- `testing.md` — broader scripted acceptance matrix.
- `local-development-and-release.md` — emulator debugging, upload-key management and signed AAB generation.
- `decisions.md` — architectural decision log.

## Documentation rule

Avoid repeating detailed behavior across documents. Link to the authoritative document for a concept instead of copying it. Root-level files should remain concise enough that coding agents can orient with minimal context.
