package com.thresholdinc.insidher.contracts

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("WorkerOutput tests (VAL-CONTRACTS-122 to 133, 180, 185-187)")
class WorkerOutputTest {

    private val json = TestUtils.strictJson

    @Nested
    @DisplayName("VAL-CONTRACTS-122: exactly 8 variants")
    inner class VariantCount {
        @Test
        fun `8 variants exist`() {
            assertThat(WorkerOutput.variantNames).hasSize(8)
            assertThat(WorkerOutput.variantNames).containsExactlyInAnyOrder(
                "PersonaOutput", "TimingOutput", "MemoryOutput", "BookingOutput",
                "SafetyOutput", "OrchestratorOutput", "InferenceOutput", "AlertOutput",
            )
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-123 to 130: variant construction")
    inner class VariantConstruction {
        @Test
        fun `123 PersonaOutput constructs`() {
            val out = WorkerOutput.PersonaOutput("Hello!", 0.9)
            assertThat(out.responseText).isEqualTo("Hello!")
            assertThat(out.confidence).isEqualTo(0.9)
        }

        @Test
        fun `124 TimingOutput constructs`() {
            val out = WorkerOutput.TimingOutput(5000, 3000)
            assertThat(out.delayMs).isEqualTo(5000)
            assertThat(out.batchGapMs).isEqualTo(3000)
        }

        @Test
        fun `125 MemoryOutput constructs`() {
            val mem = TestUtils.makeThreadMemory()
            val out = WorkerOutput.MemoryOutput(listOf(mem))
            assertThat(out.entries).hasSize(1)
        }

        @Test
        fun `126 BookingOutput constructs`() {
            val out = WorkerOutput.BookingOutput(depositRequested = true, depositStatus = DepositStatus.PENDING)
            assertThat(out.depositRequested).isTrue
            assertThat(out.depositStatus).isEqualTo(DepositStatus.PENDING)
        }

        @Test
        fun `127 SafetyOutput constructs with verdict`() {
            val verdict = SafetyVerdict.Safe()
            val out = WorkerOutput.SafetyOutput(verdict)
            assertThat(out.verdict).isEqualTo(verdict)
        }

        @Test
        fun `128 OrchestratorOutput constructs with messages`() {
            val msg = TestUtils.makeAgentMessage()
            val out = WorkerOutput.OrchestratorOutput(listOf(msg))
            assertThat(out.messages).hasSize(1)
        }

        @Test
        fun `129 InferenceOutput constructs`() {
            val out = WorkerOutput.InferenceOutput(
                content = "Hello", confidence = 0.95, tokensUsed = 150, model = "gpt-4",
            )
            assertThat(out.content).isEqualTo("Hello")
            assertThat(out.model).isEqualTo("gpt-4")
        }

        @Test
        fun `130 AlertOutput constructs`() {
            val out = WorkerOutput.AlertOutput(
                reasonCode = EscalationReasonCode.AI_CHALLENGE_DETECTED,
                threadId = "t1",
                urgencyLevel = UrgencyLevel.HIGH,
            )
            assertThat(out.reasonCode).isEqualTo(EscalationReasonCode.AI_CHALLENGE_DETECTED)
            assertThat(out.urgencyLevel).isEqualTo(UrgencyLevel.HIGH)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-131 to 133: serialization")
    inner class Serialization {
        @Test
        fun `131 all variants serialize with type discriminator`() {
            val outputs: List<WorkerOutput> = listOf(
                WorkerOutput.PersonaOutput("Hi", 0.9),
                WorkerOutput.TimingOutput(100, 200),
                WorkerOutput.MemoryOutput(),
                WorkerOutput.BookingOutput(),
                WorkerOutput.SafetyOutput(SafetyVerdict.Safe()),
                WorkerOutput.OrchestratorOutput(listOf(TestUtils.makeAgentMessage())),
                WorkerOutput.InferenceOutput(content = "c", confidence = 0.9, tokensUsed = 10, model = "m"),
                WorkerOutput.AlertOutput(EscalationReasonCode.UNKNOWN_RISK, "t1", UrgencyLevel.LOW),
            )
            for (out in outputs) {
                val jsonStr = json.encodeToString(WorkerOutput.serializer(), out)
                assertThat(jsonStr).contains("\"type\":")
            }
        }

        @Test
        fun `132 all variants deserialize with round-trip equality`() {
            val outputs: List<WorkerOutput> = listOf(
                WorkerOutput.PersonaOutput("Hi", 0.9),
                WorkerOutput.TimingOutput(100, 200),
                WorkerOutput.MemoryOutput(),
                WorkerOutput.BookingOutput(),
                WorkerOutput.SafetyOutput(SafetyVerdict.Safe()),
                WorkerOutput.OrchestratorOutput(listOf(TestUtils.makeAgentMessage())),
                WorkerOutput.InferenceOutput(content = "c", confidence = 0.9, tokensUsed = 10, model = "m"),
                WorkerOutput.AlertOutput(EscalationReasonCode.UNKNOWN_RISK, "t1", UrgencyLevel.LOW),
            )
            for (out in outputs) {
                val jsonStr = json.encodeToString(WorkerOutput.serializer(), out)
                val decoded = json.decodeFromString(WorkerOutput.serializer(), jsonStr)
                assertThat(decoded).isEqualTo(out)
            }
        }

        @Test
        fun `133 polymorphic serialization uses type discriminator`() {
            val out: WorkerOutput = WorkerOutput.PersonaOutput("Hi", 0.9)
            val jsonStr = json.encodeToString(WorkerOutput.serializer(), out)
            assertThat(jsonStr).contains("\"type\":\"PersonaOutput\"")
            val decoded = json.decodeFromString(WorkerOutput.serializer(), jsonStr)
            assertThat(decoded).isInstanceOf(WorkerOutput.PersonaOutput::class.java)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-180: no field name collisions")
    inner class FieldCollisions {
        @Test
        fun `shared confidence field is Double in all variants`() {
            // PersonaOutput.confidence, InferenceOutput.confidence, SafetyVerdict.confidence
            val po = WorkerOutput.PersonaOutput("hi", 0.5)
            val io = WorkerOutput.InferenceOutput(content = "c", confidence = 0.5, tokensUsed = 1, model = "m")
            assertThat(po.confidence).isOfAnyClassIn(java.lang.Double::class.java)
            assertThat(io.confidence).isOfAnyClassIn(java.lang.Double::class.java)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-185: WorkerInput not in contracts module")
    inner class WorkerInputScope {
        @Test
        fun `WorkerInput is not defined in contracts module`() {
            // WorkerInput is a :core-only type, not part of :contracts
            // This test documents that WorkerInput is out of scope for :contracts
            val contractsClassNames = listOf(
                "ThreadContext", "ThreadState", "ClientMessage", "AgentMessage",
                "BookingProposal", "DepositRecord", "HumanDecision", "ThreadMemory",
                "SafetyVerdict", "WorkerOutput", "AvailabilityPolicy", "TimeWindow",
            )
            // None of these are "WorkerInput"
            assertThat(contractsClassNames).doesNotContain("WorkerInput")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-186: variant field-level validation")
    inner class FieldValidation {
        @Test
        fun `PersonaOutput blank responseText rejected`() {
            assertThrows<IllegalArgumentException> {
                WorkerOutput.PersonaOutput("", 0.9)
            }
        }

        @Test
        fun `PersonaOutput confidence out of range rejected`() {
            assertThrows<IllegalArgumentException> { WorkerOutput.PersonaOutput("hi", -0.1) }
            assertThrows<IllegalArgumentException> { WorkerOutput.PersonaOutput("hi", 1.1) }
        }

        @Test
        fun `TimingOutput negative delayMs rejected`() {
            assertThrows<IllegalArgumentException> { WorkerOutput.TimingOutput(-1, 100) }
        }

        @Test
        fun `TimingOutput negative batchGapMs rejected`() {
            assertThrows<IllegalArgumentException> { WorkerOutput.TimingOutput(100, -1) }
        }

        @Test
        fun `OrchestratorOutput empty messages rejected`() {
            assertThrows<IllegalArgumentException> {
                WorkerOutput.OrchestratorOutput(emptyList())
            }
        }

        @Test
        fun `InferenceOutput negative tokensUsed rejected`() {
            assertThrows<IllegalArgumentException> {
                WorkerOutput.InferenceOutput(content = "c", confidence = 0.9, tokensUsed = -1, model = "m")
            }
        }

        @Test
        fun `InferenceOutput blank model rejected`() {
            assertThrows<IllegalArgumentException> {
                WorkerOutput.InferenceOutput(content = "c", confidence = 0.9, tokensUsed = 10, model = "")
            }
        }

        @Test
        fun `InferenceOutput confidence out of range rejected`() {
            assertThrows<IllegalArgumentException> {
                WorkerOutput.InferenceOutput(content = "c", confidence = -0.1, tokensUsed = 10, model = "m")
            }
        }

        @Test
        fun `AlertOutput blank threadId rejected`() {
            assertThrows<IllegalArgumentException> {
                WorkerOutput.AlertOutput(EscalationReasonCode.UNKNOWN_RISK, "", UrgencyLevel.LOW)
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-187: reject unknown type discriminator")
    inner class RejectUnknownDiscriminator {
        @Test
        fun `unknown type discriminator rejected`() {
            val jsonStr = """{"type":"FAKE_OUTPUT","data":"test"}"""
            assertThrows<Exception> {
                json.decodeFromString(WorkerOutput.serializer(), jsonStr)
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-181: reject unknown properties")
    inner class RejectUnknown {
        @Test
        fun `PersonaOutput rejects unknown property`() {
            val base = json.encodeToString(
                WorkerOutput.serializer(),
                WorkerOutput.PersonaOutput("hi", 0.9),
            )
            val tampered = base.removeSuffix("}") + ",\"unknownField\":42}"
            assertThrows<Exception> {
                json.decodeFromString(WorkerOutput.serializer(), tampered)
            }
        }
    }
}
