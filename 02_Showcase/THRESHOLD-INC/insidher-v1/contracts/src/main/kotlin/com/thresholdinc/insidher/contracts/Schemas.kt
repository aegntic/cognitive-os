package com.thresholdinc.insidher.contracts

/**
 * JSON Schema definitions for all contract types.
 *
 * Every schema enforces `additionalProperties: false`.
 * These schemas are the source of truth for the schema JSON files in workers-backend/src/schemas/.
 */
object Schemas {

    // ── Thread State Enum ──────────────────────────────────────────

    val threadStateEnum: List<String> = ThreadState.variants.map { it.serialName }

    // ── Deposit Status Enum ────────────────────────────────────────

    val depositStatusEnum: List<String> = DepositStatus.variants.map { it.serialName }

    // ── Escalation Reason Code Enum ────────────────────────────────

    val escalationReasonCodeEnum: List<String> = EscalationReasonCode.entries.map { it.name }

    // ── TimeWindow Schema ──────────────────────────────────────────

    val timeWindow = """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "start": { "type": "string", "format": "time" },
            "end": { "type": "string", "format": "time" }
          },
          "required": ["start", "end"],
          "additionalProperties": false
        }
    """.trimIndent()

    // ── AvailabilityPolicy Schema ──────────────────────────────────

    val availabilityPolicy = """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "timezone": { "type": "string" },
            "weeklyWindows": {
              "type": "object",
              "properties": {
                "MONDAY": { "type": "object" },
                "TUESDAY": { "type": "object" },
                "WEDNESDAY": { "type": "object" },
                "THURSDAY": { "type": "object" },
                "FRIDAY": { "type": "object" },
                "SATURDAY": { "type": "object" },
                "SUNDAY": { "type": "object" }
              }
            },
            "dndPeriods": {
              "type": "array"
            },
            "dateOverrides": {
              "type": "object"
            }
          },
          "required": ["timezone", "weeklyWindows", "dndPeriods", "dateOverrides"],
          "additionalProperties": false
        }
    """.trimIndent()

    // ── PersonaProfile Schema ──────────────────────────────────────

    val personaProfile = """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "name": { "type": "string", "minLength": 1 },
            "tone": { "type": "string", "minLength": 1 },
            "vocabulary": { "type": "array", "items": { "type": "string" } },
            "offerings": { "type": "array", "items": { "type": "string" } },
            "depositWording": { "type": ["string", "null"] },
            "boundaries": { "type": ["array", "null"], "items": { "type": "string" } },
            "availabilityPolicy": $availabilityPolicy
          },
          "required": ["name", "tone", "vocabulary", "offerings", "availabilityPolicy"],
          "additionalProperties": false
        }
    """.trimIndent()

    // ── ThreadContext Schema ───────────────────────────────────────

    val threadContext = """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "id": { "type": "string", "minLength": 1 },
            "state": { "type": "string", "enum": ${threadStateEnum.joinToString(prefix = "[", postfix = "]") { """"$it"""" }} },
            "revision": { "type": "integer", "minimum": 1 },
            "personaId": { "type": "string", "minLength": 1 },
            "clientPhone": { "type": "string", "minLength": 1 },
            "previousState": { "type": ["string", "null"] },
            "createdAt": { "type": "string", "format": "date-time" },
            "updatedAt": { "type": "string", "format": "date-time" },
            "lastMessageAt": { "type": ["string", "null"], "format": "date-time" },
            "metadata": { "type": "object" }
          },
          "required": ["id", "state", "revision", "personaId", "clientPhone", "createdAt", "updatedAt", "metadata"],
          "additionalProperties": false
        }
    """.trimIndent()

    // ── Message Schema ─────────────────────────────────────────────

    val message = """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "type": { "type": "string", "enum": ["inbound", "outbound"] },
            "direction": { "type": "string", "enum": ["inbound", "outbound"] },
            "threadId": { "type": "string", "minLength": 1 },
            "body": { "type": "string", "minLength": 1 },
            "timestamp": { "type": "string", "format": "date-time" },
            "phoneNumber": { "type": "string" },
            "worker": { "type": "string" },
            "confidence": { "type": "number", "minimum": 0.0, "maximum": 1.0 }
          },
          "required": ["type", "direction", "threadId", "body", "timestamp"],
          "additionalProperties": false
        }
    """.trimIndent()

