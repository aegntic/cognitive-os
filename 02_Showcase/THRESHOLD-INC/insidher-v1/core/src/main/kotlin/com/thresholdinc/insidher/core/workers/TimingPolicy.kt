package com.thresholdinc.insidher.core.workers

/**
 * Human-like response pacing ranges (m1).
 * Delays are non-fixed — workers sample uniformly inside each range.
 */
data class TimingPolicy(
    val initialMinMs: Long = 45_000L,
    val initialMaxMs: Long = 180_000L,
    val followUpMinMs: Long = 20_000L,
    val followUpMaxMs: Long = 90_000L,
    val batchGapMinMs: Long = 3_000L,
    val batchGapMaxMs: Long = 12_000L,
) {
    init {
        require(initialMinMs in 0..initialMaxMs) { "initial range invalid" }
        require(followUpMinMs in 0..followUpMaxMs) { "follow-up range invalid" }
        require(batchGapMinMs in 0..batchGapMaxMs) { "batch gap range invalid" }
    }

    companion object {
        val DEFAULT = TimingPolicy()
    }
}

enum class TimingKind {
    /** First reply in a thread (or after long gap). */
    INITIAL,
    /** Subsequent reply within an active exchange. */
    FOLLOW_UP,
}
