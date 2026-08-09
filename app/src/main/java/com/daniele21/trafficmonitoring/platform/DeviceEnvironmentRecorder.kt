package com.daniele21.trafficmonitoring.platform

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.daniele21.trafficmonitoring.data.ValidationRepository
import org.json.JSONObject

class DeviceEnvironmentRecorder(context: Context) {
    private val appContext = context.applicationContext

    suspend fun recordInto(repository: ValidationRepository) {
        val power = appContext.getSystemService(PowerManager::class.java)
        val activity = appContext.getSystemService(ActivityManager::class.java)
        val usageStats = appContext.getSystemService(UsageStatsManager::class.java)

        val details = JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("androidVersion", Build.VERSION.RELEASE)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("powerSaveMode", power.isPowerSaveMode)
            .put("ignoringBatteryOptimizations", power.isIgnoringBatteryOptimizations(appContext.packageName))
            .put("lowRamDevice", activity.isLowRamDevice)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            details
                .put("backgroundRestricted", activity.isBackgroundRestricted)
                .put("appStandbyBucket", usageStats.appStandbyBucket)
        }

        repository.recordLifecycle(
            kind = "device_environment",
            detailsJson = details.toString()
        )
    }
}
