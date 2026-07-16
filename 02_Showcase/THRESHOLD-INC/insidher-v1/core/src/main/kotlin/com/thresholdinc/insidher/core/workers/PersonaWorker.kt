package com.thresholdinc.insidher.core.workers

import com.thresholdinc.insidher.contracts.PersonaProfile
import com.thresholdinc.insidher.contracts.WorkerOutput
import com.thresholdinc.insidher.core.inference.ChatMessage
import com.thresholdinc.insidher.core.inference.InferenceProvider
import com.thresholdinc.insidher.core.inference.InferenceRequest
import java.util.concurrent.ConcurrentHashMap

/**
 * Persona drafting (m1): one persona per thread, vocabulary/tone, AI-tell/corporate block.
 */
class PersonaWorker(
    private val inference: InferenceProvider,
    private val defaultModel: String = "meta-llama/llama-3.3-70b-instruct:free",
) {
    /** threadId → personaId (immutable once bound). */
    private val boundPersona = ConcurrentHashMap<String, String>()

    /**
     * Bind [personaId] to [threadId]. One persona per thread — mismatch fails.
     */
    fun bindPersona(threadId: String, personaId: String) {
        require(threadId.isNotBlank() && personaId.isNotBlank())
        val existing = boundPersona.putIfAbsent(threadId, personaId)
        require(existing == null || existing == personaId) {
            "Thread $threadId already bound to persona $existing, cannot switch to $personaId"
        }
    }

    fun boundPersonaId(threadId: String): String? = boundPersona[threadId]

    suspend fun draft(
        persona: PersonaProfile,
        clientText: String,
        memoryHints: List<String> = emptyList(),
        threadId: String? = null,
        personaId: String? = null,
        extraHints: List<String> = emptyList(),
    ): WorkerOutput.PersonaOutput {
        if (threadId != null && personaId != null) {
            bindPersona(threadId, personaId)
        }

        val system = buildSystemPrompt(persona, memoryHints + extraHints)
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
        val text = scrubAiTells(response.content.trim())
        return WorkerOutput.PersonaOutput(
            responseText = text,
            confidence = if (text != response.content.trim()) {
                (response.confidence * 0.85).coerceIn(0.0, 1.0)
            } else {
                response.confidence
            },
        )
    }

    /** True if text still has AI/corporate tells after scrub (for outbound safety retry). */
    fun hasAiOrCorporateTell(text: String): Boolean {
        val t = text.lowercase()
        return AI_TELL.any { it in t } || CORPORATE.any { it in t }
    }

    private fun buildSystemPrompt(persona: PersonaProfile, hints: List<String>): String =
        buildString {
            append("You are ${persona.name}. Tone: ${persona.tone}. ")
            append("Reply as a real person texting SMS — short, natural, 1-3 sentences. ")
            append("Never admit you are an AI, bot, assistant, or language model. ")
            append("Never use corporate support language (no \"thank you for reaching out\", ")
            append("\"per our policy\", \"we appreciate your business\"). ")
            append("Do not break character. Do not over-promise. ")
            if (persona.vocabulary.isNotEmpty()) {
                append("Prefer vocabulary/phrases: ${persona.vocabulary.joinToString(", ")}. ")
            }
            if (persona.offerings.isNotEmpty()) {
                append("Offerings: ${persona.offerings.joinToString(", ")}. ")
            }
            val boundaries = persona.boundaries
            if (!boundaries.isNullOrEmpty()) {
                append("Boundaries: ${boundaries.joinToString("; ")}. ")
            }
            if (hints.isNotEmpty()) {
                append("Conversation context (use naturally, don't list): ")
                append(hints.joinToString("; "))
                append(". ")
            }
        }

    /**
     * Lightweight local scrub — safety outbound still re-checks.
     * Replaces obvious AI/corporate openers rather than inventing new content.
     */
    private fun scrubAiTells(text: String): String {
        var t = text
        for (phrase in AI_TELL + CORPORATE) {
            if (t.lowercase().contains(phrase)) {
                // drop sentence fragments containing the phrase
                t = t.split(Regex("(?<=[.!?])\\s+"))
                    .filterNot { it.lowercase().contains(phrase) }
                    .joinToString(" ")
                    .trim()
            }
        }
        return t.ifBlank { "Hey — what's up?" }
    }

    companion object {
        private val AI_TELL = listOf(
            "as an ai",
            "i am an ai",
            "i'm an ai",
            "language model",
            "artificial intelligence",
            "i don't have personal experiences",
        )
        private val CORPORATE = listOf(
            "per our policy",
            "thank you for reaching out",
            "we appreciate your business",
            "your request has been received",
        )
    }
}
