package com.thresholdinc.insidher.contracts

import kotlin.reflect.KClass
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy representing a human (owner) decision on a thread in HUMAN_REVIEW.
 *
 * Contains exactly 3 variants: APPROVE, REJECT, ESCALATE.
 * Uses polymorphic serialization with a `"type"` discriminator field.
 *
 * @see ThreadState.HUMAN_REVIEW
 */
@Serializable
sealed interface HumanDecision {
    /** ISO-8601 timestamp of the decision. */
    val timestamp: Instant
    /** Optional human-readable note accompanying the decision. */
    val note: String?

    /** Owner approves the deposit/booking. Triggers HUMAN_REVIEW → CONFIRMED. */
    @Serializable
    @SerialName("APPROVE")
    data class Approve(
        override val timestamp: Instant,
        override val note: String? = null,
    ) : HumanDecision

    /** Owner rejects the deposit/booking. Triggers HUMAN_REVIEW → ENDED. */
    @Serializable
    @SerialName("REJECT")
    data class Reject(
        override val timestamp: Instant,
        override val note: String? = null,
    ) : HumanDecision

    /** Owner escalates the thread. Triggers HUMAN_REVIEW → ESCALATED. */
    @Serializable
    @SerialName("ESCALATE")
    data class Escalate(
        override val timestamp: Instant,
        override val note: String? = null,
    ) : HumanDecision

    companion object {
        /** All 3 variant classes. */
        val variants: List<KClass<out HumanDecision>> = listOf(
            Approve::class,
            Reject::class,
            Escalate::class,
        )

        /**
         * Maps this decision to the target [ThreadState] when applied from HUMAN_REVIEW.
         */
        fun targetState(decision: HumanDecision): ThreadState = when (decision) {
            is Approve -> ThreadState.CONFIRMED
            is Reject -> ThreadState.ENDED
            is Escalate -> ThreadState.ESCALATED
        }
    }
}

/** Alias for [HumanDecision] — represents the approval/rejection/escalation decision. */
typealias ApprovalDecision = HumanDecision
