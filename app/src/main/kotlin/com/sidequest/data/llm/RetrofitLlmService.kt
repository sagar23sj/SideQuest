package com.sidequest.data.llm

import com.sidequest.domain.llm.ActionItemSummary
import com.sidequest.domain.llm.ExtractedAction
import com.sidequest.domain.llm.LlmResult
import com.sidequest.domain.llm.SharedItemContent
import com.sidequest.domain.model.ActionItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Retrofit-backed [LlmService] that calls the backend LLM Proxy with a per-call
 * timeout and maps every outcome to an [LlmResult] so callers fail soft (Req 7).
 *
 * Mapping (applied uniformly across all four features):
 * - a successful proxy response → [LlmResult.Ok] with the payload
 * - exceeding [DEFAULT_TIMEOUT_MS] → [LlmResult.TimedOut] (Req 7.5)
 * - any other error (network failure, non-2xx, deserialization) →
 *   [LlmResult.Unavailable] (Req 7.4)
 *
 * The pure handling of these results (default notification text, "unavailable"
 * signals) lives in `:domain` ([com.sidequest.domain.llm.LlmFailSoft]); this
 * class is the thin network seam. The backend LLM Proxy is implemented in
 * Milestone E, so this client targets the contract and is not yet wired into the
 * notification builder or suggestion UI (those are later tasks).
 */
@Singleton
class RetrofitLlmService @Inject constructor(
    private val api: LlmProxyApi,
) : LlmService {

    override suspend fun notificationText(items: List<ActionItemSummary>): LlmResult<String> =
        call { api.notificationText(NotificationTextRequest(items)).text }

    override suspend fun suggestActions(item: ActionItem): LlmResult<List<String>> =
        call {
            api.suggestActions(
                SuggestActionsRequest(
                    title = item.title,
                    description = item.description,
                    sourceContent = item.sourceContent,
                ),
            ).suggestions
        }

    override suspend fun describe(content: SharedItemContent): LlmResult<String> =
        call { api.describe(DescribeRequest(content)).text }

    override suspend fun extractActions(transcript: String): LlmResult<List<ExtractedAction>> =
        call { api.extractActions(ExtractActionsRequest(transcript)).actions }

    /**
     * Runs [block] under [DEFAULT_TIMEOUT_MS] on the IO dispatcher and maps its
     * outcome to an [LlmResult]: success → [LlmResult.Ok], timeout →
     * [LlmResult.TimedOut] (Req 7.5), any other throwable →
     * [LlmResult.Unavailable] (Req 7.4). It never rethrows, so callers always
     * receive a terminal result to fail soft on.
     */
    private suspend fun <T> call(block: suspend () -> T): LlmResult<T> =
        try {
            val value = withTimeout(DEFAULT_TIMEOUT_MS) {
                withContext(Dispatchers.IO) { block() }
            }
            LlmResult.Ok(value)
        } catch (_: TimeoutCancellationException) {
            LlmResult.TimedOut
        } catch (_: Exception) {
            LlmResult.Unavailable
        }

    companion object {
        /** Per-call timeout for LLM Proxy requests (Req 7.5). */
        const val DEFAULT_TIMEOUT_MS: Long = 8_000
    }
}
