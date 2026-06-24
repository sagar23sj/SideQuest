package com.actiontracker.domain.transcription

/**
 * Outcome of a time-boxed call to the Transcription_Service (via the backend
 * Transcription Proxy, Req 10.3).
 *
 * A stopped recording is sent for transcription and resolves to one of two
 * terminal states so the caller can store the result deterministically without
 * blocking or throwing:
 *
 * - [Success] — a transcript was produced (Req 10.4).
 * - [Failure] — transcription failed, was unavailable, or timed out; the audio
 *   is retained and a failure message is shown (Req 10.8).
 *
 * Per Property 20 only the success-vs-failure distinction matters for storage,
 * so timeout and any other error are folded into [Failure]. This type lives in
 * `:domain` (rather than alongside the network client in `:app`) so the pure
 * outcome-storage logic in [TranscriptionOutcome] and its Correctness Property
 * (Property 20) are testable without any Android or networking dependency. The
 * actual HTTP upload to the backend Transcription Proxy lives in `:app` as a
 * thin client that produces the [TranscriptionResult] this logic consumes.
 */
sealed interface TranscriptionResult {

    /**
     * The Transcription_Service produced a [transcript] for the recording
     * (Req 10.4).
     *
     * @property transcript the generated transcript text.
     */
    data class Success(val transcript: String) : TranscriptionResult

    /**
     * Transcription failed, was unavailable, or timed out (Req 10.8). The audio
     * recording is retained and the user is shown a failure message; they can
     * retry transcription later.
     */
    data object Failure : TranscriptionResult
}
