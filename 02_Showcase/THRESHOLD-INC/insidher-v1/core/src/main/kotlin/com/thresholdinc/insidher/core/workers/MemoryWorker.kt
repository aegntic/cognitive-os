package com.thresholdinc.insidher.core.workers

import com.thresholdinc.insidher.contracts.MemoryStore
import com.thresholdinc.insidher.contracts.ThreadMemory
import com.thresholdinc.insidher.contracts.WorkerOutput
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.ln

/**
 * Within-thread short-term memory (m1).
 * Store/retrieve per thread, exponential decay, natural hints for PersonaWorker.
 */
class MemoryWorker(
    /** Half-life for confidence decay (default 6h). */
    private val halfLifeMs: Long = 6 * 60 * 60 * 1000L,
    /** Drop entries below this confidence after decay. */
    private val minConfidence: Double = 0.15,
) {
    private val stores = ConcurrentHashMap<String, MemoryStore>()

    fun store(
        threadId: String,
        key: String,
        value: String,
        now: Instant,
        confidence: Double = 1.0,
    ): ThreadMemory {
        val store = stores.getOrPut(threadId) { MemoryStore(threadId) }
        val existing = store.get(key)
        val entry = if (existing != null) {
            existing.update(value, confidence.coerceIn(0.0, 1.0), now)
        } else {
            ThreadMemory(threadId, key, value, confidence.coerceIn(0.0, 1.0), now, now)
        }
        stores[threadId] = store.put(entry)
        return entry
    }

    fun retrieve(threadId: String, now: Instant): WorkerOutput.MemoryOutput {
        decay(threadId, now)
        val entries = stores[threadId]?.all().orEmpty()
        return WorkerOutput.MemoryOutput(entries)
    }

    /** Decay all entries for [threadId]; prune low-confidence. */
    fun decay(threadId: String, now: Instant) {
        val store = stores[threadId] ?: return
        val kept = store.entries.mapNotNull { (k, e) ->
            val age = (now.toEpochMilliseconds() - e.updatedAt.toEpochMilliseconds()).coerceAtLeast(0)
            val factor = decayFactor(age)
            val next = (e.confidence * factor).coerceIn(0.0, 1.0)
            if (next < minConfidence) null
            else k to e.copy(confidence = next)
        }.toMap()
        stores[threadId] = store.copy(entries = kept)
    }

    /**
     * Natural language hints for persona (not raw key=value dumps).
     * Higher confidence first; skipped if empty.
     */
    fun naturalHints(threadId: String, now: Instant): List<String> {
        val entries = retrieve(threadId, now).entries
            .sortedByDescending { it.confidence }
            .take(6)
        return entries.map { e ->
            when (e.key) {
                "preferred_time" -> "They mentioned preferring ${e.value}"
                "name" -> "Their name is ${e.value}"
                "service" -> "They're interested in ${e.value}"
                "location" -> "They mentioned ${e.value}"
                "deposit_claim" -> "They said they ${e.value}"
                else -> "Earlier they mentioned ${e.key.replace('_', ' ')}: ${e.value}"
            }
        }
    }

    /** Lightweight fact extraction from client text (regex, no LLM). */
    fun observe(threadId: String, clientText: String, now: Instant) {
        val t = clientText.trim()
        if (t.isBlank()) return

        // name: "I'm Alex" / "my name is Sam"
        Regex("""(?:i'?m|i am|my name is|this is)\s+([A-Z][a-z]{1,20})\b""")
            .find(t)?.groupValues?.getOrNull(1)?.let { store(threadId, "name", it, now, 0.85) }

        // time preference
        Regex(
            """(?i)\b(thursday|friday|saturday|sunday|monday|tuesday|wednesday|tonight|tomorrow|morning|afternoon|evening)\b""",
        ).find(t)?.value?.let { store(threadId, "preferred_time", it.lowercase(), now, 0.8) }

        // service-ish
        Regex("""(?i)\b(massage|company|hour|overnight|dinner|drinks)\b""")
            .find(t)?.value?.let { store(threadId, "service", it.lowercase(), now, 0.7) }

        // last client utterance (always refresh, low conf)
        val snippet = t.take(120)
        if (snippet.isNotBlank()) {
            store(threadId, "last_inbound", snippet, now, 0.6)
        }
    }

    fun clear(threadId: String) {
        stores.remove(threadId)
    }

    private fun decayFactor(ageMs: Long): Double {
        if (ageMs <= 0 || halfLifeMs <= 0) return 1.0
        // exp(-ln2 * age / halfLife)
        return exp(-ln(2.0) * ageMs.toDouble() / halfLifeMs.toDouble())
    }
}
