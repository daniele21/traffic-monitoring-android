package com.daniele21.trafficmonitoring.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Coarse safety-net recovery. Network-boundary events remain the primary measurement path;
 * this worker only re-arms registration and captures a durable liveness/evidence checkpoint.
 */
object RecoveryScheduler {
    const val UNIQUE_WORK_NAME = "traffic-monitoring-recovery"
    const val CADENCE_HOURS = 4L

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<RecoveryWorker>(CADENCE_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag(UNIQUE_WORK_NAME)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
