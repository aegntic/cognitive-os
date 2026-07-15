package com.thresholdinc.insidher.contracts

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Message tests (VAL-CONTRACTS-061 to 072, 160)")
class MessageTest {

    private val json = TestUtils.strictJson

    @Nested
    @DisplayName("VAL-CONTRACTS-061: ClientMessage construction")
    inner class ClientMessageConstruction {
        @Test
        fun `constructs with all required fields`() {
            val msg = TestUtils.makeClientMessage()
            assertThat(msg.threadId).isEqualTo("thread-001")
            assertThat(msg.body).isNotBlank
            assertThat(msg.timestamp).isEqualTo(TestUtils.fixedInstant)
            assertThat(msg.phoneNumber).isEqualTo("+61412345678")
            assertThat(msg.direction).isEqualTo("inbound")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-062: ClientMessage serialization")
    inner class ClientMessageSerialize {
        @Test
        fun `serializes with direction inbound`() {
            val msg = TestUtils.makeClientMessage()
            val jsonStr = json.encodeToString(ClientMessage.serializer(), msg)
            assertThat(jsonStr).contains("\"direction\":\"inbound\"")
            assertThat(jsonStr).contains("\"threadId\"")
            assertThat(jsonStr).contains("\"body\"")
            assertThat(jsonStr).contains("\"timestamp\"")
            assertThat(jsonStr).contains("\"phoneNumber\"")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-063: ClientMessage deserialization")
    inner class ClientMessageDeserialize {
        @Test
        fun `round-trip equality`() {
            val msg = TestUtils.makeClientMessage()
            val jsonStr = json.encodeToString(ClientMessage.serializer(), msg)
            val decoded = json.decodeFromString(ClientMessage.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(msg)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-064: ClientMessage rejects blank body")
    inner class ClientMessageBlankBody {
        @Test
        fun `blank body rejected`() {
            assertThrows<IllegalArgumentException> {
                TestUtils.makeClientMessage(body = "")
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-065: ClientMessage rejects blank phone")
    inner class ClientMessageBlankPhone {
        @Test
        fun `blank phone rejected`() {
            assertThrows<IllegalArgumentException> {
                TestUtils.makeClientMessage(phoneNumber = "")
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-066 to 071: AgentMessage tests")
    inner class AgentMessageTests {
        @Test
        fun `066 constructs with all required fields`() {
            val msg = TestUtils.makeAgentMessage()
            assertThat(msg.threadId).isEqualTo("thread-001")
            assertThat(msg.body).isNotBlank
            assertThat(msg.worker).isEqualTo("PersonaWorker")
            assertThat(msg.confidence).isEqualTo(0.95)
            assertThat(msg.direction).isEqualTo("outbound")
        }

        @Test
        fun `067 serializes with direction outbound`() {
            val msg = TestUtils.makeAgentMessage()
            val jsonStr = json.encodeToString(AgentMessage.serializer(), msg)
            assertThat(jsonStr).contains("\"direction\":\"outbound\"")
            assertThat(jsonStr).contains("\"worker\":\"PersonaWorker\"")
            assertThat(jsonStr).contains("\"confidence\":0.95")
        }

        @Test
        fun `068 round-trip equality`() {
            val msg = TestUtils.makeAgentMessage()
            val jsonStr = json.encodeToString(AgentMessage.serializer(), msg)
            val decoded = json.decodeFromString(AgentMessage.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(msg)
        }

        @Test
        fun `069 confidence below 0 rejected`() {
            assertThrows<IllegalArgumentException> {
                AgentMessage("t1", "body", TestUtils.fixedInstant, "w", -0.1)
            }
        }

        @Test
        fun `069 confidence above 1 rejected`() {
            assertThrows<IllegalArgumentException> {
                AgentMessage("t1", "body", TestUtils.fixedInstant, "w", 1.1)
            }
        }

        @Test
        fun `070 blank body rejected`() {
            assertThrows<IllegalArgumentException> {
                TestUtils.makeAgentMessage(body = "")
            }
        }

        @Test
        fun `071 blank worker rejected`() {
            assertThrows<IllegalArgumentException> {
                TestUtils.makeAgentMessage(worker = "")
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-072: threadId consistency")
    inner class ThreadIdConsistency {
        @Test
        fun `blank threadId rejected for ClientMessage`() {
            assertThrows<IllegalArgumentException> {
                TestUtils.makeClientMessage(threadId = "")
            }
        }

        @Test
        fun `blank threadId rejected for AgentMessage`() {
            assertThrows<IllegalArgumentException> {
                TestUtils.makeAgentMessage(threadId = "")
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-160: message schema matches both types")
    inner class SchemaMatch {
        @Test
        fun `message schema accommodates inbound and outbound`() {
            val schemaFields = Schemas.schemaFields("message")
            assertThat(schemaFields).contains("type", "direction", "threadId", "body", "timestamp", "phoneNumber", "worker", "confidence")
        }

        @Test
        fun `polymorphic Message serialization round-trips for both types`() {
            val client = TestUtils.makeClientMessage()
            val agent = TestUtils.makeAgentMessage()
            val clientJson = json.encodeToString(Message.serializer(), client)
            val agentJson = json.encodeToString(Message.serializer(), agent)
            assertThat(json.decodeFromString(Message.serializer(), clientJson)).isEqualTo(client)
            assertThat(json.decodeFromString(Message.serializer(), agentJson)).isEqualTo(agent)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-181: reject unknown properties")
    inner class RejectUnknown {
        @Test
        fun `ClientMessage rejects unknown property`() {
            val base = json.encodeToString(ClientMessage.serializer(), TestUtils.makeClientMessage())
            val tampered = base.removeSuffix("}") + ",\"unknownField\":42}"
            assertThrows<Exception> {
                json.decodeFromString(ClientMessage.serializer(), tampered)
            }
        }

        @Test
        fun `AgentMessage rejects unknown property`() {
            val base = json.encodeToString(AgentMessage.serializer(), TestUtils.makeAgentMessage())
            val tampered = base.removeSuffix("}") + ",\"unknownField\":42}"
            assertThrows<Exception> {
                json.decodeFromString(AgentMessage.serializer(), tampered)
            }
        }
    }
}
