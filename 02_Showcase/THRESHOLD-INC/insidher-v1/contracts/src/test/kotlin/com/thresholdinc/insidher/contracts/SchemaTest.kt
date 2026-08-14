package com.thresholdinc.insidher.contracts

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("JSON Schema tests (VAL-CONTRACTS-008, 158, 162, 163, 167, 168)")
class SchemaTest {

    private val json = TestUtils.strictJson

    @Nested
    @DisplayName("VAL-CONTRACTS-008: persona schema enforces additionalProperties false")
    inner class PersonaSchemaAdditional {
        @Test
        fun `persona schema file has additionalProperties false`() {
            val schema = Schemas.personaProfile
            assertThat(schema).contains("\"additionalProperties\": false")
        }

        @Test
        fun `persona schema rejects extra top-level property conceptually`() {
            // This is enforced at deserialization level (VAL-CONTRACTS-003)
            // and schema level by additionalProperties: false
            assertThat(Schemas.personaProfile).contains("\"additionalProperties\": false")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-162: all schemas enforce additionalProperties false")
    inner class AllSchemasAdditional {
        @Test
        fun `all schema strings contain additionalProperties false`() {
            for ((name, schema) in Schemas.all) {
                assertThat(schema)
                    .describedAs("Schema '$name' must contain additionalProperties: false")
                    .contains("\"additionalProperties\": false")
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-158: persona schema matches Kotlin type")
    inner class PersonaSchemaMatch {
        @Test
        fun `persona schema fields match Kotlin properties`() {
            val schemaFields = Schemas.schemaFields("persona")
            assertThat(schemaFields).containsExactlyInAnyOrder(
                "name", "tone", "vocabulary", "offerings",
                "depositWording", "boundaries", "availabilityPolicy",
            )
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-163: schema field types match Kotlin types")
    inner class SchemaTypeMatch {
        @Test
        fun `persona schema has string fields for name and tone`() {
            val schema = Schemas.personaProfile
            assertThat(schema).contains("\"name\": { \"type\": \"string\"")
            assertThat(schema).contains("\"tone\": { \"type\": \"string\"")
        }

        @Test
        fun `message schema has correct field types`() {
            val schema = Schemas.message
            assertThat(schema).contains("\"threadId\": { \"type\": \"string\"")
            assertThat(schema).contains("\"body\": { \"type\": \"string\"")
            assertThat(schema).contains("\"confidence\": { \"type\": \"number\"")
        }

        @Test
        fun `thread schema has integer revision`() {
            val schema = Schemas.threadContext
            assertThat(schema).contains("\"revision\": { \"type\": \"integer\"")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-167: schema files exist for all key types")
    inner class SchemaFilesExist {
        @Test
        fun `Schemas object defines schemas for all contract types`() {
            assertThat(Schemas.all.keys).contains(
                "persona", "thread", "message", "booking",
                "bookingProposal", "depositRecord", "timeWindow", "availabilityPolicy",
            )
        }

        @Test
        fun `schemaFields returns non-null for all known schemas`() {
            for (key in Schemas.all.keys) {
                assertThat(Schemas.schemaFields(key))
                    .describedAs("schemaFields for '$key' should not be null")
                    .isNotNull
            }
        }

        @Test
        fun `schemaFields returns null for unknown schema`() {
            assertThat(Schemas.schemaFields("nonexistent")).isNull()
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-168: round-trip for all contract types")
    inner class RoundTripAllTypes {
        @Test
        fun `PersonaProfile round-trips`() {
            val obj = TestUtils.makePersonaProfile()
            val jsonStr = json.encodeToString(PersonaProfile.serializer(), obj)
            val decoded = json.decodeFromString(PersonaProfile.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(obj)
        }

        @Test
        fun `ThreadContext round-trips`() {
            val obj = TestUtils.makeThreadContext()
            val jsonStr = json.encodeToString(ThreadContext.serializer(), obj)
            val decoded = json.decodeFromString(ThreadContext.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(obj)
        }

        @Test
        fun `ClientMessage round-trips`() {
            val obj = TestUtils.makeClientMessage()
            val jsonStr = json.encodeToString(ClientMessage.serializer(), obj)
            val decoded = json.decodeFromString(ClientMessage.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(obj)
        }

        @Test
        fun `AgentMessage round-trips`() {
            val obj = TestUtils.makeAgentMessage()
            val jsonStr = json.encodeToString(AgentMessage.serializer(), obj)
            val decoded = json.decodeFromString(AgentMessage.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(obj)
        }

        @Test
        fun `BookingProposal round-trips`() {
            val obj = TestUtils.makeBookingProposal()
            val jsonStr = json.encodeToString(BookingProposal.serializer(), obj)
            val decoded = json.decodeFromString(BookingProposal.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(obj)
        }

        @Test
        fun `DepositRecord round-trips`() {
            val obj = TestUtils.makeDepositRecord()
            val jsonStr = json.encodeToString(DepositRecord.serializer(), obj)
            val decoded = json.decodeFromString(DepositRecord.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(obj)
        }

        @Test
        fun `HumanDecision round-trips`() {
            val obj: HumanDecision = HumanDecision.Approve(TestUtils.fixedInstant, "ok")
            val jsonStr = json.encodeToString(HumanDecision.serializer(), obj)
            val decoded = json.decodeFromString(HumanDecision.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(obj)
        }

        @Test
        fun `ThreadMemory round-trips`() {
            val obj = TestUtils.makeThreadMemory()
            val jsonStr = json.encodeToString(ThreadMemory.serializer(), obj)
            val decoded = json.decodeFromString(ThreadMemory.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(obj)
        }

        @Test
        fun `SafetyVerdict round-trips`() {
            val obj: SafetyVerdict = SafetyVerdict.Escalate(EscalationReasonCode.COERCION_DETECTED, 0.8)
            val jsonStr = json.encodeToString(SafetyVerdict.serializer(), obj)
            val decoded = json.decodeFromString(SafetyVerdict.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(obj)
        }

        @Test
        fun `WorkerOutput round-trips`() {
            val obj: WorkerOutput = WorkerOutput.PersonaOutput("Hi", 0.9)
            val jsonStr = json.encodeToString(WorkerOutput.serializer(), obj)
            val decoded = json.decodeFromString(WorkerOutput.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(obj)
        }
    }
}
