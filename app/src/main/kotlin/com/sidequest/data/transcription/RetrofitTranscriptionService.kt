package com.sidequest.data.transcription

import com.sidequest.domain.transcription.TranscriptionResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * Retrofit-backed [TranscriptionService] that uploads recorded audio to the
 * backend Transcription Proxy with a per-call timeout and maps every outcome to
 * a [TranscriptionResult] so the caller fails soft (Req 10.8).
 *
 * Mapping:
 * - a successful proxy response → [TranscriptionResult.Success] with the
 *   transcript (Req 10.4)
 * - exceeding [DEFAULT_TIMEOUT_MS] → [TranscriptionResult.Failure] (Req 10.8)
 * - any other error (network failure, non-2xx, missing audio, deserialization)
 *   → [TranscriptionResult.Failure] (Req 10.8)
 *
 * The pure storage of these results (preserving the audio reference, setting
 * `transcriptionFailed`) lives in `:domain`
 * ([com.sidequest.domain.transcription.TranscriptionOutcome]); this class is
 * the thin network seam. The backend Transcription Proxy is implemented in
 * Milestone E, so this client targets the contract and is not yet wired
 * end-to-end.
 */
@Singleton
class RetrofitTranscriptionService @Inject constructor(
    private val api: TranscriptionProxyApi,
) : TranscriptionService {

    override suspend fun transcribe(audioRef: String): TranscriptionResult =
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val file = File(audioRef)
                    val body = file.asRequestBody(AUDIO_MEDIA_TYPE.toMediaType())
                    val part = MultipartBody.Part.createFormData("audio", file.name, body)
                    val transcript = api.transcribe(part).transcript
                    TranscriptionResult.Success(transcript)
                }
            }
        } catch (_: TimeoutCancellationException) {
            TranscriptionResult.Failure
        } catch (_: Exception) {
            TranscriptionResult.Failure
        }

    companion object {
        /** Per-call timeout for Transcription Proxy uploads (Req 10.8). */
        const val DEFAULT_TIMEOUT_MS: Long = 30_000

        /** Media type sent for the uploaded audio part. */
        const val AUDIO_MEDIA_TYPE: String = "audio/mp4"
    }
}
