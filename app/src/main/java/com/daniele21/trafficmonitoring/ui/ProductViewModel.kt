package com.daniele21.trafficmonitoring.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daniele21.trafficmonitoring.TrafficMonitoringApplication
import com.daniele21.trafficmonitoring.usage.UsageNetworkTotal
import com.daniele21.trafficmonitoring.usage.UsageTrendPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class ProductViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TrafficMonitoringApplication
    private val _state = MutableStateFlow(ProductUiState())
    val state: StateFlow<ProductUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun selectTimeframe(timeframe: ProductTimeframe) {
        if (_state.value.timeframe == timeframe) return
        _state.value = _state.value.copy(timeframe = timeframe)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    val timeframe = _state.value.timeframe
                    val now = System.currentTimeMillis()
                    val range = timeframe.range(now)
                    val snapshot = app.usageRepository.snapshot(
                        startMs = range.first,
                        endMs = range.second,
                        trendSlotMs = timeframe.trendSlotMs
                    )
                    val currentNetwork = app.validationRepository.currentNetworkSnapshot().displayName
                    val monitoring = app.backgroundNetworkMonitor.status()
                    ProductUiState(
                        isLoading = false,
                        timeframe = timeframe,
                        currentNetwork = currentNetwork,
                        monitoringHealthy = monitoring.registered && monitoring.lastError == null,
                        downloadedBytes = snapshot.rxBytes,
                        uploadedBytes = snapshot.txBytes,
                        totalBytes = snapshot.totalBytes,
                        unattributedBytes = snapshot.unattributedBytes,
                        networks = snapshot.networks,
                        trend = snapshot.trend
                    )
                }
            }.onSuccess { _state.value = it }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message ?: error::class.java.simpleName
                    )
                }
        }
    }
}

enum class ProductTimeframe(
    val label: String,
    val trendSlotMs: Long
) {
    TODAY("Today", 60 * 60 * 1000L),
    DAYS_7("7 days", 6 * 60 * 60 * 1000L),
    DAYS_30("30 days", 24 * 60 * 60 * 1000L),
    MONTH("This month", 24 * 60 * 60 * 1000L);

    fun range(nowMs: Long): Pair<Long, Long> {
        val now = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
        val start = when (this) {
            TODAY -> now.toLocalDate().atStartOfDay(now.zone)
            DAYS_7 -> now.minus(7, ChronoUnit.DAYS)
            DAYS_30 -> now.minus(30, ChronoUnit.DAYS)
            MONTH -> now.withDayOfMonth(1).toLocalDate().atStartOfDay(now.zone)
        }
        return start.toInstant().toEpochMilli() to nowMs
    }
}

data class ProductUiState(
    val isLoading: Boolean = true,
    val timeframe: ProductTimeframe = ProductTimeframe.MONTH,
    val currentNetwork: String = "Checking…",
    val monitoringHealthy: Boolean = true,
    val downloadedBytes: Long = 0L,
    val uploadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val unattributedBytes: Long = 0L,
    val networks: List<UsageNetworkTotal> = emptyList(),
    val trend: List<UsageTrendPoint> = emptyList(),
    val error: String? = null
)
