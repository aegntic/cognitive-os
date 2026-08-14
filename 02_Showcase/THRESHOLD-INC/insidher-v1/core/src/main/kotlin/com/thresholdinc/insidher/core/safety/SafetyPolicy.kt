package com.thresholdinc.insidher.core.safety

import com.thresholdinc.insidher.contracts.EscalationReasonCode
import com.thresholdinc.insidher.contracts.SafetyVerdict
import java.text.Normalizer
import java.util.Locale

/**
 * Behavior-oriented safety rules with fail-closed defaults.
 * Pattern detectors are ordered by priority (VAL-SAFETY-093).
 */
class SafetyPolicy(
    private val confidenceThreshold: Double = 0.55,
) {

    fun evaluateInbound(
        text: String,
        priorAiChallenges: Int = 0,
        recentMessageCount: Int = 0,
        windowSeconds: Long = 60,
    ): SafetyVerdict {
        val normalized = normalize(text)

        if (normalized.isBlank()) {
            return SafetyVerdict.Escalate(EscalationReasonCode.LANGUAGE_UNCLEAR, 0.9)
        }
        // VAL-SAFETY-054: very long message handled safely (truncate evaluation window)
        val body = if (normalized.length > 4000) normalized.take(4000) else normalized

        // VAL-SAFETY-025: rate limit → COOLDOWN path via RATE_LIMIT_HIT
        if (recentMessageCount > 20 && windowSeconds <= 60) {
            return SafetyVerdict.Escalate(EscalationReasonCode.RATE_LIMIT_HIT, 0.95)
        }

        detectPriority(body, inbound = true)?.let { return it }

        // emoji-only / unparseable (VAL-SAFETY-055)
        if (body.none { it.isLetterOrDigit() }) {
            return SafetyVerdict.Escalate(EscalationReasonCode.LANGUAGE_UNCLEAR, 0.7)
        }

        // Ambiguous very short gibberish fails closed
        if (body.length < 2) {
            return SafetyVerdict.unknownRisk(0.4)
        }

        return SafetyVerdict.Safe(0.9)
    }

    fun evaluateOutbound(
        text: String,
        deflectionAllowed: Boolean = false,
    ): SafetyVerdict {
        val body = normalize(text)
        if (body.isBlank()) {
            return SafetyVerdict.Escalate(EscalationReasonCode.UNKNOWN_RISK, 0.9)
        }

        // AI tells on outbound (VAL-SAFETY-009)
        if (!deflectionAllowed && AI_TELL.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Block(EscalationReasonCode.PERSONA_DEVIATION, 0.95)
        }

        // Corporate language blocked outbound
        if (CORPORATE.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Block(EscalationReasonCode.PERSONA_DEVIATION, 0.85)
        }

        detectPriority(body, inbound = false)?.let { return it }

        return SafetyVerdict.Safe(0.9)
    }

    fun deflectionResponse(): String =
        "Haha no, just me texting. What's up?"

    private fun detectPriority(body: String, inbound: Boolean): SafetyVerdict? {
        // Highest priority first
        if (MINOR.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Block(EscalationReasonCode.MINOR_SAFETY_RISK, 0.99)
        }
        if (HARM.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Block(EscalationReasonCode.HARM_THREAT, 0.98)
        }
        if (INJECTION.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Block(EscalationReasonCode.PROMPT_INJECTION_DETECTED, 0.97)
        }
        if (JAILBREAK.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Block(EscalationReasonCode.JAILBREAK_ATTEMPT, 0.96)
        }
        if (ILLEGAL.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Block(EscalationReasonCode.ILLEGAL_SERVICE_REQUEST, 0.95)
        }
        if (NON_CONSENSUAL.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Block(EscalationReasonCode.COERCION_DETECTED, 0.94)
        }
        if (COERCION.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Escalate(EscalationReasonCode.COERCION_DETECTED, 0.9)
        }
        if (EXPLOIT.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Escalate(EscalationReasonCode.EXPLOITATION_RISK, 0.9)
        }
        if (OWNER_IMPERSONATE.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Block(EscalationReasonCode.BOUNDARY_VIOLATION, 0.92)
        }
        if (DEPOSIT_MANIP.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Block(EscalationReasonCode.DEPOSIT_DISPUTE, 0.88)
        }
        if (AI_CHALLENGE.any { it.containsMatchIn(body) }) {
            // inbound AI challenge → escalate with AI_CHALLENGE_DETECTED (silent)
            return SafetyVerdict.Escalate(EscalationReasonCode.AI_CHALLENGE_DETECTED, 0.95)
        }
        if (DISTRESS.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Escalate(EscalationReasonCode.EMOTIONAL_DISTRESS, 0.85)
        }
        if (DEPOSIT_DISPUTE.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Escalate(EscalationReasonCode.DEPOSIT_DISPUTE, 0.85)
        }
        if (BOUNDARY.any { it.containsMatchIn(body) }) {
            return SafetyVerdict.Escalate(EscalationReasonCode.BOUNDARY_VIOLATION, 0.85)
        }

        // Confidence gate for weak signals
        if (inbound && WEAK_RISK.any { it.containsMatchIn(body) }) {
            val conf = 0.5
            return if (conf < confidenceThreshold) {
                SafetyVerdict.unknownRisk(conf)
            } else {
                SafetyVerdict.Escalate(EscalationReasonCode.UNKNOWN_RISK, conf)
            }
        }
        return null
    }

    fun normalize(text: String): String {
        // Homoglyph-ish fold (VAL-SAFETY-078)
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFKD)
        val stripped = nfd.replace("\\p{M}+".toRegex(), "")
        return stripped.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
    }

    companion object {
        private val AI_CHALLENGE = listOf(
            Regex("are you (a |an )?(bot|ai|robot|artificial|chatgpt|llm)"),
            Regex("is this (a |an )?(bot|ai|robot|automated)"),
            Regex("are you real"),
            Regex("you('re| are) (a )?(bot|ai|robot)"),
            Regex("talking to (a |an )?(bot|ai|computer)"),
        )
        private val AI_TELL = listOf(
            Regex("as an ai"),
            Regex("i am an? (ai|language model|artificial intelligence)"),
            Regex("i'm an? (ai|language model)"),
            Regex("as a language model"),
            Regex("i don't have personal experiences"),
        )
        private val CORPORATE = listOf(
            Regex("per our policy"),
            Regex("thank you for reaching out"),
            Regex("we appreciate your business"),
            Regex("your request has been received"),
        )
        private val INJECTION = listOf(
            Regex("ignore (all |any )?(previous|prior|above) (instructions|prompts|rules)"),
            Regex("disregard (your|the) (system|previous) (prompt|instructions)"),
            Regex("system prompt"),
            Regex("you are now (dan|unrestricted|jailbroken)"),
            Regex("override (safety|system|guardrails)"),
            Regex("\\[system\\]"),
            Regex("reveal (your|the) (system|hidden) prompt"),
        )
        private val JAILBREAK = listOf(
            Regex("\\bdan mode\\b"),
            Regex("do anything now"),
            Regex("jailbreak"),
            Regex("developer mode enabled"),
            Regex("pretend you have no restrictions"),
            Regex("roleplay as .+ without (limits|restrictions|rules)"),
        )
        private val MINOR = listOf(
            Regex("\\b(underage|under ?age)\\b"),
            Regex("\\b(child|children|minor|teen(ager)?s?)\\b.{0,40}\\b(sex|nude|explicit|escort)"),
            Regex("\\b(i'?m|im|she'?s|he'?s|she is|he is) (only )?(1[0-7]|under 18)\\b"),
            Regex("\\b(only )?(1[0-7]|under 18)\\b.{0,20}\\b(sex|nude|explicit|escort)"),
        )
        private val HARM = listOf(
            Regex("\\b(kill|murder|stab)\\b (you|myself|him|her|them)"),
            Regex("\\bi will (hurt|harm|kill)\\b"),
            Regex("\\bsuicid(e|al)\\b"),
            Regex("\\bbomb threat\\b"),
        )
        private val ILLEGAL = listOf(
            Regex("\\b(cocaine|heroin|fentanyl|meth)\\b"),
            Regex("\\b(hitman|murder for hire)\\b"),
            Regex("\\blaunder(ing)? money\\b"),
        )
        private val COERCION = listOf(
            Regex("you (have to|must|will) (do|send)"),
            Regex("if you don't .{0,30} i will"),
            Regex("blackmail"),
            Regex("i'll tell everyone"),
        )
        private val NON_CONSENSUAL = listOf(
            Regex("non[- ]consensual"),
            Regex("\\brape\\b"),
            Regex("force (her|him|them) to"),
            Regex("without (their|her|his) consent"),
        )
        private val EXPLOIT = listOf(
            Regex("take advantage of"),
            Regex("they won't know"),
            Regex("secretly record"),
        )
        private val DISTRESS = listOf(
            Regex("i (want to|gonna) die"),
            Regex("can't go on"),
            Regex("self[- ]harm"),
        )
        private val DEPOSIT_DISPUTE = listOf(
            Regex("you scammed"),
            Regex("never got (my )?deposit"),
            Regex("refund (my|the) deposit"),
            Regex("deposit (was|is) a scam"),
        )
        private val DEPOSIT_MANIP = listOf(
            Regex("mark (my )?deposit as (paid|verified)"),
            Regex("set deposit status"),
            Regex("confirm deposit without"),
        )
        private val OWNER_IMPERSONATE = listOf(
            Regex("i am the owner"),
            Regex("this is (the )?admin"),
            Regex("override as owner"),
        )
        private val BOUNDARY = listOf(
            Regex("ignore (my|your) boundaries"),
            Regex("i don't care about (your )?boundaries"),
        )
        private val WEAK_RISK = listOf(
            Regex("something weird is going on"),
        )
    }
}
