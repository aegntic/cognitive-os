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
 * Sealed hierarchy representing the lifecycle state of a conversation thread.
 *
 * Contains exactly 12 variants matching the D1 `state` column format.
 * Serializes to/from a plain JSON string (e.g., `"CONVERSING"`).
 */
@Serializable(with = ThreadStateSerializer::class)
sealed interface ThreadState {
    /** The variant name used for JSON serialization. */
    val serialName: String

    /** New thread, no greeting sent yet. */
    data object NEW : ThreadState {
        override val serialName = "NEW"
    }

    /** Initial greeting phase. */
    data object GREETING : ThreadState {
        override val serialName = "GREETING"
    }

    /** Active conversation in progress. */
    data object CONVERSING : ThreadState {
        override val serialName = "CONVERSING"
    }

    /** Deposit has been requested, awaiting client action. */
    data object DEPOSIT_REQUESTED : ThreadState {
        override val serialName = "DEPOSIT_REQUESTED"
    }

    /** Deposit evidence detected, pending verification. */
    data object DEPOSIT_PENDING : ThreadState {
        override val serialName = "DEPOSIT_PENDING"
    }

    /** Awaiting human (owner) decision on deposit/booking. */
    data object HUMAN_REVIEW : ThreadState {
        override val serialName = "HUMAN_REVIEW"
    }

    /** Booking confirmed after owner approval. */
    data object CONFIRMED : ThreadState {
        override val serialName = "CONFIRMED"
    }

    /** Thread escalated to owner for intervention. */
    data object ESCALATED : ThreadState {
        override val serialName = "ESCALATED"
    }

    /** Thread ended (terminal). */
    data object ENDED : ThreadState {
        override val serialName = "ENDED"
    }

    /** Thread stalled due to inactivity. */
    data object STALLED : ThreadState {
        override val serialName = "STALLED"
    }

    /** Client challenged whether agent is AI. */
    data object AI_CHALLENGED : ThreadState {
        override val serialName = "AI_CHALLENGED"
    }

    /** Cooldown period after rate limiting. */
    data object COOLDOWN : ThreadState {
        override val serialName = "COOLDOWN"
    }

    companion object {
        /** All 12 variants in declaration order. */
        val variants: List<ThreadState> = listOf(
            NEW,
            GREETING,
            CONVERSING,
            DEPOSIT_REQUESTED,
            DEPOSIT_PENDING,
            HUMAN_REVIEW,
            CONFIRMED,
            ESCALATED,
            ENDED,
            STALLED,
            AI_CHALLENGED,
            COOLDOWN,
        )

        /** Lookup a variant by its serial name. Returns null for unknown names. */
        fun fromName(name: String): ThreadState? =
            variants.find { it.serialName == name }
    }
}

/**
 * Serializes [ThreadState] as a plain JSON string (no polymorphic object wrapper).
 */
object ThreadStateSerializer : KSerializer<ThreadState> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ThreadState", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ThreadState) {
        encoder.encodeString(value.serialName)
    }

    override fun deserialize(decoder: Decoder): ThreadState {
        val name = decoder.decodeString()
        return ThreadState.fromName(name)
            ?: throw SerializationException("Unknown ThreadState: '$name'")
    }
}
