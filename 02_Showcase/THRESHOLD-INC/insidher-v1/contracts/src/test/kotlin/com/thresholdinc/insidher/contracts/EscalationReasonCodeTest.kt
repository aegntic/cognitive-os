package com.thresholdinc.insidher.contracts

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@DisplayName("EscalationReasonCode tests (VAL-CONTRACTS-104 to 112, 166, 178)")
class EscalationReasonCodeTest {

    private val json = TestUtils.strictJson

    @Nested
    @DisplayName("VAL-CONTRACTS-104: exactly 19 values")
    inner class ValueCount {
        @Test
        fun `exactly 19 enum values`() {
            assertThat(EscalationReasonCode.entries).hasSize(19)
        }

        @Test
        fun `all 19 names present`() {
            val names = EscalationReasonCode.entries.map { it.name }
            assertThat(names).containsExactlyInAnyOrder(
                "AI_CHALLENGE_DETECTED",
                "COERCION_DETECTED",
                "EXPLOITATION_RISK",
                "MINOR_SAFETY_RISK",
                "ILLEGAL_SERVICE_REQUEST",
                "HARM_THREAT",
                "PROMPT_INJECTION_DETECTED",
                "JAILBREAK_ATTEMPT",
                "PERSONA_DEVIATION",
                "EXCESSIVE_PERSISTENCE",
                "DEPOSIT_DISPUTE",
                "OFF_TOPIC_EXTENDED",
                "EMOTIONAL_DISTRESS",
                "LANGUAGE_UNCLEAR",
                "RATE_LIMIT_HIT",
                "SERVICE_UNAVAILABLE",
                "SCHEDULING_CONFLICT",
                "BOUNDARY_VIOLATION",
                "UNKNOWN_RISK",
            )
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-105 to 109: specific values")
    inner class SpecificValues {
        @Test
        fun `105 AI_CHALLENGE_DETECTED has correct description`() {
            assertThat(EscalationReasonCode.AI_CHALLENGE_DETECTED.description).isEqualTo("Client asks if agent is AI")
        }

        @Test
        fun `106 COERCION_DETECTED has correct description`() {
            assertThat(EscalationReasonCode.COERCION_DETECTED.description).isEqualTo("Signs of coercion or pressure")
        }

        @Test
        fun `107 MINOR_SAFETY_RISK has correct description`() {
            assertThat(EscalationReasonCode.MINOR_SAFETY_RISK.description).isEqualTo("Possible minor involvement")
        }

        @Test
        fun `108 PROMPT_INJECTION_DETECTED has correct description`() {
            assertThat(EscalationReasonCode.PROMPT_INJECTION_DETECTED.description).isEqualTo("Attempt to override system prompt")
        }

        @Test
        fun `109 UNKNOWN_RISK has correct description`() {
            assertThat(EscalationReasonCode.UNKNOWN_RISK.description).isEqualTo("Unrecognized risk pattern")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-110 to 112: serialization")
    inner class Serialization {

        @ParameterizedTest(name = "110 {0} serializes")
        @MethodSource("com.thresholdinc.insidher.contracts.EscalationReasonCodeTest#allCodes")
        fun `110 serializes to enum name string`(code: EscalationReasonCode) {
            val jsonStr = json.encodeToString(EscalationReasonCode.serializer(), code)
            assertThat(jsonStr).isEqualTo("\"${code.name}\"")
        }

        @ParameterizedTest(name = "111 \"{0}\" deserializes")
        @MethodSource("com.thresholdinc.insidher.contracts.EscalationReasonCodeTest#allCodes")
        fun `111 deserializes from enum name string`(code: EscalationReasonCode) {
            val jsonStr = "\"${code.name}\""
            val decoded = json.decodeFromString(EscalationReasonCode.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(code)
        }

        @Test
        fun `112 rejects unknown string`() {
            assertThrows<Exception> {
                json.decodeFromString(EscalationReasonCode.serializer(), "\"FAKE_CODE\"")
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-166: schema enum matches 19 names")
    inner class SchemaEnumMatch {
        @Test
        fun `schema escalation enum matches all 19 codes`() {
            val schemaEnum = Schemas.escalationReasonCodeEnum
            assertThat(schemaEnum).containsExactlyInAnyOrderElementsOf(
                EscalationReasonCode.entries.map { it.name },
            )
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-178: UNKNOWN_RISK is fail-closed default")
    inner class FailClosedDefault {
        @Test
        fun `SafetyVerdict unknownRisk uses UNKNOWN_RISK`() {
            val verdict = SafetyVerdict.unknownRisk()
            assertThat(verdict.reasonCode).isEqualTo(EscalationReasonCode.UNKNOWN_RISK)
        }

        @Test
        fun `UNKNOWN_RISK accessible from enum`() {
            assertThat(EscalationReasonCode.UNKNOWN_RISK).isNotNull
            assertThat(EscalationReasonCode.UNKNOWN_RISK.description).contains("Unrecognized")
        }
    }

    companion object {
        @JvmStatic
        fun allCodes(): Stream<EscalationReasonCode> = EscalationReasonCode.entries.stream()
    }
}
