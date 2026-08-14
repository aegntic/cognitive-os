package com.thresholdinc.insidher.contracts

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("ThreadMemory tests (VAL-CONTRACTS-113 to 121, 193)")
class ThreadMemoryTest {

    private val json = TestUtils.strictJson

    @Nested
    @DisplayName("VAL-CONTRACTS-113: construction")
    inner class Construction {
        @Test
        fun `constructs with key, value, confidence`() {
            val mem = TestUtils.makeThreadMemory()
            assertThat(mem.threadId).isEqualTo("thread-001")
            assertThat(mem.key).isEqualTo("preferred_time")
            assertThat(mem.value).isEqualTo("Saturday mornings")
            assertThat(mem.confidence).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-114 to 115: serialization")
    inner class Serialization {
        @Test
        fun `114 serializes with all fields`() {
            val mem = TestUtils.makeThreadMemory()
            val jsonStr = json.encodeToString(ThreadMemory.serializer(), mem)
            assertThat(jsonStr).contains("\"key\"")
            assertThat(jsonStr).contains("\"value\"")
            assertThat(jsonStr).contains("\"confidence\"")
            assertThat(jsonStr).contains("\"threadId\"")
            assertThat(jsonStr).contains("\"createdAt\"")
            assertThat(jsonStr).contains("\"updatedAt\"")
        }

        @Test
        fun `115 round-trip equality`() {
            val mem = TestUtils.makeThreadMemory()
            val jsonStr = json.encodeToString(ThreadMemory.serializer(), mem)
            val decoded = json.decodeFromString(ThreadMemory.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(mem)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-116: confidence defaults to 1.0")
    inner class DefaultConfidence {
        @Test
        fun `default confidence is 1_0`() {
            val mem = ThreadMemory(
                threadId = "t1",
                key = "k",
                value = "v",
                createdAt = TestUtils.fixedInstant,
                updatedAt = TestUtils.fixedInstant,
            )
            assertThat(mem.confidence).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-117: confidence range validation")
    inner class ConfidenceRange {
        @Test
        fun `confidence below 0 rejected`() {
            assertThrows<IllegalArgumentException> {
                ThreadMemory("t1", "k", "v", -0.1, TestUtils.fixedInstant, TestUtils.fixedInstant)
            }
        }

        @Test
        fun `confidence above 1 rejected`() {
            assertThrows<IllegalArgumentException> {
                ThreadMemory("t1", "k", "v", 1.1, TestUtils.fixedInstant, TestUtils.fixedInstant)
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-118 to 119: non-blank key and value")
    inner class NonBlankFields {
        @Test
        fun `118 blank key rejected`() {
            assertThrows<IllegalArgumentException> {
                ThreadMemory("t1", "", "v", 1.0, TestUtils.fixedInstant, TestUtils.fixedInstant)
            }
        }

        @Test
        fun `119 blank value rejected`() {
            assertThrows<IllegalArgumentException> {
                ThreadMemory("t1", "k", "", 1.0, TestUtils.fixedInstant, TestUtils.fixedInstant)
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-120: retrievable by key via MemoryStore")
    inner class KeyRetrieval {
        @Test
        fun `entries retrievable by key`() {
            val store = MemoryStore("t1")
                .put(ThreadMemory("t1", "color", "red", 1.0, TestUtils.fixedInstant, TestUtils.fixedInstant))
                .put(ThreadMemory("t1", "size", "large", 0.9, TestUtils.fixedInstant, TestUtils.fixedInstant))
            assertThat(store.get("color")?.value).isEqualTo("red")
            assertThat(store.get("size")?.value).isEqualTo("large")
            assertThat(store.get("missing")).isNull()
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-121: update with confidence decay")
    inner class UpdateWithDecay {
        @Test
        fun `update preserves createdAt and changes updatedAt`() {
            val mem = ThreadMemory(
                threadId = "t1", key = "k", value = "v1",
                createdAt = TestUtils.fixedInstant, updatedAt = TestUtils.fixedInstant,
            )
            val updated = mem.update("v2", 0.5, TestUtils.fixedInstant2)
            assertThat(updated.value).isEqualTo("v2")
            assertThat(updated.confidence).isEqualTo(0.5)
            assertThat(updated.createdAt).isEqualTo(TestUtils.fixedInstant)
            assertThat(updated.updatedAt).isEqualTo(TestUtils.fixedInstant2)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-193: threadId non-blank, timestamps non-null, updatedAt >= createdAt")
    inner class AdvancedValidation {
        @Test
        fun `blank threadId rejected`() {
            assertThrows<IllegalArgumentException> {
                ThreadMemory("", "k", "v", 1.0, TestUtils.fixedInstant, TestUtils.fixedInstant)
            }
        }

        @Test
        fun `timestamps non-null enforced by type system`() {
            val mem = TestUtils.makeThreadMemory()
            assertThat(mem.createdAt).isNotNull
            assertThat(mem.updatedAt).isNotNull
        }

        @Test
        fun `updatedAt before createdAt rejected`() {
            assertThrows<IllegalArgumentException> {
                ThreadMemory(
                    "t1", "k", "v", 1.0,
                    createdAt = TestUtils.fixedInstant2, // later
                    updatedAt = TestUtils.fixedInstant,  // earlier
                )
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-181: reject unknown properties")
    inner class RejectUnknown {
        @Test
        fun `ThreadMemory rejects unknown property`() {
            val base = json.encodeToString(ThreadMemory.serializer(), TestUtils.makeThreadMemory())
            val tampered = base.removeSuffix("}") + ",\"unknownField\":42}"
            assertThrows<Exception> {
                json.decodeFromString(ThreadMemory.serializer(), tampered)
            }
        }
    }
}
