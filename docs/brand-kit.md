# Traffic Monitoring — Android brand application

This document applies the approved **Traffic Monitoring shield brand kit** to the Android product.

## Brand idea

Traffic Monitoring should feel:

- reliable — accurate, calm and dependable;
- in control — visibility without alarmism;
- technical — credible, but never cryptic;
- direct — plain labels and obvious actions.

The core principle is unchanged:

> The logo can feel premium and dimensional; the product UI stays flat, restrained and highly legible.

## Logo system

The shield remains the primary brand symbol. In Android:

- launcher / splash / compact product surfaces use the shield;
- the validation header uses the shield beside the explicit `Traffic Monitoring` name;
- the shield is not repeated inside every card or analytics panel;
- small UI surfaces use the symbol rather than shrinking a full wordmark.

The current Android resource `app/src/main/res/drawable/ic_brand_shield.xml` is a flat small-surface derivative for product rendering. The approved dimensional shield/lockup remain the canonical launch/marketing source assets and must not be modified with extra glow, bevel, shadow or altered proportions.

## Color palette

Canonical brand colors:

| Role | Hex | Product use |
| --- | --- | --- |
| Midnight | `#020D2C` | high-trust framing, splash, deep backgrounds |
| Deep Navy | `#0E2345` | dark surfaces, secondary brand framing |
| Royal Blue | `#002996` | primary action, selection, principal network series |
| Network Blue | `#207CCE` | secondary network series and supporting emphasis |
| Signal Cyan | `#0DC1F9` | live activity, current state, meaningful peaks |
| Surface | `#F2F7FD` | quiet light-mode cards/surfaces |
| Dark UI | `#10141A` | dark-mode app background |
| Healthy | `#22C55E` | semantic success only |
| Warning | `#F59E0B` | semantic warning only |
| Critical | `#EF4444` | semantic error only |

Semantic green/amber/red must never become decorative brand colors.

## Typography

The source brand kit specifies SF Pro for macOS and Inter as fallback. Android intentionally uses the platform sans-serif stack for native rendering and accessibility rather than shipping a font solely for visual matching.

Hierarchy:

- regular body text;
- semibold labels and section titles;
- bold only for exceptional key metrics;
- technical IDs/diagnostics remain secondary to user-facing labels.

## Product language

Primary product wording:

- `Total used`
- `Downloaded`
- `Uploaded`
- `Current network`
- `Usage by network`
- `Peak usage`

Avoid exposing `Raw RX`, `Raw TX`, interface names such as `wlan0`, or protocol jargon as primary labels. Technical evidence stays inside validation/monitor detail.

The M1C validation UI already follows this split: user-facing counter labels say **Downloaded / Uploaded**, while callback source, confidence and network handles stay in diagnostic sections.

## UI rules

- support light and dark mode equally;
- use rounded cards and calm spacing, without excessive chrome;
- Royal Blue = primary action / selection;
- Signal Cyan = live/background capture state;
- keep charts flat when analytics is introduced: 2–3 px lines, no heavy gradients/fills;
- always show units and timeframe;
- keep network colors stable inside a view;
- prefer direct labels over legends when readable.

## Short descriptor

**Know your network usage.**

See how much data you use, when you use it, and on which network.
