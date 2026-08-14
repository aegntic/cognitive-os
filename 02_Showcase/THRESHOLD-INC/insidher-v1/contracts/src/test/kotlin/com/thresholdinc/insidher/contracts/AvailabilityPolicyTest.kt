package com.thresholdinc.insidher.contracts

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("AvailabilityPolicy and TimeWindow tests (VAL-CONTRACTS-146 to 157)")
class AvailabilityPolicyTest {

    private val json = TestUtils.strictJson

    @Nested
    @DisplayName("VAL-CONTRACTS-146: construction")
    inner class Construction {
        @Test
        fun `constructs with timezone and weekly windows`() {
            val policy = AvailabilityPolicy(
                timezone = "Australia/Sydney",
                weeklyWindows = AvailabilityPolicy.defaultWeeklyWindows(),
                dndPeriods = emptyList(),
                dateOverrides = emptyMap(),
            )
            assertThat(policy.timezone).isEqualTo("Australia/Sydney")
            assertThat(policy.weeklyWindows).hasSize(7)
            assertThat(policy.dndPeriods).isEmpty()
            assertThat(policy.dateOverrides).isEmpty()
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-147 to 148: serialization")
    inner class Serialization {
        @Test
        fun `147 serializes with all fields`() {
            val policy = AvailabilityPolicy()
            val jsonStr = json.encodeToString(AvailabilityPolicy.serializer(), policy)
            assertThat(jsonStr).contains("\"timezone\"")
            assertThat(jsonStr).contains("\"weeklyWindows\"")
            assertThat(jsonStr).contains("\"dndPeriods\"")
            assertThat(jsonStr).contains("\"dateOverrides\"")
        }

        @Test
        fun `148 round-trip equality`() {
            val policy = AvailabilityPolicy(timezone = "America/New_York")
            val jsonStr = json.encodeToString(AvailabilityPolicy.serializer(), policy)
            val decoded = json.decodeFromString(AvailabilityPolicy.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(policy)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-149: timezone validation")
    inner class TimezoneValidation {
        @Test
        fun `valid IANA timezone accepted`() {
            val policy = AvailabilityPolicy(timezone = "Europe/London")
            assertThat(policy.timezone).isEqualTo("Europe/London")
        }

        @Test
        fun `invalid timezone rejected`() {
            assertThrows<IllegalArgumentException> {
                AvailabilityPolicy(timezone = "Fake/Zone/Extra")
            }
        }

        @Test
        fun `single-word timezone rejected`() {
            assertThrows<IllegalArgumentException> {
                AvailabilityPolicy(timezone = "Sydney")
            }
        }

        @Test
        fun `blank timezone rejected`() {
            assertThrows<IllegalArgumentException> {
                AvailabilityPolicy(timezone = "")
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-150: weeklyWindows covers all 7 days")
    inner class SevenDayCoverage {
        @Test
        fun `default covers all 7 days with 08-22`() {
            val policy = AvailabilityPolicy()
            for (day in DayOfWeek.entries) {
                val window = policy.weeklyWindows[day]
                assertThat(window).isNotNull
                assertThat(window!!.start).isEqualTo(LocalTime(8, 0))
                assertThat(window.end).isEqualTo(LocalTime(22, 0))
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-151: TimeWindow start before end")
    inner class TimeWindowValidation {
        @Test
        fun `start before end accepted`() {
            val tw = TimeWindow(LocalTime(8, 0), LocalTime(22, 0))
            assertThat(tw.start).isEqualTo(LocalTime(8, 0))
        }

        @Test
        fun `start equals end rejected`() {
            assertThrows<IllegalArgumentException> {
                TimeWindow(LocalTime(10, 0), LocalTime(10, 0))
            }
        }

        @Test
        fun `start after end rejected`() {
            assertThrows<IllegalArgumentException> {
                TimeWindow(LocalTime(22, 0), LocalTime(8, 0))
            }
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-152: dateOverrides null means closed")
    inner class DateOverridesNull {
        @Test
        fun `null override value represents closed`() {
            val date = LocalDate(2024, 12, 25)
            val policy = AvailabilityPolicy(dateOverrides = mapOf(date to null))
            assertThat(policy.dateOverrides[date]).isNull()
            // Distinguishable from absent: get returns null, but containsKey returns true
            assertThat(policy.dateOverrides.containsKey(date)).isTrue()
        }

        @Test
        fun `absent override means no override`() {
            val policy = AvailabilityPolicy()
            assertThat(policy.dateOverrides.containsKey(LocalDate(2024, 12, 25))).isFalse()
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-153: DND periods override weekly windows")
    inner class DndOverride {
        @Test
        fun `DND period defined in policy`() {
            val dnd = TimeWindow(LocalTime(12, 0), LocalTime(13, 0))
            val policy = AvailabilityPolicy(dndPeriods = listOf(dnd))
            assertThat(policy.dndPeriods).hasSize(1)
            assertThat(policy.dndPeriods[0]).isEqualTo(dnd)
        }

        @Test
        fun `DND window can overlap active window`() {
            // The policy accepts DND periods that overlap active windows
            // The actual override logic is in :core
            val activeEnd = LocalTime(22, 0)
            val dnd = TimeWindow(LocalTime(14, 0), LocalTime(15, 0))
            val policy = AvailabilityPolicy(dndPeriods = listOf(dnd))
            // Verify DND is within active window
            assertThat(dnd.start).isGreaterThan(LocalTime(8, 0))
            assertThat(dnd.end).isLessThan(activeEnd)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-154: default is 08-22 all days")
    inner class DefaultPolicy {
        @Test
        fun `default policy has expected values`() {
            val policy = AvailabilityPolicy()
            assertThat(policy.timezone).isEqualTo("Australia/Sydney")
            assertThat(policy.weeklyWindows).hasSize(7)
            assertThat(policy.dndPeriods).isEmpty()
            assertThat(policy.dateOverrides).isEmpty()
            val window = policy.weeklyWindows[DayOfWeek.MONDAY]
            assertThat(window!!.start).isEqualTo(LocalTime(8, 0))
            assertThat(window.end).isEqualTo(LocalTime(22, 0))
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-155 to 156: TimeWindow serialization")
    inner class TimeWindowSerialization {
        @Test
        fun `155 serializes to ISO time strings`() {
            val tw = TimeWindow(LocalTime(8, 0), LocalTime(22, 0))
            val jsonStr = json.encodeToString(TimeWindow.serializer(), tw)
            assertThat(jsonStr).contains("\"start\"")
            assertThat(jsonStr).contains("\"end\"")
        }

        @Test
        fun `156 round-trip equality`() {
            val tw = TimeWindow(LocalTime(9, 30), LocalTime(17, 0))
            val jsonStr = json.encodeToString(TimeWindow.serializer(), tw)
            val decoded = json.decodeFromString(TimeWindow.serializer(), jsonStr)
            assertThat(decoded).isEqualTo(tw)
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-157: user-configurable")
    inner class UserConfigurable {
        @Test
        fun `custom policy preserves non-default values`() {
            val customWindow = TimeWindow(LocalTime(10, 0), LocalTime(18, 0))
            val customDnd = TimeWindow(LocalTime(12, 0), LocalTime(13, 0))
            val customDate = LocalDate(2024, 1, 1)
            val policy = AvailabilityPolicy(
                timezone = "America/Los_Angeles",
                weeklyWindows = DayOfWeek.entries.associateWith { customWindow },
                dndPeriods = listOf(customDnd),
                dateOverrides = mapOf(customDate to null),
            )
            assertThat(policy.timezone).isEqualTo("America/Los_Angeles")
            assertThat(policy.weeklyWindows[DayOfWeek.MONDAY]).isEqualTo(customWindow)
            assertThat(policy.dndPeriods).containsExactly(customDnd)
            assertThat(policy.dateOverrides[customDate]).isNull()
        }
    }

    @Nested
    @DisplayName("VAL-CONTRACTS-181: reject unknown properties")
    inner class RejectUnknown {
        @Test
        fun `AvailabilityPolicy rejects unknown property`() {
            val base = json.encodeToString(AvailabilityPolicy.serializer(), AvailabilityPolicy())
            val tampered = base.removeSuffix("}") + ",\"unknownField\":42}"
            assertThrows<Exception> {
                json.decodeFromString(AvailabilityPolicy.serializer(), tampered)
            }
        }

        @Test
        fun `TimeWindow rejects unknown property`() {
            val tw = TimeWindow(LocalTime(8, 0), LocalTime(22, 0))
            val base = json.encodeToString(TimeWindow.serializer(), tw)
            val tampered = base.removeSuffix("}") + ",\"unknownField\":42}"
            assertThrows<Exception> {
                json.decodeFromString(TimeWindow.serializer(), tampered)
            }
        }
    }
}
