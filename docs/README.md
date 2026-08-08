# Documentation map

The documentation follows progressive disclosure. Start from the smallest document that answers the current task and follow links only when necessary.

## Reading paths

### Implementing the current measurement spike

1. `../AGENTS.md`
2. `implementation-plan.md`
3. `measurement-engine.md`
4. `m1b-validation.md` for the current executable protocol
5. `background-strategy.md` before M1C
6. `testing.md` for the complete acceptance matrix

### Debugging locally or preparing a Play internal-test build

1. `../AGENTS.md`
2. `local-development-and-release.md`

### Changing network attribution behavior

1. `../AGENTS.md`
2. `measurement-engine.md`
3. `data-and-export.md` if stored evidence or derived intervals change
4. `m1b-validation.md` to update the matching field-test protocol
5. `decisions.md` only when revisiting a recorded choice

### Changing background execution behavior

1. `../AGENTS.md`
2. `background-strategy.md`
3. `testing.md`
4. `decisions.md` if the selected execution mode changes

### Changing validation/export behavior

1. `../AGENTS.md`
2. `data-and-export.md`
3. `testing.md`

### Reviewing product scope

1. `product-spec.md`
2. `architecture.md`

## Documents

- `product-spec.md` — user problem, measurement scope, non-goals and success criteria.
- `architecture.md` — layers, components and runtime data flow.
- `measurement-engine.md` — traffic counters, network identity, attribution intervals, confidence and reset handling.
- `background-strategy.md` — PendingIntent network events, process lifecycle, recovery, Unrestricted battery mode and Foreground Service escalation.
- `data-and-export.md` — Room entities, raw evidence, derived intervals and validation export bundle.
- `implementation-plan.md` — phased delivery plan with hard acceptance gates.
- `m1b-validation.md` — current M1B emulator/physical-device protocol and expected export evidence.
- `testing.md` — complete scripted device-test matrix and evaluation criteria.
- `local-development-and-release.md` — emulator debugging, local upload-key management, signed AAB generation and Play internal-testing flow.
- `decisions.md` — compact architectural decision log.

## Documentation rule

Avoid repeating detailed behavior across documents. Link to the authoritative document for a concept instead of copying it. Root-level files should remain concise enough that coding agents can orient with minimal context.
