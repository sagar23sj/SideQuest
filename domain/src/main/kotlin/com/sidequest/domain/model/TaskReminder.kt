package com.sidequest.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * An optional reminder attached to an [ActionItem] (Req 6).
 *
 * The reminder fires at [hour]:[minute] local time. When [recurring] is true it
 * fires every day at that time up to and including [untilDate]; when false it
 * fires once on [untilDate]. In both cases reminders stop once the item is
 * marked completed (enforced by the scheduling layer) and never fire after
 * [untilDate].
 *
 * Times are interpreted in the device's local time zone at scheduling time so a
 * reminder lands on the intended wall-clock time even across time-zone changes
 * (Req 6.9). Stored as a portable value (not `java.time.LocalTime`) so it
 * round-trips cleanly through Room and the shared schema.
 *
 * @property hour hour of day in `0..23`.
 * @property minute minute of hour in `0..59`.
 * @property untilDate the last calendar day (inclusive) the reminder may fire.
 * @property recurring whether the reminder repeats daily until [untilDate].
 */
@Serializable
data class TaskReminder(
    val hour: Int,
    val minute: Int,
    @Serializable(with = LocalDateIso8601Serializer::class)
    val untilDate: LocalDate,
    val recurring: Boolean,
) {
    init {
        require(hour in 0..23) { "hour must be in 0..23 but was $hour" }
        require(minute in 0..59) { "minute must be in 0..59 but was $minute" }
    }
}
