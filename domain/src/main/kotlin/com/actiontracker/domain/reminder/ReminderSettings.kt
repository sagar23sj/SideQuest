package com.actiontracker.domain.reminder

import kotlinx.serialization.Serializable

/**
 * The user's daily-reminder preferences (Req 6.2, 6.3).
 *
 * Lives in `:domain` so it is portable, serializable (aligned with the shared
 * OpenAPI schema), and free of any Android/DataStore/WorkManager dependency.
 * The persistence layer ([com.actiontracker.data.reminder.ReminderSettingsStore])
 * stores and emits this value; the scheduler
 * ([com.actiontracker.data.reminder.ReminderScheduler]) turns it into WorkManager
 * scheduling.
 *
 * @property enabled whether daily reminders are turned on (Req 6.2). When
 *   `false`, no reminder is scheduled.
 * @property time the local time of day the reminder should fire (Req 6.3).
 */
@Serializable
data class ReminderSettings(
    val enabled: Boolean = DEFAULT_ENABLED,
    val time: ReminderTime = ReminderTime.DEFAULT,
) {
    companion object {
        /**
         * Reminders are off until the user enables them, so the app never
         * schedules notifications the user did not ask for.
         */
        const val DEFAULT_ENABLED: Boolean = false

        /** The default settings used before the user changes anything. */
        val DEFAULT: ReminderSettings = ReminderSettings()
    }
}

/**
 * A local wall-clock time of day for the daily reminder (Req 6.3), validated to
 * a 24-hour clock.
 *
 * Kept as a small portable value (rather than `java.time.LocalTime`) so it
 * round-trips cleanly through the settings store and the shared schema. The
 * [hour]/[minute] are validated on construction so an out-of-range time can
 * never be persisted or scheduled.
 *
 * @property hour hour of day in `0..23`.
 * @property minute minute of hour in `0..59`.
 */
@Serializable
data class ReminderTime(
    val hour: Int,
    val minute: Int,
) {
    init {
        require(hour in 0..23) { "hour must be in 0..23 but was $hour" }
        require(minute in 0..59) { "minute must be in 0..59 but was $minute" }
    }

    companion object {
        /** Default reminder time: 9:00 AM local. */
        val DEFAULT: ReminderTime = ReminderTime(hour = 9, minute = 0)
    }
}
