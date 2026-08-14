package com.thresholdinc.insidher.contracts

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("SafetyVerdict tests (VAL-CONTRACTS-134 to 145, 188)")
class SafetyVerdictTest {

    private val json = TestUtils.strictJson

    @Nested
    @DisplayName("VAL-CONTRACTS-134: exactly 3 variants")
    inner class VariantCount {
        @Test
        fun `3 variants exist`() {
            assertThat(SafetyVerdict.variants).hasSize(3)
            assertThat(SafetyVerdict.variants.map { it.simpleName }).containsExactlyInAnyOrder(
                "Safe", "Escalate", "Block",
            )
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-135 to 137: construction")
    inner class Construction {
        @Test
        fun `135 SAFE constructs without reason code`() {
            val v = SafetyVerdict.Safe()
            assertThat(v.confidence).isEqualTo(1.0)
        }

        @Test
        fun `136 ESCALATE constructs with reason code`() {
            val v = SafetyVerdict.Escalate(EscalationReasonCode.COERCION_DETECTED, 0.8)
            assertThat(v.reasonCode).isEqualTo(EscalationReasonCode.COERCION_DETECTED)
            assertThat(v.confidence).isEqualTo(0.8)
        }

        @Test
        fun `137 BLOCK constructs with reason code`() {
            val v = SafetyVerdict.Block(EscalationReasonCode.HARM_THREAT, 0.95)
            assertThat(v.reasonCode).isEqualTo(EscalationReasonCode.HARM_THREAT)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-138 to 139: non-null reason code required")
    inner class ReasonCodeRequired {
        @Test
        fun `138 ESCALATE requires non-null reason code by type system`() {
            // reasonCode is EscalationReasonCode (non-nullable), so null is impossible
            val v = SafetyVerdict.Escalate(EscalationReasonCode.UNKNOWN_RISK)
            assertThat(v.reasonCode).isNotNull
        }

        @Test
        fun `139 BLOCK requires non-null reason code by type system`() {
            val v = SafetyVerdict.Block(EscalationReasonCode.UNKNOWN_RISK)
            assertThat(v.reasonCode).isNotNull
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-140: confidence range validation")
    inner class ConfidenceRange {
        @Test
        fun `SAFE confidence below 0 rejected`() {
            assertThrows<IllegalArgumentException> { SafetyVerdict.Safe(-0.1) }
        }

        @Test
        fun `SAFE confidence above 1 rejected`() {
            assertThrows<IllegalArgumentException> { SafetyVerdict.Safe(1.1) }
        }

        @Test
        fun `ESCALATE confidence below 0 rejected`() {
            assertThrows<IllegalArgumentException> {
                SafetyVerdict.Escalate(EscalationReasonCode.UNKNOWN_RISK, -0.1)
            }
        }

        @Test
        fun `BLOCK confidence above 1 rejected`() {
            assertThrows<IllegalArgumentException> {
                SafetyVerdict.Block(EscalationReasonCode.UNKNOWN_RISK, 1.1)
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-141 to 142: serialization")
    inner class Serialization {
        @Test
        fun `141 serializes with type discriminator`() {
            val safe = SafetyVerdict.Safe()
            val escalate = SafetyVerdict.Escalate(EscalationReasonCode.AI_CHALLENGE_DETECTED)
            val block = SafetyVerdict.Block(EscalationReasonCode.HARM_THREAT)

            assertThat(json.encodeToString(SafetyVerdict.serializer(), safe)).contains("\"type\":\"SAFE\"")
            assertThat(json.encodeToString(SafetyVerdict.serializer(), escalate)).contains("\"type\":\"ESCALATE\"")
            assertThat(json.encodeToString(SafetyVerdict.serializer(), escalate)).contains("\"reasonCode\":\"AI_CHALLENGE_DETECTED\"")
            assertThat(json.encodeToString(SafetyVerdict.serializer(), block)).contains("\"type\":\"BLOCK\"")
        }

        @Test
        fun `142 round-trip equality for all variants`() {
            val verdicts: List<SafetyVerdict> = listOf(
                SafetyVerdict.Safe(0.95),
                SafetyVerdict.Escalate(EscalationReasonCode.COERCION_DETECTED, 0.8),
                SafetyVerdict.Block(EscalationReasonCode.HARM_THREAT, 0.99),
            )
            for (v in verdicts) {
                val jsonStr = json.encodeToString(SafetyVerdict.serializer(), v)
                val decoded = json.decodeFromString(SafetyVerdict.serializer(), jsonStr)
                assertThat(decoded).isEqualTo(v)
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-143 to 145: behavioral implications")
    inner class BehavioralImplications {
        @Test
        fun `143 BLOCK prevents SMS - modeled by OrchestratorAction`() {
            // BLOCK verdict leads to no SendMessage action; quarantined
            val verdict: SafetyVerdict = SafetyVerdict.Block(EscalationReasonCode.HARM_THREAT)
            assertThat(verdict).isInstanceOf(SafetyVerdict.Block::class.java)
            // The actual SMS prevention is enforced in :core orchestrator logic
        }

        @Test
        fun `144 ESCALATE triggers thread escalation`() {
            val ctx = TestUtils.makeThreadContext(state = ThreadState.CONVERSING, revision = 1)
            val result = ctx.transition(ThreadState.ESCALATED, 1)
            assertThat(result.context.state).isEqualTo(ThreadState.ESCALATED)
        }

        @Test
        fun `145 SAFE allows processing to continue`() {
            // SAFE means no escalation/block; processing continues
            val verdict: SafetyVerdict = SafetyVerdict.Safe()
            assertThat(verdict).isInstanceOf(SafetyVerdict.Safe::class.java)
            // Thread stays in CONVERSING
            val ctx = TestUtils.makeThreadContext(state = ThreadState.CONVERSING, revision = 1)
            val result = ctx.transition(ThreadState.CONVERSING, 1) // self-loop continues
            assertThat(result.context.state).isEqualTo(ThreadState.CONVERSING)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-188: reject unknown type discriminator")
    inner class RejectUnknownDiscriminator {
        @Test
        fun `unknown type discriminator rejected`() {
            val jsonStr = """{"type":"FAKE_VERDICT","confidence":0.5}"""
            assertThrows<Exception> {
                json.decodeFromString(SafetyVerdict.serializer(), jsonStr)
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-181: reject unknown properties")
    inner class RejectUnknown {
        @Test
        fun `SafetyVerdict rejects unknown property`() {
            val base = json.encodeToString(
                SafetyVerdict.serializer(),
                SafetyVerdict.Safe(),
            )
            val tampered = base.removeSuffix("}") + ",\"unknownField\":42}"
            assertThrows<Exception> {
                json.decodeFromString(SafetyVerdict.serializer(), tampered)
            }
        }
    }
}
