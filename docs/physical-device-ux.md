# Physical-device UX and Wi-Fi identity

## Status

First real-phone UX findings addressed in `0.3.1-e1-dev`.

The first physical installation exposed two issues that were not obvious on the emulator:

1. the product header could over-compress trailing text actions on a narrow phone, causing `Monitor` to wrap vertically and inflate the entire header;
2. connected Wi-Fi showed only a generic label because Android redacted the SSID without location-sensitive Wi-Fi identity access.

## Responsive product rules

The phone UI must not rely on horizontal space that happens to exist on one emulator profile.

Current rules:

- the compact header uses the shield + one-line product name + compact refresh/monitor actions;
- the short descriptor is rendered on its own line below the header row;
- primary product navigation is only `Overview` and `Networks`;
- Evidence remains a contextual drill-down from the Overview evidence card;
- timeframe controls are arranged in two bounded rows instead of a horizontally clipped list;
- Downloaded / Uploaded cards stay side by side when space permits and stack on very narrow layouts;
- long network names use bounded lines/ellipsis rather than expanding the whole layout;
- no critical navigation or timeframe option should require discovering a horizontal scroll gesture.

The adaptive implementation is `ui/AdaptiveProductScreen.kt`.

## Wi-Fi identity behavior

Traffic Monitoring measures traffic without needing the Wi-Fi name, but recurring per-network grouping is materially better when the connected SSID is available.

Android treats the connected Wi-Fi identity exposed through `WifiInfo` as location-sensitive information. Traffic Monitoring therefore asks for location access only when the user chooses **Show Wi-Fi name**. On Android 12+ the runtime request includes both coarse and precise location because Android requires them to be requested together; the SSID path proceeds only when precise location is actually granted.

The app does not request location updates and does not collect or store physical coordinates.

`ConnectivityManager.getNetworkCapabilities()` is not sufficient for the current SSID because Android strips location-sensitive transport information from synchronous capability reads. The current-network reader therefore uses the active Wi-Fi connection's `WifiInfo` after the required permission and system Location prerequisites are satisfied. Background/event work remains separate from this product-facing current-network lookup.

User-facing Wi-Fi identity states are explicit:

```text
known                SSID is available and shown
permission_required  precise location has not been granted
location_disabled    permission exists but Android Location is disabled
unavailable          prerequisites exist but Android still redacts/omits the SSID
not_applicable       current connection is not Wi-Fi
```

If the user declines the permission or grants only approximate location, monitoring continues. The current network is shown generically as `Wi-Fi` and attribution remains conservative.

If precise permission is granted but Android Location is disabled, the product offers a direct path to Location settings rather than repeatedly asking for the same permission.

## Privacy copy

The product explanation must remain concrete:

> Android treats the connected Wi-Fi name as location-sensitive information. Allow precise location access so Traffic Monitoring can distinguish this Wi-Fi from your other networks. Physical coordinates are not collected or stored.

Do not claim that the Android permission itself is non-location-sensitive. The platform classifies the information as location-sensitive even though this app uses it only as a network identity.

## Validation export

The validation manifest derives `wifiIdentityPermissionState` from the latest observed Wi-Fi identity state instead of hard-coding `not_requested`. The ZIP README also states that Wi-Fi names are location-sensitive Android information and that physical coordinates are not collected.

## Validation

Physical-device checks for this change:

1. install `0.3.1-e1-dev` (versionCode 2) on a narrow phone;
2. confirm the header no longer creates a vertical `Monitor` label or excess blank height;
3. confirm Overview and Networks are both immediately visible;
4. confirm all five timeframe options are visible without horizontal scrolling;
5. while connected to Wi-Fi, tap **Show Wi-Fi name**;
6. choose **Precise** in the Android permission dialog and allow access;
7. with Android Location enabled, refresh and confirm the SSID replaces the generic `Wi-Fi` label where the platform exposes it;
8. revoke permission and verify monitoring continues with explicit `permission_required` state;
9. grant precise permission but disable Android Location and verify `location_disabled` plus **Open location settings**;
10. repeat with larger system font/display scaling to ensure controls remain usable.
