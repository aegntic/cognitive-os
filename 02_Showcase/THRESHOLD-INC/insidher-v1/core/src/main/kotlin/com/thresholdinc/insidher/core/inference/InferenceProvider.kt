package com.thresholdinc.insidher.core.inference

import kotlinx.serialization.json.JsonObject

/**
 * LLM abstraction (VAL-LLM-064). OpenRouter is the v1 implementation; swappable without worker changes.
 * API key never lives in this module's call sites that ship to APK — providers are injected.
 */
interface InferenceProvider {
    suspend fun complete(request: InferenceRequest): InferenceResponse

    /**
     * VAL-LLM-002: when [schema] is null, delegates to [complete] with null structuredContent.
     */
    suspend fun completeStructured(
        request: InferenceRequest,
        schema: JsonObject?,
        schemaName: String = "response",
    ): InferenceResponse
}
