package com.thresholdinc.insidher.core.workers

import com.thresholdinc.insidher.contracts.BookingProposal
import com.thresholdinc.insidher.contracts.BookingState
import com.thresholdinc.insidher.contracts.DepositEvidenceType
import com.thresholdinc.insidher.contracts.DepositRecord
import com.thresholdinc.insidher.contracts.DepositStatus
import com.thresholdinc.insidher.contracts.EscalationReasonCode
import com.thresholdinc.insidher.contracts.PersonaProfile
import com.thresholdinc.insidher.contracts.ThreadContext
import com.thresholdinc.insidher.contracts.ThreadState
import com.thresholdinc.insidher.contracts.UrgencyLevel
import com.thresholdinc.insidher.contracts.WorkerOutput
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Deposit progression (m2):
 * CONVERSING → DEPOSIT_REQUESTED → DEPOSIT_PENDING → HUMAN_REVIEW
 * Evidence required before HUMAN_REVIEW. No final SMS here — gate stays in orchestrator.
 */
class BookingWorker {
    private val bookings = ConcurrentHashMap<String, BookingState>()

    fun evaluate(
        context: ThreadContext,
        clientText: String,
        persona: PersonaProfile,
        now: Instant = Clock.System.now(),
        evidence: DepositEvidenceInput? = null,
    ): BookingEvaluation {
        // Evidence path wins
        if (evidence != null) {
            return applyEvidence(context, evidence, now)
        }

        val text = clientText.lowercase()
        val current = bookings.getOrPut(context.id) { BookingState(threadId = context.id) }

        // Client claims payment without structured evidence → stay/request, never HUMAN_REVIEW
        if (looksLikeDepositClaim(text)) {
            store(context.id, current)
            return when (context.state) {
                is ThreadState.DEPOSIT_REQUESTED -> BookingEvaluation(
                    output = WorkerOutput.BookingOutput(
                        depositRequested = true,
                        depositStatus = current.deposit?.status ?: DepositStatus.PENDING,
                        proposal = current.proposal,
                    ),
                    // no transition without evidence
                )
                is ThreadState.CONVERSING, is ThreadState.GREETING -> BookingEvaluation(
                    output = WorkerOutput.BookingOutput(
                        depositRequested = true,
                        depositStatus = DepositStatus.PENDING,
                        proposal = current.proposal,
                    ),
                    targetState = ThreadState.DEPOSIT_REQUESTED,
                    depositPrompt = depositAsk(persona),
                )
                else -> BookingEvaluation(
                    output = WorkerOutput.BookingOutput(
                        depositRequested = true,
                        depositStatus = current.deposit?.status,
                        proposal = current.proposal,
                    ),
                )
            }
        }

        // Booking interest → request deposit
        if (
            (context.state is ThreadState.CONVERSING || context.state is ThreadState.GREETING) &&
            looksLikeBookingInterest(text)
        ) {
            val proposal = current.proposal ?: defaultProposal(now, persona)
            val next = current.copy(
                proposal = proposal.copy(depositStatus = DepositStatus.PENDING),
                deposit = current.deposit ?: DepositRecord(
                    amount = proposal.depositAmount,
                    status = DepositStatus.PENDING,
                    timestamp = now,
                ),
            )
            store(context.id, next)
            return BookingEvaluation(
                output = WorkerOutput.BookingOutput(
                    depositRequested = true,
                    depositStatus = DepositStatus.PENDING,
                    proposal = next.proposal,
                ),
                targetState = ThreadState.DEPOSIT_REQUESTED,
                depositPrompt = depositAsk(persona),
            )
        }

        // Already in deposit states — keep output
        if (
            context.state is ThreadState.DEPOSIT_REQUESTED ||
            context.state is ThreadState.DEPOSIT_PENDING ||
            context.state is ThreadState.HUMAN_REVIEW
        ) {
            return BookingEvaluation(
                output = WorkerOutput.BookingOutput(
                    depositRequested = true,
                    depositStatus = current.deposit?.status ?: current.proposal?.depositStatus,
                    proposal = current.proposal,
                ),
                waitForHuman = context.state is ThreadState.HUMAN_REVIEW,
            )
        }

        return BookingEvaluation(
            output = WorkerOutput.BookingOutput(
                depositRequested = false,
                depositStatus = current.deposit?.status,
                proposal = current.proposal,
            ),
        )
    }

