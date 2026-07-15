package com.thresholdinc.insidher.contracts

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("PersonaProfile contract tests (VAL-CONTRACTS-001 to 008, 158, 179, 198)")
class PersonaTest {

    private val json = TestUtils.strictJson

    @Nested
    @DisplayName("VAL-CONTRACTS-001: PersonaProfile serializes to valid JSON")
    inner class SerializeTest {
        @Test
        fun `serializes to JSON with all fields`() {
            val persona = TestUtils.makePersonaProfile()
            val jsonStr = json.encodeToString(PersonaProfile.serializer(), persona)
            assertThat(jsonStr).contains("\"name\":\"Anita\"")
            assertThat(jsonStr).contains("\"tone\":\"warm and professional\"")
            assertThat(jsonStr).contains("\"vocabulary\"")
            assertThat(jsonStr).contains("\"offerings\"")
            assertThat(jsonStr).contains("\"depositWording\"")
            assertThat(jsonStr).contains("\"boundaries\"")
            assertThat(jsonStr).contains("\"availabilityPolicy\"")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-002: PersonaProfile deserializes from valid JSON")
    inner class DeserializeTest {
        @Test
        fun `round-trip equality holds`() {
            val persona = TestUtils.makePersonaProfile()
            val jsonStr = json.encodeToString(PersonaProfile.serializer(), persona)
            val decoded = json.decodeFromString(PersonaProfile.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(persona)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-003: rejects unknown properties")
    inner class RejectUnknownTest {
        @Test
        fun `rejects extra property on deserialization`() {
            val jsonStr = """{"name":"Anita","tone":"warm","vocabulary":[],"offerings":[],"availabilityPolicy":{"timezone":"Australia/Sydney","weeklyWindows":{"MONDAY":{"start":"08:00","end":"22:00"},"TUESDAY":{"start":"08:00","end":"22:00"},"WEDNESDAY":{"start":"08:00","end":"22:00"},"THURSDAY":{"start":"08:00","end":"22:00"},"FRIDAY":{"start":"08:00","end":"22:00"},"SATURDAY":{"start":"08:00","end":"22:00"},"SUNDAY":{"start":"08:00","end":"22:00"}},"dndPeriods":[],"dateOverrides":{}},"extraField":"value"}"""
            assertThrows<Exception> {
                json.decodeFromString(PersonaProfile.serializer(), jsonStr)
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-004: handles empty optional fields")
    inner class EmptyOptionalsTest {
        @Test
        fun `empty vocabulary, offerings, null depositWording round-trips`() {
            val persona = PersonaProfile(
                name = "Test",
                tone = "neutral",
                vocabulary = emptyList(),
                offerings = emptyList(),
                depositWording = null,
                boundaries = null,
            )
            val jsonStr = json.encodeToString(PersonaProfile.serializer(), persona)
            val decoded = json.decodeFromString(PersonaProfile.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(persona)
            assertThat(decoded.vocabulary).isEmpty()
            assertThat(decoded.offerings).isEmpty()
            assertThat(decoded.depositWording).isNull()
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-005: name is non-blank")
    inner class NameValidationTest {
        @Test
        fun `blank name rejected`() {
            assertThrows<IllegalArgumentException> {
                PersonaProfile(name = "", tone = "warm")
            }
        }

        @Test
        fun `whitespace-only name rejected`() {
            assertThrows<IllegalArgumentException> {
                PersonaProfile(name = "   ", tone = "warm")
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-006: tone is non-blank")
    inner class ToneValidationTest {
        @Test
        fun `blank tone rejected`() {
            assertThrows<IllegalArgumentException> {
                PersonaProfile(name = "Anita", tone = "")
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-007: embeds AvailabilityPolicy correctly")
    inner class AvailabilityPolicyTest {
        @Test
        fun `nested availabilityPolicy round-trips`() {
            val policy = AvailabilityPolicy(
                timezone = "America/New_York",
                weeklyWindows = AvailabilityPolicy.defaultWeeklyWindows(),
                dndPeriods = listOf(TimeWindow(kotlinx.datetime.LocalTime(12, 0), kotlinx.datetime.LocalTime(13, 0))),
            )
            val persona = PersonaProfile(
                name = "Test",
                tone = "warm",
                availabilityPolicy = policy,
            )
            val jsonStr = json.encodeToString(PersonaProfile.serializer(), persona)
            val decoded = json.decodeFromString(PersonaProfile.serializer(), jsonStr)
            assertThat(decoded.availabilityPolicy).isEqualTo(policy)
            assertThat(decoded.availabilityPolicy.timezone).isEqualTo("America/New_York")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-179: boundaries handling")
    inner class BoundariesTest {
        @Test
        fun `null boundaries allowed`() {
            val persona = PersonaProfile(name = "T", tone = "t", boundaries = null)
            assertThat(persona.boundaries).isNull()
        }

        @Test
        fun `empty boundaries allowed`() {
            val persona = PersonaProfile(name = "T", tone = "t", boundaries = emptyList())
            assertThat(persona.boundaries).isEmpty()
        }

        @Test
        fun `non-empty boundaries preserved`() {
            val persona = PersonaProfile(name = "T", tone = "t", boundaries = listOf("No refunds"))
            assertThat(persona.boundaries).containsExactly("No refunds")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-198: vocabulary and offerings list validation")
    inner class ListValidationTest {
        @Test
        fun `blank vocabulary entry rejected`() {
            assertThrows<IllegalArgumentException> {
                PersonaProfile(name = "T", tone = "t", vocabulary = listOf("good", ""))
            }
        }

        @Test
        fun `blank offerings entry rejected`() {
            assertThrows<IllegalArgumentException> {
                PersonaProfile(name = "T", tone = "t", offerings = listOf("Service", ""))
            }
        }

        @Test
        fun `blank depositWording rejected when present`() {
            assertThrows<IllegalArgumentException> {
                PersonaProfile(name = "T", tone = "t", depositWording = "")
            }
        }

        @Test
        fun `null depositWording allowed`() {
            val persona = PersonaProfile(name = "T", tone = "t", depositWording = null)
            assertThat(persona.depositWording).isNull()
        }
    }
}
