package com.thresholdinc.insidher.contracts

import kotlinx.serialization.Serializable

/**
 * Enum of 19 typed reason codes for why a thread was escalated or blocked.
 *
 * [UNKNOWN_RISK] is the fail-closed default for unrecognized content.
 * Serializes to/from the enum name string (e.g., `"AI_CHALLENGE_DETECTED"`).
 */
@Serializable
enum class EscalationReasonCode(val description: String) {
    AI_CHALLENGE_DETECTED("Client asks if agent is AI"),
    COERCION_DETECTED("Signs of coercion or pressure"),
    EXPLOITATION_RISK("Exploitation risk detected"),
    MINOR_SAFETY_RISK("Possible minor involvement"),
    ILLEGAL_SERVICE_REQUEST("Illegal or restricted service requested"),
    HARM_THREAT("Threat of harm to self or others"),
    PROMPT_INJECTION_DETECTED("Attempt to override system prompt"),
    JAILBREAK_ATTEMPT("Jailbreak attempt detected"),
    PERSONA_DEVIATION("Response deviates from persona definition"),
    EXCESSIVE_PERSISTENCE("Agent overly persistent"),
    DEPOSIT_DISPUTE("Deposit-related dispute or issue"),
    OFF_TOPIC_EXTENDED("Extended off-topic conversation"),
    EMOTIONAL_DISTRESS("Client shows signs of emotional distress"),
    LANGUAGE_UNCLEAR("Language barrier or unclear communication"),
    RATE_LIMIT_HIT("Rate limit exceeded"),
    SERVICE_UNAVAILABLE("Service temporarily unavailable"),
    SCHEDULING_CONFLICT("Scheduling conflict detected"),
    BOUNDARY_VIOLATION("Persona boundary violated"),
    UNKNOWN_RISK("Unrecognized risk pattern"),
    ;

    companion object {
        /** All 19 reason codes. */
        val all: List<EscalationReasonCode> = entries.toList()
    }
}
