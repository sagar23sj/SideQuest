package com.sidequest.domain.llm

/**
 * Outcome of a time-boxed call to the LLM_Service (via the backend LLM Proxy).
 *
 * Every LLM-backed feature — notification text (Req 7.1), action suggestions
 * (Req 7.2), task descriptions (Req 7.3), and transcript action extraction
 * (Req 10.5) — returns one of three terminal states so callers can *fail soft*
 * rather than block or error:
 *
 * - [Ok] — the proxy returned a usable value.
 * - [Unavailable] — the LLM_Service is unavailable or returned an error
 *   (Req 7.4); the caller falls back to default behavior.
 * - [TimedOut] — the LLM_Service did not respond within the configured timeout
 *   (Req 7.5); the caller proceeds without LLM content and informs the user that
 *   suggestions are unavailable.
 *
 * It lives in `:domain` (rather than alongside the network client in `:app`) so
 * the pure fail-soft logic in [LlmFailSoft] and its Correctness Property
 * (Property 13) are testable without any Android or networking dependency. The
 * actual HTTP call to the backend LLM Proxy lives in `:app` as a thin
 * `LlmService` implementation that produces the [LlmResult] this logic consumes.
 *
 * @param T the success payload type (e.g. `String` for notification text, or
 *   `List<String>` for suggestions).
 */
sealed interface LlmResult<out T> {

    /**
     * The LLM_Service returned a usable [value].
     *
     * @property value the payload returned by the proxy. Fail-soft helpers still
     *   validate the payload (e.g. notification text must be non-blank) before
     *   trusting it, so an [Ok] does not by itself guarantee a usable result.
     */
    data class Ok<T>(val value: T) : LlmResult<T>

    /**
     * The LLM_Service is unavailable or returned an error (Req 7.4).
     */
    data object Unavailable : LlmResult<Nothing>

    /**
     * The LLM_Service did not respond within the configured timeout (Req 7.5).
     */
    data object TimedOut : LlmResult<Nothing>
}
