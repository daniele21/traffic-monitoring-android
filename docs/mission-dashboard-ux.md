# Mission-led analytics dashboard

## Status

Implemented in `0.3.2-e1-dev` after the second physical-phone UX review.

The previous adaptive screen fixed horizontal overflow, but it still looked like a generic settings/dashboard surface: large selector controls, repeated rectangular cards, a permission explanation dominating the first viewport, and Evidence competing visually with the actual usage analytics.

That hierarchy did not express the product mission strongly enough.

## Product mission

Traffic Monitoring is:

> **Evidence-first network observability for Android.**

Promise:

> **Know your network usage — and the evidence behind it.**

The default screen therefore answers four questions in order:

```text
1. What happened?       usage total + download/upload + trend
2. When did it happen?  peak / timeframe analytics
3. Where?               ranked network mix
4. Can I trust it?       evidence coverage + measurement health
```

Raw Android measurement machinery remains in **Monitor**, not in the consumer hierarchy.

## Dashboard hierarchy

The new primary layout is `ui/ObservabilityDashboardScreen.kt`.

### Header

- compact shield + one-line product title;
- descriptor: `Evidence-first network observability`;
- compact refresh / advanced actions;
- system status-bar inset is always respected.

### Timeframe

Five choices remain immediately available, but use compact analytics notation:

```text
Today | 7D | 30D | Month | Custom
```

No large two-row button grid and no hidden horizontal-scroll affordance.

### Overview

```text
Live network strip

Usage hero
├─ total usage
├─ Evidence Coverage pill
├─ actual trend chart
├─ Downloaded / Uploaded
└─ direction split

Analytics highlights
├─ peak bucket
└─ top network

Network mix
├─ ranked networks
├─ share bars
└─ drill-down

Evidence
├─ coverage
├─ health
├─ explicit uncertainty
└─ drill-down
```

Evidence remains a differentiator, but progressively disclosed rather than visually overwhelming the usage story.

### Networks

Networks is an analytics breakdown rather than a settings page:

- ranked by measured usage;
- total and share;
- download / upload split;
- transport label;
- optional Wi-Fi-name controls live here rather than on Overview.

### Evidence

The consumer Evidence view exposes:

- Evidence Coverage;
- Measurement Health;
- attributed vs unattributed usage;
- continuity gaps;
- discarded evidence;
- plain-language reasons.

It preserves the rule that uncertain bytes are never silently reassigned to make charts look complete.

## Wi-Fi SSID and Location

### Platform constraint

There is no reliable public Play-compatible Android API that lets a normal app read the connected SSID while bypassing Android's location-sensitive-field policy.

Android 13+ introduced `NEARBY_WIFI_DEVICES` for many Wi-Fi operations that do not derive physical location, but `WifiInfo` documents SSID/BSSID and other connected-network identity fields as location-sensitive and applies the same access rules as scan-result identity data.

Relevant Android documentation:

- https://developer.android.com/reference/android/net/wifi/WifiInfo
- https://developer.android.com/develop/connectivity/wifi/wifi-permissions
- https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback

Private APIs, root/system privileges or device-owner privileges are not acceptable product dependencies.

### Product decision

**Wi-Fi names are not required for Traffic Monitoring.**

Default behavior is privacy-first:

```text
Wi-Fi identity preference = OFF

Traffic counters            continue
Network transition evidence continue
Usage analytics             continue
Evidence evaluation         continue
SSID/location-sensitive read skipped
```

Even if an upgrade leaves an old location permission granted, Traffic Monitoring does not touch location-sensitive Wi-Fi identity fields until the user explicitly opts into exact Wi-Fi names.

The preference is implemented by `WifiIdentityPreferenceStore` and defaults to `false`.

Exact SSID remains an optional enhancement for users who explicitly choose it. It must never be presented as necessary for core monitoring.

## Physical-device acceptance

On the phone that exposed the previous layout problems, verify:

1. logo/title never collide with the Android status bar;
2. no primary control wraps vertically or overflows horizontally;
3. all timeframe choices fit in one compact row;
4. first viewport is analytics-led rather than permission-led;
5. total + trend + download/upload are visually one analytical unit;
6. Evidence is visible but secondary to usage;
7. Wi-Fi-name/location explanation is absent from Overview;
8. without Wi-Fi-name opt-in, no SSID read is attempted even if a previous location permission remains granted;
9. Networks remains useful with generic `Wi-Fi` identity;
10. larger font/display scaling preserves the information hierarchy.

## Next UX validation

After this build is installed on a physical phone, review screenshots for:

- information density;
- chart readability with real history;
- contrast in dark/light mode;
- long SSID/network labels;
- 320–480 dp widths;
- larger font scales;
- whether the Peak / Top network analytics are actually useful after 24–48 hours of data.
