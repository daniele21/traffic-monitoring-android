package com.daniele21.trafficmonitoring.background

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.daniele21.trafficmonitoring.data.ValidationRepository
import org.json.JSONObject

/** Records process-death evidence that helps explain gaps without trying to infer traffic from it. */
class ProcessExitRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun captureInto(repository: ValidationRepository) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val manager = appContext.getSystemService(ActivityManager::class.java)
        val lastTimestamp = preferences.getLong(KEY_LAST_TIMESTAMP, 0L)
        val exits = manager
            .getHistoricalProcessExitReasons(appContext.packageName, 0, 20)
            .filter { it.timestamp > lastTimestamp }
            .sortedBy { it.timestamp }

        exits.forEach { info ->
            repository.recordLifecycle(
                kind = "historical_process_exit",
                detailsJson = JSONObject()
                    .put("exitTimestampMs", info.timestamp)
                    .put("reason", reasonName(info.reason))
                    .put("reasonCode", info.reason)
                    .put("status", info.status)
                    .put("importance", info.importance)
                    .put("pssKb", info.pss)
                    .put("rssKb", info.rss)
                    .toString()
            )
        }

        exits.maxOfOrNull { it.timestamp }?.let { newest ->
            preferences.edit().putLong(KEY_LAST_TIMESTAMP, newest).apply()
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit_self"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission_change"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource_usage"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user_stopped"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
        ApplicationExitInfo.REASON_FREEZER -> "freezer"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "package_state_change"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "package_updated"
        ApplicationExitInfo.REASON_OTHER -> "other"
        else -> "unknown"
    }

    private companion object {
        const val PREFS = "process_exit_evidence"
        const val KEY_LAST_TIMESTAMP = "last_recorded_exit_timestamp"
    }
}
