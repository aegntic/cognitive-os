package com.thresholdinc.insidher.contracts

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Immutable snapshot of a conversation thread's state with optimistic concurrency (CAS) support.
 *
 * Invariants:
 * - revision starts at 1 and increments by 1 on every transition
 * - personaId is immutable once set
 * - state transitions are validated by [StateMachine]
 */
@Serializable
data class ThreadContext(
    /** Primary key. Must be non-blank. */
    val id: String,
    /** Current lifecycle state. */
    val state: ThreadState,
    /** CAS revision. Starts at 1, increments on each transition. */
    val revision: Int = 1,
    /** Persona reference. Immutable per thread. */
    val personaId: String,
    /** Client phone number. Must be non-blank. */
    val clientPhone: String,
    /** Previous state, used for AI_CHALLENGED / COOLDOWN restoration. */
    val previousState: ThreadState? = null,
    /** Creation timestamp. */
    val createdAt: Instant,
    /** Last update timestamp. */
    val updatedAt: Instant,
    /** Last inbound message timestamp. */
    val lastMessageAt: Instant? = null,
    /** Flexible JSON blob for arbitrary metadata. */
    val metadata: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(revision >= 1) { "revision must be >= 1, was $revision" }
        require(personaId.isNotBlank()) { "personaId must not be blank" }
        require(clientPhone.isNotBlank()) { "clientPhone must not be blank" }
    }

    /**
     * Performs an atomic state transition with CAS (Compare-And-Swap) validation.
     *
     * @param newState the target state
     * @param expectedRevision the CAS revision the caller believes is current
     * @param now timestamp for the transition (defaults to Clock.System.now())
     * @return a [TransitionResult] containing the updated [ThreadContext] and a [TransitionRecord]
     * @throws IllegalArgumentException if the CAS revision does not match or the transition is invalid
     */
    fun transition(
        newState: ThreadState,
        expectedRevision: Int,
        now: Instant = Clock.System.now(),
        actor: String = "system",
    ): TransitionResult {
        require(expectedRevision == revision) {
            "CAS failed: expected revision $expectedRevision but current is $revision"
        }
        val previousForNewState: ThreadState? = when (newState) {
            is ThreadState.AI_CHALLENGED -> this.state
            is ThreadState.COOLDOWN -> this.state
            else -> this.previousState
        }
        require(StateMachine.isValidTransition(this.state, newState, this.previousState)) {
            "Invalid transition: ${this.state.serialName} → ${newState.serialName}"
        }
        val updated = copy(
            state = newState,
            revision = revision + 1,
            updatedAt = now,
            previousState = previousForNewState,
        )
        val record = TransitionRecord(
            threadId = id,
            fromState = this.state,
            toState = newState,
            timestamp = now,
            actor = actor,
            revisionFrom = this.revision,
            revisionTo = revision + 1,
        )
        return TransitionResult(updated, record)
    }

    /**
     * Attempts a CAS transition, returning null on failure instead of throwing.
     */
    fun tryTransition(
        newState: ThreadState,
        expectedRevision: Int,
        now: Instant = Clock.System.now(),
        actor: String = "system",
    ): TransitionResult? = try {
        transition(newState, expectedRevision, now, actor)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Result of a state transition: the new [ThreadContext] plus an audit [TransitionRecord].
 */
@Serializable
data class TransitionResult(
    val context: ThreadContext,
    val record: TransitionRecord,
)

/**
 * Audit log entry for a single state transition.
 */
@Serializable
data class TransitionRecord(
    val threadId: String,
    val fromState: ThreadState,
    val toState: ThreadState,
    val timestamp: Instant,
    val actor: String,
    val revisionFrom: Int,
    val revisionTo: Int,
)

/**
 * A complete snapshot of a thread: context plus recent messages and memories.
 */
@Serializable
data class ThreadSnapshot(
    val context: ThreadContext,
    val messages: List<Message> = emptyList(),
    val memories: List<ThreadMemory> = emptyList(),
)
