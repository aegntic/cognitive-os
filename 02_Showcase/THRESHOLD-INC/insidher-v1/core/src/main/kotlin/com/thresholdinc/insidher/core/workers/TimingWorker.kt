package com.thresholdinc.insidher.core.workers

import com.thresholdinc.insidher.contracts.AvailabilityPolicy
import com.thresholdinc.insidher.contracts.WorkerOutput
import com.thresholdinc.insidher.core.safety.AvailabilityChecker
import kotlinx.datetime.Instant
import kotlin.random.Random

/**
 * Calculates human-like delays and batch gaps.
 * Quiet hours: [shouldHold] true — no send (caller holds).
 * Delays are sampled, never a fixed constant (m1).
 */
class TimingWorker(
    private val policy: TimingPolicy = TimingPolicy.DEFAULT,
    private val availability: AvailabilityChecker = AvailabilityChecker(),
    private val random: Random = Random.Default,
) {
    fun compute(
        kind: TimingKind,
        availabilityPolicy: AvailabilityPolicy,
        now: Instant,
    ): TimingDecision {
        if (availability.isQuietHours(availabilityPolicy, now)) {
            return TimingDecision(
                output = WorkerOutput.TimingOutput(delayMs = 0L, batchGapMs = 0L),
                shouldHold = true,
            )
        }
        val delay = when (kind) {
            TimingKind.INITIAL -> sample(policy.initialMinMs, policy.initialMaxMs)
            TimingKind.FOLLOW_UP -> sample(policy.followUpMinMs, policy.followUpMaxMs)
        }
        val gap = sample(policy.batchGapMinMs, policy.batchGapMaxMs)
        return TimingDecision(
            output = WorkerOutput.TimingOutput(delayMs = delay, batchGapMs = gap),
            shouldHold = false,
        )
    }

    private fun sample(minMs: Long, maxMs: Long): Long {
        if (minMs == maxMs) return minMs
        return random.nextLong(minMs, maxMs + 1)
    }
}

data class TimingDecision(
    val output: WorkerOutput.TimingOutput,
    val shouldHold: Boolean,
)
