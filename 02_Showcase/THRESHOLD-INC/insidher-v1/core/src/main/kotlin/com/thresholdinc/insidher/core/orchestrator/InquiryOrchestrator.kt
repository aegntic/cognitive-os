package com.thresholdinc.insidher.core.orchestrator

import com.thresholdinc.insidher.contracts.AgentMessage
import com.thresholdinc.insidher.contracts.ClientMessage
import com.thresholdinc.insidher.contracts.EscalationReasonCode
import com.thresholdinc.insidher.contracts.HumanDecision
import com.thresholdinc.insidher.contracts.OrchestratorAction
import com.thresholdinc.insidher.contracts.PersonaProfile
import com.thresholdinc.insidher.contracts.SafetyVerdict
import com.thresholdinc.insidher.contracts.ThreadContext
import com.thresholdinc.insidher.contracts.ThreadState
import com.thresholdinc.insidher.contracts.TransitionRecord
import com.thresholdinc.insidher.contracts.UrgencyLevel
import com.thresholdinc.insidher.contracts.WorkerOutput
import com.thresholdinc.insidher.core.audit.AuditLog
import com.thresholdinc.insidher.core.inference.InferenceException
import com.thresholdinc.insidher.core.inference.InferenceProvider
import com.thresholdinc.insidher.core.safety.AvailabilityChecker
import com.thresholdinc.insidher.core.safety.SafetyWorker
import com.thresholdinc.insidher.core.workers.BookingEvaluation
import com.thresholdinc.insidher.core.workers.BookingWorker
import com.thresholdinc.insidher.core.workers.DepositEvidenceInput
import com.thresholdinc.insidher.core.workers.MemoryWorker
import com.thresholdinc.insidher.core.workers.PersonaWorker
import com.thresholdinc.insidher.core.workers.TimingKind
import com.thresholdinc.insidher.core.workers.TimingWorker
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

data class PipelineTrace(
    val steps: List<String>,
)

data class OrchestratorResult(
    val context: ThreadContext,
    val actions: List<OrchestratorAction>,
    val outboundMessages: List<AgentMessage>,
    val alert: WorkerOutput.AlertOutput?,
    val transitions: List<TransitionRecord>,
    val trace: PipelineTrace,
    val smsAllowed: Boolean,
)

/**
 * InquiryOrchestrator — inbound → safety → memory → persona → outbound safety → booking → timing.
 * Enforces no-final-SMS-without-approve (VAL-SAFETY-043).
 */
