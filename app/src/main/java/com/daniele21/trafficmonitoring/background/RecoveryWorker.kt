package com.daniele21.trafficmonitoring.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.daniele21.trafficmonitoring.TrafficMonitoringApplication
import org.json.JSONObject

class RecoveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as TrafficMonitoringApplication
        val repository = app.validationRepository
        repository.recordLifecycle(
            kind = "recovery_worker_started",
            detailsJson = JSONObject()
                .put("attempt", runAttemptCount)
                .put("cadenceHours", RecoveryScheduler.CADENCE_HOURS)
                .toString()
        )

        return runCatching {
            app.backgroundNetworkMonitor.register("recovery_worker")
                .onSuccess { status ->
                    repository.recordLifecycle(
                        kind = "pending_intent_registered",
                        detailsJson = JSONObject()
                            .put("registrationVersion", status.registrationVersion)
                            .put("registeredAtMs", status.registeredAtMs)
                            .put("reason", "recovery_worker")
                            .toString()
                    )
                }
                .onFailure { error ->
                    repository.recordLifecycle(
                        kind = "pending_intent_registration_failed",
                        detailsJson = JSONObject()
                            .put("reason", "recovery_worker")
                            .put("error", error::class.java.simpleName)
                            .put("message", error.message)
                            .toString()
                    )
                }

            repository.captureNetworkSnapshot(
                source = "recovery_worker",
                kind = "periodic_recovery"
            )
            app.processExitRecorder.captureInto(repository)
            repository.recordLifecycle("recovery_worker_completed")
            Result.success()
        }.getOrElse { error ->
            runCatching {
                repository.recordLifecycle(
                    kind = "recovery_worker_failed",
                    detailsJson = JSONObject()
                        .put("error", error::class.java.simpleName)
                        .put("message", error.message)
                        .put("attempt", runAttemptCount)
                        .toString()
                )
            }
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
