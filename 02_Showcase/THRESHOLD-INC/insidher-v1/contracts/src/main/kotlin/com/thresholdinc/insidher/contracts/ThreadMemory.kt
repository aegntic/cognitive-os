package com.thresholdinc.insidher.contracts

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A memory entry for a thread: key, value, confidence, timestamps.
 *
 * Used to persist learned facts about a client (e.g., preferred_time, past_services).
 */
@Serializable
data class ThreadMemory(
    val threadId: String,
    val key: String,
    val value: String,
    val confidence: Double = 1.0,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(threadId.isNotBlank()) { "threadId must not be blank" }
        require(key.isNotBlank()) { "key must not be blank" }
        require(value.isNotBlank()) { "value must not be blank" }
        require(confidence in 0.0..1.0) {
            "confidence must be in [0.0, 1.0], was $confidence"
        }
        require(updatedAt >= createdAt) {
            "updatedAt ($updatedAt) must be >= createdAt ($createdAt)"
        }
    }

    /**
     * Creates an updated copy with a new [value], [confidence], and [updatedAt].
     * The [createdAt] is preserved.
     */
    fun update(
        newValue: String,
        newConfidence: Double = confidence,
        now: Instant,
    ): ThreadMemory = copy(
        value = newValue,
        confidence = newConfidence,
        updatedAt = now,
    ).also {
        require(newValue.isNotBlank()) { "value must not be blank" }
        require(newConfidence in 0.0..1.0) {
            "confidence must be in [0.0, 1.0], was $newConfidence"
        }
    }
}

/** Alias for [ThreadMemory]. */
typealias MemoryEntry = ThreadMemory

/**
 * A store of [ThreadMemory] entries for a thread, retrievable by key.
 */
@Serializable
data class MemoryStore(
    val threadId: String,
    val entries: Map<String, ThreadMemory> = emptyMap(),
) {
    init {
        require(threadId.isNotBlank()) { "threadId must not be blank" }
    }

    /** Retrieves a memory entry by key. */
    fun get(key: String): ThreadMemory? = entries[key]

    /** Adds or replaces a memory entry. */
    fun put(entry: ThreadMemory): MemoryStore = copy(entries = entries + (entry.key to entry))

    /** All memory entries as a list. */
    fun all(): List<ThreadMemory> = entries.values.toList()
}
