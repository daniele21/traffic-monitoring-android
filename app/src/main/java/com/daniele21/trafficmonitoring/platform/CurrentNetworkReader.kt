package com.daniele21.trafficmonitoring.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
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
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

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
        val wifiInfo = capabilities?.transportInfo as? WifiInfo
        val rawSsid = wifiInfo?.ssid
        val ssid = rawSsid
            ?.takeUnless { it.equals("<unknown ssid>", ignoreCase = true) }
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
        val interfaceName = connectivityManager.getLinkProperties(network)?.interfaceName
        val isMetered = capabilities?.let {
            !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }

        val displayName = when {
            ssid != null -> ssid
            "wifi" in transports -> "Wi-Fi · name unavailable"
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
            .toString()

        return NetworkContextSnapshot(
            networkHandle = network.networkHandle.toString(),
            transportSet = if (transports.isEmpty()) "unknown" else transports.joinToString("|"),
            isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            isMetered = isMetered,
            isNotVpn = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
            vpnPresent = vpnPresent,
            ssid = ssid,
            ssidAvailability = if (ssid != null) "known" else "unavailable",
            interfaceNames = interfaceName,
            displayName = displayName,
            rawSummaryJson = raw
        )
    }

    private fun offlineSnapshot(): NetworkContextSnapshot = NetworkContextSnapshot(
        networkHandle = null,
        transportSet = "offline",
        isValidated = false,
        isMetered = null,
        isNotVpn = null,
        vpnPresent = false,
        ssid = null,
        ssidAvailability = "unavailable",
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
