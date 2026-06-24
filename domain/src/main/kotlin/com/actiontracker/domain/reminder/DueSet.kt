package com.actiontracker.domain.reminder

import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.Timeframe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure "due today" resolution for the daily reminder (Req 6.4). Lives in
 * `:domain` so it is portable and validated with the shared Correctness
 * Properties (Property 12) without any Android/WorkManager dependency; the
 * Notification_Service feeds it the current items, the target date, and the
 * device's zone, and uses the result to summarize the items due that day.
 *
 * The functions here are pure and total: they never mutate their inputs and
 * never throw for any input.
 *
 * ## Resolution semantics
 *
 * A [Timeframe] is anchored at the item's *capture date* — the [LocalDate] of
 * its [ActionItem.createdAt] epoch-millis timestamp, computed in a supplied
 * [ZoneId] so the logic stays pure (no implicit system clock or zone). Each
 * timeframe resolves to a *single* due date, and an item is "due" on a target
 * date `today` if and only if its resolved due date equals `today`. This
 * single-resolved-date interpretation matches Property 12's wording — items
 * "whose timeframe resolves to that date" — and keeps "exactly the items due
 * that day" well-defined and testable.
 *
 * The due date for each timeframe, given the item's capture date `created`:
 *  - [Timeframe.Today]            -> `created` (due the day it was captured)
 *  - [Timeframe.WithinADay]       -> `created + 1 day`
 *  - [Timeframe.WithinAWeek]      -> `created + 7 days`
 *  - [Timeframe.SpecificDate]`(d)`-> `d` (the chosen calendar date)
 */
object DueSet {

    /**
     * Resolves the single due date for [timeframe] given the item's capture
     * date [createdDate], per the mapping documented on [DueSet]:
     *  - [Timeframe.Today] -> [createdDate]
     *  - [Timeframe.WithinADay] -> [createdDate] + 1 day
     *  - [Timeframe.WithinAWeek] -> [createdDate] + 7 days
     *  - [Timeframe.SpecificDate] -> the carried date
     *
     * Pure and total for every input.
     */
    fun dueDate(timeframe: Timeframe, createdDate: LocalDate): LocalDate = when (timeframe) {
        Timeframe.Today -> createdDate
        Timeframe.WithinADay -> createdDate.plusDays(1)
        Timeframe.WithinAWeek -> createdDate.plusDays(7)
        is Timeframe.SpecificDate -> timeframe.date
    }

    /**
     * The capture date of [item]: the [LocalDate] of its [ActionItem.createdAt]
     * epoch-millis timestamp in [zone].
     */
    fun createdDate(item: ActionItem, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(item.createdAt).atZone(zone).toLocalDate()

    /**
     * Returns exactly those [items] whose resolved due date equals [today]
     * (Req 6.4, Property 12). Each item's capture date is derived from its
     * [ActionItem.createdAt] epoch millis in [zone], then its timeframe is
     * resolved via [dueDate].
     *
     * Input order is preserved and no item is duplicated, so the result is a
     * faithful subset of [items].
     */
    fun dueOn(items: List<ActionItem>, today: LocalDate, zone: ZoneId): List<ActionItem> =
        items.filter { item ->
            dueDate(item.timeframe, createdDate(item, zone)) == today
        }
}
