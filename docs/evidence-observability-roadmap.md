# Evidence-first observability roadmap

## Positioning

Traffic Monitoring is not positioned as a generic data-usage meter and it is not a packet-inspection product.

The target category is:

> **Evidence-first network observability for Android.**

The product promise is:

> **Know your network usage — and the evidence behind it.**

The differentiator is not that the app can display a byte total. The differentiator is that every meaningful usage claim is paired with an explicit statement of how much evidence supports it, what remains uncertain, and which raw observations explain the result.

This makes Traffic Monitoring a practical example of an evaluation-first engineering philosophy:

```text
Observe
What happened?

Attribute
Which network most likely carried it?

Evaluate
How strong is the supporting evidence?

Explain
Which observations and gaps justify that conclusion?
```

## Relationship with the M roadmap

The existing `M0–M5` roadmap remains the measurement/reliability track.

This document introduces a parallel **E roadmap** for the evidence/observability product layer.

```text
Measurement reliability                         Evidence product

M1–M5                                            E0–E6
Android delivery / counters                     Evidence semantics
Attribution correctness                         Coverage / health
Process/background behavior       ───────→       Experiments
OEM reliability                                 Assertions
                                                   Evidence packs
                                                   Optional integrations
```

Code may be implemented ahead of field validation, but product claims must never exceed the evidence earned by M1E/M5.

A green CI build does not prove background measurement reliability.

## Product principles

1. **A metric without provenance is incomplete.** Important totals should expose the evidence quality behind them.
2. **Uncertainty is first-class.** `Unattributed` and continuity gaps are product information, not debug leftovers.
3. **Local-first by default.** Measurement, experiments and evidence packs stay on-device unless the user explicitly exports or enables an integration.
4. **No hidden packet inspection.** The core product does not collect packet contents, destinations, URLs, DNS history or browsing history.
5. **Evaluation before assertion.** The product can check claims only when the underlying Android data source can actually support them.
6. **Observability without vendor lock-in.** Machine-readable exports and later open-standard adapters are preferred over proprietary-only telemetry.
7. **Consumer simplicity and engineering depth coexist.** The default UI stays minimal; evidence detail is progressively disclosed.

## Target information architecture

Do not turn the consumer UI into the current validation console.

Target structure:

```text
Overview
├─ Current network
├─ Total used
├─ Downloaded / Uploaded
├─ Usage trend
├─ Usage by network
└─ Evidence coverage summary

Networks
├─ Ranked networks
├─ Per-network usage
└─ Confidence / evidence summary

Experiments
├─ Start experiment
├─ Active experiment
├─ Results
├─ Assertions
└─ Evidence pack

Evidence
├─ Coverage
├─ Continuity
├─ Attribution breakdown
└─ Human-readable timeline

Advanced
└─ Monitor
   ├─ raw network events
   ├─ raw counters
   ├─ lifecycle events
   ├─ callback / PendingIntent detail
   └─ validation export
```

`Monitor` remains an engineering diagnostic console. **Evidence** is a separate product layer that translates the same provenance into understandable, auditable concepts.

## Core product metric — Evidence Coverage

### Goal

Answer:

> How much of the measured usage is supported strongly enough to be assigned to a specific network?

Initial definition for a timeframe:

```text
accountedBytes = inferredBytes + unattributedBytes

attributedBytes = inferredBytes

Evidence Coverage = attributedBytes / accountedBytes
```

`discarded` intervals are not included in usage totals because their counter evidence is invalid or unusable. They must instead affect a separate **Measurement Health** signal.

The formula can evolve once `confirmed` evidence exists, but changes require a versioned metric definition.

### Supporting metrics

```text
Evidence coverage             percentage of accountable bytes attributed
Attributed usage              bytes assigned to a network
Unattributed usage            valid bytes whose owner cannot be proven
Continuity gaps               gaps where measurement continuity was lost
Discarded intervals           invalid/reset/clock-discontinuous evidence
Longest evidence gap          maximum period without trustworthy continuity
Measurement health            compact derived status, never a hidden heuristic
```

### Truthfulness rule

Do not collapse evidence coverage and measurement health into one unexplained score.

A user must be able to understand why either is degraded.

---

# E0 — Positioning and semantics

**Status: documentation foundation.**

Deliverables:

- evidence-first positioning;
- product promise and vocabulary;
- separation between Evidence and Monitor;
- metric definitions;
- E roadmap and dependency rules;
- README/product-spec/architecture alignment.

Gate:

- a contributor can explain what makes Traffic Monitoring different from a generic traffic meter in one sentence;
- no source-of-truth document implies packet-level observability the app does not provide.

---

# E1 — Evidence Coverage and Measurement Health

**Priority: highest.**

This is the first implementation milestone because most required data already exists in `AttributionInterval`, usage history and lifecycle evidence.

## Deliverables

### Domain

Add a pure evidence-summary component, e.g.:

```text
EvidenceSummaryCalculator
EvidenceCoverageSummary
MeasurementHealthSummary
```

Suggested output:

