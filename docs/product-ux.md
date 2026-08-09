# Product UX — minimal by default

## Principle

Traffic Monitoring should answer the user's first question in a few seconds:

> How much data did I use, when did I use it, and on which network?

Then, without forcing technical jargon into the home screen, it should make a second question easy to answer:

> How much evidence supports those numbers?

The product surface must stay simpler than the measurement system underneath it.

## Product hierarchy

There are three levels of detail:

```text
Usage
What happened?

Evidence
How trustworthy / explainable is it?

Monitor
How did Android measure it?
```

This separation is a core UX rule.

## Information architecture

### Current primary product navigation

```text
Overview
Networks
```

### Planned evidence-first expansion

After E1/E3:

```text
Overview
Networks
Experiments
```

**Evidence** is primarily entered contextually from Overview, Networks or an Experiment result rather than becoming a dense dashboard by default.

`Monitor` remains advanced diagnostics and is not a peer analytics destination.

## Overview hierarchy

Show, in this order:

1. **Current network** + simple monitoring status;
2. selected timeframe;
3. **Total used** as the dominant metric;
4. **Downloaded** and **Uploaded**;
5. one restrained usage trend with peak;
6. top networks;
7. a compact **Evidence coverage** summary;
8. a factual unattributed-usage note only when needed.

Target Evidence card:

```text
Evidence coverage
92%

11.4 GB attributed
0.8 GB unattributed
Measurement health · Good

View evidence →
```

Do not show implementation terms such as `TrafficStats`, RX/TX, network handles, interfaces, callback kinds, PendingIntent wake counts or raw attribution reasons on the normal product surface.

## Networks

Show a ranked list with:

- human-readable network name;
- total used;
- percentage of selected-timeframe usage;
- friendly transport label such as Wi-Fi, Mobile or Ethernet.

Unattributed usage is visible as **Unattributed**, not silently redistributed.

A network detail may later expose a simple evidence summary for that network/timeframe, but raw provenance remains behind drill-down.

## Evidence

Evidence is a human-readable product layer, not the validation console.

It should answer:

- how much usage was attributed;
- how much remained unattributed;
- whether continuity gaps occurred;
- whether invalid/discarded evidence occurred;
- how long the largest gap was;
- why Measurement Health is limited/degraded;
- which normalized timeline events explain the result.

Preferred language:

```text
Evidence coverage
Attributed
Unattributed
Measurement health
Continuity gap
Monitoring recovered
Evidence timeline
```

Avoid raw callback/source names unless the user explicitly opens Monitor.

Evidence Coverage and Measurement Health must remain separate. Do not hide multiple technical conditions behind one unexplained trust score.

## Experiments

After E3, Experiments becomes the evaluation-oriented surface.

### Start

Keep creation short:

```text
New experiment

Name
Local inference privacy test

Expected behavior (optional)
No meaningful network activity

[ Start experiment ]
```

Do not require the user to configure assertions before starting a simple experiment.

### Running

Show only the current essentials:

```text
Experiment running · 02:31
Current network · Home Wi-Fi
Observed · 42 KB
Evidence coverage · 100%

[ Stop experiment ]
```

### Result

```text
Local inference privacy test
5m 04s
42 KB observed
Evidence coverage · 100%
0 continuity gaps

Checks
✓ Upload below 100 KB
? No cellular fallback · Inconclusive

[ View evidence ]
[ Export evidence pack ]
```

Assertion result language must support:

- Pass;
- Fail;
- Inconclusive.

Inconclusive is not an error state; it is the truthful result when evidence is insufficient.

## Monitor

Monitor owns technical/debug detail:

- raw network observations;
- counter snapshots;
- attribution confidence/reasons;
- lifecycle events;
- background registration/wake state;
- recovery-worker evidence;
- validation markers and ZIP export.

Rule:

> If it explains *network usage*, it belongs in Usage.  
> If it explains *how trustworthy the result is*, it belongs in Evidence.  
> If it explains *how Android measured it*, it belongs in Monitor.

## Language

Use in the primary product:

- Total used
- Downloaded
- Uploaded
- Current network
- Usage by network
- Usage trend
- Peak usage
- Monitoring / Monitoring needs attention
- Evidence coverage
- Measurement health
- Attributed
- Unattributed

Avoid protocol/counter jargon in the primary experience.

## Visual behavior

Follow `brand-kit.md`:

- shield mainly on identity surfaces;
- flat, quiet interface;
- Royal Blue for primary selection/action;
- Signal Cyan for live state/meaningful peak;
- semantic green/amber/red only for actual status;
- light and dark mode equally supported;
- no decorative gradients/heavy chart fills in product UI.

Evidence should not become visually alarmist. Uncertainty is information, not automatically an error.

## Progressive disclosure

Use compact summaries first:

```text
Evidence coverage · 92%
Measurement health · Good
```

Then detail:

```text
Attributed          11.4 GB
Unattributed         0.8 GB
Continuity gaps           1
Longest gap             18 s
```

Only after another explicit action should the user see raw Monitor data.

## UX acceptance checklist

A first-time user should be able to answer without opening Monitor:

- Which network am I on?
- Is monitoring active?
- How much have I used in the selected timeframe?
- How much was download vs upload?
- Which networks used the most data?
- When was usage highest?
- Was any usage not confidently attributable?
- How much evidence supports the displayed attribution?
- Was measurement continuity healthy?

An experiment user should additionally be able to answer:

- What period did I evaluate?
- What traffic was observed during it?
- Which checks passed, failed or were inconclusive?
- Can I export a self-contained evidence report?

See `evidence-observability-roadmap.md` for implementation milestones E0–E6.
