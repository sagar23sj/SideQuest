package com.actiontracker.data.audio

/**
 * A thin, injectable seam over the platform audio-capture API (Req 10.2).
 *
 * Recording captures audio to an app-internal file and continues until
 * [stop] is called. Keeping this an interface lets the repository and tests
 * depend on the capture behavior rather than `android.media.MediaRecorder`
 * directly, so the recording/persistence logic can be reasoned about without a
 * device. The default production implementation is
 * [MediaRecorderAudioRecorder].
 */
interface AudioRecorder {

    /** Whether a recording is currently in progress. */
    val isRecording: Boolean

    /**
     * Starts capturing audio to a fresh app-internal file and returns the
     * absolute path of that file (the `audioRef`). Capture continues until
     * [stop] is called (Req 10.2).
     *
     * @throws IllegalStateException if a recording is already in progress.
     * @throws AudioRecordingException if the recorder could not be started
     *   (e.g. the microphone is unavailable).
     */
    suspend fun start(): String

    /**
     * Stops the in-progress recording and finalizes the file, returning the
     * absolute path of the recorded audio (the same path returned by [start]).
     *
     * @throws IllegalStateException if no recording is in progress.
     * @throws AudioRecordingException if the recording could not be finalized.
     */
    suspend fun stop(): String

    /**
     * Cancels an in-progress recording and deletes the partial file, if any.
     * Safe to call when nothing is recording (no-op). Used to release the
     * microphone without persisting a partial capture.
     */
    suspend fun cancel()
}

/**
 * Raised when audio capture cannot start or finalize. Wraps the underlying
 * platform failure so callers can surface a recording error without depending
 * on `MediaRecorder` internals.
 */
class AudioRecordingException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
