package com.daniele21.trafficmonitoring.platform

enum class WifiIdentityAvailability(val storageValue: String) {
    KNOWN("known"),
    NOT_APPLICABLE("not_applicable"),
    OPT_IN_REQUIRED("opt_in_required"),
    PERMISSION_REQUIRED("permission_required"),
    LOCATION_DISABLED("location_disabled"),
    UNAVAILABLE("unavailable")
}

object WifiIdentityAccess {
    fun resolve(
        isWifi: Boolean,
        identityEnabled: Boolean,
        ssidKnown: Boolean,
        preciseLocationGranted: Boolean,
        locationEnabled: Boolean
    ): WifiIdentityAvailability = when {
        ssidKnown -> WifiIdentityAvailability.KNOWN
        !isWifi -> WifiIdentityAvailability.NOT_APPLICABLE
        !identityEnabled -> WifiIdentityAvailability.OPT_IN_REQUIRED
        !preciseLocationGranted -> WifiIdentityAvailability.PERMISSION_REQUIRED
        !locationEnabled -> WifiIdentityAvailability.LOCATION_DISABLED
        else -> WifiIdentityAvailability.UNAVAILABLE
    }
}
