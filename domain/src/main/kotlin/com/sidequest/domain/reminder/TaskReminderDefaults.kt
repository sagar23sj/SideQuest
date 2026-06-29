package com.sidequest.domain.reminder

import com.sidequest.domain.model.TaskReminder
import com.sidequest.domain.model.Timeframe
import java.time.ZonedDateTime

/**
 * Derives a sensible default [TaskReminder] from the vague timeframe a user
 * picks at capture, so every quest gets a concrete reminder time instead of
 * none. The user can always edit or remove it later on the quest's detail
 * screen.
 *
 * The chosen times favor a useful, non-annoying nudge:
 *  - Today → 6:00 PM today (or +1h from now if it's already evening),
 *  - Within a day → 9:00 AM tomorrow,
 *  - Within a week → 9:00 AM in three days,
 *  - A specific date → 9:00 AM on that date (or +1h from now if that's today
 *    and 9 AM has passed).
 *
 * Pure and testable: the "now" is injected rather than read from the system.
 */
object TaskReminderDefaults {

    private const val EVENING_HOUR = 18
    private const val MORNING_HOUR = 9

    fun forTimeframe(timeframe: Timeframe, now: ZonedDateTime): TaskReminder {
        val today = now.toLocalDate()
        return when (timeframe) {
            Timeframe.Today ->
                if (now.hour < EVENING_HOUR) {
                    TaskReminder(EVENING_HOUR, 0, today, recurring = false)
                } else {
                    inAnHour(now)
                }

            Timeframe.WithinADay ->
                TaskReminder(MORNING_HOUR, 0, today.plusDays(1), recurring = false)

            Timeframe.WithinAWeek ->
                TaskReminder(MORNING_HOUR, 0, today.plusDays(3), recurring = false)

            is Timeframe.SpecificDate -> {
                val date = timeframe.date
                if (date == today && now.hour >= MORNING_HOUR) {
                    inAnHour(now)
                } else {
                    TaskReminder(MORNING_HOUR, 0, date, recurring = false)
                }
            }
        }
    }

    /** A one-off reminder roughly an hour from [now], with an until-date that covers it. */
    private fun inAnHour(now: ZonedDateTime): TaskReminder {
        val later = now.plusHours(1)
        return TaskReminder(
            hour = later.hour,
            minute = later.minute,
            untilDate = later.toLocalDate(),
            recurring = false,
        )
    }
}
