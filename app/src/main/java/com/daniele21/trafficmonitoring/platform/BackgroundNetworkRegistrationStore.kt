package com.daniele21.trafficmonitoring.platform

import android.content.Context

/** Small durable status store for the M1C PendingIntent experiment. */
class BackgroundNetworkRegistrationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "traffic_monitoring_background_registration",
        Context.MODE_PRIVATE
    )

    fun markRegistered(reason: String, atMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putBoolean(KEY_REGISTERED, true)
            .putLong(KEY_REGISTERED_AT_MS, atMs)
            .putString(KEY_REGISTER_REASON, reason)
            .putString(KEY_LAST_ERROR, null)
            .apply()
    }

    fun markRegistrationFailed(error: String, atMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putBoolean(KEY_REGISTERED, false)
            .putLong(KEY_REGISTRATION_ATTEMPT_AT_MS, atMs)
            .putString(KEY_LAST_ERROR, error)
            .apply()
    }

    fun markWake(networkHandle: String?, atMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(KEY_LAST_WAKE_AT_MS, atMs)
            .putString(KEY_LAST_WAKE_NETWORK_HANDLE, networkHandle)
            .putInt(KEY_WAKE_COUNT, prefs.getInt(KEY_WAKE_COUNT, 0) + 1)
            .apply()
    }

    fun status(): BackgroundNetworkRegistrationStatus = BackgroundNetworkRegistrationStatus(
        registered = prefs.getBoolean(KEY_REGISTERED, false),
        registeredAtMs = prefs.getLong(KEY_REGISTERED_AT_MS, 0L).takeIf { it > 0L },
        registrationReason = prefs.getString(KEY_REGISTER_REASON, null),
        lastWakeAtMs = prefs.getLong(KEY_LAST_WAKE_AT_MS, 0L).takeIf { it > 0L },
        lastWakeNetworkHandle = prefs.getString(KEY_LAST_WAKE_NETWORK_HANDLE, null),
        wakeCount = prefs.getInt(KEY_WAKE_COUNT, 0),
        lastError = prefs.getString(KEY_LAST_ERROR, null),
        registrationVersion = REGISTRATION_VERSION
    )

    companion object {
        const val REGISTRATION_VERSION = 1

        private const val KEY_REGISTERED = "registered"
        private const val KEY_REGISTERED_AT_MS = "registered_at_ms"
        private const val KEY_REGISTRATION_ATTEMPT_AT_MS = "registration_attempt_at_ms"
        private const val KEY_REGISTER_REASON = "registration_reason"
        private const val KEY_LAST_WAKE_AT_MS = "last_wake_at_ms"
        private const val KEY_LAST_WAKE_NETWORK_HANDLE = "last_wake_network_handle"
        private const val KEY_WAKE_COUNT = "wake_count"
        private const val KEY_LAST_ERROR = "last_error"
    }
}

data class BackgroundNetworkRegistrationStatus(
    val registered: Boolean,
    val registeredAtMs: Long?,
    val registrationReason: String?,
    val lastWakeAtMs: Long?,
    val lastWakeNetworkHandle: String?,
    val wakeCount: Int,
    val lastError: String?,
    val registrationVersion: Int
)
