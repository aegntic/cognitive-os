package com.thresholdinc.insidher.contracts

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Sealed hierarchy representing the lifecycle status of a deposit.
 *
 * Contains exactly 4 variants: PENDING, RECEIVED, VERIFIED, FAILED.
 * Serializes to/from a plain JSON string (e.g., `"VERIFIED"`).
 */
@Serializable(with = DepositStatusSerializer::class)
sealed interface DepositStatus {
    /** The variant name used for JSON serialization. */
    val serialName: String

    /** Deposit requested, awaiting evidence. */
    data object PENDING : DepositStatus {
        override val serialName = "PENDING"
    }

    /** Deposit evidence detected, pending verification. */
    data object RECEIVED : DepositStatus {
        override val serialName = "RECEIVED"
    }

    /** Deposit verified by owner or Stripe webhook. Terminal. */
    data object VERIFIED : DepositStatus {
        override val serialName = "VERIFIED"
    }

    /** Deposit failed. Terminal. */
    data object FAILED : DepositStatus {
        override val serialName = "FAILED"
    }

    companion object {
        /** All 4 variants in declaration order. */
        val variants: List<DepositStatus> = listOf(PENDING, RECEIVED, VERIFIED, FAILED)

        /** Lookup a variant by its serial name. Returns null for unknown names. */
        fun fromName(name: String): DepositStatus? =
            variants.find { it.serialName == name }

        /**
         * Validates a deposit status transition.
         *
         * Valid transitions:
         * - PENDING → RECEIVED, FAILED
         * - RECEIVED → VERIFIED, FAILED
         * - VERIFIED → (terminal, no transitions)
         * - FAILED → (terminal, no transitions)
         */
        fun isValidTransition(from: DepositStatus, to: DepositStatus): Boolean {
            if (from == to) return false
            return when (from) {
                PENDING -> to == RECEIVED || to == FAILED
                RECEIVED -> to == VERIFIED || to == FAILED
                VERIFIED -> false
                FAILED -> false
            }
        }
    }
}

/**
 * Serializes [DepositStatus] as a plain JSON string.
 */
object DepositStatusSerializer : KSerializer<DepositStatus> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DepositStatus", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DepositStatus) {
        encoder.encodeString(value.serialName)
    }

    override fun deserialize(decoder: Decoder): DepositStatus {
        val name = decoder.decodeString()
        return DepositStatus.fromName(name)
            ?: throw SerializationException("Unknown DepositStatus: '$name'")
    }
}
