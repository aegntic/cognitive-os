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

@DisplayName("Booking and Deposit tests (VAL-CONTRACTS-073 to 092, 161, 165, 177, 184, 194-195, 197)")
class BookingTest {

    private val json = TestUtils.strictJson

    // ── BookingProposal ──────────────────────────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-073 to 076: BookingProposal")
    inner class BookingProposalTests {
        @Test
        fun `073 constructs with all fields`() {
            val bp = TestUtils.makeBookingProposal()
            assertThat(bp.date).isEqualTo(TestUtils.fixedDate)
            assertThat(bp.time).isEqualTo(TestUtils.fixedTime)
            assertThat(bp.service).isNotBlank
            assertThat(bp.depositAmount).isEqualTo(75.0)
            assertThat(bp.depositStatus).isEqualTo(DepositStatus.PENDING)
        }

        @Test
        fun `074 serializes to valid JSON`() {
            val bp = TestUtils.makeBookingProposal()
            val jsonStr = json.encodeToString(BookingProposal.serializer(), bp)
            assertThat(jsonStr).contains("\"service\"")
            assertThat(jsonStr).contains("\"depositAmount\"")
            assertThat(jsonStr).contains("\"depositStatus\":\"PENDING\"")
        }

        @Test
        fun `075 round-trip equality`() {
            val bp = TestUtils.makeBookingProposal()
            val jsonStr = json.encodeToString(BookingProposal.serializer(), bp)
            val decoded = json.decodeFromString(BookingProposal.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(bp)
        }

        @Test
        fun `076 negative depositAmount rejected`() {
            assertThrows<IllegalArgumentException> {
                BookingProposal(TestUtils.fixedDate, TestUtils.fixedTime, "Service", -1.0, DepositStatus.PENDING)
            }
        }
    }

    // ── DepositStatus ────────────────────────────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-077 to 079: DepositStatus hierarchy")
    inner class DepositStatusHierarchy {
        @Test
        fun `077 exactly 4 variants`() {
            assertThat(DepositStatus.variants).hasSize(4)
            assertThat(DepositStatus.variants.map { it.serialName }).containsExactlyInAnyOrder(
                "PENDING", "RECEIVED", "VERIFIED", "FAILED",
            )
        }

        @Test
        fun `078 serializes to variant name string`() {
            for (status in DepositStatus.variants) {
                val jsonStr = json.encodeToString(DepositStatus.serializer(), status)
                assertThat(jsonStr).isEqualTo("\"${status.serialName}\"")
            }
        }

        @Test
        fun `079 deserializes from variant name string`() {
            for (status in DepositStatus.variants) {
                val decoded = json.decodeFromString(DepositStatus.serializer(), "\"${status.serialName}\"")
                assertThat(decoded).isEqualTo(status)
            }
        }

        @Test
        fun `189 rejects unknown string`() {
            assertThrows<Exception> {
                json.decodeFromString(DepositStatus.serializer(), "\"UNDER_REVIEW\"")
            }
        }

        // 165: Schema enum matches 4 variant names
        @Test
        fun `165 schema deposit status enum matches 4 variants`() {
            assertThat(Schemas.depositStatusEnum).containsExactlyInAnyOrder(
                "PENDING", "RECEIVED", "VERIFIED", "FAILED",
            )
        }
    }

    // ── DepositRecord ────────────────────────────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-080 to 086: DepositRecord")
    inner class DepositRecordTests {
        @Test
        fun `080 constructs with required fields`() {
            val dr = TestUtils.makeDepositRecord()
            assertThat(dr.amount).isEqualTo(75.0)
            assertThat(dr.currency).isEqualTo("AUD")
            assertThat(dr.status).isEqualTo(DepositStatus.PENDING)
        }

        @Test
        fun `081 serializes to valid JSON`() {
            val dr = TestUtils.makeDepositRecord()
            val jsonStr = json.encodeToString(DepositRecord.serializer(), dr)
            assertThat(jsonStr).contains("\"amount\"")
            assertThat(jsonStr).contains("\"currency\":\"AUD\"")
            assertThat(jsonStr).contains("\"status\":\"PENDING\"")
        }

        @Test
        fun `082 round-trip equality`() {
            val dr = TestUtils.makeDepositRecord()
            val jsonStr = json.encodeToString(DepositRecord.serializer(), dr)
            val decoded = json.decodeFromString(DepositRecord.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(dr)
        }

        @Test
        fun `083 negative amount rejected`() {
            assertThrows<IllegalArgumentException> {
                DepositRecord(amount = -1.0, status = DepositStatus.PENDING, timestamp = TestUtils.fixedInstant)
            }
        }

        @Test
        fun `084 currency defaults to AUD`() {
            val dr = DepositRecord(amount = 50.0, status = DepositStatus.PENDING, timestamp = TestUtils.fixedInstant)
            assertThat(dr.currency).isEqualTo("AUD")
        }

        @Test
        fun `085 evidence type accepts STRIPE_WEBHOOK`() {
            val dr = DepositRecord(
                amount = 50.0,
                status = DepositStatus.RECEIVED,
                evidenceType = DepositEvidenceType.STRIPE_WEBHOOK,
                evidenceRef = "evt_123",
                timestamp = TestUtils.fixedInstant,
            )
            assertThat(dr.evidenceType).isEqualTo(DepositEvidenceType.STRIPE_WEBHOOK)
        }

        @Test
        fun `085 evidence type accepts MANUAL_FLAG`() {
            val dr = DepositRecord(
                amount = 50.0,
                status = DepositStatus.RECEIVED,
                evidenceType = DepositEvidenceType.MANUAL_FLAG,
                timestamp = TestUtils.fixedInstant,
            )
            assertThat(dr.evidenceType).isEqualTo(DepositEvidenceType.MANUAL_FLAG)
        }

        @Test
        fun `086 VERIFIED without evidence rejected`() {
            assertThrows<IllegalArgumentException> {
                DepositRecord(
                    amount = 50.0,
                    status = DepositStatus.VERIFIED,
                    evidenceType = null,
                    verifiedAt = TestUtils.fixedInstant,
                    timestamp = TestUtils.fixedInstant,
                )
            }
        }
    }

    // ── DepositStatus transitions ────────────────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-087 to 092, 184: DepositStatus transitions")
    inner class DepositStatusTransitions {

        @Test fun `087 PENDING to RECEIVED valid`() {
            assertThat(DepositStatus.isValidTransition(DepositStatus.PENDING, DepositStatus.RECEIVED)).isTrue()
        }
        @Test fun `088 RECEIVED to VERIFIED valid`() {
            assertThat(DepositStatus.isValidTransition(DepositStatus.RECEIVED, DepositStatus.VERIFIED)).isTrue()
        }
        @Test fun `089 PENDING to FAILED valid`() {
            assertThat(DepositStatus.isValidTransition(DepositStatus.PENDING, DepositStatus.FAILED)).isTrue()
        }
        @Test fun `090 RECEIVED to FAILED valid`() {
            assertThat(DepositStatus.isValidTransition(DepositStatus.RECEIVED, DepositStatus.FAILED)).isTrue()
        }
        @Test fun `091 VERIFIED to PENDING invalid`() {
            assertThat(DepositStatus.isValidTransition(DepositStatus.VERIFIED, DepositStatus.PENDING)).isFalse()
        }
        @Test fun `092 FAILED to VERIFIED invalid`() {
            assertThat(DepositStatus.isValidTransition(DepositStatus.FAILED, DepositStatus.VERIFIED)).isFalse()
        }

        // 184: Exhaustive 4x4 matrix
        @ParameterizedTest(name = "184 {0} → {1}")
        @MethodSource("com.thresholdinc.insidher.contracts.BookingTest#depositStatusPairs")
        fun `all 16 deposit status combinations classified`(from: DepositStatus, to: DepositStatus) {
            val actual = DepositStatus.isValidTransition(from, to)
            val expected = isExpectedValid(from, to)
            assertThat(actual).describedAs("$from to $to").isEqualTo(expected)
        }

        private fun isExpectedValid(from: DepositStatus, to: DepositStatus): Boolean = when (from) {
            DepositStatus.PENDING -> to == DepositStatus.RECEIVED || to == DepositStatus.FAILED
            DepositStatus.RECEIVED -> to == DepositStatus.VERIFIED || to == DepositStatus.FAILED
            DepositStatus.VERIFIED -> false
            DepositStatus.FAILED -> false
        }
    }

    // ── Advanced validation ──────────────────────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-177: verifiedAt only on VERIFIED")
    inner class VerifiedAtValidation {
        @Test
        fun `verifiedAt non-null for VERIFIED`() {
            val dr = DepositRecord(
                amount = 50.0,
                status = DepositStatus.VERIFIED,
                evidenceType = DepositEvidenceType.MANUAL_FLAG,
                verifiedAt = TestUtils.fixedInstant,
                timestamp = TestUtils.fixedInstant,
            )
            assertThat(dr.verifiedAt).isNotNull
        }

        @Test
        fun `verifiedAt null for non-VERIFIED`() {
            val dr = TestUtils.makeDepositRecord(status = DepositStatus.PENDING)
            assertThat(dr.verifiedAt).isNull()
        }

        @Test
        fun `verifiedAt rejected on non-VERIFIED`() {
            assertThrows<IllegalArgumentException> {
                DepositRecord(
                    amount = 50.0,
                    status = DepositStatus.RECEIVED,
                    verifiedAt = TestUtils.fixedInstant,
                    timestamp = TestUtils.fixedInstant,
                )
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-194: BookingProposal field validation")
    inner class BookingProposalValidation {
        @Test
        fun `blank service rejected`() {
            assertThrows<IllegalArgumentException> {
                BookingProposal(TestUtils.fixedDate, TestUtils.fixedTime, "", 50.0, DepositStatus.PENDING)
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-195: DepositRecord timestamp and evidenceRef")
    inner class DepositRecordTimestampEvidence {
        @Test
        fun `STRIPE_WEBHOOK without evidenceRef rejected`() {
            assertThrows<IllegalArgumentException> {
                DepositRecord(
                    amount = 50.0,
                    status = DepositStatus.RECEIVED,
                    evidenceType = DepositEvidenceType.STRIPE_WEBHOOK,
                    evidenceRef = null,
                    timestamp = TestUtils.fixedInstant,
                )
            }
        }

        @Test
        fun `STRIPE_WEBHOOK with evidenceRef accepted`() {
            val dr = DepositRecord(
                amount = 50.0,
                status = DepositStatus.RECEIVED,
                evidenceType = DepositEvidenceType.STRIPE_WEBHOOK,
                evidenceRef = "evt_abc123",
                timestamp = TestUtils.fixedInstant,
            )
            assertThat(dr.evidenceRef).isEqualTo("evt_abc123")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-197: DepositRecord currency validation")
    inner class CurrencyValidation {
        @Test
        fun `accepts AUD`() {
            val dr = DepositRecord(amount = 50.0, currency = "AUD", status = DepositStatus.PENDING, timestamp = TestUtils.fixedInstant)
            assertThat(dr.currency).isEqualTo("AUD")
        }

        @Test
        fun `accepts USD`() {
            val dr = DepositRecord(amount = 50.0, currency = "USD", status = DepositStatus.PENDING, timestamp = TestUtils.fixedInstant)
            assertThat(dr.currency).isEqualTo("USD")
        }

        @Test
        fun `accepts EUR`() {
            val dr = DepositRecord(amount = 50.0, currency = "EUR", status = DepositStatus.PENDING, timestamp = TestUtils.fixedInstant)
            assertThat(dr.currency).isEqualTo("EUR")
        }

        @Test
        fun `accepts GBP`() {
            val dr = DepositRecord(amount = 50.0, currency = "GBP", status = DepositStatus.PENDING, timestamp = TestUtils.fixedInstant)
            assertThat(dr.currency).isEqualTo("GBP")
        }

        @Test
        fun `rejects lowercase currency`() {
            assertThrows<IllegalArgumentException> {
                DepositRecord(amount = 50.0, currency = "aud", status = DepositStatus.PENDING, timestamp = TestUtils.fixedInstant)
            }
        }

        @Test
        fun `rejects 2-letter currency`() {
            assertThrows<IllegalArgumentException> {
                DepositRecord(amount = 50.0, currency = "AU", status = DepositStatus.PENDING, timestamp = TestUtils.fixedInstant)
            }
        }

        @Test
        fun `rejects 4-letter currency`() {
            assertThrows<IllegalArgumentException> {
                DepositRecord(amount = 50.0, currency = "AUDA", status = DepositStatus.PENDING, timestamp = TestUtils.fixedInstant)
            }
        }

        @Test
        fun `rejects non-letter currency`() {
            assertThrows<IllegalArgumentException> {
                DepositRecord(amount = 50.0, currency = "12A", status = DepositStatus.PENDING, timestamp = TestUtils.fixedInstant)
            }
        }
    }

    // ── Schema match ─────────────────────────────────────────────

    @Nested
    @DisplayName("VAL-CONTRACTS-161: booking schema matches types")
    inner class SchemaMatch {
        @Test
        fun `booking schema covers BookingProposal and DepositRecord fields`() {
            val schemaFields = Schemas.schemaFields("booking")
            // BookingProposal fields
            assertThat(schemaFields).contains("date", "time", "service", "depositAmount", "depositStatus")
            // DepositRecord fields
            assertThat(schemaFields).contains("amount", "currency", "status", "evidenceType", "evidenceRef", "timestamp", "verifiedAt")
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-181: reject unknown properties")
    inner class RejectUnknown {
        @Test
        fun `BookingProposal rejects unknown property`() {
            val base = json.encodeToString(BookingProposal.serializer(), TestUtils.makeBookingProposal())
            val tampered = base.removeSuffix("}") + ",\"unknownField\":42}"
            assertThrows<Exception> {
                json.decodeFromString(BookingProposal.serializer(), tampered)
            }
        }

        @Test
        fun `DepositRecord rejects unknown property`() {
            val base = json.encodeToString(DepositRecord.serializer(), TestUtils.makeDepositRecord())
            val tampered = base.removeSuffix("}") + ",\"unknownField\":42}"
            assertThrows<Exception> {
                json.decodeFromString(DepositRecord.serializer(), tampered)
            }
        }
    }

    companion object {
        @JvmStatic
        fun depositStatusPairs(): Stream<Arguments> =
            DepositStatus.variants.flatMap { from ->
                DepositStatus.variants.map { to -> Arguments.of(from, to) }
            }.stream()
    }
}
