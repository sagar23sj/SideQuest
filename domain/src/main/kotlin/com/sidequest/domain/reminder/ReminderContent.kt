package com.sidequest.domain.reminder

import com.sidequest.domain.llm.ActionItemSummary

/**
 * Pure decision logic for *what* a daily reminder should say (Req 6.4, 6.6).
 *
 * Lives in `:domain` so it is portable and unit-testable without any
 * Android/WorkManager/notification dependency: the Notification_Service
 * ([com.sidequest.data.reminder.DailyReminderWorker]) first resolves the
 * items due that day via [DueSet.dueOn], summarizes them, and then asks this
 * object to choose the reminder content:
 *  - when at least one item is due → a [ReminderPlan.DueItems] carrying the
 *    summaries, whose notification text the caller curates via the LLM_Service
 *    (Req 7.1) and falls back to [defaultDueText] on error/timeout (Req 7.4);
 *  - when nothing is due → a [ReminderPlan.ReviewUpcoming] whose notification
 *    text is the fixed, always-non-empty [REVIEW_UPCOMING_TEXT] prompting the
 *    user to review upcoming Action_Items (Req 6.6). The empty-due-set reminder
 *    does not need LLM text — it is a fixed prompt.
 *
 * Every function here is pure and total: it never mutates its inputs and never
 * throws for any input.
 */
object ReminderContent {

    /**
     * The fixed, guaranteed-non-empty prompt delivered when no Action_Items are
     * due on a given day (Req 6.6). A plain constant rather than LLM-generated
     * text, so the empty-due-set reminder is always deliverable.
     */
    const val REVIEW_UPCOMING_TEXT: String =
        "Nothing is due today. Take a moment to review your upcoming action items."

    /**
     * The reminder content chosen for a given day's due-set.
     *
     * A sealed type so the two mutually exclusive outcomes — there are items due
     * today, or there are not — are exhaustive and the caller cannot forget the
     * empty case (Req 6.6). [hasDueItems] is a convenience discriminator for
     * call sites that only need the boolean.
     */
    sealed interface ReminderPlan {

        /** True when at least one Action_Item is due today. */
        val hasDueItems: Boolean

        /**
         * At least one Action_Item is due today (Req 6.4). The reminder
         * summarizes [summaries]; the caller curates the notification text via
         * the LLM_Service (Req 7.1), falling back to [defaultDueText] on
         * error/timeout (Req 7.4).
         *
         * @property summaries the non-empty summaries of the items due today.
         */
        data class DueItems(val summaries: List<ActionItemSummary>) : ReminderPlan {
            override val hasDueItems: Boolean get() = true
        }

        /**
         * No Action_Items are due today (Req 6.6). The reminder uses the fixed
         * [REVIEW_UPCOMING_TEXT] prompt; no LLM text is required.
         */
        data object ReviewUpcoming : ReminderPlan {
            override val hasDueItems: Boolean get() = false
        }
    }

    /**
     * Chooses the reminder content for a day given the items due that day
     * (Req 6.4, 6.6).
     *
     * - [dueItems] non-empty → [ReminderPlan.DueItems] carrying the summaries.
     * - [dueItems] empty → [ReminderPlan.ReviewUpcoming], the "review upcoming"
     *   prompt.
     *
     * Pure and total for every input.
     */
    fun plan(dueItems: List<ActionItemSummary>): ReminderPlan =
        if (dueItems.isEmpty()) {
            ReminderPlan.ReviewUpcoming
        } else {
            ReminderPlan.DueItems(dueItems)
        }

    /**
     * A non-empty default reminder text summarizing how many Action_Items are
     * due today, used as the fallback passed to the LLM_Service request so the
     * notification always has usable text even when the LLM is unavailable or
     * times out (Req 7.4).
     *
     * @param count the number of items due today; coerced to at least 1 since
     *   the due-items case only arises with a non-empty due-set.
     */
    fun defaultDueText(count: Int): String {
        val safeCount = count.coerceAtLeast(1)
        return if (safeCount == 1) {
            "You have 1 action item due today."
        } else {
            "You have $safeCount action items due today."
        }
    }
}
