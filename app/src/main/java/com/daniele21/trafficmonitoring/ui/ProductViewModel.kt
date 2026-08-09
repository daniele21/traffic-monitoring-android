package com.daniele21.trafficmonitoring.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daniele21.trafficmonitoring.TrafficMonitoringApplication
import com.daniele21.trafficmonitoring.evidence.EvidenceCoverageSummary
import com.daniele21.trafficmonitoring.evidence.EvidenceSummaryCalculator
import com.daniele21.trafficmonitoring.usage.UsageBucketAllocator
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
    private val evidenceCalculator = EvidenceSummaryCalculator()
    private val _state = MutableStateFlow(ProductUiState())
    val state: StateFlow<ProductUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun selectTimeframe(timeframe: ProductTimeframe) {
        if (timeframe == ProductTimeframe.CUSTOM) return
        if (_state.value.timeframe == timeframe) return
        _state.value = _state.value.copy(timeframe = timeframe)
        refresh()
    }

    fun selectCustomRange(startMs: Long, endExclusiveMs: Long) {
        if (endExclusiveMs <= startMs) return
        _state.value = _state.value.copy(
            timeframe = ProductTimeframe.CUSTOM,
            customStartMs = startMs,
            customEndExclusiveMs = endExclusiveMs
        )
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    val currentState = _state.value
                    val timeframe = currentState.timeframe
                    val now = System.currentTimeMillis()
                    val range = if (
                        timeframe == ProductTimeframe.CUSTOM &&
                        currentState.customStartMs != null &&
                        currentState.customEndExclusiveMs != null
                    ) {
                        currentState.customStartMs to currentState.customEndExclusiveMs
                    } else {
                        timeframe.range(now)
                    }
                    val snapshot = app.usageRepository.snapshot(
                        startMs = range.first,
                        endMs = range.second,
                        trendSlotMs = timeframe.trendSlotMs
                    )
                    val evidenceIntervals = app.validationRepository.loadEvidenceIntervals(
                        startMs = range.first,
                        endMs = range.second
                    )
                    val unattributed = snapshot.networks.firstOrNull {
                        it.identity == UsageBucketAllocator.UNATTRIBUTED_ID
                    }
                    val evidence = evidenceCalculator.calculate(
                        startMs = range.first,
                        endMs = range.second,
                        totalRxBytes = snapshot.rxBytes,
                        totalTxBytes = snapshot.txBytes,
                        unattributedRxBytes = unattributed?.rxBytes ?: 0L,
                        unattributedTxBytes = unattributed?.txBytes ?: 0L,
                        intervals = evidenceIntervals
                    )
                    val currentNetworkSnapshot = app.validationRepository.currentNetworkSnapshot()
                    val monitoring = app.backgroundNetworkMonitor.status()
                    ProductUiState(
                        isLoading = false,
                        timeframe = timeframe,
                        customStartMs = currentState.customStartMs,
                        customEndExclusiveMs = currentState.customEndExclusiveMs,
                        currentNetwork = currentNetworkSnapshot.displayName,
                        wifiIdentityStatus = currentNetworkSnapshot.ssidAvailability,
                        monitoringHealthy = monitoring.registered && monitoring.lastError == null,
                        downloadedBytes = snapshot.rxBytes,
                        uploadedBytes = snapshot.txBytes,
                        totalBytes = snapshot.totalBytes,
                        unattributedBytes = snapshot.unattributedBytes,
                        networks = snapshot.networks,
                        trend = snapshot.trend,
                        evidence = evidence
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
    MONTH("This month", 24 * 60 * 60 * 1000L),
    CUSTOM("Custom", 24 * 60 * 60 * 1000L);

    fun range(nowMs: Long): Pair<Long, Long> {
        val now = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
        val start = when (this) {
            TODAY -> now.toLocalDate().atStartOfDay(now.zone)
            DAYS_7 -> now.minus(7, ChronoUnit.DAYS)
            DAYS_30 -> now.minus(30, ChronoUnit.DAYS)
            MONTH -> now.withDayOfMonth(1).toLocalDate().atStartOfDay(now.zone)
            CUSTOM -> now.minus(7, ChronoUnit.DAYS)
        }
        return start.toInstant().toEpochMilli() to nowMs
    }
}

data class ProductUiState(
    val isLoading: Boolean = true,
    val timeframe: ProductTimeframe = ProductTimeframe.MONTH,
    val customStartMs: Long? = null,
    val customEndExclusiveMs: Long? = null,
    val currentNetwork: String = "Checking…",
    val wifiIdentityStatus: String = "unknown",
    val monitoringHealthy: Boolean = true,
    val downloadedBytes: Long = 0L,
    val uploadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val unattributedBytes: Long = 0L,
    val networks: List<UsageNetworkTotal> = emptyList(),
    val trend: List<UsageTrendPoint> = emptyList(),
    val evidence: EvidenceCoverageSummary? = null,
    val error: String? = null
)
