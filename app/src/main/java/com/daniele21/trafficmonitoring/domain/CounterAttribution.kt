package com.daniele21.trafficmonitoring.domain

import kotlin.math.abs

data class AttributionEvidence(
    val wallClockMs: Long,
    val elapsedRealtimeMs: Long,
    val bootGeneration: String,
    val rxBytes: Long?,
    val txBytes: Long?,
    val eventId: String?,
    val networkIdentity: String?,
    val networkDisplayName: String?,
    val transport: String
)

data class AttributionResult(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val networkIdentity: String?,
    val networkDisplayName: String?,
    val transport: String,
    val rxBytes: Long?,
    val txBytes: Long?,
    val confidence: String,
    val reason: String
)

/**
 * M1B attribution is intentionally conservative.
 *
 * TrafficStats total counters tell us how many bytes changed between two pieces of evidence, but they do
 * not prove which network carried those bytes if a transition was missed. Therefore M1B never emits
 * `confirmed`: a stable observed identity is only `inferred`, while ambiguous boundaries remain
 * `unattributed` or `discarded`.
 */
object CounterAttribution {
    const val CLOCK_DISCONTINUITY_TOLERANCE_MS = 30_000L

    fun between(previous: AttributionEvidence, current: AttributionEvidence): AttributionResult? {
        val previousRx = previous.rxBytes ?: return null
        val previousTx = previous.txBytes ?: return null
        val currentRx = current.rxBytes ?: return null
        val currentTx = current.txBytes ?: return null

        if (current.elapsedRealtimeMs <= previous.elapsedRealtimeMs) {
            return discarded(previous, current, "non_monotonic_elapsed_realtime")
        }

        if (previous.bootGeneration != current.bootGeneration &&
            previous.bootGeneration != "boot:unknown" &&
            current.bootGeneration != "boot:unknown"
        ) {
            return discarded(previous, current, "boot_changed")
        }

        val wallDelta = current.wallClockMs - previous.wallClockMs
        val elapsedDelta = current.elapsedRealtimeMs - previous.elapsedRealtimeMs
        if (wallDelta < 0L || abs(wallDelta - elapsedDelta) > CLOCK_DISCONTINUITY_TOLERANCE_MS) {
            return discarded(previous, current, "clock_discontinuity")
        }

        if (currentRx < previousRx || currentTx < previousTx) {
            return discarded(previous, current, "counter_reset")
        }

        val rxDelta = currentRx - previousRx
        val txDelta = currentTx - previousTx
        val sameIdentity = previous.networkIdentity != null && previous.networkIdentity == current.networkIdentity
        val vpnAmbiguous = previous.transport.contains("vpn") || current.transport.contains("vpn")

        return when {
            vpnAmbiguous -> AttributionResult(
                startedAtMs = previous.wallClockMs,
                endedAtMs = current.wallClockMs,
                networkIdentity = null,
                networkDisplayName = null,
                transport = transitionTransport(previous.transport, current.transport),
                rxBytes = rxDelta,
                txBytes = txDelta,
                confidence = "unattributed",
                reason = "vpn_active_total_counter_ambiguous"
            )

            sameIdentity && previous.networkIdentity != "offline" -> AttributionResult(
                startedAtMs = previous.wallClockMs,
                endedAtMs = current.wallClockMs,
                networkIdentity = previous.networkIdentity,
                networkDisplayName = previous.networkDisplayName,
                transport = previous.transport,
                rxBytes = rxDelta,
                txBytes = txDelta,
                confidence = "inferred",
                reason = "same_network_between_evidence"
            )

            else -> AttributionResult(
                startedAtMs = previous.wallClockMs,
                endedAtMs = current.wallClockMs,
                networkIdentity = null,
                networkDisplayName = null,
                transport = transitionTransport(previous.transport, current.transport),
                rxBytes = rxDelta,
                txBytes = txDelta,
                confidence = "unattributed",
                reason = if (sameIdentity) "offline_or_unknown_network" else "network_changed_between_evidence"
            )
        }
    }

    private fun discarded(
        previous: AttributionEvidence,
        current: AttributionEvidence,
        reason: String
    ): AttributionResult = AttributionResult(
        startedAtMs = previous.wallClockMs,
        endedAtMs = current.wallClockMs,
        networkIdentity = null,
        networkDisplayName = null,
        transport = transitionTransport(previous.transport, current.transport),
        rxBytes = null,
        txBytes = null,
        confidence = "discarded",
        reason = reason
    )

    private fun transitionTransport(previous: String, current: String): String =
        if (previous == current) previous else "$previous->$current"
}
