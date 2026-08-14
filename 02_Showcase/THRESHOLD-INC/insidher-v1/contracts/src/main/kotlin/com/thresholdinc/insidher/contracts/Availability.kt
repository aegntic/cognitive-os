package com.thresholdinc.insidher.contracts

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * A time window with [start] and [end] using [LocalTime].
 *
 * Invariant: start must be strictly before end.
 */
@Serializable
data class TimeWindow(
    val start: LocalTime,
    val end: LocalTime,
) {
    init {
        require(start < end) { "start ($start) must be before end ($end)" }
    }

    /**
     * Checks whether [time] falls within this window (inclusive of start, exclusive of end).
     */
    fun contains(time: LocalTime): Boolean = time >= start && time < end
}

/**
 * Availability policy defining when the agent is active and accepting messages.
 *
 * Defaults to 08:00–22:00 active all 7 days, no DND periods, no date overrides.
 */
@Serializable
data class AvailabilityPolicy(
    /** IANA timezone string (e.g., "Australia/Sydney"). */
    val timezone: String = "Australia/Sydney",
    /** Weekly active windows keyed by day of week. */
    val weeklyWindows: Map<DayOfWeek, TimeWindow> = defaultWeeklyWindows(),
    /** Do-Not-Disturb periods that override weekly windows. */
    val dndPeriods: List<TimeWindow> = emptyList(),
    /** Date-specific overrides. Null value = closed on that date. */
    val dateOverrides: Map<LocalDate, TimeWindow?> = emptyMap(),
) {
    init {
        require(timezone.isNotBlank()) { "timezone must not be blank" }
        try {
            java.time.ZoneId.of(timezone)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "timezone must be a valid IANA timezone (e.g., 'Australia/Sydney'), was '$timezone'",
            )
        }
    }

    companion object {
        /** Default active window: 08:00 to 22:00. */
        val DEFAULT_WINDOW: TimeWindow = TimeWindow(
            LocalTime(8, 0),
            LocalTime(22, 0),
        )

        /** Creates the default 7-day weekly windows map. */
        fun defaultWeeklyWindows(): Map<DayOfWeek, TimeWindow> =
            DayOfWeek.entries.associateWith { DEFAULT_WINDOW }
    }
}