```text
startMs
endMs
attributedRxBytes
attributedTxBytes
unattributedRxBytes
unattributedTxBytes
discardedIntervalCount
continuityGapCount
longestGapMs
evidenceCoveragePercent
healthState: good | limited | degraded
healthReasons[]
metricDefinitionVersion
```

### Persistence/query

Prefer deriving the first version from existing attribution/history data rather than introducing another database prematurely.

If queries become expensive, add bounded daily evidence aggregates later.

### UI

Add a small card to Overview:

```text
Evidence coverage
92%

11.4 GB attributed
0.8 GB unattributed
Measurement health · Good
```

Tap-through opens Evidence detail, not Monitor.

### Evidence detail

Show:

- attributed vs unattributed;
- continuity gaps;
- discarded evidence count;
- longest gap;
- plain-language reasons;
- selected timeframe.

Do not show raw Android handles/callback names here.

## Tests

- 100% attributed fixture;
- mixed attributed/unattributed fixture;
- discarded-only fixture;
- process-gap fixture;
- counter-reset fixture;
- zero-usage timeframe;
- exact byte reconciliation.

## Gate

Given a deterministic fixture, Evidence Coverage must reconcile exactly with underlying attribution totals and explain every degraded state.

---

# E2 — Human-readable Evidence Timeline and Evidence Pack

## Goal

Turn the existing validation provenance into something shareable and understandable without opening raw CSV files.

## Evidence timeline

Add a product-level timeline containing normalized events such as:

```text
10:02  Monitoring started
10:14  Wi-Fi changed to Home Wi-Fi
10:14  1.8 MB could not be attributed during transition
11:05  Measurement continuity lost for 18 s
11:05  Monitoring recovered
```

This timeline is **derived from raw evidence**. The raw evidence remains untouched and available in Monitor.

## Evidence Pack

Keep the current validation ZIP for engineering diagnostics and introduce a distinct user-facing evidence export:

```text
traffic-monitoring-evidence/
├── report.md
├── report.json
├── methodology.json
├── environment.json
├── metrics/
│   ├── usage.csv
│   ├── networks.csv
│   └── evidence-summary.csv
├── evidence/
│   ├── attribution.csv
│   ├── network-events.csv
│   └── continuity.csv
└── manifest.sha256
```

`report.md` should explain:

- period;
- device/Android environment without stable hardware identifiers;
- total/download/upload;
- attributed vs unattributed;
- Evidence Coverage;
- continuity gaps;
- methodology/version;
- explicit measurement limitations.

`manifest.sha256` provides integrity checking for the exported files. It does **not** claim cryptographic attestation of device behavior.

## Privacy

Evidence export is explicit user action.

Add optional network-name redaction before the evidence pack becomes a public-facing feature.

## Gate

A person who did not run the experiment should be able to understand the result, evidence quality and limitations from `report.md` alone, then trace claims to machine-readable evidence when needed.

---

# E3 — Experiment Mode

## Goal

Make Traffic Monitoring useful as a repeatable evaluation harness, especially for local-first/privacy-sensitive Android workflows.

An experiment is a bounded observation period with user intent and its own evidence summary.

## Data model

Suggested entities:

```text
Experiment
- id
- name
- description?
- startedAtMs
- endedAtMs?
- status: running | completed | cancelled
- expectedNetwork?
- notes?
- evidenceMetricVersion

ExperimentEvidenceRef
- experimentId
- attributionIntervalId / evidence range reference
```

Avoid duplicating raw network/counter rows into experiment tables. Experiments should reference a bounded time/evidence range.

## UX

Start flow:

```text
New experiment

Name
Local inference privacy test

Expected behavior (optional)
No meaningful network activity

[ Start experiment ]
```

Running state:

```text
Experiment running · 02:31
Current network · Home Wi-Fi
Observed · 42 KB
Evidence coverage · 100%

[ Stop ]
```

Result:

```text
Local inference privacy test
5m 04s
42 KB observed
Evidence coverage · 100%
0 continuity gaps

[ View evidence ]
[ Export evidence pack ]
```

## Gate

Experiment start/stop boundaries must map deterministically to the stored usage/evidence timeline and survive process recreation without silently changing the experiment period.

---

# E4 — Assertions and evaluation results

## Goal

Move from passive observability to explicit, reproducible checks.

Assertions evaluate stored experiment evidence; they do not control or intercept traffic.

## Initial assertion types

Only support assertions backed by available evidence:

```text
TOTAL_USAGE_BELOW(bytes)
UPLOAD_BELOW(bytes)
DOWNLOAD_BELOW(bytes)
NO_CELLULAR_FALLBACK
NETWORK_REMAINS(expectedIdentity)
EVIDENCE_COVERAGE_AT_LEAST(percent)
MAX_CONTINUITY_GAP_BELOW(duration)
NO_COUNTER_RESET
```

Potential UI:

```text
Checks
✓ Upload below 100 KB
✓ Evidence coverage ≥ 95%
✕ No cellular fallback

Cellular became available at 10:14:21
View evidence →
```