    // ── Booking Schema ─────────────────────────────────────────────

    val bookingProposal = """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "date": { "type": "string", "format": "date" },
            "time": { "type": "string", "format": "time" },
            "service": { "type": "string", "minLength": 1 },
            "depositAmount": { "type": "number", "minimum": 0 },
            "depositStatus": { "type": "string", "enum": ${depositStatusEnum.joinToString(prefix = "[", postfix = "]") { """"$it"""" }} }
          },
          "required": ["date", "time", "service", "depositAmount", "depositStatus"],
          "additionalProperties": false
        }
    """.trimIndent()

    val depositRecord = """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "amount": { "type": "number", "minimum": 0 },
            "currency": { "type": "string", "pattern": "^[A-Z]{3}${'$'}" },
            "status": { "type": "string", "enum": ${depositStatusEnum.joinToString(prefix = "[", postfix = "]") { """"$it"""" }} },
            "evidenceType": { "type": ["string", "null"], "enum": ["STRIPE_WEBHOOK", "MANUAL_FLAG", null] },
            "evidenceRef": { "type": ["string", "null"] },
            "timestamp": { "type": "string", "format": "date-time" },
            "verifiedAt": { "type": ["string", "null"], "format": "date-time" }
          },
          "required": ["amount", "currency", "status", "timestamp"],
          "additionalProperties": false
        }
    """.trimIndent()

    val booking = """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "date": { "type": "string", "format": "date" },
            "time": { "type": "string", "format": "time" },
            "service": { "type": "string", "minLength": 1 },
            "depositAmount": { "type": "number", "minimum": 0 },
            "depositStatus": { "type": "string", "enum": ${depositStatusEnum.joinToString(prefix = "[", postfix = "]") { """"$it"""" }} },
            "amount": { "type": "number", "minimum": 0 },
            "currency": { "type": "string", "pattern": "^[A-Z]{3}${'$'}" },
            "status": { "type": "string", "enum": ${depositStatusEnum.joinToString(prefix = "[", postfix = "]") { """"$it"""" }} },
            "evidenceType": { "type": ["string", "null"] },
            "evidenceRef": { "type": ["string", "null"] },
            "timestamp": { "type": "string", "format": "date-time" },
            "verifiedAt": { "type": ["string", "null"], "format": "date-time" }
          },
          "additionalProperties": false
        }
    """.trimIndent()

    /** All schema name → content pairs. */
    val all: Map<String, String> = mapOf(
        "persona" to personaProfile,
        "thread" to threadContext,
        "message" to message,
        "booking" to booking,
        "bookingProposal" to bookingProposal,
        "depositRecord" to depositRecord,
        "timeWindow" to timeWindow,
        "availabilityPolicy" to availabilityPolicy,
    )

    /**
     * Returns the set of field names for a given schema key.
     */
    fun schemaFields(schemaKey: String): Set<String>? = when (schemaKey) {
        "persona" -> setOf("name", "tone", "vocabulary", "offerings", "depositWording", "boundaries", "availabilityPolicy")
        "thread" -> setOf("id", "state", "revision", "personaId", "clientPhone", "previousState", "createdAt", "updatedAt", "lastMessageAt", "metadata")
        "message" -> setOf("type", "direction", "threadId", "body", "timestamp", "phoneNumber", "worker", "confidence")
        "bookingProposal" -> setOf("date", "time", "service", "depositAmount", "depositStatus")
        "booking" -> setOf("date", "time", "service", "depositAmount", "depositStatus", "amount", "currency", "status", "evidenceType", "evidenceRef", "timestamp", "verifiedAt")
        "depositRecord" -> setOf("amount", "currency", "status", "evidenceType", "evidenceRef", "timestamp", "verifiedAt")
        "timeWindow" -> setOf("start", "end")
        "availabilityPolicy" -> setOf("timezone", "weeklyWindows", "dndPeriods", "dateOverrides")
        else -> null
    }
}
