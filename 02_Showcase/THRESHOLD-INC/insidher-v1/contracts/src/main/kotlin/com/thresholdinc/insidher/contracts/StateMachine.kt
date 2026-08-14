package com.thresholdinc.insidher.contracts

/**
 * State machine logic for [ThreadState] transitions.
 *
 * Defines the complete transition matrix and provides validation.
 */
object StateMachine {

    /**
     * The set of valid target states for each source state (excluding context-dependent transitions).
     */
    private val baseTransitions: Map<ThreadState, Set<ThreadState>> = mapOf(
        ThreadState.NEW to setOf(
            ThreadState.GREETING,
            ThreadState.STALLED,
            ThreadState.ESCALATED,
            ThreadState.ENDED,
        ),
        ThreadState.GREETING to setOf(
            ThreadState.CONVERSING,
            ThreadState.STALLED,
            ThreadState.ESCALATED,
            ThreadState.ENDED,
        ),
        ThreadState.CONVERSING to setOf(
            ThreadState.CONVERSING, // self-loop
            ThreadState.DEPOSIT_REQUESTED,
            ThreadState.AI_CHALLENGED,
            ThreadState.COOLDOWN,
            ThreadState.STALLED,
            ThreadState.ESCALATED,
            ThreadState.ENDED,
        ),
        ThreadState.DEPOSIT_REQUESTED to setOf(
            ThreadState.DEPOSIT_PENDING,
            ThreadState.CONVERSING,
            ThreadState.STALLED,
            ThreadState.ESCALATED,
            ThreadState.ENDED,
        ),
        ThreadState.DEPOSIT_PENDING to setOf(
            ThreadState.HUMAN_REVIEW,
            ThreadState.DEPOSIT_REQUESTED,
            ThreadState.STALLED,
            ThreadState.ESCALATED,
            ThreadState.ENDED,
        ),
        ThreadState.HUMAN_REVIEW to setOf(
            ThreadState.CONFIRMED,
            ThreadState.STALLED,
            ThreadState.ESCALATED,
            ThreadState.ENDED,
        ),
        ThreadState.CONFIRMED to setOf(
            ThreadState.ENDED,
        ),
        ThreadState.ESCALATED to setOf(
            ThreadState.ENDED,
        ),
        ThreadState.ENDED to emptySet(),
        ThreadState.STALLED to setOf(
            ThreadState.CONVERSING,
            ThreadState.STALLED, // self-transition allowed for cron re-check
            ThreadState.ESCALATED,
            ThreadState.ENDED,
        ),
        ThreadState.AI_CHALLENGED to setOf(
            ThreadState.ESCALATED,
            ThreadState.STALLED,
            ThreadState.ENDED,
        ),
        ThreadState.COOLDOWN to setOf(
            ThreadState.STALLED,
            ThreadState.ESCALATED,
            ThreadState.ENDED,
        ),
    )

    /**
     * Checks whether a transition from [from] to [to] is valid.
     *
     * @param from current state
     * @param to target state
     * @param previousState the state before entering AI_CHALLENGED or COOLDOWN (for restoration)
     * @return true if the transition is valid
     */
    fun isValidTransition(
        from: ThreadState,
        to: ThreadState,
        previousState: ThreadState? = null,
    ): Boolean {
        // Check base transitions first
        val targets = baseTransitions[from]
        if (targets != null && to in targets) return true

        // AI_CHALLENGED → previousState (restoration)
        if (from is ThreadState.AI_CHALLENGED && previousState != null) {
            // Can only restore to active conversational states
            val restorable = setOf(
                ThreadState.CONVERSING,
                ThreadState.DEPOSIT_REQUESTED,
                ThreadState.DEPOSIT_PENDING,
                ThreadState.GREETING,
            )
            if (to == previousState && to in restorable) return true
        }

        // COOLDOWN → previousState (restoration)
        if (from is ThreadState.COOLDOWN && previousState != null) {
            val restorable = setOf(
                ThreadState.CONVERSING,
                ThreadState.DEPOSIT_REQUESTED,
                ThreadState.DEPOSIT_PENDING,
            )
            if (to == previousState && to in restorable) return true
        }

        return false
    }

    /**
     * Returns the set of all valid target states from [from], given optional [previousState].
     */
    fun validTargets(from: ThreadState, previousState: ThreadState? = null): Set<ThreadState> {
        val targets = baseTransitions[from]?.toMutableSet() ?: mutableSetOf()

        if (from is ThreadState.AI_CHALLENGED && previousState != null) {
            val restorable = setOf(
                ThreadState.CONVERSING,
                ThreadState.DEPOSIT_REQUESTED,
                ThreadState.DEPOSIT_PENDING,
                ThreadState.GREETING,
            )
            if (previousState in restorable) targets.add(previousState)
        }

        if (from is ThreadState.COOLDOWN && previousState != null) {
            val restorable = setOf(
                ThreadState.CONVERSING,
                ThreadState.DEPOSIT_REQUESTED,
                ThreadState.DEPOSIT_PENDING,
            )
            if (previousState in restorable) targets.add(previousState)
        }

        return targets.toSet()
    }

    /**
     * Returns all valid (from, to) transition pairs for testing.
     * Excludes context-dependent AI_CHALLENGED/COOLDOWN → previousState transitions.
     */
    fun allValidTransitions(): List<Pair<ThreadState, ThreadState>> =
        baseTransitions.flatMap { (from, targets) ->
            targets.map { from to it }
        }
}
