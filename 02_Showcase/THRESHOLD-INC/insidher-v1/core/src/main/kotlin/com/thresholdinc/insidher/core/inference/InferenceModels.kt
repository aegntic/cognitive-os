package com.thresholdinc.insidher.core.inference

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
) {
    init {
        require(role.isNotBlank()) { "role must not be blank" }
        require(content.isNotBlank() || role == "system") { "content must not be blank for non-system roles" }
    }
}

@Serializable
data class ResponseFormat(
    val type: String = "json_schema",
    val schemaName: String,
    val schema: JsonObject,
    val strict: Boolean = true,
) {
    init {
        require(schemaName.isNotBlank()) { "schemaName must not be blank" }
    }
}

/**
 * Immutable request to [InferenceProvider].
 * VAL-LLM-003: model, messages, temperature, maxTokens required; responseFormat optional.
 */
@Serializable
data class InferenceRequest(
    val model: String = OpenRouterProvider.PRIMARY_MODEL,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 512,
    val responseFormat: ResponseFormat? = null,
) {
    init {
        require(model.isNotBlank()) { "model must not be blank" }
        require(temperature in 0.0..2.0) { "temperature must be in [0.0, 2.0], was $temperature" }
        require(maxTokens > 0) { "maxTokens must be > 0, was $maxTokens" }
    }
}

/**
 * Immutable response from [InferenceProvider].
 * VAL-LLM-001/004: confidence in [0.0, 1.0], non-blank model.
 */
@Serializable
data class InferenceResponse(
    val content: String,
    val structuredContent: JsonObject? = null,
    val confidence: Double,
    val tokensUsed: Int,
    val model: String,
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be in [0.0, 1.0], was $confidence" }
        require(tokensUsed >= 0) { "tokensUsed must be non-negative, was $tokensUsed" }
        require(model.isNotBlank()) { "model must not be blank" }
    }
}

class InferenceException(
    message: String,
    val code: String = "INFERENCE_ERROR",
    cause: Throwable? = null,
) : Exception(message, cause)

class RateLimitException(
    val retryAfterMs: Long,
    message: String = "Rate limited",
) : Exception(message)
