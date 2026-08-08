package com.daniele21.trafficmonitoring.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

/**
 * M1C low-power background candidate.
 *
 * Android documents that registerNetworkCallback(NetworkRequest, PendingIntent) may outlive the
 * calling application. The PendingIntent is deliberately explicit and mutable: ConnectivityManager
 * fills EXTRA_NETWORK / EXTRA_NETWORK_REQUEST into the delivered broadcast, while the explicit
 * receiver component prevents another app from redirecting the mutable PendingIntent.
 */
class PendingIntentNetworkMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val store = BackgroundNetworkRegistrationStore(appContext)

    fun register(reason: String = "manual"): Result<BackgroundNetworkRegistrationStatus> = runCatching {
        connectivityManager.registerNetworkCallback(networkRequest(), pendingIntent())
        store.markRegistered(reason)
        store.status()
    }.onFailure { error ->
        store.markRegistrationFailed("${error::class.java.simpleName}: ${error.message.orEmpty()}")
    }

    fun unregister(): Result<Unit> = runCatching {
        connectivityManager.unregisterNetworkCallback(pendingIntent())
    }

    fun status(): BackgroundNetworkRegistrationStatus = store.status()

    fun pendingIntent(): PendingIntent {
        val intent = Intent(appContext, BackgroundNetworkReceiver::class.java)
            .setAction(ACTION_NETWORK_AVAILABLE)
            .setPackage(appContext.packageName)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        return PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            flags
        )
    }

    private fun networkRequest(): NetworkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    companion object {
        const val ACTION_NETWORK_AVAILABLE =
            "com.daniele21.trafficmonitoring.action.BACKGROUND_NETWORK_AVAILABLE"
        private const val REQUEST_CODE = 4107
    }
}
