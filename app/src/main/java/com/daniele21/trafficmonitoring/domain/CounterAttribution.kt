package com.daniele21.trafficmonitoring.domain

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
 * Compatibility facade retained for validation fixtures created during M1B.
 * New production code should depend on [AttributionEngine].
 */
object CounterAttribution {
    const val CLOCK_DISCONTINUITY_TOLERANCE_MS = 30_000L
    private val engine = AttributionEngine(
        AttributionPolicy(clockDiscontinuityToleranceMs = CLOCK_DISCONTINUITY_TOLERANCE_MS)
    )

    fun between(
        previous: AttributionEvidence,
        current: AttributionEvidence,
        continuityBrokenReason: String? = null
    ): AttributionResult? = engine.between(previous, current, continuityBrokenReason)
}
