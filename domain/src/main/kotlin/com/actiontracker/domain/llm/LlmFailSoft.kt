package com.actiontracker.domain.llm

/**
 * The outcome of an LLM-backed list flow (action suggestions per item, Req 7.2,
 * or extracted actions, Req 10.5) after fail-soft handling.
 *
 * Rather than throwing or blocking when the LLM_Service is unavailable or timed
 * out, callers receive a completed outcome carrying a [unavailable] signal the
 * UI shows as "suggestions unavailable" (Req 7.5). On [LlmResult.Ok] the payload
 * is carried through and [unavailable] is false.
 *
 * @property values the items produced by the LLM (empty when unavailable).
 * @property unavailable true when the LLM_Service was unavailable or timed out,
 *   so the UI surfaces an "unavailable" signal instead of blocking or erroring.
 */
data class LlmOutcome<out T>(
    val values: List<T>,
    val unavailable: Boolean,
)

/**
 * The outcome of an LLM-backed single-value description flow (Req 7.3) after
 * fail-soft handling.
 *
 * Mirrors [LlmOutcome] for a single optional value: on [LlmResult.Ok] the
 * description is carried through with [unavailable] false; otherwise [value] is
 * null and [unavailable] is true so the UI can show that the description is
 * unavailable rather than blocking or erroring (Req 7.5).
 *
 * @property value the generated description, or null when unavailable.
 * @property unavailable true when the LLM_Service was unavailable or timed out.
 */
data class LlmTextOutcome(
    val value: String?,
    val unavailable: Boolean,
)

/**
 * Pure fail-soft logic for LLM-backed features — the heart of Property 13.
 *
 * This object turns an [LlmResult] into a value the caller can always use:
 * notification text always resolves to non-empty text (Req 7.4), and
 * suggestion/description flows always *complete* with an explicit "unavailable"
 * signal rather than blocking or erroring (Req 7.5). It lives in `:domain` with
 * no Android/networking dependency so Property 13 validates it directly.
 *
 * Every function here is pure and total: it never mutates its inputs, never
 * throws for any input, and always returns a usable result.
 */
object LlmFailSoft {

    /**
     * A constant, guaranteed-non-empty fallback used when even the caller's
     * supplied default is blank, so [notificationTextOrDefault] can never return
     * empty text (Req 7.4).
     */
    const val GENERIC_NOTIFICATION_TEXT: String = "You have action items to review."

    /**
     * Resolves the text to use for a reminder notification (Req 7.1, 7.4).
     *
     * Returns the LLM value only when [result] is [LlmResult.Ok] *and* that
     * value is non-blank; otherwise it falls back to [default]. If [default] is
     * itself blank, it falls back further to [GENERIC_NOTIFICATION_TEXT]. The
     * returned text is therefore always non-blank, so a reminder is always
     * deliverable with usable text regardless of the LLM outcome.
     *
     * @param result the LLM_Service outcome for the notification text request.
     * @param default the caller's preferred default text when the LLM provides
     *   nothing usable.
     * @return non-blank text suitable for the notification.
     */
    fun notificationTextOrDefault(result: LlmResult<String>, default: String): String {
        val llmText = (result as? LlmResult.Ok)?.value
        return when {
            llmText != null && llmText.isNotBlank() -> llmText
            default.isNotBlank() -> default
            else -> GENERIC_NOTIFICATION_TEXT
        }
    }

    /**
     * Resolves an LLM-backed list flow (suggestions Req 7.2, extracted actions
     * Req 10.5) into a completed [LlmOutcome] (Req 7.5).
     *
     * - [LlmResult.Ok] → the carried list with `unavailable == false`.
     * - [LlmResult.Unavailable] / [LlmResult.TimedOut] → an empty list with
     *   `unavailable == true`, so the flow completes and the UI can show
     *   "suggestions unavailable" rather than blocking or erroring.
     */
    fun <T> listOrUnavailable(result: LlmResult<List<T>>): LlmOutcome<T> =
        when (result) {
            is LlmResult.Ok -> LlmOutcome(values = result.value, unavailable = false)
            LlmResult.Unavailable, LlmResult.TimedOut ->
                LlmOutcome(values = emptyList(), unavailable = true)
        }

    /**
     * Resolves an LLM-backed description flow (Req 7.3) into a completed
     * [LlmTextOutcome] (Req 7.5).
     *
     * - [LlmResult.Ok] → the carried description with `unavailable == false`.
     * - [LlmResult.Unavailable] / [LlmResult.TimedOut] → a null value with
     *   `unavailable == true`, so the flow completes with an "unavailable"
     *   signal rather than blocking or erroring.
     */
    fun descriptionOrUnavailable(result: LlmResult<String>): LlmTextOutcome =
        when (result) {
            is LlmResult.Ok -> LlmTextOutcome(value = result.value, unavailable = false)
            LlmResult.Unavailable, LlmResult.TimedOut ->
                LlmTextOutcome(value = null, unavailable = true)
        }
}