## Important semantic rule

An assertion can have three outcomes:

```text
PASS
FAIL
INCONCLUSIVE
```

If evidence quality is insufficient, return **INCONCLUSIVE**, never PASS by absence of evidence.

This is a core evidence-first invariant.

## Gate

All assertion outcomes must be reproducible from exported experiment evidence and include a machine-readable reason.

---

# E5 — Optional app-level context

## Goal

Add coarse historical application context only if it materially improves the evaluation use case.

This is **not** required for the evidence-first positioning and must remain optional.

## Constraints

Android does not give this app arbitrary real-time per-UID `TrafficStats` visibility for other apps. Any future app-level view must use platform-supported historical usage APIs and clearly represent their granularity/permission limitations.

## Product rules

- explicit Usage Access opt-in;
- explain why permission is requested before opening system settings;
- store only the minimum aggregate required;
- label app-level values as historical/coarse when appropriate;
- never imply packet-level attribution;
- core network observability continues to work without Usage Access.

## Candidate use

An experiment could show:

```text
Device usage during experiment
42 MB

Historical app context
Local LLM Harness · 2.1 MB
Chrome            · 31 MB
Other             · 8.9 MB
```

Only ship this if device testing proves the data is useful enough at Android's actual aggregation granularity.

## Gate

A controlled multi-app transfer test must demonstrate that the displayed historical app totals are directionally and temporally useful enough for the stated UI claim.

---

# E6 — Open observability export

## Goal

Make Traffic Monitoring interoperable with external observability workflows without turning remote telemetry into a requirement.

## First step

Define an internal neutral export model before implementing a transport:

```text
EvidenceMetric
EvidenceEvent
ExperimentResult
AssertionResult
```

## Candidate metrics

```text
traffic.network.rx.bytes
traffic.network.tx.bytes
traffic.attribution.coverage
traffic.attribution.unattributed.bytes
traffic.measurement.continuity_gap.count
traffic.measurement.longest_gap.ms
traffic.measurement.discarded_interval.count
traffic.experiment.assertion.pass.count
traffic.experiment.assertion.fail.count
traffic.experiment.assertion.inconclusive.count
```

## OpenTelemetry / OTLP adapter

A later optional adapter may map the neutral evidence model to OpenTelemetry/OTLP.

Rules:

- disabled by default;
- explicit endpoint/user configuration;
- never export SSID by default;
- no stable device identifiers;
- no packet/destination data;
- local evidence remains authoritative;
- transport failure must not affect measurement.

Do not couple domain entities directly to OpenTelemetry SDK classes.

## Gate

The same completed experiment must serialize deterministically to the local Evidence Pack and to the neutral observability model with matching totals and assertion outcomes.

---

# Recommended implementation order

The evidence roadmap should proceed in this order:

```text
E0 Positioning / semantics
        ↓
E1 Evidence Coverage + Health
        ↓
E2 Evidence Timeline + Evidence Pack
        ↓
E3 Experiment Mode
        ↓
E4 Assertions
        ↓
E5 Optional app-level context
        ↓
E6 Optional OTLP/OpenTelemetry adapter
```

E1–E4 can be developed while M1E/M5 physical testing continues, as long as their outputs remain explicit about measurement confidence.

E5 and E6 should wait until the core evidence semantics are stable.

## Dependencies on measurement milestones

```text
E1 requires M2/M3 data semantics                 already implemented foundation
E2 requires stable evidence/export provenance    existing M1 export foundation
E3 requires durable time/evidence ranges         available foundation
E4 requires E3 + versioned evidence metrics      future
E5 requires separate Android API validation       future experiment
E6 requires stable neutral evidence schema        future
```

M1E/M5 remain required before making broad reliability claims such as “always captures network changes in the background”.

## What not to build

Do not broaden the positioning by adding features that weaken the evidence-first story:

- packet capture;
- website/domain history;
- DNS logging;
- destination-IP history;
- a local VPN solely to inspect traffic;
- cloud accounts as a prerequisite;
- remote telemetry enabled by default;
- a single unexplained “trust score”;
- AI-generated explanations that replace deterministic evidence;
- per-app precision claims unsupported by Android data sources.

## Product messaging hierarchy

### Category

**Evidence-first network observability for Android**

### Primary promise

**Know your network usage — and the evidence behind it.**

### Supporting proof points

**Evidence-first attribution**  
Uncertain traffic is never silently assigned to the wrong network.

**Local-first by design**  
No packet contents, browsing history or destination telemetry is required.

**Reproducible evaluation**  
Experiments, assertions and evidence packs make network-behavior claims auditable.

**Open by design**  
Machine-readable evidence comes before any optional external observability integration.

## Portfolio / daniele21 mission fit

Traffic Monitoring should serve as a concrete engineering demonstration of a broader philosophy:

> Measure before claiming. Preserve uncertainty. Keep the user in control. Make results auditable.

The repository should therefore optimize not only for a useful Android utility, but also for being a credible reference implementation of evidence-first evaluation and observability under real platform constraints.