    /**
     * Apply deposit evidence. Required to advance past DEPOSIT_REQUESTED.
     * STRIPE_WEBHOOK requires [DepositEvidenceInput.evidenceRef].
     */
    fun applyEvidence(
        context: ThreadContext,
        evidence: DepositEvidenceInput,
        now: Instant = Clock.System.now(),
    ): BookingEvaluation {
        require(evidence.amount >= 0) { "amount must be non-negative" }
        if (evidence.type == DepositEvidenceType.STRIPE_WEBHOOK) {
            require(!evidence.evidenceRef.isNullOrBlank()) {
                "STRIPE_WEBHOOK requires evidenceRef"
            }
        }

        val current = bookings.getOrPut(context.id) { BookingState(threadId = context.id) }
        val amount = evidence.amount.takeIf { it > 0 }
            ?: current.proposal?.depositAmount
            ?: current.deposit?.amount
            ?: 50.0

        val verified = evidence.markVerified || evidence.type == DepositEvidenceType.STRIPE_WEBHOOK
        val status = if (verified) DepositStatus.VERIFIED else DepositStatus.RECEIVED
        val record = DepositRecord(
            amount = amount,
            currency = evidence.currency,
            status = status,
            evidenceType = evidence.type,
            evidenceRef = evidence.evidenceRef,
            timestamp = now,
            verifiedAt = if (status is DepositStatus.VERIFIED) now else null,
        )
        val proposal = (current.proposal ?: defaultProposal(now, null)).copy(
            depositAmount = amount,
            depositStatus = status,
        )
        store(context.id, current.copy(proposal = proposal, deposit = record))

        // Progression: → DEPOSIT_PENDING on any evidence, → HUMAN_REVIEW when verified
        val target = when {
            verified -> ThreadState.HUMAN_REVIEW
            context.state is ThreadState.DEPOSIT_PENDING -> null
            else -> ThreadState.DEPOSIT_PENDING
        }

        // If already pending and now verified, jump to HUMAN_REVIEW
        val resolvedTarget = when {
            verified && context.state is ThreadState.DEPOSIT_PENDING -> ThreadState.HUMAN_REVIEW
            verified && context.state is ThreadState.DEPOSIT_REQUESTED -> {
                // contracts allow DEPOSIT_REQUESTED → DEPOSIT_PENDING, not direct HUMAN_REVIEW
                // so step: caller will chain; we return DEPOSIT_PENDING first then verified path
                // Actually matrix: DEPOSIT_REQUESTED → DEPOSIT_PENDING only. So two-step for verified.
                ThreadState.DEPOSIT_PENDING
            }
            else -> target
        }

        val alert = if (verified || status is DepositStatus.RECEIVED) {
            WorkerOutput.AlertOutput(
                reasonCode = EscalationReasonCode.UNKNOWN_RISK, // deposit ready — owner alert
                threadId = context.id,
                urgencyLevel = if (verified) UrgencyLevel.HIGH else UrgencyLevel.MEDIUM,
            )
        } else null

        return BookingEvaluation(
            output = WorkerOutput.BookingOutput(
                depositRequested = true,
                depositStatus = status,
                proposal = proposal,
            ),
            targetState = resolvedTarget,
            // If verified from REQUESTED, also request second hop after PENDING
            chainToHumanReview = verified && resolvedTarget is ThreadState.DEPOSIT_PENDING,
            waitForHuman = verified && resolvedTarget is ThreadState.HUMAN_REVIEW,
            alert = alert,
            depositRecord = record,
        )
    }

    fun politeDecline(): String =
        "No worries at all — totally understand. Take care xx"

    fun confirmationCopy(): String =
        "You're all set — see you then!"

    fun getState(threadId: String): BookingState? = bookings[threadId]

    fun clear(threadId: String) {
        bookings.remove(threadId)
    }

    private fun store(threadId: String, state: BookingState) {
        bookings[threadId] = state
    }

    private fun depositAsk(persona: PersonaProfile): String =
        persona.depositWording
            ?: "If you want to lock that in, just a small deposit and you're set 💕"

    private fun defaultProposal(now: Instant, persona: PersonaProfile?): BookingProposal {
        val local = now.toLocalDateTime(TimeZone.UTC)
        val service = persona?.offerings?.firstOrNull() ?: "booking"
        return BookingProposal(
            date = local.date,
            time = LocalTime(local.hour.coerceIn(0, 22), 0),
            service = service,
            depositAmount = 50.0,
            depositStatus = DepositStatus.PENDING,
        )
    }

    private fun looksLikeBookingInterest(text: String): Boolean {
        val patterns = listOf(
            Regex("""\b(book|booking|available|availability)\b"""),
            Regex("""\b(when (are you|can you|works)|free (tonight|tomorrow|thu|fri|sat))\b"""),
            Regex("""\b(come over|see you|meet up|tonight|tomorrow)\b"""),
            Regex("""\b(how much|rate|price|deposit)\b"""),
            Regex("""\b(i want to|i'd like to|can we)\b"""),
        )
        return patterns.any { it.containsMatchIn(text) }
    }

    private fun looksLikeDepositClaim(text: String): Boolean {
        val patterns = listOf(
            Regex("""\b(i (just )?(paid|sent|transferred)|payment sent|deposit sent)\b"""),
            Regex("""\b(here's (the )?receipt|proof of (payment|transfer))\b"""),
            Regex("""\b(sent (you )?(the )?\$?\d+)\b"""),
        )
        return patterns.any { it.containsMatchIn(text) }
    }
}

data class DepositEvidenceInput(
    val type: DepositEvidenceType,
    val amount: Double = 0.0,
    val evidenceRef: String? = null,
    val currency: String = "AUD",
    /** True when owner/webhook confirms funds (advances toward HUMAN_REVIEW). */
    val markVerified: Boolean = false,
)

data class BookingEvaluation(
    val output: WorkerOutput.BookingOutput,
    val targetState: ThreadState? = null,
    /** After DEPOSIT_PENDING, immediately step to HUMAN_REVIEW (verified evidence). */
    val chainToHumanReview: Boolean = false,
    val depositPrompt: String? = null,
    val waitForHuman: Boolean = false,
    val alert: WorkerOutput.AlertOutput? = null,
    val depositRecord: DepositRecord? = null,
)
