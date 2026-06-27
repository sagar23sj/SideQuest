package com.sidequest.data.transcription

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit contract for the backend Transcription Proxy (Req 10.3).
 *
 * The proxy keeps speech-to-text provider keys server-side and exposes a single
 * endpoint that accepts the recorded audio as a multipart upload and returns the
 * generated transcript. The backend that serves this route is implemented in
 * Milestone E; this interface defines the contract the thin
 * [RetrofitTranscriptionService] targets now.
 *
 * The call is `suspend` so [RetrofitTranscriptionService] can wrap it in a
 * per-call timeout and map outcomes to
 * [com.sidequest.domain.transcription.TranscriptionResult].
 */
interface TranscriptionProxyApi {

    /**
     * Uploads the recorded audio for transcription and returns the transcript
     * (Req 10.3).
     *
     * @param audio the recorded audio file part.
     */
    @Multipart
    @POST("transcription/transcribe")
    suspend fun transcribe(@Part audio: MultipartBody.Part): TranscriptResponse
}

/** Response carrying the generated transcript text (Req 10.4). */
@Serializable
data class TranscriptResponse(val transcript: String)
