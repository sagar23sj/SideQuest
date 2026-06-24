package com.actiontracker.data.llm

import com.actiontracker.domain.llm.ActionItemSummary
import com.actiontracker.domain.llm.LlmFailSoft
import javax.inject.Inject

/**
 * The trigger that requests notification text from the LLM_Service when a
 * reminder is being prepared (Req 7.1).
 *
 * When the Notification_Service prepares a daily reminder it asks the
 * LLM_Service for text summarizing the relevant Action_Items. This use case is
 * that single seam: it calls [LlmService.notificationText] for the supplied
 * [summaries] and runs the result through the pure fail-soft mapping
 * [LlmFailSoft.notificationTextOrDefault], so the returned text is always
 * non-blank — the LLM prose on success, or [default] (and ultimately
 * [LlmFailSoft.GENERIC_NOTIFICATION_TEXT]) when the LLM_Service is unavailable
 * or timed out (Req 7.4, 7.5). It never blocks or throws, so a reminder is
 * always deliverable.
 *
 * Notification delivery and scheduling consume this seam in task 16; isolating
 * the trigger here keeps the LLM request testable independently of WorkManager
 * and the OS notification stack.
 */
class PrepareReminderTextUseCase @Inject constructor(
    private val llmService: LlmService,
) {

    /**
     * Requests reminder notification text for [summaries] from the LLM_Service
     * (Req 7.1) and applies fail-soft handling.
     *
     * @param summaries the items the reminder is about.
     * @param default the caller's preferred fallback text when the LLM provides
     *   nothing usable.
     * @return non-blank text suitable for the reminder notification.
     */
    suspend fun prepareNotificationText(
        summaries: List<ActionItemSummary>,
        default: String,
    ): String =
        LlmFailSoft.notificationTextOrDefault(
            result = llmService.notificationText(summaries),
            default = default,
        )
}
