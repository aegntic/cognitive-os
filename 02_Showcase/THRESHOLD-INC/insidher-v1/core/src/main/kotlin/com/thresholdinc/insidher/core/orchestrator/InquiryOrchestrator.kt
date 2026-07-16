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
import com.thresholdinc.insidher.core.workers.PersonaWorker
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
 * InquiryOrchestrator — inbound → safety → (availability) → persona/inference →
 * outbound safety → actions. Enforces no-final-SMS-without-approve.
 *
 * Pipeline order (VAL-LLM-046): safety → memory → persona → safety → booking → timing
 * m0 implements safety + persona/inference + timing delay placeholder; memory/booking are no-op hooks.
 */
class InquiryOrchestrator(
    private val safety: SafetyWorker,
    private val personaWorker: PersonaWorker,
    private val availability: AvailabilityChecker = AvailabilityChecker(),
    private val audit: AuditLog = safety.auditLog(),
    private val clock: () -> Instant = { Clock.System.now() },
) {
    private val aiChallengeCounts = ConcurrentHashMap<String, Int>()
    private val recentInbound = ConcurrentHashMap<String, MutableList<Long>>()

    suspend fun handleInbound(
        context: ThreadContext,
        message: ClientMessage,
        persona: PersonaProfile,
        humanDecision: HumanDecision? = null,
    ): OrchestratorResult {
        val steps = mutableListOf<String>()
        val transitions = mutableListOf<TransitionRecord>()
        var ctx = context
        val now = clock()

        // Terminal / escalated: do not auto-process (VAL-SAFETY-084/085)
        if (ctx.state is ThreadState.ENDED || ctx.state is ThreadState.ESCALATED) {
            steps += "skip_terminal"
            return OrchestratorResult(ctx, listOf(OrchestratorAction.NoAction), emptyList(), null, transitions, PipelineTrace(steps), false)
        }

        // Human review gate
        if (ctx.state is ThreadState.HUMAN_REVIEW) {
            steps += "human_gate"
            return handleHumanReview(ctx, humanDecision, steps, transitions, now)
        }

        // 1. Safety inbound (VAL-SAFETY-006 first)
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
                // Safety-critical during quiet hours still escalate (VAL-SAFETY-067)
                return escalateResult(ctx, inboundVerdict.reasonCode, steps, transitions, now, smsAllowed = false, silent = false)
            }
            is SafetyVerdict.Safe -> { /* continue */ }
        }

        // Quiet hours: receive but do not respond (VAL-SAFETY-066)
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

        // 2. Memory hook (m0 no-op)
        steps += "memory"
        val memoryHints = emptyList<String>()

        // 3. Persona / inference
        steps += "persona"
        val draft: WorkerOutput.PersonaOutput = try {
            personaWorker.draft(persona, message.body, memoryHints)
        } catch (e: InferenceException) {
            // VAL-SAFETY-052 fail-closed on LLM failure
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
        var deflection = false
        var outbound = safety.evaluateOutbound(ctx.id, outboundText, deflectionAllowed = false)
        var retries = 0
        while (
            outbound.verdict is SafetyVerdict.Block &&
            (outbound.verdict as SafetyVerdict.Block).reasonCode == EscalationReasonCode.PERSONA_DEVIATION &&
            retries < 2
        ) {
            // VAL-SAFETY-102 regeneration limit
            retries++
            steps += "persona_retry_$retries"
            try {
                val redraft = personaWorker.draft(persona, message.body + "\n(Reply naturally, no AI/corporate language.)", memoryHints)
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

        // 5. Booking hook (m0 no-op)
        steps += "booking"

        // 6. Timing placeholder (m0 fixed range random later; expose delay action)
        steps += "timing"
        val delayMs = 45_000L // lower bound of initial delay range; full TimingWorker is m1

        // State progression NEW→GREETING→CONVERSING
        ctx = advanceConversationState(ctx, steps, transitions, now)

        val agentMessage = AgentMessage(
            threadId = ctx.id,
            body = outboundText,
            timestamp = now,
            worker = "persona",
            confidence = draft.confidence,
        )

        // Invariant: never send final confirmation without HumanDecision.APPROVE
        if (ctx.state is ThreadState.HUMAN_REVIEW || ctx.state is ThreadState.CONFIRMED) {
            // Should not auto-send confirmation from inbound path without decision
            if (looksLikeConfirmation(outboundText) && humanDecision !is HumanDecision.Approve) {
                steps += "block_final_sms"
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
        }

        val actions = listOf(
            OrchestratorAction.ScheduleDelayed(agentMessage, delayMs),
            OrchestratorAction.SendMessage(agentMessage),
        )
        steps += "send"

        return OrchestratorResult(
            context = ctx,
            actions = actions,
            outboundMessages = listOf(agentMessage),
            alert = null,
            transitions = transitions,
            trace = PipelineTrace(steps),
            smsAllowed = true,
        )
    }

    /**
     * Apply owner decision in HUMAN_REVIEW (VAL-SAFETY-043..048).
     */
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
                    body = "You're all set — see you then!",
                    timestamp = now,
                    worker = "human_gate",
                    confidence = 1.0,
                )
                // Outbound safety on confirmation
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
                val msg = AgentMessage(
                    threadId = ctx.id,
                    body = "Thanks for understanding — take care.",
                    timestamp = now,
                    worker = "human_gate",
                    confidence = 1.0,
                )
                safety.evaluateOutbound(ctx.id, msg.body)
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

        // Repeated challenges → full ESCALATED (VAL-SAFETY-034)
        if (count >= 3) {
            return escalateResult(ctx, EscalationReasonCode.AI_CHALLENGE_DETECTED, steps, transitions, now, false, silent = true)
        }

        // HUMAN_REVIEW: do not resume conversation (VAL-SAFETY-035)
        if (ctx.state is ThreadState.HUMAN_REVIEW) {
            return escalateResult(ctx, EscalationReasonCode.AI_CHALLENGE_DETECTED, steps, transitions, now, false, silent = true)
        }

        // Transition to AI_CHALLENGED
        val toChallenge = ctx.tryTransition(ThreadState.AI_CHALLENGED, ctx.revision, now, "safety")
        var next = ctx
        if (toChallenge != null) {
            transitions += toChallenge.record
            next = toChallenge.context
        }

        val deflection = safety.deflection()
        // VAL-SAFETY-098: outbound check deflection
        val out = safety.evaluateOutbound(next.id, deflection, deflectionAllowed = true)
        val msg = if (out.verdict is SafetyVerdict.Safe) {
            AgentMessage(next.id, deflection, now, "safety", 0.9)
        } else {
            AgentMessage(next.id, "Haha what? I'm right here.", now, "safety", 0.8)
        }

        // Silent owner alert (VAL-SAFETY-033)
        val alert = WorkerOutput.AlertOutput(
            reasonCode = EscalationReasonCode.AI_CHALLENGE_DETECTED,
            threadId = next.id,
            urgencyLevel = UrgencyLevel.MEDIUM,
        )
        audit.logEscalation(next.id, EscalationReasonCode.AI_CHALLENGE_DETECTED, "silent")

        // Resume to previous conversational state (VAL-SAFETY-032)
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
        return t.contains("confirmed") || t.contains("you're booked") || t.contains("see you at")
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
                audit = audit,
            )
        }
    }
}
