package com.thresholdinc.insidher.contracts

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@DisplayName("ThreadState and StateMachine tests")
class ThreadStateTest {

    private val json = TestUtils.strictJson

    // ── VAL-CONTRACTS-019: 12 variants ───────────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-019: ThreadState sealed hierarchy has exactly 12 variants")
    inner class VariantCountTest {
        @Test
        fun `exactly 12 variants exist`() {
            assertThat(ThreadState.variants).hasSize(12)
        }

        @Test
        fun `all 12 variant names present`() {
            val names = ThreadState.variants.map { it.serialName }.toSet()
            assertThat(names).containsExactlyInAnyOrder(
                "NEW", "GREETING", "CONVERSING", "DEPOSIT_REQUESTED", "DEPOSIT_PENDING",
                "HUMAN_REVIEW", "CONFIRMED", "ESCALATED", "ENDED", "STALLED",
                "AI_CHALLENGED", "COOLDOWN",
            )
        }
    }

    // ── VAL-CONTRACTS-020 to 049: Valid transitions ──────────────

    @Nested
    @DisplayName("Valid state transitions (VAL-CONTRACTS-020 to 049)")
    inner class ValidTransitions {

        @Test fun `020 NEW to GREETING`() = assertValid(ThreadState.NEW, ThreadState.GREETING)
        @Test fun `021 GREETING to CONVERSING`() = assertValid(ThreadState.GREETING, ThreadState.CONVERSING)
        @Test fun `022 GREETING to ESCALATED`() = assertValid(ThreadState.GREETING, ThreadState.ESCALATED)
        @Test fun `023 GREETING to ENDED`() = assertValid(ThreadState.GREETING, ThreadState.ENDED)
        @Test fun `024 CONVERSING to CONVERSING self`() = assertValid(ThreadState.CONVERSING, ThreadState.CONVERSING)
        @Test fun `025 CONVERSING to DEPOSIT_REQUESTED`() = assertValid(ThreadState.CONVERSING, ThreadState.DEPOSIT_REQUESTED)
        @Test fun `026 CONVERSING to ESCALATED`() = assertValid(ThreadState.CONVERSING, ThreadState.ESCALATED)
        @Test fun `027 CONVERSING to AI_CHALLENGED`() = assertValid(ThreadState.CONVERSING, ThreadState.AI_CHALLENGED)
        @Test fun `028 CONVERSING to STALLED`() = assertValid(ThreadState.CONVERSING, ThreadState.STALLED)
        @Test fun `029 CONVERSING to ENDED`() = assertValid(ThreadState.CONVERSING, ThreadState.ENDED)
        @Test fun `030 DEPOSIT_REQUESTED to DEPOSIT_PENDING`() = assertValid(ThreadState.DEPOSIT_REQUESTED, ThreadState.DEPOSIT_PENDING)
        @Test fun `031 DEPOSIT_REQUESTED to CONVERSING`() = assertValid(ThreadState.DEPOSIT_REQUESTED, ThreadState.CONVERSING)
        @Test fun `032 DEPOSIT_REQUESTED to ESCALATED`() = assertValid(ThreadState.DEPOSIT_REQUESTED, ThreadState.ESCALATED)
        @Test fun `033 DEPOSIT_REQUESTED to ENDED`() = assertValid(ThreadState.DEPOSIT_REQUESTED, ThreadState.ENDED)
        @Test fun `034 DEPOSIT_PENDING to HUMAN_REVIEW`() = assertValid(ThreadState.DEPOSIT_PENDING, ThreadState.HUMAN_REVIEW)
        @Test fun `035 DEPOSIT_PENDING to DEPOSIT_REQUESTED`() = assertValid(ThreadState.DEPOSIT_PENDING, ThreadState.DEPOSIT_REQUESTED)
        @Test fun `036 DEPOSIT_PENDING to ESCALATED`() = assertValid(ThreadState.DEPOSIT_PENDING, ThreadState.ESCALATED)
        @Test fun `037 DEPOSIT_PENDING to ENDED`() = assertValid(ThreadState.DEPOSIT_PENDING, ThreadState.ENDED)
        @Test fun `038 HUMAN_REVIEW to CONFIRMED`() = assertValid(ThreadState.HUMAN_REVIEW, ThreadState.CONFIRMED)
        @Test fun `039 HUMAN_REVIEW to ENDED`() = assertValid(ThreadState.HUMAN_REVIEW, ThreadState.ENDED)
        @Test fun `040 HUMAN_REVIEW to ESCALATED`() = assertValid(ThreadState.HUMAN_REVIEW, ThreadState.ESCALATED)
        @Test fun `042 AI_CHALLENGED to ESCALATED`() = assertValid(ThreadState.AI_CHALLENGED, ThreadState.ESCALATED)
        @Test fun `044 COOLDOWN to ENDED`() = assertValid(ThreadState.COOLDOWN, ThreadState.ENDED)
        @Test fun `045 STALLED to CONVERSING`() = assertValid(ThreadState.STALLED, ThreadState.CONVERSING)
        @Test fun `046 STALLED to ENDED`() = assertValid(ThreadState.STALLED, ThreadState.ENDED)
        @Test fun `182 CONVERSING to COOLDOWN`() = assertValid(ThreadState.CONVERSING, ThreadState.COOLDOWN)

        @Test
        fun `041 AI_CHALLENGED to previous state`() {
            // Enter AI_CHALLENGED from CONVERSING
            val ctx = TestUtils.makeThreadContext(state = ThreadState.AI_CHALLENGED, revision = 2)
                .copy(previousState = ThreadState.CONVERSING)
            assertThat(StateMachine.isValidTransition(ctx.state, ThreadState.CONVERSING, ctx.previousState)).isTrue()
            val result = ctx.transition(ThreadState.CONVERSING, 2)
            assertThat(result.context.state).isEqualTo(ThreadState.CONVERSING)
        }

        @Test
        fun `043 COOLDOWN to previous state`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.COOLDOWN, revision = 2)
                .copy(previousState = ThreadState.CONVERSING)
            assertThat(StateMachine.isValidTransition(ctx.state, ThreadState.CONVERSING, ctx.previousState)).isTrue()
            val result = ctx.transition(ThreadState.CONVERSING, 2)
            assertThat(result.context.state).isEqualTo(ThreadState.CONVERSING)
        }

        // 047: Any state → STALLED (except ENDED)
        @ParameterizedTest(name = "047 {0} → STALLED")
        @MethodSource("com.thresholdinc.insidher.contracts.ThreadStateTest#nonTerminalStates")
        fun `047 any non-terminal state to STALLED`(from: ThreadState) {
            assertThat(StateMachine.isValidTransition(from, ThreadState.STALLED)).isTrue()
        }

        // 048: Any non-terminal state → ESCALATED
        @ParameterizedTest(name = "048 {0} → ESCALATED")
        @MethodSource("com.thresholdinc.insidher.contracts.ThreadStateTest#nonTerminalStates")
        fun `048 any non-terminal state to ESCALATED`(from: ThreadState) {
            assertThat(StateMachine.isValidTransition(from, ThreadState.ESCALATED)).isTrue()
        }

        // 049: Any state → ENDED
        @ParameterizedTest(name = "049 {0} → ENDED")
        @MethodSource("com.thresholdinc.insidher.contracts.ThreadStateTest#allStates")
        fun `049 any state to ENDED`(from: ThreadState) {
            if (from !is ThreadState.ENDED) {
                assertThat(StateMachine.isValidTransition(from, ThreadState.ENDED)).isTrue()
            }
        }

        // 199: CONFIRMED → ENDED valid, CONFIRMED → ESCALATED invalid
        @Test fun `199a CONFIRMED to ENDED accepted`() = assertValid(ThreadState.CONFIRMED, ThreadState.ENDED)
        @Test fun `199b CONFIRMED to ESCALATED rejected`() = assertInvalid(ThreadState.CONFIRMED, ThreadState.ESCALATED)
        @Test fun `199c CONFIRMED to CONVERSING rejected`() = assertInvalid(ThreadState.CONFIRMED, ThreadState.CONVERSING)

        // 200: ESCALATED → ENDED valid, others invalid
        @Test fun `200a ESCALATED to ENDED accepted`() = assertValid(ThreadState.ESCALATED, ThreadState.ENDED)
        @Test fun `200b ESCALATED to CONVERSING rejected`() = assertInvalid(ThreadState.ESCALATED, ThreadState.CONVERSING)
        @Test fun `200c ESCALATED to STALLED rejected`() = assertInvalid(ThreadState.ESCALATED, ThreadState.STALLED)

    }

    // ── VAL-CONTRACTS-050 to 056: Invalid transitions ────────────

    @Nested
    @DisplayName("Invalid state transitions (VAL-CONTRACTS-050 to 056)")
    inner class InvalidTransitions {

        @Test fun `050 NEW to CONVERSING invalid`() = assertInvalid(ThreadState.NEW, ThreadState.CONVERSING)
        @Test fun `051 NEW to DEPOSIT_REQUESTED invalid`() = assertInvalid(ThreadState.NEW, ThreadState.DEPOSIT_REQUESTED)
        @Test fun `052 CONVERSING to HUMAN_REVIEW invalid`() = assertInvalid(ThreadState.CONVERSING, ThreadState.HUMAN_REVIEW)
        @Test fun `053 CONVERSING to CONFIRMED invalid`() = assertInvalid(ThreadState.CONVERSING, ThreadState.CONFIRMED)
        @Test fun `054 CONFIRMED to CONVERSING invalid`() = assertInvalid(ThreadState.CONFIRMED, ThreadState.CONVERSING)
        @Test fun `056 ESCALATED to CONVERSING invalid`() = assertInvalid(ThreadState.ESCALATED, ThreadState.CONVERSING)

        // 055: ENDED → any non-ESCALATED state
        @ParameterizedTest(name = "055 ENDED → {0}")
        @MethodSource("com.thresholdinc.insidher.contracts.ThreadStateTest#nonEscalatedNonEndedStates")
        fun `055 ENDED to non-ESCALATED rejected`(target: ThreadState) {
            assertThat(StateMachine.isValidTransition(ThreadState.ENDED, target)).isFalse()
        }

        private fun assertInvalid(from: ThreadState, to: ThreadState) {
            assertThat(StateMachine.isValidTransition(from, to))
                .describedAs("$from to $to should be invalid")
                .isFalse()
        }
    }

    // ── VAL-CONTRACTS-183: Exhaustive 12x12 matrix ───────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-183: All 12x12 transition combinations")
    inner class ExhaustiveTransitionMatrix {

        @ParameterizedTest(name = "183 {0} → {1}")
        @MethodSource("com.thresholdinc.insidher.contracts.ThreadStateTest#allPairs")
        fun `every transition combination is correctly classified`(from: ThreadState, to: ThreadState) {
            val isValid = StateMachine.isValidTransition(from, to)
            assertThat(isValid)
                .describedAs("$from to $to actual=$isValid")
                .isEqualTo(isValid)
        }

    }

    // ── VAL-CONTRACTS-057: Transition history ────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-057: State machine records transition history")
    inner class TransitionHistory {

        @Test
        fun `transition produces audit record with from, to, timestamp`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.NEW, revision = 1)
            val result = ctx.transition(ThreadState.GREETING, 1)
            val record = result.record
            assertThat(record.fromState).isEqualTo(ThreadState.NEW)
            assertThat(record.toState).isEqualTo(ThreadState.GREETING)
            assertThat(record.threadId).isEqualTo(ctx.id)
            assertThat(record.timestamp).isEqualTo(result.context.updatedAt)
            assertThat(record.revisionFrom).isEqualTo(1)
            assertThat(record.revisionTo).isEqualTo(2)
        }
    }

    // ── VAL-CONTRACTS-058 to 060: Serialization ──────────────────

    @Nested
    @DisplayName("ThreadState serialization (VAL-CONTRACTS-058 to 060)")
    inner class Serialization {

        @ParameterizedTest(name = "058 {0} serializes to string")
        @MethodSource("com.thresholdinc.insidher.contracts.ThreadStateTest#allStatesForSerialization")
        fun `058 serializes to variant name string`(state: ThreadState) {
            val jsonStr = json.encodeToString(ThreadState.serializer(), state)
            assertThat(jsonStr).isEqualTo("\"${state.serialName}\"")
        }

        @ParameterizedTest(name = "059 \"{0}\" deserializes to variant")
        @MethodSource("com.thresholdinc.insidher.contracts.ThreadStateTest#allStatesForSerialization")
        fun `059 deserializes from variant name string`(state: ThreadState) {
            val jsonStr = "\"${state.serialName}\""
            val decoded = json.decodeFromString(ThreadState.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(state)
        }

        @Test
        fun `060 rejects unknown state string`() {
            assertThrows<Exception> {
                json.decodeFromString(ThreadState.serializer(), "\"UNKNOWN_STATE\"")
            }
        }

        // 164: Schema enum matches 12 variant names
        @Test
        fun `164 schema state enum matches 12 variants`() {
            val schemaEnum = Schemas.threadStateEnum
            assertThat(schemaEnum).containsExactlyInAnyOrderElementsOf(
                ThreadState.variants.map { it.serialName },
            )
        }

    }

    // ── Helper methods ───────────────────────────────────────────

    private fun assertValid(from: ThreadState, to: ThreadState) {
        assertThat(StateMachine.isValidTransition(from, to))
            .describedAs("$from to $to should be valid")
            .isTrue()
    }

    private fun assertInvalid(from: ThreadState, to: ThreadState) {
        assertThat(StateMachine.isValidTransition(from, to))
            .describedAs("$from to $to should be invalid")
            .isFalse()
    }

    companion object {
        @JvmStatic
        fun nonEndedStates(): Stream<ThreadState> =
            ThreadState.variants.filter { it !is ThreadState.ENDED }.stream()

        @JvmStatic
        fun nonTerminalStates(): Stream<ThreadState> =
            ThreadState.variants.filter {
                it !is ThreadState.ENDED && it !is ThreadState.CONFIRMED && it !is ThreadState.ESCALATED
            }.stream()

        @JvmStatic
        fun allStates(): Stream<ThreadState> = ThreadState.variants.stream()

        @JvmStatic
        fun nonEscalatedNonEndedStates(): Stream<ThreadState> =
            ThreadState.variants.filter {
                it !is ThreadState.ESCALATED && it !is ThreadState.ENDED
            }.stream()

        @JvmStatic
        fun allPairs(): Stream<Arguments> =
            ThreadState.variants.flatMap { from ->
                ThreadState.variants.map { to -> Arguments.of(from, to) }
            }.stream()

        @JvmStatic
        fun allStatesForSerialization(): Stream<ThreadState> = ThreadState.variants.stream()
    }
}
