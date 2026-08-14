package com.thresholdinc.insidher.contracts

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * Type of evidence used to detect a deposit.
 */
@Serializable
enum class DepositEvidenceType {
    STRIPE_WEBHOOK,
    MANUAL_FLAG,
}

/**
 * A booking proposal: date, time, service, deposit details.
 */
@Serializable
data class BookingProposal(
    val date: LocalDate,
    val time: LocalTime,
    val service: String,
    val depositAmount: Double,
    val depositStatus: DepositStatus,
) {
    init {
        require(service.isNotBlank()) { "service must not be blank" }
        require(depositAmount >= 0) { "depositAmount must be non-negative, was $depositAmount" }
    }
}

/**
 * Overall booking state for a thread.
 */
@Serializable
data class BookingState(
    val threadId: String,
    val proposal: BookingProposal? = null,
    val deposit: DepositRecord? = null,
) {
    init {
        require(threadId.isNotBlank()) { "threadId must not be blank" }
    }
}

/**
 * A deposit record: amount, currency, status, evidence, timestamps.
 *
 * Invariants:
 * - amount >= 0
 * - VERIFIED status requires non-null [evidenceType] and non-null [verifiedAt]
 * - STRIPE_WEBHOOK evidence requires non-null [evidenceRef]
 * - currency defaults to "AUD"
 */
@Serializable
data class DepositRecord(
    val amount: Double,
    val currency: String = "AUD",
    val status: DepositStatus,
    val evidenceType: DepositEvidenceType? = null,
    val evidenceRef: String? = null,
    val timestamp: Instant,
    val verifiedAt: Instant? = null,
) {
    init {
        require(amount >= 0) { "amount must be non-negative, was $amount" }
        // VERIFIED requires evidence
        if (status is DepositStatus.VERIFIED) {
            require(evidenceType != null) {
                "VERIFIED status requires non-null evidenceType"
            }
            require(verifiedAt != null) {
                "VERIFIED status requires non-null verifiedAt"
            }
        }
        // STRIPE_WEBHOOK evidence requires evidenceRef
        if (evidenceType == DepositEvidenceType.STRIPE_WEBHOOK) {
            require(evidenceRef != null) {
                "STRIPE_WEBHOOK evidenceType requires non-null evidenceRef"
            }
        }
        // verifiedAt only set on VERIFIED
        if (status !is DepositStatus.VERIFIED) {
            require(verifiedAt == null) {
                "verifiedAt must be null for non-VERIFIED status"
            }
        }
        // Validate currency is a valid ISO 4217 code
        require(currency.length == 3 && currency.all { it.isUpperCase() && it.isLetter() }) {
            "currency must be a valid ISO 4217 code (3 uppercase letters), was '$currency'"
        }
    }
}

/**
 * Confirmation that a deposit has been verified.
 */
@Serializable
data class DepositConfirmation(
    val threadId: String,
    val depositRecord: DepositRecord,
    val confirmedBy: String,
    val confirmedAt: Instant,
) {
    init {
        require(threadId.isNotBlank()) { "threadId must not be blank" }
        require(confirmedBy.isNotBlank()) { "confirmedBy must not be blank" }
        require(
            depositRecord.status is DepositStatus.VERIFIED,
        ) { "depositRecord must have VERIFIED status" }
    }
}
