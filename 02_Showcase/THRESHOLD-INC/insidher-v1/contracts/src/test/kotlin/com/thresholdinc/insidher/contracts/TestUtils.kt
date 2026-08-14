package com.thresholdinc.insidher.contracts

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json

/**
 * Shared test utilities and fixtures for contract tests.
 */
object TestUtils {
    /** Strict Json instance: rejects unknown keys (additionalProperties: false behavior). */
    val strictJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    /** Json instance that includes all fields including nulls. */
    val fullJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = true
    }

    val fixedInstant: Instant = Instant.parse("2024-06-15T10:30:00Z")
    val fixedInstant2: Instant = Instant.parse("2024-06-15T11:00:00Z")

    val fixedDate: LocalDate = LocalDate(2024, 7, 20)
    val fixedTime: LocalTime = LocalTime(14, 30)

    fun makeThreadContext(
        id: String = "thread-001",
        state: ThreadState = ThreadState.NEW,
        revision: Int = 1,
        personaId: String = "persona-anita",
        clientPhone: String = "+61412345678",
        createdAt: Instant = fixedInstant,
        updatedAt: Instant = fixedInstant,
    ): ThreadContext = ThreadContext(
        id = id,
        state = state,
        revision = revision,
        personaId = personaId,
        clientPhone = clientPhone,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun makePersonaProfile(
        name: String = "Anita",
        tone: String = "warm and professional",
    ): PersonaProfile = PersonaProfile(
        name = name,
        tone = tone,
        vocabulary = listOf("darling", "gorgeous"),
        offerings = listOf("Blowdry", "Colour"),
        depositWording = "A 50% deposit secures your booking",
        boundaries = listOf("No same-day cancellations"),
        availabilityPolicy = AvailabilityPolicy(),
    )

    fun makeClientMessage(
        threadId: String = "thread-001",
        body: String = "Hi, I'd like to book an appointment",
        phoneNumber: String = "+61412345678",
    ): ClientMessage = ClientMessage(
        threadId = threadId,
        body = body,
        timestamp = fixedInstant,
        phoneNumber = phoneNumber,
    )

    fun makeAgentMessage(
        threadId: String = "thread-001",
        body: String = "Hello! I'd love to help you book. What service are you after?",
        worker: String = "PersonaWorker",
        confidence: Double = 0.95,
    ): AgentMessage = AgentMessage(
        threadId = threadId,
        body = body,
        timestamp = fixedInstant,
        worker = worker,
        confidence = confidence,
    )

    fun makeBookingProposal(
        depositStatus: DepositStatus = DepositStatus.PENDING,
    ): BookingProposal = BookingProposal(
        date = fixedDate,
        time = fixedTime,
        service = "Full Colour & Blowdry",
        depositAmount = 75.0,
        depositStatus = depositStatus,
    )

    fun makeDepositRecord(
        amount: Double = 75.0,
        status: DepositStatus = DepositStatus.PENDING,
        evidenceType: DepositEvidenceType? = null,
    ): DepositRecord {
        val verifiedAt = if (status is DepositStatus.VERIFIED) fixedInstant else null
        return DepositRecord(
            amount = amount,
            status = status,
            evidenceType = evidenceType,
            timestamp = fixedInstant,
            verifiedAt = verifiedAt,
        )
    }

    fun makeThreadMemory(
        threadId: String = "thread-001",
        key: String = "preferred_time",
        value: String = "Saturday mornings",
    ): ThreadMemory = ThreadMemory(
        threadId = threadId,
        key = key,
        value = value,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
    )
}
