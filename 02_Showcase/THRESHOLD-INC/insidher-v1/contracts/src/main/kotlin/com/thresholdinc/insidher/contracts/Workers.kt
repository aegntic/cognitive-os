package com.thresholdinc.insidher.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Urgency level for an [WorkerOutput.AlertOutput].
 */
@Serializable
enum class UrgencyLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

/**
 * Sealed hierarchy of typed outputs produced by workers in the agent pipeline.
 *
 * Contains exactly 8 variants. Uses polymorphic serialization with a `"type"` discriminator.
 */
@Serializable
sealed interface WorkerOutput {

    /** Output from the PersonaWorker: drafted response text and confidence. */
    @Serializable
    @SerialName("PersonaOutput")
    data class PersonaOutput(
        val responseText: String,
        val confidence: Double,
    ) : WorkerOutput {
        init {
            require(responseText.isNotBlank()) { "responseText must not be blank" }
            require(confidence in 0.0..1.0) {
                "confidence must be in [0.0, 1.0], was $confidence"
            }
        }
    }

    /** Output from the TimingWorker: delay and batch gap in milliseconds. */
    @Serializable
    @SerialName("TimingOutput")
    data class TimingOutput(
        val delayMs: Long,
        val batchGapMs: Long,
    ) : WorkerOutput {
        init {
            require(delayMs >= 0) { "delayMs must be non-negative, was $delayMs" }
            require(batchGapMs >= 0) { "batchGapMs must be non-negative, was $batchGapMs" }
        }
    }

    /** Output from the MemoryWorker: retrieved memory entries. */
    @Serializable
    @SerialName("MemoryOutput")
    data class MemoryOutput(
        val entries: List<ThreadMemory> = emptyList(),
    ) : WorkerOutput

    /** Output from the BookingWorker: deposit progression information. */
    @Serializable
    @SerialName("BookingOutput")
    data class BookingOutput(
        val depositRequested: Boolean = false,
        val depositStatus: DepositStatus? = null,
        val proposal: BookingProposal? = null,
    ) : WorkerOutput

    /** Output from the SafetyWorker: a safety verdict. */
    @Serializable
    @SerialName("SafetyOutput")
    data class SafetyOutput(
        val verdict: SafetyVerdict,
    ) : WorkerOutput

    /** Output from the OrchestratorWorker: final AgentMessage(s) to be sent. */
    @Serializable
    @SerialName("OrchestratorOutput")
    data class OrchestratorOutput(
        val messages: List<AgentMessage>,
    ) : WorkerOutput {
        init {
            require(messages.isNotEmpty()) { "messages must not be empty" }
        }
    }

    /** Output from the InferenceWorker: raw LLM response data. */
    @Serializable
    @SerialName("InferenceOutput")
    data class InferenceOutput(
        val content: String,
        val structuredContent: JsonObject? = null,
        val confidence: Double,
        val tokensUsed: Int,
        val model: String,
    ) : WorkerOutput {
        init {
            require(confidence in 0.0..1.0) {
                "confidence must be in [0.0, 1.0], was $confidence"
            }
            require(tokensUsed >= 0) { "tokensUsed must be non-negative, was $tokensUsed" }
            require(model.isNotBlank()) { "model must not be blank" }
        }
    }

    /** Output from the AlertWorker: alert data for the owner. */
    @Serializable
    @SerialName("AlertOutput")
    data class AlertOutput(
        val reasonCode: EscalationReasonCode,
        val threadId: String,
        val urgencyLevel: UrgencyLevel,
    ) : WorkerOutput {
        init {
            require(threadId.isNotBlank()) { "threadId must not be blank" }
        }
    }

    companion object {
        /** All 8 variant class names. */
        val variantNames: List<String> = listOf(
            "PersonaOutput",
            "TimingOutput",
            "MemoryOutput",
            "BookingOutput",
            "SafetyOutput",
            "OrchestratorOutput",
            "InferenceOutput",
            "AlertOutput",
        )
    }
}
