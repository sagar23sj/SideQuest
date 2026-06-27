package com.sidequest.domain.reminder

import com.sidequest.domain.model.TaskReminder
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Pure scheduling logic for per-task reminders (Req 6.5–6.9).
 *
 * Lives in `:domain` so the "when does this reminder next fire" decision is
 * portable and testable without Android/AlarmManager. The Android scheduler
 * calls [nextTrigger] to get the next fire instant (in epoch millis) and arms an
 * exact alarm for it; on fire it re-arms using [nextTrigger] again for recurring
 * reminders.
 */
object TaskReminderSchedule {

    /**
     * Computes the next fire time for [reminder] strictly after [now], or null
     * when the reminder has no more occurrences.
     *
     * Rules:
     * - The reminder fires at its local hour/minute (Req 6.9 — anchored to the
     *   zone of [now], which the caller sets to the device zone).
     * - A non-recurring reminder fires once on its `untilDate`; if that instant
     *   is already past, there is no next trigger (null).
     * - A recurring reminder fires each day at the time, starting today (if the
     *   time is still ahead) or tomorrow, but never after `untilDate`.
     *
     * Returns the trigger as epoch milliseconds so the Android layer can hand it
     * directly to AlarmManager.
     */
    fun nextTrigger(reminder: TaskReminder, now: ZonedDateTime): Long? {
        val zone = now.zone
        // Candidate fire time today at the reminder's local time.
        var candidateDate: LocalDate = now.toLocalDate()
        val timeToday = candidateDate
            .atTime(reminder.hour, reminder.minute)
            .atZone(zone)

        if (!reminder.recurring) {
            val fire = reminder.untilDate
                .atTime(reminder.hour, reminder.minute)
                .atZone(zone)
            return if (fire.isAfter(now)) fire.toInstant().toEpochMilli() else null
        }

        // Recurring: pick today if its time is still ahead, else tomorrow.
        if (!timeToday.isAfter(now)) {
            candidateDate = candidateDate.plusDays(1)
        }
        // Never fire after the until-date (inclusive).
        if (candidateDate.isAfter(reminder.untilDate)) return null

        return candidateDate
            .atTime(reminder.hour, reminder.minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }
}
