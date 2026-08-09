package com.daniele21.trafficmonitoring.platform

enum class WifiIdentityAvailability(val storageValue: String) {
    KNOWN("known"),
    NOT_APPLICABLE("not_applicable"),
    PERMISSION_REQUIRED("permission_required"),
    LOCATION_DISABLED("location_disabled"),
    UNAVAILABLE("unavailable")
}

object WifiIdentityAccess {
    fun resolve(
        isWifi: Boolean,
        ssidKnown: Boolean,
        preciseLocationGranted: Boolean,
        locationEnabled: Boolean
    ): WifiIdentityAvailability = when {
        ssidKnown -> WifiIdentityAvailability.KNOWN
        !isWifi -> WifiIdentityAvailability.NOT_APPLICABLE
        !preciseLocationGranted -> WifiIdentityAvailability.PERMISSION_REQUIRED
        !locationEnabled -> WifiIdentityAvailability.LOCATION_DISABLED
        else -> WifiIdentityAvailability.UNAVAILABLE
    }
}