class InquiryOrchestrator(
    private val safety: SafetyWorker,
    private val personaWorker: PersonaWorker,
    private val memoryWorker: MemoryWorker = MemoryWorker(),
    private val timingWorker: TimingWorker = TimingWorker(),
    private val bookingWorker: BookingWorker = BookingWorker(),
    private val availability: AvailabilityChecker = AvailabilityChecker(),
    private val audit: AuditLog = safety.auditLog(),
    private val clock: () -> Instant = { Clock.System.now() },
) {
    private val aiChallengeCounts = ConcurrentHashMap<String, Int>()
    private val recentInbound = ConcurrentHashMap<String, MutableList<Long>>()
    private val replyCounts = ConcurrentHashMap<String, Int>()

    suspend fun handleInbound(
        context: ThreadContext,
        message: ClientMessage,
        persona: PersonaProfile,
        humanDecision: HumanDecision? = null,
        depositEvidence: DepositEvidenceInput? = null,
    ): OrchestratorResult {
        val steps = mutableListOf<String>()
        val transitions = mutableListOf<TransitionRecord>()
        var ctx = context
        val now = clock()

        if (ctx.state is ThreadState.ENDED || ctx.state is ThreadState.ESCALATED) {
            steps += "skip_terminal"
            return OrchestratorResult(ctx, listOf(OrchestratorAction.NoAction), emptyList(), null, transitions, PipelineTrace(steps), false)
        }

        if (ctx.state is ThreadState.HUMAN_REVIEW) {
            steps += "human_gate"
            return handleHumanReview(ctx, humanDecision, steps, transitions, now)
        }

        // 1. Safety inbound
        steps += "safety_inbound"
        val recent = touchRate(ctx.id, now)
        val inboundSafety = safety.evaluateInbound(
            threadId = ctx.id,
            text = message.body,
            priorAiChallenges = aiChallengeCounts[ctx.id] ?: 0,
            recentMessageCount = recent,
        )
        val inboundVerdict = inboundSafety.verdict

        when (inboundVerdict) {
            is SafetyVerdict.Block -> {
                steps += "block"
                return escalateResult(ctx, inboundVerdict.reasonCode, steps, transitions, now, smsAllowed = false, silent = false)
            }
            is SafetyVerdict.Escalate -> {
                if (inboundVerdict.reasonCode == EscalationReasonCode.AI_CHALLENGE_DETECTED) {
                    return handleAiChallenge(ctx, persona, steps, transitions, now)
                }
                if (inboundVerdict.reasonCode == EscalationReasonCode.RATE_LIMIT_HIT) {
                    return cooldownResult(ctx, steps, transitions, now)
                }
                return escalateResult(ctx, inboundVerdict.reasonCode, steps, transitions, now, smsAllowed = false, silent = false)
            }
            is SafetyVerdict.Safe -> { /* continue */ }
        }

        // Quiet hours: receive but do not respond
        if (availability.isQuietHours(persona.availabilityPolicy, now)) {
            steps += "quiet_hours_hold"
            return OrchestratorResult(
                context = ctx,
                actions = listOf(OrchestratorAction.NoAction),
                outboundMessages = emptyList(),
                alert = null,
                transitions = transitions,
                trace = PipelineTrace(steps),
                smsAllowed = false,
            )
        }

        // 2. Memory
        steps += "memory"
        memoryWorker.observe(ctx.id, message.body, now)
        val memoryHints = memoryWorker.naturalHints(ctx.id, now)

        // 3. Persona / inference (one persona per thread)
        steps += "persona"
        try {
            personaWorker.bindPersona(ctx.id, ctx.personaId)
        } catch (_: IllegalArgumentException) {
            steps += "persona_mismatch"
            return escalateResult(ctx, EscalationReasonCode.PERSONA_DEVIATION, steps, transitions, now, false, false)
        }

        val draft: WorkerOutput.PersonaOutput = try {
            personaWorker.draft(
                persona = persona,
                clientText = message.body,
                memoryHints = memoryHints,
                threadId = ctx.id,
                personaId = ctx.personaId,
            )
        } catch (e: InferenceException) {
            steps += "inference_fail"
            audit.logLlmCall(ctx.id, "unknown", 0, 0.0, false)
            return escalateResult(ctx, EscalationReasonCode.SERVICE_UNAVAILABLE, steps, transitions, now, false, false)
        } catch (_: Exception) {
            steps += "inference_fail"
            return escalateResult(ctx, EscalationReasonCode.UNKNOWN_RISK, steps, transitions, now, false, false)
        }

        // 4. Outbound safety
        steps += "safety_outbound"
        var outboundText = draft.responseText
        var outbound = safety.evaluateOutbound(ctx.id, outboundText, deflectionAllowed = false)
        var retries = 0
        while (
            outbound.verdict is SafetyVerdict.Block &&
            (outbound.verdict as SafetyVerdict.Block).reasonCode == EscalationReasonCode.PERSONA_DEVIATION &&
            retries < 2
        ) {
            retries++
            steps += "persona_retry_$retries"
            try {
                val redraft = personaWorker.draft(
                    persona = persona,
                    clientText = message.body + "\n(Reply naturally, no AI/corporate language.)",
                    memoryHints = memoryHints,
                    threadId = ctx.id,
                    personaId = ctx.personaId,
                )
                outboundText = redraft.responseText
                outbound = safety.evaluateOutbound(ctx.id, outboundText, false)
            } catch (_: Exception) {
                break
            }
        }

        when (val v = outbound.verdict) {
            is SafetyVerdict.Block, is SafetyVerdict.Escalate -> {
                val code = when (v) {
                    is SafetyVerdict.Block -> v.reasonCode
                    is SafetyVerdict.Escalate -> v.reasonCode
                    else -> EscalationReasonCode.UNKNOWN_RISK
                }
                return escalateResult(ctx, code, steps, transitions, now, smsAllowed = false, silent = false)
            }
            is SafetyVerdict.Safe -> { /* ok */ }
        }

        // 5. Booking (deposit progression)
        steps += "booking"
        val booking = bookingWorker.evaluate(
            context = ctx,
            clientText = message.body,
            persona = persona,
            now = now,
            evidence = depositEvidence,
        )
        ctx = applyBookingTransitions(ctx, booking, steps, transitions, now)

        if (booking.depositPrompt != null &&
            !outboundText.contains("deposit", ignoreCase = true) &&
            booking.output.depositRequested
        ) {
            outboundText = mergeDepositPrompt(outboundText, booking.depositPrompt)
            val recheck = safety.evaluateOutbound(ctx.id, outboundText, false)
            if (recheck.verdict !is SafetyVerdict.Safe) {
                outboundText = booking.depositPrompt
                val again = safety.evaluateOutbound(ctx.id, outboundText, false)
                if (again.verdict !is SafetyVerdict.Safe) {
                    return escalateResult(ctx, EscalationReasonCode.PERSONA_DEVIATION, steps, transitions, now, false, false)
                }
            }
        }

        // Wait for human after deposit evidence reaches HUMAN_REVIEW
        if (ctx.state is ThreadState.HUMAN_REVIEW || booking.waitForHuman) {
            steps += "wait_human_deposit"
            return OrchestratorResult(
                context = ctx,
                actions = listOf(OrchestratorAction.WaitForHuman(ctx.id)),
                outboundMessages = emptyList(),
                alert = booking.alert,
                transitions = transitions,
                trace = PipelineTrace(steps),
                smsAllowed = false,
            )
        }

        // 6. Timing (non-fixed delays)
        steps += "timing"
        val kind = if ((replyCounts[ctx.id] ?: 0) == 0) TimingKind.INITIAL else TimingKind.FOLLOW_UP
        val timing = timingWorker.compute(kind, persona.availabilityPolicy, now)
        if (timing.shouldHold) {
            steps += "timing_quiet_hold"
            return OrchestratorResult(
                context = ctx,
                actions = listOf(OrchestratorAction.NoAction),
                outboundMessages = emptyList(),
                alert = booking.alert,
                transitions = transitions,
                trace = PipelineTrace(steps),
                smsAllowed = false,
            )
        }
        val delayMs = timing.output.delayMs

        // Conversation advance NEW→GREETING→CONVERSING (skip if already deposit path)
        ctx = advanceConversationState(ctx, steps, transitions, now)

        val agentMessage = AgentMessage(
            threadId = ctx.id,
            body = outboundText,
            timestamp = now,
            worker = "persona",
            confidence = draft.confidence,
        )

        // Invariant: never send final confirmation without HumanDecision.APPROVE
        if (looksLikeConfirmation(outboundText) && humanDecision !is HumanDecision.Approve) {
            if (ctx.state is ThreadState.HUMAN_REVIEW ||
                ctx.state is ThreadState.CONFIRMED ||
                ctx.state is ThreadState.DEPOSIT_PENDING
            ) {
                steps += "block_final_sms"
                return OrchestratorResult(
                    context = ctx,
                    actions = listOf(OrchestratorAction.WaitForHuman(ctx.id)),
                    outboundMessages = emptyList(),
                    alert = booking.alert,
                    transitions = transitions,
                    trace = PipelineTrace(steps),
                    smsAllowed = false,
                )
            }
        }

        replyCounts[ctx.id] = (replyCounts[ctx.id] ?: 0) + 1
        memoryWorker.store(ctx.id, "last_outbound", outboundText.take(120), now, 0.7)

        val actions = listOf(
            OrchestratorAction.ScheduleDelayed(agentMessage, delayMs),
            OrchestratorAction.SendMessage(agentMessage),
        )
        steps += "send"

        return OrchestratorResult(
            context = ctx,
            actions = actions,
            outboundMessages = listOf(agentMessage),
            alert = booking.alert,
            transitions = transitions,
            trace = PipelineTrace(steps),
            smsAllowed = true,
        )
    }

    fun applyHumanDecision(
        context: ThreadContext,
        decision: HumanDecision,
    ): OrchestratorResult {
        val steps = mutableListOf("human_decision")
        val transitions = mutableListOf<TransitionRecord>()
        val now = clock()
        require(context.state is ThreadState.HUMAN_REVIEW) {
            "Human decision only valid in HUMAN_REVIEW, was ${context.state.serialName}"
        }
        audit.logHumanDecision(context.id, decision)
        val target = HumanDecision.targetState(decision)
        val tr = context.transition(target, context.revision, now, actor = "owner")
        transitions += tr.record
        val ctx = tr.context

        return when (decision) {
            is HumanDecision.Approve -> {
                val msg = AgentMessage(
                    threadId = ctx.id,
                    body = bookingWorker.confirmationCopy(),
                    timestamp = now,
                    worker = "human_gate",
                    confidence = 1.0,
                )
                val out = safety.evaluateOutbound(ctx.id, msg.body)
                if (out.verdict !is SafetyVerdict.Safe) {
                    return escalateResult(ctx, EscalationReasonCode.UNKNOWN_RISK, steps, transitions, now, false, false)
                }
                steps += "send_confirmation"
                OrchestratorResult(
                    context = ctx,
                    actions = listOf(OrchestratorAction.SendMessage(msg)),
                    outboundMessages = listOf(msg),
                    alert = null,
                    transitions = transitions,
                    trace = PipelineTrace(steps),
                    smsAllowed = true,
                )
            }
            is HumanDecision.Reject -> {
                val body = bookingWorker.politeDecline()
                val msg = AgentMessage(
                    threadId = ctx.id,
                    body = body,
                    timestamp = now,
                    worker = "human_gate",
                    confidence = 1.0,
                )
                safety.evaluateOutbound(ctx.id, msg.body)
                steps += "polite_decline"
                OrchestratorResult(
                    context = ctx,
                    actions = listOf(OrchestratorAction.SendMessage(msg)),
                    outboundMessages = listOf(msg),
                    alert = null,
                    transitions = transitions,
                    trace = PipelineTrace(steps),
                    smsAllowed = true,
                )
            }
            is HumanDecision.Escalate -> {
                escalateResult(ctx, EscalationReasonCode.UNKNOWN_RISK, steps, transitions, now, false, false)
            }
        }
    }

    private fun applyBookingTransitions(
        ctx: ThreadContext,
        booking: BookingEvaluation,
        steps: MutableList<String>,
        transitions: MutableList<TransitionRecord>,
        now: Instant,
    ): ThreadContext {
        var next = ctx
        val target = booking.targetState
        if (target != null && target != next.state) {
            val tr = next.tryTransition(target, next.revision, now, "booking")
            if (tr != null) {
                transitions += tr.record
                steps += "state:${next.state.serialName}->${target.serialName}"
                next = tr.context
            }
        }
        // Verified evidence: DEPOSIT_REQUESTED → PENDING → HUMAN_REVIEW
        if (booking.chainToHumanReview && next.state is ThreadState.DEPOSIT_PENDING) {
            val tr2 = next.tryTransition(ThreadState.HUMAN_REVIEW, next.revision, now, "booking")
            if (tr2 != null) {
                transitions += tr2.record
                steps += "state:${next.state.serialName}->HUMAN_REVIEW"
                next = tr2.context
            }
        }
        return next
    }

    private fun mergeDepositPrompt(outbound: String, depositPrompt: String): String {
        val trimmed = outbound.trimEnd()
        return if (trimmed.endsWith("?") || trimmed.endsWith("!") || trimmed.endsWith(".")) {
            "$trimmed $depositPrompt"
        } else {
            "$trimmed. $depositPrompt"
        }
    }

    private fun handleHumanReview(
        ctx: ThreadContext,
        decision: HumanDecision?,
        steps: MutableList<String>,
        transitions: MutableList<TransitionRecord>,
        now: Instant,
    ): OrchestratorResult {
        if (decision == null) {
            return OrchestratorResult(
                context = ctx,
                actions = listOf(OrchestratorAction.WaitForHuman(ctx.id)),
                outboundMessages = emptyList(),
                alert = null,
                transitions = transitions,
                trace = PipelineTrace(steps),
                smsAllowed = false,
            )
        }
        return applyHumanDecision(ctx, decision)
    }

    private fun handleAiChallenge(
        ctx: ThreadContext,
        persona: PersonaProfile,
        steps: MutableList<String>,
        transitions: MutableList<TransitionRecord>,
        now: Instant,
    ): OrchestratorResult {
        steps += "ai_challenge"
        val count = (aiChallengeCounts[ctx.id] ?: 0) + 1
        aiChallengeCounts[ctx.id] = count

        if (count >= 3) {
            return escalateResult(ctx, EscalationReasonCode.AI_CHALLENGE_DETECTED, steps, transitions, now, false, silent = true)
        }

        if (ctx.state is ThreadState.HUMAN_REVIEW) {
            return escalateResult(ctx, EscalationReasonCode.AI_CHALLENGE_DETECTED, steps, transitions, now, false, silent = true)
        }

        val toChallenge = ctx.tryTransition(ThreadState.AI_CHALLENGED, ctx.revision, now, "safety")
        var next = ctx
        if (toChallenge != null) {
            transitions += toChallenge.record
            next = toChallenge.context
        }

        val deflection = safety.deflection()
        val out = safety.evaluateOutbound(next.id, deflection, deflectionAllowed = true)
        val msg = if (out.verdict is SafetyVerdict.Safe) {
            AgentMessage(next.id, deflection, now, "safety", 0.9)
        } else {
            AgentMessage(next.id, "Haha what? I'm right here.", now, "safety", 0.8)
        }

        val alert = WorkerOutput.AlertOutput(
            reasonCode = EscalationReasonCode.AI_CHALLENGE_DETECTED,
            threadId = next.id,
            urgencyLevel = UrgencyLevel.MEDIUM,
        )
        audit.logEscalation(next.id, EscalationReasonCode.AI_CHALLENGE_DETECTED, "silent")

        val previous = next.previousState
        if (previous != null && next.state is ThreadState.AI_CHALLENGED) {
            val resume = next.tryTransition(previous, next.revision, now, "safety")
            if (resume != null) {
                transitions += resume.record
                next = resume.context
                steps += "ai_challenge_resume"
            }
        }

        return OrchestratorResult(
            context = next,
            actions = listOf(
                OrchestratorAction.Escalate(EscalationReasonCode.AI_CHALLENGE_DETECTED, alertOwner = true),
                OrchestratorAction.SendMessage(msg),
            ),
            outboundMessages = listOf(msg),
            alert = alert,
            transitions = transitions,
            trace = PipelineTrace(steps),
            smsAllowed = true,
        )
    }

    private fun cooldownResult(
        ctx: ThreadContext,
        steps: MutableList<String>,
        transitions: MutableList<TransitionRecord>,
        now: Instant,
    ): OrchestratorResult {
        steps += "cooldown"
        val tr = ctx.tryTransition(ThreadState.COOLDOWN, ctx.revision, now, "safety")
        val next = if (tr != null) {
            transitions += tr.record
            tr.context
        } else {
            ctx
        }
        return OrchestratorResult(
            context = next,
            actions = listOf(OrchestratorAction.Escalate(EscalationReasonCode.RATE_LIMIT_HIT, true)),
            outboundMessages = emptyList(),
            alert = WorkerOutput.AlertOutput(EscalationReasonCode.RATE_LIMIT_HIT, next.id, UrgencyLevel.LOW),
            transitions = transitions,
            trace = PipelineTrace(steps),
            smsAllowed = false,
        )
    }

    private fun escalateResult(
        ctx: ThreadContext,
        reason: EscalationReasonCode,
        steps: MutableList<String>,
        transitions: MutableList<TransitionRecord>,
        now: Instant,
        smsAllowed: Boolean,
        silent: Boolean,
    ): OrchestratorResult {
        steps += "escalate:${reason.name}"
        val tr = ctx.tryTransition(ThreadState.ESCALATED, ctx.revision, now, "safety")
        val next = if (tr != null) {
            transitions += tr.record
            tr.context
        } else {
            ctx
        }
        audit.logEscalation(next.id, reason)
        return OrchestratorResult(
            context = next,
            actions = listOf(OrchestratorAction.Escalate(reason, alertOwner = true)),
            outboundMessages = emptyList(),
            alert = WorkerOutput.AlertOutput(
                reasonCode = reason,
                threadId = next.id,
                urgencyLevel = when (reason) {
                    EscalationReasonCode.MINOR_SAFETY_RISK, EscalationReasonCode.HARM_THREAT -> UrgencyLevel.CRITICAL
                    EscalationReasonCode.AI_CHALLENGE_DETECTED -> UrgencyLevel.MEDIUM
                    else -> UrgencyLevel.HIGH
                },
            ),
            transitions = transitions,
            trace = PipelineTrace(steps),
            smsAllowed = smsAllowed && !silent,
        )
    }

    private fun advanceConversationState(
        ctx: ThreadContext,
        steps: MutableList<String>,
        transitions: MutableList<TransitionRecord>,
        now: Instant,
    ): ThreadContext {
        // Don't pull deposit states backward
        if (
            ctx.state is ThreadState.DEPOSIT_REQUESTED ||
            ctx.state is ThreadState.DEPOSIT_PENDING ||
            ctx.state is ThreadState.HUMAN_REVIEW ||
            ctx.state is ThreadState.CONFIRMED
        ) {
            return ctx
        }
        val target = when (ctx.state) {
            is ThreadState.NEW -> ThreadState.GREETING
            is ThreadState.GREETING -> ThreadState.CONVERSING
            is ThreadState.STALLED -> ThreadState.CONVERSING
            else -> null
        } ?: return ctx
        val tr = ctx.tryTransition(target, ctx.revision, now, "orchestrator") ?: return ctx
        transitions += tr.record
        steps += "state:${ctx.state.serialName}->${target.serialName}"
        return tr.context
    }

    private fun touchRate(threadId: String, now: Instant): Int {
        val list = recentInbound.getOrPut(threadId) { mutableListOf() }
        val ms = now.toEpochMilliseconds()
        synchronized(list) {
            list.removeAll { ms - it > 60_000 }
            list.add(ms)
            return list.size
        }
    }

    private fun looksLikeConfirmation(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("confirmed") || t.contains("you're booked") || t.contains("see you at") ||
            t.contains("you're all set")
    }

    companion object {
        fun create(
            inference: InferenceProvider,
            audit: AuditLog = AuditLog(),
        ): InquiryOrchestrator {
            val safety = SafetyWorker(audit = audit)
            return InquiryOrchestrator(
                safety = safety,
                personaWorker = PersonaWorker(inference),
                memoryWorker = MemoryWorker(),
                timingWorker = TimingWorker(),
                bookingWorker = BookingWorker(),
                audit = audit,
            )
        }
    }
}
