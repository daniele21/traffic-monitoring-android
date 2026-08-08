package com.daniele21.trafficmonitoring.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import com.daniele21.trafficmonitoring.TrafficMonitoringApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Manifest receiver used by the M1C PendingIntent experiment and for re-registration after reboot
 * or app replacement. Work is bounded to a small Room write and TrafficStats/network-context read.
 */
class BackgroundNetworkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val app = appContext as TrafficMonitoringApplication
                when (intent.action) {
                    PendingIntentNetworkMonitor.ACTION_NETWORK_AVAILABLE -> {
                        val network = intent.networkExtra()
                        val networkHandle = network?.networkHandle?.toString()
                        BackgroundNetworkRegistrationStore(appContext).markWake(networkHandle)

                        val snapshot = network?.let { AndroidNetworkContextReader(appContext).readNetwork(it) }
                            ?: AndroidNetworkContextReader(appContext).readCurrent()

                        app.validationRepository.recordLifecycle(
                            kind = "pending_intent_received",
                            detailsJson = JSONObject()
                                .put("registrationVersion", BackgroundNetworkRegistrationStore.REGISTRATION_VERSION)
                                .put("networkHandle", networkHandle)
                                .put("transportSet", snapshot.transportSet)
                                .toString()
                        )
                        app.validationRepository.captureNetworkSnapshot(
                            source = "pending_intent",
                            kind = "available",
                            snapshotOverride = snapshot
                        )
                    }

                    Intent.ACTION_BOOT_COMPLETED -> {
                        app.validationRepository.recordLifecycle("boot_received")
                        app.backgroundNetworkMonitor.register("boot_completed")
                            .onSuccess { status ->
                                app.validationRepository.recordLifecycle(
                                    "pending_intent_registered",
                                    registrationDetails(status)
                                )
                            }
                            .onFailure { error ->
                                app.validationRepository.recordLifecycle(
                                    "pending_intent_registration_failed",
                                    errorDetails("boot_completed", error)
                                )
                            }
                    }

                    Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        app.backgroundNetworkMonitor.register("package_replaced")
                            .onSuccess { status ->
                                app.validationRepository.recordLifecycle(
                                    "pending_intent_registered",
                                    registrationDetails(status)
                                )
                            }
                            .onFailure { error ->
                                app.validationRepository.recordLifecycle(
                                    "pending_intent_registration_failed",
                                    errorDetails("package_replaced", error)
                                )
                            }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun Intent.networkExtra(): Network? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(ConnectivityManager.EXTRA_NETWORK, Network::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(ConnectivityManager.EXTRA_NETWORK)
    }

    private fun registrationDetails(status: BackgroundNetworkRegistrationStatus): String = JSONObject()
        .put("registrationVersion", status.registrationVersion)
        .put("registered", status.registered)
        .put("registeredAtMs", status.registeredAtMs)
        .put("reason", status.registrationReason)
        .toString()

    private fun errorDetails(reason: String, error: Throwable): String = JSONObject()
        .put("reason", reason)
        .put("error", error::class.java.simpleName)
        .put("message", error.message)
        .toString()
}
