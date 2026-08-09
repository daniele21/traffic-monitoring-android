# Product UX — minimal by default

## Principle

Traffic Monitoring should answer the user's question in a few seconds:

> How much data did I use, when did I use it, and on which network?

The product surface must stay simpler than the measurement system underneath it.

## Information architecture

Primary product navigation is intentionally small:

```text
Overview
Networks
```

`Monitor` is an advanced diagnostic surface and is not a primary analytics destination.

## Overview hierarchy

Show, in this order:

1. **Current network** + simple monitoring status;
2. selected timeframe;
3. **Total used** as the dominant metric;
4. **Downloaded** and **Uploaded**;
5. one restrained usage trend with peak;
6. top networks;
7. a factual unattributed-usage note only when needed.

Do not show implementation terms such as `TrafficStats`, RX/TX, network handles, interfaces, callback kinds, PendingIntent wake counts or attribution reasons on the normal product surface.

## Networks

Show a ranked list with:

- human-readable network name;
- total used;
- percentage of selected-timeframe usage;
- friendly transport label such as Wi-Fi, Mobile or Ethernet.

Unattributed usage is visible as **Unattributed**, not silently redistributed.

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

> If it explains *network usage*, it belongs in the product UI. If it explains *how Android measured it*, it belongs in Monitor.

## Language

Use:

- Total used
- Downloaded
- Uploaded
- Current network
- Usage by network
- Usage trend
- Peak usage
- Monitoring / Monitoring needs attention

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

## UX acceptance checklist

A first-time user should be able to answer without opening Monitor:

- Which network am I on?
- Is monitoring active?
- How much have I used in the selected timeframe?
- How much was download vs upload?
- Which networks used the most data?
- When was usage highest?
- Was any usage not confidently attributable?
