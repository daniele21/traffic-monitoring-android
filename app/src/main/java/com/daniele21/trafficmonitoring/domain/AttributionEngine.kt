package com.daniele21.trafficmonitoring.domain

import kotlin.math.abs

data class AttributionPolicy(
    val clockDiscontinuityToleranceMs: Long = 30_000L
)

/**
 * Deterministic production attribution state transition.
 *
 * Device-wide counters establish byte deltas; evidence quality decides whether those bytes can be
 * assigned to a network. Unknown transitions stay unattributed and continuity failures are discarded.
 */
class AttributionEngine(
    private val policy: AttributionPolicy = AttributionPolicy()
) {
    fun between(
        previous: AttributionEvidence,
        current: AttributionEvidence,
        continuityBrokenReason: String? = null
    ): AttributionResult? {
        val previousRx = previous.rxBytes ?: return null
        val previousTx = previous.txBytes ?: return null
        val currentRx = current.rxBytes ?: return null
        val currentTx = current.txBytes ?: return null

        if (continuityBrokenReason != null) {
            return discarded(previous, current, continuityBrokenReason)
        }

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
        if (wallDelta < 0L || abs(wallDelta - elapsedDelta) > policy.clockDiscontinuityToleranceMs) {
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
            vpnAmbiguous -> unattributed(
                previous,
                current,
                rxDelta,
                txDelta,
                "vpn_active_total_counter_ambiguous"
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

            else -> unattributed(
                previous,
                current,
                rxDelta,
                txDelta,
                if (sameIdentity) "offline_or_unknown_network" else "network_changed_between_evidence"
            )
        }
    }

    private fun unattributed(
        previous: AttributionEvidence,
        current: AttributionEvidence,
        rxBytes: Long,
        txBytes: Long,
        reason: String
    ): AttributionResult = AttributionResult(
        startedAtMs = previous.wallClockMs,
        endedAtMs = current.wallClockMs,
        networkIdentity = null,
        networkDisplayName = null,
        transport = transitionTransport(previous.transport, current.transport),
        rxBytes = rxBytes,
        txBytes = txBytes,
        confidence = "unattributed",
        reason = reason
    )

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
