package com.daniele21.trafficmonitoring.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiIdentityAccessTest {
    @Test
    fun `known ssid wins over permission diagnostics`() {
        assertEquals(
            WifiIdentityAvailability.KNOWN,
            WifiIdentityAccess.resolve(
                isWifi = true,
                identityEnabled = true,
                ssidKnown = true,
                preciseLocationGranted = false,
                locationEnabled = false
            )
        )
    }

    @Test
    fun `non wifi network does not ask for identity access`() {
        assertEquals(
            WifiIdentityAvailability.NOT_APPLICABLE,
            WifiIdentityAccess.resolve(
                isWifi = false,
                identityEnabled = false,
                ssidKnown = false,
                preciseLocationGranted = false,
                locationEnabled = false
            )
        )
    }

    @Test
    fun `wifi names are opt in even when old location grant exists`() {
        assertEquals(
            WifiIdentityAvailability.OPT_IN_REQUIRED,
            WifiIdentityAccess.resolve(
                isWifi = true,
                identityEnabled = false,
                ssidKnown = false,
                preciseLocationGranted = true,
                locationEnabled = true
            )
        )
    }

    @Test
    fun `opted in wifi without permission asks for permission`() {
        assertEquals(
            WifiIdentityAvailability.PERMISSION_REQUIRED,
            WifiIdentityAccess.resolve(
                isWifi = true,
                identityEnabled = true,
                ssidKnown = false,
                preciseLocationGranted = false,
                locationEnabled = true
            )
        )
    }

    @Test
    fun `wifi with permission but disabled location asks to enable location`() {
        assertEquals(
            WifiIdentityAvailability.LOCATION_DISABLED,
            WifiIdentityAccess.resolve(
                isWifi = true,
                identityEnabled = true,
                ssidKnown = false,
                preciseLocationGranted = true,
                locationEnabled = false
            )
        )
    }

    @Test
    fun `wifi can remain unavailable after all prerequisites`() {
        assertEquals(
            WifiIdentityAvailability.UNAVAILABLE,
            WifiIdentityAccess.resolve(
                isWifi = true,
                identityEnabled = true,
                ssidKnown = false,
                preciseLocationGranted = true,
                locationEnabled = true
            )
        )
    }
}
