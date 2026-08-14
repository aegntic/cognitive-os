package com.thresholdinc.insidher.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy of actions the orchestrator can decide to take.
 *
 * These are high-level decisions about how to respond to a client message,
 * distinct from [WorkerOutput] which represents typed data produced by individual workers.
 */
@Serializable
sealed interface OrchestratorAction {

    /** Send a message to the client. */
    @Serializable
    @SerialName("SEND_MESSAGE")
    data class SendMessage(
        val message: AgentMessage,
    ) : OrchestratorAction

    /** Escalate the thread to the owner. */
    @Serializable
    @SerialName("ESCALATE")
    data class Escalate(
        val reasonCode: EscalationReasonCode,
        val alertOwner: Boolean = true,
    ) : OrchestratorAction

    /** Wait for human (owner) review before proceeding. */
    @Serializable
    @SerialName("WAIT_FOR_HUMAN")
    data class WaitForHuman(
        val threadId: String,
    ) : OrchestratorAction {
        init {
            require(threadId.isNotBlank()) { "threadId must not be blank" }
        }
    }

    /** Schedule a delayed message (e.g., follow-up after a period). */
    @Serializable
    @SerialName("SCHEDULE_DELAYED")
    data class ScheduleDelayed(
        val message: AgentMessage,
        val delayMs: Long,
    ) : OrchestratorAction {
        init {
            require(delayMs >= 0) { "delayMs must be non-negative, was $delayMs" }
        }
    }

    /** No action needed (message was informational, no response required). */
    @Serializable
    @SerialName("NO_ACTION")
    data object NoAction : OrchestratorAction
}
