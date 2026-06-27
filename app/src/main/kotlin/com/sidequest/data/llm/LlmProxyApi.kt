package com.sidequest.data.llm

import com.sidequest.domain.llm.ActionItemSummary
import com.sidequest.domain.llm.ExtractedAction
import com.sidequest.domain.llm.SharedItemContent
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit contract for the backend LLM Proxy (Req 7.1, 7.2, 7.3, 10.5).
 *
 * The proxy keeps provider keys server-side and exposes one endpoint per LLM
 * feature. Request/response payloads reuse the portable `:domain` contract types
 * ([ActionItemSummary], [SharedItemContent], [ExtractedAction]) so client and
 * server share one schema. The backend that serves these routes is implemented
 * in Milestone E; this interface defines the contract the thin
 * [RetrofitLlmService] targets now.
 *
 * All calls are `suspend` so [RetrofitLlmService] can wrap them in a per-call
 * timeout and map outcomes to [com.sidequest.domain.llm.LlmResult].
 */
interface LlmProxyApi {

    /** Generates reminder notification text from the supplied item summaries (Req 7.1). */
    @POST("llm/notification-text")
    suspend fun notificationText(@Body request: NotificationTextRequest): TextResponse

    /** Generates suggested next actions for an item (Req 7.2). */
    @POST("llm/suggest-actions")
    suspend fun suggestActions(@Body request: SuggestActionsRequest): SuggestionsResponse

    /** Generates a task description from shared content (Req 7.3). */
    @POST("llm/describe")
    suspend fun describe(@Body request: DescribeRequest): TextResponse

    /** Extracts actionable items from a transcript (Req 10.5). */
    @POST("llm/extract-actions")
    suspend fun extractActions(@Body request: ExtractActionsRequest): ExtractedActionsResponse
}

/** Request body for [LlmProxyApi.notificationText]. */
@Serializable
data class NotificationTextRequest(val items: List<ActionItemSummary>)

/** Request body for [LlmProxyApi.suggestActions]. */
@Serializable
data class SuggestActionsRequest(
    val title: String,
    val description: String? = null,
    val sourceContent: String? = null,
)

/** Request body for [LlmProxyApi.describe]. */
@Serializable
data class DescribeRequest(val content: SharedItemContent)

/** Request body for [LlmProxyApi.extractActions]. */
@Serializable
data class ExtractActionsRequest(val transcript: String)

/** A single-text response (notification text, description). */
@Serializable
data class TextResponse(val text: String)

/** A list-of-strings response (action suggestions). */
@Serializable
data class SuggestionsResponse(val suggestions: List<String>)

/** A list-of-actions response (transcript extraction). */
@Serializable
data class ExtractedActionsResponse(val actions: List<ExtractedAction>)
