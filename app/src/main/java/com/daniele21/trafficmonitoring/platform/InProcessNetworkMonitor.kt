package com.daniele21.trafficmonitoring.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import com.daniele21.trafficmonitoring.data.ValidationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * M1B diagnostic callback. This deliberately does not claim background survival: it only records
 * ConnectivityManager signals while this application process exists. M1C will test the PendingIntent path.
 */
class InProcessNetworkMonitor(
    context: Context,
    private val repository: ValidationRepository,
    private val scope: CoroutineScope
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val signals = Channel<Signal>(capacity = Channel.UNLIMITED)
    private var started = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            signals.trySend(Signal("available", network.networkHandle.toString()))
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            signals.trySend(Signal("capabilities_changed", network.networkHandle.toString()))
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            signals.trySend(Signal("link_properties_changed", network.networkHandle.toString()))
        }

        override fun onLost(network: Network) {
            signals.trySend(Signal("lost", network.networkHandle.toString()))
        }
    }

    init {
        scope.launch {
            for (signal in signals) {
                runCatching {
                    repository.captureNetworkSnapshot(source = "callback", kind = signal.kind)
                }.onFailure { error ->
                    repository.recordLifecycle(
                        kind = "network_callback_capture_failed",
                        detailsJson = JSONObject()
                            .put("callbackKind", signal.kind)
                            .put("callbackNetworkHandle", signal.networkHandle)
                            .put("error", error::class.java.simpleName)
                            .toString()
                    )
                }
            }
        }
    }

    fun start() {
        if (started) return
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            started = true
            scope.launch {
                repository.recordLifecycle("network_callback_registered")
            }
        } catch (error: RuntimeException) {
            scope.launch {
                repository.recordLifecycle(
                    kind = "network_callback_registration_failed",
                    detailsJson = JSONObject()
                        .put("error", error::class.java.simpleName)
                        .put("message", error.message)
                        .toString()
                )
            }
        }
    }

    private data class Signal(
        val kind: String,
        val networkHandle: String
    )
}
