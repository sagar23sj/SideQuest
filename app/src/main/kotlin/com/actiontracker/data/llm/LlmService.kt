package com.actiontracker.data.llm

import com.actiontracker.domain.llm.ActionItemSummary
import com.actiontracker.domain.llm.ExtractedAction
import com.actiontracker.domain.llm.LlmResult
import com.actiontracker.domain.llm.SharedItemContent
import com.actiontracker.domain.model.ActionItem

/**
 * Client seam for the LLM_Service, reached via the backend LLM Proxy (Req 7).
 *
 * The client never holds provider keys; it calls the backend proxy, which calls
 * the LLM provider. Every method is time-boxed and returns an [LlmResult] rather
 * than throwing, so callers can *fail soft* via
 * [com.actiontracker.domain.llm.LlmFailSoft]:
 * - success → [LlmResult.Ok]
 * - timeout → [LlmResult.TimedOut] (Req 7.5)
 * - any other error / unavailable → [LlmResult.Unavailable] (Req 7.4)
 *
 * The pure fail-soft mapping of these results lives in `:domain`; this interface
 * is the network seam in `:app`. The backend LLM Proxy itself is implemented in
 * Milestone E, so [RetrofitLlmService] targets the contract and is not yet wired
 * end-to-end.
 */
interface LlmService {

    /**
     * Requests reminder notification text summarizing [items] (Req 7.1).
     */
    suspend fun notificationText(items: List<ActionItemSummary>): LlmResult<String>

    /**
     * Requests suggested next actions for [item] (Req 7.2).
     */
    suspend fun suggestActions(item: ActionItem): LlmResult<List<String>>

    /**
     * Requests a task description summarizing the shared [content] (Req 7.3).
     */
    suspend fun describe(content: SharedItemContent): LlmResult<String>

    /**
     * Requests actionable items extracted from a voice-journal [transcript]
     * (Req 10.5).
     */
    suspend fun extractActions(transcript: String): LlmResult<List<ExtractedAction>>
}
