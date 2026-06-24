package com.actiontracker.data.llm

import com.actiontracker.domain.llm.ActionItemSummary
import com.actiontracker.domain.llm.LlmFailSoft
import com.actiontracker.domain.llm.LlmResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

/**
 * Example test proving that preparing a reminder requests notification text from
 * the LLM_Service (Req 7.1).
 *
 * [PrepareReminderTextUseCase] is the trigger the Notification_Service invokes
 * when building a daily reminder. The behavior under test is the *request
 * itself*: the use case must call [LlmService.notificationText] with the item
 * summaries the reminder is about, and resolve the text through the pure
 * fail-soft mapping ([LlmFailSoft.notificationTextOrDefault]) so a reminder is
 * always deliverable with non-blank text. The [LlmService] is mocked so the
 * test verifies the trigger without any network.
 *
 * _Requirements: 7.1_
 */
class PrepareReminderTextUseCaseTest : StringSpec({

    val summaries = listOf(
        ActionItemSummary(title = "Call the dentist", bucketName = "Health", dueDescription = "today"),
        ActionItemSummary(title = "Read Kyoto article", bucketName = "Travel", dueDescription = "today"),
    )

    "preparing a reminder requests LLM text for the relevant items" {
        runTest {
            val llmText = "Two things to tackle today: a dentist call and your Kyoto reading."
            val llmService: LlmService = mockk {
                coEvery { notificationText(summaries) } returns LlmResult.Ok(llmText)
            }

            val text = PrepareReminderTextUseCase(llmService)
                .prepareNotificationText(summaries, default = "You have items due today.")

            // The trigger must reach the LLM_Service with exactly the summaries.
            coVerify(exactly = 1) { llmService.notificationText(summaries) }
            // ...and use the returned prose for the reminder.
            text shouldBe llmText
        }
    }

    "preparing a reminder falls back to default text when the LLM is unavailable" {
        runTest {
            val default = "You have items due today."
            val llmService: LlmService = mockk {
                coEvery { notificationText(summaries) } returns LlmResult.TimedOut
            }

            val text = PrepareReminderTextUseCase(llmService)
                .prepareNotificationText(summaries, default = default)

            // The request is still made when preparing the reminder; the text
            // fails soft to the default so the reminder is still deliverable
            // (Req 7.4, 7.5).
            coVerify(exactly = 1) { llmService.notificationText(summaries) }
            text shouldBe default
        }
    }

    "preparing a reminder yields non-blank text even with a blank default" {
        runTest {
            val llmService: LlmService = mockk {
                coEvery { notificationText(summaries) } returns LlmResult.Unavailable
            }

            val text = PrepareReminderTextUseCase(llmService)
                .prepareNotificationText(summaries, default = "")

            coVerify(exactly = 1) { llmService.notificationText(summaries) }
            text shouldBe LlmFailSoft.GENERIC_NOTIFICATION_TEXT
        }
    }
})
