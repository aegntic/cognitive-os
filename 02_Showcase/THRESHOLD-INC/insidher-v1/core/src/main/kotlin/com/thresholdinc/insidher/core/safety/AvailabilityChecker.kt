package com.thresholdinc.insidher.core.safety

import com.thresholdinc.insidher.contracts.AvailabilityPolicy
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId

/**
 * Quiet hours / active window enforcement (VAL-SAFETY-060..069, VAL-LLM-025/026).
 */
class AvailabilityChecker {

    fun isActive(policy: AvailabilityPolicy, at: Instant): Boolean {
        val zone = try {
            TimeZone.of(policy.timezone)
        } catch (_: Exception) {
            TimeZone.of(ZoneId.of(policy.timezone).id)
        }
        val local = at.toLocalDateTime(zone)
        val date = local.date
        val time = local.time
        val day = local.dayOfWeek

        // Date override wins (null = closed all day)
        if (policy.dateOverrides.containsKey(date)) {
            val window = policy.dateOverrides[date] ?: return false
            if (policy.dndPeriods.any { it.contains(time) }) return false
            return window.contains(time)
        }

        if (policy.dndPeriods.any { it.contains(time) }) return false

        val weekly = policy.weeklyWindows[day] ?: return false
        return weekly.contains(time)
    }

    fun isQuietHours(policy: AvailabilityPolicy, at: Instant): Boolean = !isActive(policy, at)
}
