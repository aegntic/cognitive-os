package com.thresholdinc.insidher.contracts

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("ThreadContext CAS and transition tests (VAL-CONTRACTS-009 to 018, 159, 173, 175-176, 190, 196)")
class ThreadContextTest {

    private val json = TestUtils.strictJson

    // ── Serialization ────────────────────────────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-009: ThreadContext serializes to valid JSON")
    inner class SerializeTest {
        @Test
        fun `serializes with all fields`() {
            val ctx = TestUtils.makeThreadContext()
            val jsonStr = json.encodeToString(ThreadContext.serializer(), ctx)
            assertThat(jsonStr).contains("\"id\":\"thread-001\"")
            assertThat(jsonStr).contains("\"state\":\"NEW\"")
            assertThat(jsonStr).contains("\"revision\":1")
            assertThat(jsonStr).contains("\"personaId\":\"persona-anita\"")
            assertThat(jsonStr).contains("\"clientPhone\":\"+61412345678\"")
            assertThat(jsonStr).contains("\"createdAt\"")
            assertThat(jsonStr).contains("\"updatedAt\"")
            assertThat(jsonStr).contains("\"metadata\"")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-010: ThreadContext deserializes from valid JSON")
    inner class DeserializeTest {
        @Test
        fun `round-trip equality holds`() {
            val ctx = TestUtils.makeThreadContext()
            val jsonStr = json.encodeToString(ThreadContext.serializer(), ctx)
            val decoded = json.decodeFromString(ThreadContext.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(ctx)
        }
    }

    // ── CAS and revision ─────────────────────────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-011: revision starts at 1")
    inner class RevisionStart {
        @Test
        fun `new thread has revision 1`() {
            val ctx = TestUtils.makeThreadContext()
            assertThat(ctx.revision).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-012: revision increments on transition")
    inner class RevisionIncrement {
        @Test
        fun `revision increments by 1 on valid transition`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.NEW, revision = 1)
            val result = ctx.transition(ThreadState.GREETING, 1)
            assertThat(result.context.revision).isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-013: CAS fails on stale revision")
    inner class CasStaleFail {
        @Test
        fun `stale revision rejected`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.NEW, revision = 3)
            assertThrows<IllegalArgumentException> {
                ctx.transition(ThreadState.GREETING, 2)
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-014: CAS succeeds on matching revision")
    inner class CasMatchSuccess {
        @Test
        fun `matching revision succeeds`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.NEW, revision = 1)
            val result = ctx.transition(ThreadState.GREETING, 1)
            assertThat(result.context.state).isEqualTo(ThreadState.GREETING)
            assertThat(result.context.revision).isEqualTo(2)
        }
    }

    // ── Immutability ─────────────────────────────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-015: personaId is immutable")
    inner class PersonaImmutable {
        @Test
        fun `personaId is a val property`() {
            val ctx = TestUtils.makeThreadContext()
            assertThat(ctx.personaId).isEqualTo("persona-anita")
            // copy() preserves personaId, can only change explicitly
            val copied = ctx.copy(state = ThreadState.GREETING)
            assertThat(copied.personaId).isEqualTo("persona-anita")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-016: clientPhone is non-blank")
    inner class ClientPhoneValidation {
        @Test
        fun `blank clientPhone rejected`() {
            assertThrows<IllegalArgumentException> {
                TestUtils.makeThreadContext(clientPhone = "")
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-017: metadata is flexible JSON blob")
    inner class MetadataTest {
        @Test
        fun `arbitrary metadata round-trips`() {
            val meta = buildJsonObject {
                put("source", "sms")
                put("priority", 5)
                put("tags", kotlinx.serialization.json.buildJsonArray { add("vip"); add("repeat") })
            }
            val ctx = TestUtils.makeThreadContext().copy(metadata = meta)
            val jsonStr = json.encodeToString(ThreadContext.serializer(), ctx)
            val decoded = json.decodeFromString(ThreadContext.serializer(), jsonStr)
            assertThat(decoded.metadata).isEqualTo(meta)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-018: state field is ThreadState sealed type")
    inner class StateTypeSafety {
        @Test
        fun `state is ThreadState instance`() {
            val ctx = TestUtils.makeThreadContext()
            assertThat(ctx.state).isInstanceOf(ThreadState::class.java)
        }
    }

    // ── Advanced CAS edge cases ──────────────────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-173: atomic state+revision transition")
    inner class AtomicTransition {
        @Test
        fun `success updates both state and revision`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.NEW, revision = 1)
            val result = ctx.transition(ThreadState.GREETING, 1)
            assertThat(result.context.state).isEqualTo(ThreadState.GREETING)
            assertThat(result.context.revision).isEqualTo(2)
        }

        @Test
        fun `failure changes neither state nor revision`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.NEW, revision = 1)
            val result = ctx.tryTransition(ThreadState.CONVERSING, 1) // invalid transition
            assertThat(result).isNull()
            assertThat(ctx.state).isEqualTo(ThreadState.NEW)
            assertThat(ctx.revision).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-175: AI_CHALLENGED preserves and restores previous state")
    inner class AiChallengeRestoration {
        @Test
        fun `previous state preserved and restored`() {
            // CONVERSING → AI_CHALLENGED → CONVERSING
            val ctx = TestUtils.makeThreadContext(state = ThreadState.CONVERSING, revision = 1)
            val challenged = ctx.transition(ThreadState.AI_CHALLENGED, 1)
            assertThat(challenged.context.state).isEqualTo(ThreadState.AI_CHALLENGED)
            assertThat(challenged.context.previousState).isEqualTo(ThreadState.CONVERSING)

            val restored = challenged.context.transition(ThreadState.CONVERSING, 2)
            assertThat(restored.context.state).isEqualTo(ThreadState.CONVERSING)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-176: every transition produces audit entry")
    inner class AuditEntry {
        @Test
        fun `transition produces TransitionRecord`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.GREETING, revision = 1)
            val result = ctx.transition(ThreadState.CONVERSING, 1, actor = "owner")
            assertThat(result.record).isNotNull
            assertThat(result.record.fromState).isEqualTo(ThreadState.GREETING)
            assertThat(result.record.toState).isEqualTo(ThreadState.CONVERSING)
            assertThat(result.record.actor).isEqualTo("owner")
            assertThat(result.record.revisionFrom).isEqualTo(1)
            assertThat(result.record.revisionTo).isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-190: id is non-blank")
    inner class IdValidation {
        @Test
        fun `blank id rejected`() {
            assertThrows<IllegalArgumentException> {
                TestUtils.makeThreadContext(id = "")
            }
        }

        @Test
        fun `whitespace-only id rejected`() {
            assertThrows<IllegalArgumentException> {
                TestUtils.makeThreadContext(id = "  ")
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-196: CAS edge cases")
    inner class CasEdgeCases {
        @Test
        fun `revision 0 fails`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.NEW, revision = 1)
            assertThrows<IllegalArgumentException> {
                ctx.transition(ThreadState.GREETING, 0)
            }
        }

        @Test
        fun `negative revision fails`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.NEW, revision = 1)
            assertThrows<IllegalArgumentException> {
                ctx.transition(ThreadState.GREETING, -1)
            }
        }

        @Test
        fun `future revision fails`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.NEW, revision = 1)
            assertThrows<IllegalArgumentException> {
                ctx.transition(ThreadState.GREETING, 99)
            }
        }

        @Test
        fun `stale revision from several transitions ago fails`() {
            var ctx = TestUtils.makeThreadContext(state = ThreadState.NEW, revision = 1)
            ctx = ctx.transition(ThreadState.GREETING, 1).context // rev 2
            ctx = ctx.transition(ThreadState.CONVERSING, 2).context // rev 3
            ctx = ctx.transition(ThreadState.DEPOSIT_REQUESTED, 3).context // rev 4
            // Try with stale revision 1
            assertThrows<IllegalArgumentException> {
                ctx.transition(ThreadState.DEPOSIT_PENDING, 1)
            }
        }

        @Test
        fun `concurrent CAS - one succeeds, one fails`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.NEW, revision = 1)
            // First transition succeeds, consuming revision 1 -> new context has revision 2
            val result1 = ctx.transition(ThreadState.GREETING, 1)
            assertThat(result1).isNotNull
            // Second transition on the NEW context with stale revision 1 fails
            val updatedCtx = result1.context
            val result2 = updatedCtx.tryTransition(ThreadState.STALLED, 1)
            assertThat(result2).isNull()
            // Second transition with correct revision 2 succeeds
            val result3 = updatedCtx.tryTransition(ThreadState.STALLED, 2)
            assertThat(result3).isNotNull
            // Original context unchanged
            assertThat(ctx.revision).isEqualTo(1)
        }
    }

    // ── Schema field match ───────────────────────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-159: thread schema matches Kotlin type")
    inner class SchemaMatch {
        @Test
        fun `thread schema fields match ThreadContext properties`() {
            val schemaFields = Schemas.schemaFields("thread")
            assertThat(schemaFields).containsExactlyInAnyOrder(
                "id", "state", "revision", "personaId", "clientPhone",
                "previousState", "createdAt", "updatedAt", "lastMessageAt", "metadata",
            )
        }
    }
}
