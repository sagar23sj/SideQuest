package com.actiontracker.data.llm

import com.actiontracker.domain.llm.LlmFailSoft
import com.actiontracker.domain.llm.LlmOutcome
import com.actiontracker.domain.model.ActionItem
import javax.inject.Inject

/**
 * The trigger that requests suggested actions for an Action_Item when the user
 * opens it (Req 7.2).
 *
 * Opening an item's detail view is the entry point that asks the LLM_Service
 * "what could I do next for this?". This use case is that single seam: it calls
 * [LlmService.suggestActions] for the opened [item] and immediately runs the
 * result through the pure fail-soft mapping [LlmFailSoft.listOrUnavailable], so
 * the caller always receives a completed [LlmOutcome] — the suggestions on
 * success, or an empty list with `unavailable == true` when the LLM_Service is
 * unavailable or timed out (Req 7.5). It never blocks or throws.
 *
 * Keeping the trigger this thin lets the item-detail UI (and its tests) depend
 * on a small, injectable seam rather than on the network client directly, and
 * keeps all fail-soft policy in `:domain`.
 */
class RequestSuggestionsUseCase @Inject constructor(
    private val llmService: LlmService,
) {

    /**
     * Requests suggested next actions for [item] from the LLM_Service (Req 7.2)
     * and applies fail-soft handling.
     *
     * @param item the Action_Item the user just opened.
     * @return an [LlmOutcome] carrying the suggestions on success, or an empty
     *   list with `unavailable == true` when the LLM_Service is unavailable or
     *   timed out.
     */
    suspend fun requestSuggestions(item: ActionItem): LlmOutcome<String> =
        LlmFailSoft.listOrUnavailable(llmService.suggestActions(item))
}
