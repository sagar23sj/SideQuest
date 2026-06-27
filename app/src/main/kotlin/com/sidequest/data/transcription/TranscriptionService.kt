package com.sidequest.data.transcription

import com.sidequest.domain.transcription.TranscriptionResult

/**
 * Client seam for the Transcription_Service, reached via the backend
 * Transcription Proxy (Req 10.3).
 *
 * The client never holds provider keys; it uploads the recorded audio to the
 * backend proxy, which calls the speech-to-text provider. The call is
 * time-boxed and returns a [TranscriptionResult] rather than throwing, so the
 * caller can store the outcome deterministically via
 * [com.sidequest.domain.transcription.TranscriptionOutcome]:
 * - success → [TranscriptionResult.Success] with the transcript (Req 10.4)
 * - error / unavailable / timeout → [TranscriptionResult.Failure], so the audio
 *   is retained and a failure message is shown (Req 10.8)
 *
 * The pure outcome-storage mapping of these results lives in `:domain`; this
 * interface is the network seam in `:app`. The backend Transcription Proxy
 * itself is implemented in Milestone E, so [RetrofitTranscriptionService]
 * targets the contract and is not yet wired end-to-end.
 */
interface TranscriptionService {

    /**
     * Sends the recording referenced by [audioRef] for transcription (Req 10.3)
     * and returns its outcome. Never throws: errors and timeouts are mapped to
     * [TranscriptionResult.Failure] so the caller always fails soft (Req 10.8).
     *
     * @param audioRef on-device path (or storage key) to the recorded audio.
     */
    suspend fun transcribe(audioRef: String): TranscriptionResult
}
