package com.thresholdinc.insidher.core.workers

import com.thresholdinc.insidher.contracts.PersonaProfile
import com.thresholdinc.insidher.contracts.WorkerOutput
import com.thresholdinc.insidher.core.inference.ChatMessage
import com.thresholdinc.insidher.core.inference.InferenceProvider
import com.thresholdinc.insidher.core.inference.InferenceRequest

/**
 * Thin m0 PersonaWorker: drafts via InferenceProvider with persona system prompt.
 * Full tone/memory polish is m1.
 */
class PersonaWorker(
    private val inference: InferenceProvider,
    private val defaultModel: String = "meta-llama/llama-3.3-70b-instruct:free",
) {
    suspend fun draft(
        persona: PersonaProfile,
        clientText: String,
        memoryHints: List<String> = emptyList(),
    ): WorkerOutput.PersonaOutput {
        val system = buildString {
            append("You are ${persona.name}. Tone: ${persona.tone}. ")
            append("Reply as a real person texting SMS — short, natural. ")
            append("Never admit you are an AI, bot, or language model. ")
            append("Never use corporate support language. ")
            if (persona.vocabulary.isNotEmpty()) {
                append("Prefer vocabulary: ${persona.vocabulary.joinToString(", ")}. ")
            }
            val boundaries = persona.boundaries
            if (!boundaries.isNullOrEmpty()) {
                append("Boundaries: ${boundaries.joinToString("; ")}. ")
            }
            if (memoryHints.isNotEmpty()) {
                append("Context: ${memoryHints.joinToString("; ")}. ")
            }
        }
        val response = inference.complete(
            InferenceRequest(
                model = defaultModel,
                messages = listOf(
                    ChatMessage("system", system),
                    ChatMessage("user", clientText),
                ),
                temperature = 0.8,
                maxTokens = 256,
            ),
        )
        return WorkerOutput.PersonaOutput(
            responseText = response.content.trim(),
            confidence = response.confidence,
        )
    }
}
