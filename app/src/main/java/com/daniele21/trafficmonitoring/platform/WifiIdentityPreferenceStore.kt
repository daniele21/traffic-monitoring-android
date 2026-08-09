package com.daniele21.trafficmonitoring.platform

import android.content.Context

/**
 * Product preference for location-sensitive Wi-Fi identity access.
 *
 * Monitoring never depends on this setting. The default is false so upgrading users do not start
 * touching location-sensitive Wi-Fi fields merely because a runtime permission happens to exist.
 */
class WifiIdentityPreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "wifi_identity_preferences"
        private const val KEY_ENABLED = "wifi_identity_enabled"
    }
}
