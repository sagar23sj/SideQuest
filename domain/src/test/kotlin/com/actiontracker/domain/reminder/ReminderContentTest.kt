package com.actiontracker.domain.reminder

import com.actiontracker.domain.llm.ActionItemSummary
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Example/smoke tests for the daily-reminder content decision (Req 6.6).
 *
 * [ReminderContent.plan] is the pure choice between the two mutually exclusive
 * reminder outcomes:
 *  - an empty due-set yields the fixed "review upcoming" prompt
 *    ([ReminderContent.ReminderPlan.ReviewUpcoming]) whose text is the
 *    always-non-empty [ReminderContent.REVIEW_UPCOMING_TEXT] (Req 6.6);
 *  - a non-empty due-set yields [ReminderContent.ReminderPlan.DueItems]
 *    carrying exactly the supplied summaries (Req 6.4).
 *
 * These are concrete example tests (no property traceability) that lock in the
 * empty-due-set behaviour the Notification_Service relies on, plus the
 * non-empty default text used as the LLM fallback. They run pure-JVM in
 * `:domain` with no Android dependency.
 *
 * _Requirements: 6.6_
 */
class ReminderContentTest : StringSpec({

    "empty due-set yields the review-upcoming reminder" {
        val plan = ReminderContent.plan(emptyList())

        plan.shouldBeInstanceOf<ReminderContent.ReminderPlan.ReviewUpcoming>()
        plan.hasDueItems shouldBe false
    }

    "review-upcoming text is the fixed, non-empty prompt" {
        ReminderContent.REVIEW_UPCOMING_TEXT.shouldNotBeBlank()
    }

    "non-empty due-set yields a DueItems plan carrying the supplied summaries" {
        val summaries = listOf(
            ActionItemSummary(title = "Book flights", bucketName = "Travel", dueDescription = "today"),
            ActionItemSummary(title = "Buy onions", bucketName = "Cooking"),
        )

        val plan = ReminderContent.plan(summaries)

        plan.shouldBeInstanceOf<ReminderContent.ReminderPlan.DueItems>()
        plan.hasDueItems shouldBe true
        plan.summaries shouldContainExactly summaries
    }

    "a single due item still yields DueItems, not the review-upcoming prompt" {
        val summaries = listOf(ActionItemSummary(title = "Renew passport", bucketName = "Travel"))

        val plan = ReminderContent.plan(summaries)

        plan.shouldBeInstanceOf<ReminderContent.ReminderPlan.DueItems>()
        plan.summaries shouldContainExactly summaries
    }

    "defaultDueText is non-empty for any non-negative count" {
        // The due-items case only arises for a non-empty due-set, but the text
        // must remain usable as the LLM fallback (Req 7.4) for any count >= 0,
        // including the coerced lower bound.
        listOf(0, 1, 2, 5, 100).forEach { count ->
            ReminderContent.defaultDueText(count).shouldNotBeBlank()
        }
    }

    "defaultDueText uses the singular form for a single item and plural otherwise" {
        ReminderContent.defaultDueText(1) shouldBe "You have 1 action item due today."
        ReminderContent.defaultDueText(3) shouldBe "You have 3 action items due today."
        // count is coerced to at least 1, so zero reads as the singular form.
        ReminderContent.defaultDueText(0) shouldBe "You have 1 action item due today."
    }
})
