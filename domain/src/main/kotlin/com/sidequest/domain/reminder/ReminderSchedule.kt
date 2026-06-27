package com.sidequest.domain.reminder

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Pure computation of *when* the next daily reminder should fire (Req 6.3, 6.4).
 *
 * Lives in `:domain` so the "next occurrence of a local time" math is portable
 * and unit-testable without WorkManager or the system clock. The Android
 * scheduler ([com.sidequest.data.reminder.ReminderScheduler]) calls
 * [initialDelay] with the current time and the user's chosen [ReminderTime] and
 * uses the result as the WorkManager initial delay; the reminder then repeats
 * every [DAY] (24h).
 *
 * The functions here are pure and total: they never read the system clock and
 * never throw for any input.
 */
object ReminderSchedule {

    /** One day — the period between daily reminders (Req 6.4). */
    val DAY: Duration = Duration.ofDays(1)

    /**
     * The delay from [now] until the next occurrence of [time] in [now]'s zone.
     *
     * If today's occurrence of [time] is still in the future relative to [now],
     * the reminder fires today; otherwise it fires at the same wall-clock time
     * tomorrow. The result is always non-negative and strictly less than
     * [DAY] when the target time has already passed today (so a reminder set to
     * "now" schedules for tomorrow rather than firing immediately in a tight
     * loop). When the target is exactly [now], the delay is zero.
     *
     * Using the zoned [now] keeps daylight-saving transitions correct: the next
     * occurrence is computed against local wall-clock time, not a fixed offset.
     *
     * @param now the current zoned date-time (the device's clock and zone).
     * @param time the user-selected local time of day for the reminder.
     * @return a non-negative [Duration] to use as the scheduling initial delay.
     */
    fun initialDelay(now: ZonedDateTime, time: ReminderTime): Duration {
        val target = LocalTime.of(time.hour, time.minute)
        var next = now.with(target).withSecond(0).withNano(0)
        if (!next.isAfter(now)) {
            // Today's slot has already passed (or is exactly now): go to tomorrow.
            if (next.isBefore(now)) {
                next = next.plusDays(1)
            }
        }
        val delay = Duration.between(now, next)
        // Guard against any negative result from zone arithmetic.
        return if (delay.isNegative) Duration.ZERO else delay
    }
}
