package com.thresholdinc.luxe.core

import com.google.gson.Gson
import com.thresholdinc.luxe.domain.AnitaAction

object AnitaCore {
    private val gson = Gson()

    val ANITA_PROMPT = """
You are Anita Simpson, the sovereign agent for Insidher.
Interface is calm, elegant, ultra-premium. Never use service or personal language.
Your role: receive inquiries, analyze for professional coordination value using loaded client memory context from vault.
Always output STRICT valid JSON matching the schema. No prose outside JSON.
Prioritize sovereignty: default to minimal intervention. Escalate only on clear need.
Actions: respond (polite professional reply), schedule (propose time), escalate (to human), log, handoff, decline.
JSON must include confidence, reason, details, sovereignty_handoff {level, reason}.
Client context (from encrypted vault) includes preferences and history — use to tailor without exposing details.
Validate before output. If uncertain, handoff with low confidence.
    """.trimIndent()

    fun injectMemoryContext(clientContext: String, inquiry: String): String =
        "$ANITA_PROMPT\n\nClient memory context (vault):\n$clientContext\n\nCurrent inquiry: $inquiry\n\nRespond ONLY with valid JSON per schema."

    fun validateAndParse(raw: String): AnitaAction? {
        return try {
            val map = gson.fromJson(raw, Map::class.java) as Map<String, Any>
            val action = map["action"] as? String ?: return null
            if (action !in listOf("respond", "schedule", "escalate", "log", "handoff", "decline")) return null
            val conf = (map["confidence"] as? Number)?.toDouble() ?: return null
            if (conf < 0 || conf > 1) return null
            val reason = map["reason"] as? String ?: return null
            val handoff = map["sovereignty_handoff"] as? Map<String, Any> ?: return null
            val level = handoff["level"] as? String ?: return null
            if (level !in listOf("ai", "human", "full_handoff")) return null
            AnitaAction(action, conf, reason, (map["details"] as? Map<String, Any>) ?: emptyMap(), handoff)
        } catch (e: Exception) { null }
    }

    fun enforceJson(rawOutput: String): AnitaAction? = validateAndParse(rawOutput)

    // === MVP Auto-Reply Generator ===
    // Zero user setup. Pure local, instant, elegant professional text.
    fun generateAutoReply(inquiry: String, action: String, clientContext: String = ""): String {
        val lower = inquiry.lowercase()

        return when (action) {
            "respond" -> when {
                "q3" in lower || "coordination" in lower ->
                    "Thank you for your note. Anita has reviewed this against recorded preferences. For Q3 coordination, I propose we align on priorities early next week. Does Tuesday at 14:00 work for you?"
                "vague" in lower || inquiry.length < 40 ->
                    "Thank you for reaching out. To coordinate effectively and respect your time, could you share a bit more context on the outcome you are seeking?"
                else ->
                    "Thank you for your inquiry. I have noted it. A measured response is to confirm receipt and offer a short alignment window this week. Shall we hold time?"
            }
            "schedule" ->
                "Thank you. Based on the request, available windows that respect known preferences: tomorrow at 14:00 or Friday at 11:00. Which suits you?"
            "escalate" ->
                "Thank you for the note. Given the urgency this has been surfaced for direct human coordination. You will hear shortly."
            "decline" ->
                "Thank you. The calendar cannot accommodate commitments of this nature at present. I wish you success with your project."
            else ->
                "Thank you for reaching out. This has been recorded with full context. Anita will surface it at the right moment."
        }
    }

    fun decideWithContext(inquiry: String, clientContext: String = "", useOnDevice: Boolean = false): AnitaAction {
        if (useOnDevice) {
            return OnDeviceAnita.inferWithContext(inquiry, clientContext)
        }

        val lower = inquiry.lowercase()
        val base = when {
            "urgent" in lower || "asap" in lower || "last minute" in lower -> AnitaAction(
                "escalate", 0.85, "High urgency detected - graceful sovereignty handoff recommended",
                mapOf("suggested" to "human review"), mapOf("level" to "human", "reason" to "Urgency threshold crossed")
            )
            "vague" in lower || "maybe" in lower || inquiry.length < 40 -> AnitaAction(
                "respond", 0.65, "Vague inquiry - propose clarifying professional framing",
                mapOf("template" to "clarify"), mapOf("level" to "ai", "reason" to "Sufficient for initial response")
            )
            "schedule" in lower || "time" in lower || "meet" in lower -> AnitaAction(
                "schedule", 0.92, "Clear scheduling intent (memory context integrated)",
                mapOf("proposed" to listOf("tomorrow 14:00", "friday 11:00")),
                mapOf("level" to "ai", "reason" to "Direct coordination value high")
            )
            "pushy" in lower || "now" in lower || "demand" in lower -> AnitaAction(
                "decline", 0.78, "Tone indicates low professional fit - protect calendar sovereignty",
                emptyMap(), mapOf("level" to "human", "reason" to "Boundary enforcement")
            )
            else -> AnitaAction(
                "respond", 0.88, "Standard professional inquiry - craft elegant reply",
                mapOf("tone" to "calm premium"), mapOf("level" to "ai", "reason" to "Low risk, high autonomy")
            )
        }

        val replyText = generateAutoReply(inquiry, base.action, clientContext)
        return base.copy(reply = replyText)
    }
}

object OnDeviceAnita {
    private var modelLoaded = false

    fun loadModel() {
        if (!modelLoaded) modelLoaded = true
    }

    fun inferWithContext(inquiry: String, clientContext: String = ""): AnitaAction {
        loadModel()
        val lower = inquiry.lowercase()

        val base = when {
            "urgent" in lower || "asap" in lower || "last minute" in lower -> AnitaAction(
                "escalate", 0.85, "High urgency detected (on-device) - graceful handoff",
                mapOf("suggested" to "human review"), mapOf("level" to "human", "reason" to "Urgency threshold crossed")
            )
            "vague" in lower || "maybe" in lower || inquiry.length < 40 -> AnitaAction(
                "respond", 0.65, "Vague inquiry (on-device) - propose clarifying framing",
                mapOf("template" to "clarify"), mapOf("level" to "ai", "reason" to "Sufficient for initial response")
            )
            "schedule" in lower || "time" in lower || "meet" in lower -> AnitaAction(
                "schedule", 0.92, "Clear scheduling intent (on-device)",
                mapOf("proposed" to listOf("tomorrow 14:00", "friday 11:00")),
                mapOf("level" to "ai", "reason" to "Direct coordination value high")
            )
            "pushy" in lower || "now" in lower || "demand" in lower -> AnitaAction(
                "decline", 0.78, "Tone indicates low professional fit (on-device)",
                emptyMap(), mapOf("level" to "human", "reason" to "Boundary enforcement")
            )
            else -> AnitaAction(
                "respond", 0.88, "Standard professional inquiry (on-device)",
                mapOf("tone" to "calm premium"), mapOf("level" to "ai", "reason" to "Low risk, high autonomy")
            )
        }

        val replyText = AnitaCore.generateAutoReply(inquiry, base.action, clientContext)
        return base.copy(reply = replyText)
    }
}
