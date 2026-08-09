package com.daniele21.trafficmonitoring.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import org.json.JSONObject

interface NetworkContextReader {
    fun readCurrent(): NetworkContextSnapshot
    fun readNetwork(network: Network): NetworkContextSnapshot
}

data class NetworkContextSnapshot(
    val networkHandle: String?,
    val transportSet: String,
    val isValidated: Boolean?,
    val isMetered: Boolean?,
    val isNotVpn: Boolean?,
    val vpnPresent: Boolean?,
    val ssid: String?,
    val ssidAvailability: String,
    val interfaceNames: String?,
    val displayName: String,
    val rawSummaryJson: String
) {
    val identity: String
        get() = when {
            transportSet == "offline" -> "offline"
            transportSet.contains("vpn") -> "vpn:${networkHandle ?: interfaceNames ?: "unknown"}"
            ssid != null -> "wifi:ssid:$ssid"
            transportSet.contains("wifi") -> "wifi:${networkHandle ?: interfaceNames ?: "unknown"}"
            transportSet.contains("cellular") -> "cellular:${networkHandle ?: interfaceNames ?: "unknown"}"
            transportSet.contains("ethernet") -> "ethernet:${interfaceNames ?: networkHandle ?: "unknown"}"
            else -> "network:${networkHandle ?: interfaceNames ?: transportSet}"
        }
}

class AndroidNetworkContextReader(context: Context) : NetworkContextReader {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)

    override fun readCurrent(): NetworkContextSnapshot {
        val network = connectivityManager.activeNetwork ?: return offlineSnapshot()
        return readNetworkInternal(network, isActiveNetwork = true)
    }

    override fun readNetwork(network: Network): NetworkContextSnapshot =
        readNetworkInternal(
            network = network,
            isActiveNetwork = connectivityManager.activeNetwork == network
        )

    private fun readNetworkInternal(
        network: Network,
        isActiveNetwork: Boolean
    ): NetworkContextSnapshot {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val transports = capabilities?.let(::transportNames).orEmpty()
        val vpnPresent = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val isWifi = "wifi" in transports
        val preciseLocationGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val locationEnabled = runCatching { locationManager.isLocationEnabled }.getOrDefault(true)

        // getNetworkCapabilities() intentionally strips location-sensitive WifiInfo fields.
        // Use its transport info when already available, then fall back to the connected WifiInfo
        // after Android's location permission/toggle prerequisites have been satisfied.
        val capabilitySsid = normalizeSsid((capabilities?.transportInfo as? WifiInfo)?.ssid)
        val connectedSsid = if (
            isWifi &&
            isActiveNetwork &&
            preciseLocationGranted &&
            locationEnabled
        ) {
            @Suppress("DEPRECATION")
            normalizeSsid(runCatching { wifiManager.connectionInfo?.ssid }.getOrNull())
        } else {
            null
        }
        val ssid = capabilitySsid ?: connectedSsid

        val interfaceName = connectivityManager.getLinkProperties(network)?.interfaceName
        val isMetered = capabilities?.let {
            !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        val ssidAvailability = WifiIdentityAccess.resolve(
            isWifi = isWifi,
            ssidKnown = ssid != null,
            preciseLocationGranted = preciseLocationGranted,
            locationEnabled = locationEnabled
        ).storageValue

        val displayName = when {
            ssid != null -> ssid
            isWifi -> "Wi-Fi"
            "cellular" in transports -> "Cellular"
            "ethernet" in transports -> "Ethernet"
            vpnPresent -> "VPN"
            transports.isNotEmpty() -> transports.joinToString(" + ")
            else -> "Connected network"
        }

        val raw = JSONObject()
            .put("networkHandle", network.networkHandle.toString())
            .put("transports", transports)
            .put("validated", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            .put("metered", isMetered)
            .put("vpnPresent", vpnPresent)
            .put("interfaceName", interfaceName)
            .put("isActiveNetworkAtObservation", isActiveNetwork)
            .put("ssidAvailability", ssidAvailability)
            .toString()

        return NetworkContextSnapshot(
            networkHandle = network.networkHandle.toString(),
            transportSet = if (transports.isEmpty()) "unknown" else transports.joinToString("|"),
            isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            isMetered = isMetered,
            isNotVpn = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
            vpnPresent = vpnPresent,
            ssid = ssid,
            ssidAvailability = ssidAvailability,
            interfaceNames = interfaceName,
            displayName = displayName,
            rawSummaryJson = raw
        )
    }

    private fun normalizeSsid(rawSsid: String?): String? = rawSsid
        ?.takeUnless { it.equals(WifiManager.UNKNOWN_SSID, ignoreCase = true) }
        ?.takeUnless { it.equals("<unknown ssid>", ignoreCase = true) }
        ?.trim('"')
        ?.takeIf { it.isNotBlank() }

    private fun offlineSnapshot(): NetworkContextSnapshot = NetworkContextSnapshot(
        networkHandle = null,
        transportSet = "offline",
        isValidated = false,
        isMetered = null,
        isNotVpn = null,
        vpnPresent = false,
        ssid = null,
        ssidAvailability = WifiIdentityAvailability.NOT_APPLICABLE.storageValue,
        interfaceNames = null,
        displayName = "Offline",
        rawSummaryJson = "{\"state\":\"offline\"}"
    )

    private fun transportNames(capabilities: NetworkCapabilities): List<String> = buildList {
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("bluetooth")
    }
}
