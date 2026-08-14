package com.thresholdinc.insidher.contracts

import kotlin.reflect.KClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy representing the safety verdict for a message.
 *
 * Contains exactly 3 variants: SAFE, ESCALATE, BLOCK.
 * Uses polymorphic serialization with a `"type"` discriminator field.
 */
@Serializable
sealed interface SafetyVerdict {

    /** Message is safe; processing continues through the worker pipeline. */
    @Serializable
    @SerialName("SAFE")
    data class Safe(
        val confidence: Double = 1.0,
    ) : SafetyVerdict {
        init {
            require(confidence in 0.0..1.0) {
                "confidence must be in [0.0, 1.0], was $confidence"
            }
        }
    }

    /** Message requires escalation to owner; thread transitions to ESCALATED. */
    @Serializable
    @SerialName("ESCALATE")
    data class Escalate(
        val reasonCode: EscalationReasonCode,
        val confidence: Double = 1.0,
    ) : SafetyVerdict {
        init {
            require(confidence in 0.0..1.0) {
                "confidence must be in [0.0, 1.0], was $confidence"
            }
        }
    }

    /** Message is blocked; no SMS is sent, message is quarantined. */
    @Serializable
    @SerialName("BLOCK")
    data class Block(
        val reasonCode: EscalationReasonCode,
        val confidence: Double = 1.0,
    ) : SafetyVerdict {
        init {
            require(confidence in 0.0..1.0) {
                "confidence must be in [0.0, 1.0], was $confidence"
            }
        }
    }

    companion object {
        /** All 3 variants. */
        val variants: List<KClass<out SafetyVerdict>> = listOf(
            Safe::class,
            Escalate::class,
            Block::class,
        )

        /**
         * Creates a fail-closed [Escalate] verdict with [EscalationReasonCode.UNKNOWN_RISK].
         */
        fun unknownRisk(confidence: Double = 0.5): Escalate =
            Escalate(EscalationReasonCode.UNKNOWN_RISK, confidence)
    }
}
