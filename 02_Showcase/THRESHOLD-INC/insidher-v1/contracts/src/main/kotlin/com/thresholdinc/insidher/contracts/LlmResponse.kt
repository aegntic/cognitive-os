package com.thresholdinc.insidher.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Sealed hierarchy representing the response from an LLM inference call.
 *
 * Uses polymorphic serialization with a `"type"` discriminator.
 */
@Serializable
sealed interface LlmResponse {

    /** Successful LLM response with content and metadata. */
    @Serializable
    @SerialName("SUCCESS")
    data class Success(
        val content: String,
        val structuredContent: JsonObject? = null,
        val confidence: Double,
        val tokensUsed: Int,
        val model: String,
    ) : LlmResponse {
        init {
            require(content.isNotBlank()) { "content must not be blank" }
            require(confidence in 0.0..1.0) {
                "confidence must be in [0.0, 1.0], was $confidence"
            }
            require(tokensUsed >= 0) { "tokensUsed must be non-negative, was $tokensUsed" }
            require(model.isNotBlank()) { "model must not be blank" }
        }
    }

    /** LLM returned a rate-limit error. */
    @Serializable
    @SerialName("RATE_LIMITED")
    data class RateLimited(
        val retryAfterMs: Long,
    ) : LlmResponse {
        init {
            require(retryAfterMs >= 0) { "retryAfterMs must be non-negative, was $retryAfterMs" }
        }
    }

    /** LLM returned an error. */
    @Serializable
    @SerialName("ERROR")
    data class Error(
        val message: String,
        val cause: String? = null,
    ) : LlmResponse {
        init {
            require(message.isNotBlank()) { "message must not be blank" }
        }
    }

    /** LLM returned an empty response. */
    @Serializable
    @SerialName("EMPTY")
    data object Empty : LlmResponse
}
