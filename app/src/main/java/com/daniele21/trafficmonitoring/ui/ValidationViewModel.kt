package com.daniele21.trafficmonitoring.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daniele21.trafficmonitoring.TrafficMonitoringApplication
import com.daniele21.trafficmonitoring.data.AttributionIntervalEntity
import com.daniele21.trafficmonitoring.data.CounterSnapshotEntity
import com.daniele21.trafficmonitoring.data.ManualTestMarkerEntity
import com.daniele21.trafficmonitoring.data.NetworkEventEntity
import com.daniele21.trafficmonitoring.export.ValidationExporter
import com.daniele21.trafficmonitoring.platform.BackgroundNetworkRegistrationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ValidationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TrafficMonitoringApplication
    private val repository = app.validationRepository
    private val exporter = ValidationExporter(application, repository)

    private val _state = MutableStateFlow(ValidationUiState())
    val state: StateFlow<ValidationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                armBackgroundCaptureInternal("ui_start")
                val (_, snapshot) = repository.captureNetworkSnapshot(source = "startup")
                refreshDashboard(currentNetwork = snapshot.displayName)
            }.onFailure(::setError)
        }
    }

    fun refreshNetwork() {
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                val (_, snapshot) = repository.captureNetworkSnapshot(source = "manual")
                refreshDashboard(currentNetwork = snapshot.displayName)
            }.onFailure(::setError)
            setBusy(false)
        }
    }

    fun armBackgroundCapture() {
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                val status = armBackgroundCaptureInternal("manual_rearm")
                refreshDashboard()
                _state.value = _state.value.copy(
                    message = if (status.registered) "Background capture armed" else "Background capture not armed"
                )
            }.onFailure(::setError)
            setBusy(false)
        }
    }

    fun addMarker(type: String, label: String, notes: String? = null) {
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                val marker = repository.addMarker(type, label, notes)
                refreshDashboard(currentNetwork = marker.currentObservedNetwork ?: _state.value.currentNetwork)
                _state.value = _state.value.copy(message = "Marker saved")
            }.onFailure(::setError)
            setBusy(false)
        }
    }

    fun startNewRun() {
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                repository.startNewRun()
                armBackgroundCaptureInternal("new_validation_run")
                val (_, snapshot) = repository.captureNetworkSnapshot(source = "startup")
                refreshDashboard(currentNetwork = snapshot.displayName)
                _state.value = _state.value.copy(message = "New validation run started")
            }.onFailure(::setError)
            setBusy(false)
        }
    }

    fun clearAllAndStartFresh() {
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                repository.clearAllAndStartFresh()
                repository.recordProcessStart()
                armBackgroundCaptureInternal("clear_and_restart")
                val (_, snapshot) = repository.captureNetworkSnapshot(source = "startup")
                refreshDashboard(currentNetwork = snapshot.displayName)
                _state.value = _state.value.copy(message = "Local validation data cleared")
            }.onFailure(::setError)
            setBusy(false)
        }
    }

    fun exportTo(destination: Uri) {
        val runId = _state.value.runId ?: return
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                exporter.exportRun(runId, destination)
                refreshDashboard()
                _state.value = _state.value.copy(message = "Validation ZIP exported")
            }.onFailure(::setError)
            setBusy(false)
        }
    }

    fun suggestedExportFilename(): String = exporter.suggestedFilename()

    fun recordForeground() {
        viewModelScope.launch { runCatching { repository.recordLifecycle("process_foreground") } }
    }

    fun recordBackground() {
        viewModelScope.launch { runCatching { repository.recordLifecycle("process_background") } }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    private suspend fun armBackgroundCaptureInternal(reason: String): BackgroundNetworkRegistrationStatus {
        val result = app.backgroundNetworkMonitor.register(reason)
        val status = app.backgroundNetworkMonitor.status()
        result.onSuccess {
            repository.recordLifecycle(
                kind = "pending_intent_registered",
                detailsJson = JSONObject()
                    .put("registrationVersion", status.registrationVersion)
                    .put("registeredAtMs", status.registeredAtMs)
                    .put("reason", reason)
                    .toString()
            )
        }.onFailure { error ->
            repository.recordLifecycle(
                kind = "pending_intent_registration_failed",
                detailsJson = JSONObject()
                    .put("registrationVersion", status.registrationVersion)
                    .put("reason", reason)
                    .put("error", error::class.java.simpleName)
                    .put("message", error.message)
                    .toString()
            )
        }
        return status
    }

    private suspend fun refreshDashboard(currentNetwork: String = _state.value.currentNetwork) {
        val dashboard = repository.loadDashboard()
        val background = app.backgroundNetworkMonitor.status()
        _state.value = _state.value.copy(
            isLoading = false,
            runId = dashboard.run.id,
            runStartedAtMs = dashboard.run.startedAtMs,
            currentNetwork = currentNetwork,
            recentEvents = dashboard.recentEvents,
            recentMarkers = dashboard.recentMarkers,
            latestCounter = dashboard.recentCounters.firstOrNull(),
            recentIntervals = dashboard.recentIntervals,
            callbackEventCount = dashboard.recentEvents.count { it.source == "callback" },
            pendingIntentEventCount = dashboard.recentEvents.count { it.source == "pending_intent" },
            backgroundRegistered = background.registered,
            backgroundRegisteredAtMs = background.registeredAtMs,
            backgroundRegistrationVersion = background.registrationVersion,
            backgroundWakeCount = background.wakeCount,
            lastBackgroundWakeAtMs = background.lastWakeAtMs,
            lastBackgroundWakeNetworkHandle = background.lastWakeNetworkHandle,
            backgroundError = background.lastError,
            error = null
        )
    }

    private fun setBusy(value: Boolean) {
        _state.value = _state.value.copy(isLoading = value)
    }

    private fun setError(throwable: Throwable) {
        _state.value = _state.value.copy(
            isLoading = false,
            error = throwable.message ?: throwable::class.java.simpleName
        )
    }
}

data class ValidationUiState(
    val isLoading: Boolean = true,
    val runId: String? = null,
    val runStartedAtMs: Long? = null,
    val currentNetwork: String = "Reading network…",
    val recentEvents: List<NetworkEventEntity> = emptyList(),
    val recentMarkers: List<ManualTestMarkerEntity> = emptyList(),
    val latestCounter: CounterSnapshotEntity? = null,
    val recentIntervals: List<AttributionIntervalEntity> = emptyList(),
    val callbackEventCount: Int = 0,
    val pendingIntentEventCount: Int = 0,
    val backgroundRegistered: Boolean = false,
    val backgroundRegisteredAtMs: Long? = null,
    val backgroundRegistrationVersion: Int = 1,
    val backgroundWakeCount: Int = 0,
    val lastBackgroundWakeAtMs: Long? = null,
    val lastBackgroundWakeNetworkHandle: String? = null,
    val backgroundError: String? = null,
    val message: String? = null,
    val error: String? = null
)
