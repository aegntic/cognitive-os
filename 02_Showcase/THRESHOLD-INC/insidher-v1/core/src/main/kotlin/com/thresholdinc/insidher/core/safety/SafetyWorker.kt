package com.thresholdinc.insidher.core.safety

import com.thresholdinc.insidher.contracts.EscalationReasonCode
import com.thresholdinc.insidher.contracts.SafetyVerdict
import com.thresholdinc.insidher.contracts.WorkerOutput
import com.thresholdinc.insidher.core.audit.AuditLog

/**
 * Behavior-based SafetyWorker — first and last gate in the pipeline.
 * Fail-closed on errors (VAL-SAFETY-005, 010, 051).
 */
class SafetyWorker(
    private val policy: SafetyPolicy = SafetyPolicy(),
    private val audit: AuditLog = AuditLog(),
) {
    fun evaluateInbound(
        threadId: String,
        text: String,
        priorAiChallenges: Int = 0,
        recentMessageCount: Int = 0,
    ): WorkerOutput.SafetyOutput {
        return try {
            val verdict = policy.evaluateInbound(text, priorAiChallenges, recentMessageCount)
            audit.logSafety(threadId, verdict, "inbound", text.take(200))
            if (verdict is SafetyVerdict.Escalate || verdict is SafetyVerdict.Block) {
                val code = when (verdict) {
                    is SafetyVerdict.Escalate -> verdict.reasonCode
                    is SafetyVerdict.Block -> verdict.reasonCode
                    else -> EscalationReasonCode.UNKNOWN_RISK
                }
                audit.logEscalation(threadId, code)
            }
            WorkerOutput.SafetyOutput(verdict)
        } catch (_: Exception) {
            val verdict = SafetyVerdict.Escalate(EscalationReasonCode.UNKNOWN_RISK, 0.5)
            audit.logSafety(threadId, verdict, "inbound", text.take(200))
            audit.logEscalation(threadId, EscalationReasonCode.UNKNOWN_RISK, "safety_error")
            WorkerOutput.SafetyOutput(verdict)
        }
    }

    fun evaluateOutbound(
        threadId: String,
        text: String,
        deflectionAllowed: Boolean = false,
    ): WorkerOutput.SafetyOutput {
        return try {
            val verdict = policy.evaluateOutbound(text, deflectionAllowed)
            audit.logSafety(threadId, verdict, "outbound", text.take(200))
            WorkerOutput.SafetyOutput(verdict)
        } catch (_: Exception) {
            val verdict = SafetyVerdict.Escalate(EscalationReasonCode.UNKNOWN_RISK, 0.5)
            audit.logSafety(threadId, verdict, "outbound", text.take(200))
            WorkerOutput.SafetyOutput(verdict)
        }
    }

    fun deflection(): String = policy.deflectionResponse()

    fun auditLog(): AuditLog = audit
}
