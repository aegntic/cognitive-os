package com.thresholdinc.insidher.core.audit

import com.thresholdinc.insidher.contracts.EscalationReasonCode
import com.thresholdinc.insidher.contracts.HumanDecision
import com.thresholdinc.insidher.contracts.SafetyVerdict
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

data class AuditEntry(
    val id: Long,
    val timestamp: Instant,
    val action: String,
    val actor: String,
    val threadId: String?,
    val details: Map<String, String>,
)

/**
 * Append-only in-memory audit trail (VAL-SAFETY-070..076).
 * Production backs this with D1; core tests use this store.
 */
class AuditLog {
    private val entries = CopyOnWriteArrayList<AuditEntry>()
    private var seq = 0L

    fun append(
        action: String,
        actor: String,
        threadId: String? = null,
        details: Map<String, String> = emptyMap(),
        timestamp: Instant = Clock.System.now(),
    ): AuditEntry {
        val entry = AuditEntry(
            id = ++seq,
            timestamp = timestamp,
            action = action,
            actor = actor,
            threadId = threadId,
            details = details,
        )
        entries.add(entry)
        return entry
    }

    fun logSafety(
        threadId: String,
        verdict: SafetyVerdict,
        direction: String,
        contentSnippet: String? = null,
    ) {
        val (type, reason, confidence) = when (verdict) {
            is SafetyVerdict.Safe -> Triple("SAFE", null, verdict.confidence)
            is SafetyVerdict.Escalate -> Triple("ESCALATE", verdict.reasonCode.name, verdict.confidence)
            is SafetyVerdict.Block -> Triple("BLOCK", verdict.reasonCode.name, verdict.confidence)
        }
        append(
            action = "safety_check",
            actor = "worker:safety",
            threadId = threadId,
            details = buildMap {
                put("verdict", type)
                put("direction", direction)
                put("confidence", confidence.toString())
                reason?.let { put("reasonCode", it) }
                contentSnippet?.let { put("content", it.take(500)) }
            },
        )
    }

    fun logEscalation(threadId: String, reason: EscalationReasonCode, note: String? = null) {
        append(
            action = "escalation",
            actor = "worker:safety",
            threadId = threadId,
            details = buildMap {
                put("reasonCode", reason.name)
                note?.let { put("note", it) }
            },
        )
    }

    fun logHumanDecision(threadId: String, decision: HumanDecision) {
        append(
            action = "human_decision",
            actor = "owner",
            threadId = threadId,
            details = buildMap {
                put(
                    "decision",
                    when (decision) {
                        is HumanDecision.Approve -> "APPROVE"
                        is HumanDecision.Reject -> "REJECT"
                        is HumanDecision.Escalate -> "ESCALATE"
                    },
                )
                put("timestamp", decision.timestamp.toString())
                decision.note?.let { put("note", it) }
            },
        )
    }

    fun logLlmCall(
        threadId: String,
        model: String,
        tokensUsed: Int,
        confidence: Double,
        success: Boolean,
    ) {
        append(
            action = "llm_call",
            actor = "worker:inference",
            threadId = threadId,
            details = mapOf(
                "model" to model,
                "tokensUsed" to tokensUsed.toString(),
                "confidence" to confidence.toString(),
                "success" to success.toString(),
            ),
        )
    }

    fun all(): List<AuditEntry> = Collections.unmodifiableList(entries.toList())

    fun forThread(threadId: String): List<AuditEntry> =
        entries.filter { it.threadId == threadId }
}
