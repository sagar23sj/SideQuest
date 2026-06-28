package com.sidequest.data.transcription

/**
 * On-device, live speech-to-text seam used while a voice journal is being
 * recorded (Req 10.3, on-device transcription).
 *
 * Unlike [TranscriptionService] (which transcribes a finished audio file via the
 * backend), this runs the platform's *on-device* recognizer on the live
 * microphone stream so transcription works fully offline on supported devices.
 * It is intentionally fail-soft: when on-device recognition is unavailable or
 * errors, [stop] returns null and the caller falls back to the file-based
 * [TranscriptionService].
 */
interface LiveTranscriber {

    /** True when on-device live recognition can be used on this device. */
    val isAvailable: Boolean

    /** Begins listening to the microphone (no-op when [isAvailable] is false). */
    suspend fun start()

    /**
     * Stops listening and returns the accumulated transcript, or null when
     * nothing was recognized / recognition was unavailable or failed.
     */
    suspend fun stop(): String?

    /** Cancels listening and discards any partial transcript. */
    suspend fun cancel()
}

/** A no-op [LiveTranscriber] for environments without on-device recognition. */
class NoOpLiveTranscriber : LiveTranscriber {
    override val isAvailable: Boolean = false
    override suspend fun start() = Unit
    override suspend fun stop(): String? = null
    override suspend fun cancel() = Unit
}
