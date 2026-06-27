package com.sidequest.domain.transcription

import com.sidequest.domain.model.VoiceJournalEntry

/**
 * Pure logic that applies a [TranscriptionResult] to a [VoiceJournalEntry] —
 * the heart of Property 20.
 *
 * After a recording is stopped and persisted (with `transcript == null` and
 * `transcriptionFailed == false`), it is sent for transcription (Req 10.3). The
 * service's outcome is folded into the stored entry by [applyTranscription]:
 *
 * - [TranscriptionResult.Success] → the [transcript] is filled in and
 *   `transcriptionFailed` is cleared to false (Req 10.4).
 * - [TranscriptionResult.Failure] → the [VoiceJournalEntry.transcript] is left
 *   null and `transcriptionFailed` is set true so the audio is retained and the
 *   UI shows a failure message (Req 10.8).
 *
 * In both cases the [VoiceJournalEntry.audioRef] (and every other field) is
 * preserved. The function lives in `:domain` with no Android/networking
 * dependency so Property 20 validates it directly.
 *
 * Every function here is pure and total: it never mutates its input, never
 * throws for any input, and always returns a usable result.
 */
object TranscriptionOutcome {

    /**
     * Returns [entry] updated to reflect the transcription [result] (Req 10.4,
     * 10.8), preserving the audio reference and all identity/sync fields.
     *
     * - On [TranscriptionResult.Success], the transcript is stored and
     *   `transcriptionFailed` is false.
     * - On [TranscriptionResult.Failure], the transcript stays null and
     *   `transcriptionFailed` is true (audio retained for retry).
     *
     * @param entry the persisted entry to update (carries the `audioRef`).
     * @param result the Transcription_Service outcome.
     * @return a copy of [entry] with `transcript` / `transcriptionFailed` set
     *   per the rules above; `audioRef` is always preserved.
     */
    fun applyTranscription(
        entry: VoiceJournalEntry,
        result: TranscriptionResult,
    ): VoiceJournalEntry =
        when (result) {
            is TranscriptionResult.Success ->
                entry.copy(transcript = result.transcript, transcriptionFailed = false)
            TranscriptionResult.Failure ->
                entry.copy(transcript = null, transcriptionFailed = true)
        }
}
