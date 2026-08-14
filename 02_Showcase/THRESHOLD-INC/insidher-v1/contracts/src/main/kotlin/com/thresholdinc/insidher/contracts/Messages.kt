package com.thresholdinc.insidher.contracts

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy for messages in a conversation thread.
 *
 * [ClientMessage] represents inbound (from client), [AgentMessage] represents outbound (from agent).
 */
@Serializable
sealed interface Message {
    /** Thread this message belongs to. */
    val threadId: String
    /** Message body. */
    val body: String
    /** Timestamp. */
    val timestamp: Instant
    /** Direction discriminator: "inbound" or "outbound". */
    val direction: String
}

/**
 * Inbound message from a client (SMS received).
 *
 * @see Message
 */
@Serializable
@SerialName("inbound")
data class ClientMessage(
    override val threadId: String,
    override val body: String,
    override val timestamp: Instant,
    val phoneNumber: String,
    override val direction: String = "inbound",
) : Message {
    init {
        require(threadId.isNotBlank()) { "threadId must not be blank" }
        require(body.isNotBlank()) { "body must not be blank" }
        require(phoneNumber.isNotBlank()) { "phoneNumber must not be blank" }
    }
}

/**
 * Outbound message from an agent worker (SMS to be sent).
 *
 * @see Message
 */
@Serializable
@SerialName("outbound")
data class AgentMessage(
    override val threadId: String,
    override val body: String,
    override val timestamp: Instant,
    val worker: String,
    val confidence: Double,
    override val direction: String = "outbound",
) : Message {
    init {
        require(threadId.isNotBlank()) { "threadId must not be blank" }
        require(body.isNotBlank()) { "body must not be blank" }
        require(worker.isNotBlank()) { "worker must not be blank" }
        require(confidence in 0.0..1.0) {
            "confidence must be in [0.0, 1.0], was $confidence"
        }
    }
}

/** Alias for [ClientMessage]. */
typealias InboundMessage = ClientMessage

/** Alias for [AgentMessage]. */
typealias OutboundMessage = AgentMessage
