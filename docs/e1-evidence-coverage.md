# E1 — Evidence Coverage + Measurement Health

## Status

**Implementation complete. Emulator/product validation pending.**

E1 is the first product milestone in the evidence-first observability roadmap.

It turns existing attribution/history data into two explicit product concepts:

1. **Evidence Coverage** — how much accountable usage can be assigned to a specific network;
2. **Measurement Health** — whether invalid or discontinuous evidence makes the selected timeframe less trustworthy.

These concepts are intentionally separate.

## Why two metrics

A single trust score would hide important failure modes.

Example:

```text
Evidence Coverage = 99%
Measurement Health = Degraded
```

This is possible when nearly all valid measured bytes are attributable, but a long process/clock continuity gap also exists. The high coverage number must not erase that fact.

## Evidence Coverage v1

For the selected timeframe:

```text
accountedBytes = attributedBytes + unattributedBytes

Evidence Coverage = attributedBytes / accountedBytes
```

Where:

- **Attributed** means accepted usage assigned to a specific network identity;
- **Unattributed** means accepted usage whose network owner cannot be supported strongly enough;
- **Discarded** intervals do not enter usage totals because their counter/continuity evidence is invalid.

If `accountedBytes == 0`, coverage is **not calculated**. The UI shows `Not enough data`; it never reports 100% from absence of evidence.

Metric definition version: `1`.

## Measurement Health v1

States:

```text
Good
Limited
Degraded
```

Current deterministic thresholds:

### Good

- Evidence Coverage >= 95%;
- no discarded interval overlaps the timeframe.

### Limited

Any of:

- no accountable usage yet;
- Evidence Coverage is between 80% and 95%;
- at least one discarded interval overlaps the timeframe.

### Degraded

Any of:

- Evidence Coverage < 80%;
- a continuity gap lasts at least 30 minutes;
- three or more discarded intervals overlap the timeframe.

These thresholds are product semantics, not Android platform guarantees. If changed, bump the metric-definition version and preserve deterministic tests.

## Continuity gap reasons

The first version treats these discarded reasons as continuity gaps:

```text
process_restart_boundary
clock_discontinuity
boot_changed
non_monotonic_elapsed_realtime
```

`counter_reset` is still discarded evidence and affects Measurement Health, but is not counted as a continuity gap in E1.

## Implementation

### Domain

`evidence/EvidenceSummary.kt`

Contains:

```text
EvidenceIntervalInput
EvidenceCoverageSummary
MeasurementHealthState
EvidenceSummaryCalculator
```

The calculator is pure Kotlin and has no Android/Room dependency.

### Data access

`ValidationDao.attributionIntervalsBetween(startMs, endMs)` returns intervals overlapping the selected timeframe.

`ValidationRepository.loadEvidenceIntervals(...)` maps Room entities into neutral evidence inputs.

### Product state

`ProductViewModel` combines:

```text
UsageRepository snapshot
        +
validation attribution intervals
        ↓
EvidenceSummaryCalculator
        ↓
ProductUiState.evidence
```

The usage snapshot remains the source for accountable byte totals. Validation intervals provide discarded/continuity evidence that is intentionally absent from consumer usage history.

## UI

### Overview

A compact **Evidence** card shows:

- Evidence Coverage;
- Measurement Health;
- a one-line explanation;
- `View evidence →`.

### Evidence

A dedicated product section shows:

- coverage;
- health and explicit reasons;
- attributed vs unattributed usage;
- continuity-gap count;
- discarded-interval count;
- longest continuity gap;
- the exact Coverage v1 methodology.

This screen is not Monitor.

### Monitor

Monitor continues to own:

- raw counter values;
- event IDs;
- Android network handles;
- callback/PendingIntent details;
- raw lifecycle evidence;
- validation export.

## Acceptance tests

Unit fixtures cover:

- 100% attributed usage => Good;
- unattributed usage reduces coverage;
- no usage does not become fake 100%;
- process restart is visible as continuity loss;
- long gap degrades health;
- gap duration is clipped to the selected timeframe.

## Remaining E1 validation

Before calling E1 product-validated:

1. build/install on emulator and confirm Overview remains minimal;
2. generate attributed + unattributed fixtures through real app behavior;
3. verify the Evidence screen reconciles with Monitor/export values;
4. verify small-screen and dark-mode readability;
5. repeat against a physical-device M1E export once available.

E1 implementation can be complete before M1E/M5 reliability gates pass, because uncertainty is surfaced rather than hidden.
