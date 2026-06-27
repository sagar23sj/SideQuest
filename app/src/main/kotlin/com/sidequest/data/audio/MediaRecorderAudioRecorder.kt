package com.sidequest.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.sidequest.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AudioRecorder] backed by `android.media.MediaRecorder`, writing AAC audio in
 * an MPEG-4 container to app-internal storage (Req 10.2).
 *
 * Each recording goes to `filesDir/voice_journal/<uuid>.m4a`, so the returned
 * `audioRef` is a private path no other app can read; the sync layer later
 * uploads the file and rewrites the reference to an object-storage key. The
 * recorder is lifecycle-safe: it holds at most one active `MediaRecorder`,
 * releases it on stop/cancel, and deletes the partial file on cancel or a
 * failed start.
 *
 * Blocking `MediaRecorder` calls (`prepare`, `stop`, `release`) are dispatched
 * onto the injected [ioDispatcher] so the caller's coroutine never blocks a UI
 * thread.
 */
@Singleton
class MediaRecorderAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    override val isRecording: Boolean
        get() = recorder != null

    override suspend fun start(): String = withContext(ioDispatcher) {
        check(recorder == null) { "A recording is already in progress." }

        val outputDir = File(context.filesDir, VOICE_JOURNAL_DIR).apply { mkdirs() }
        val outputFile = File(outputDir, "${UUID.randomUUID()}.$FILE_EXTENSION")

        val newRecorder = newMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(AUDIO_ENCODING_BIT_RATE)
            setAudioSamplingRate(AUDIO_SAMPLING_RATE)
            setOutputFile(outputFile.absolutePath)
        }

        try {
            newRecorder.prepare()
            newRecorder.start()
        } catch (e: Exception) {
            // Release the recorder and clean up the empty/partial file so a
            // failed start leaves no orphan recording behind.
            runCatching { newRecorder.release() }
            outputFile.delete()
            throw AudioRecordingException("Failed to start audio recording.", e)
        }

        recorder = newRecorder
        currentFile = outputFile
        outputFile.absolutePath
    }

    override suspend fun stop(): String = withContext(ioDispatcher) {
        val activeRecorder = recorder
        val activeFile = currentFile
        checkNotNull(activeRecorder) { "No recording is in progress." }
        checkNotNull(activeFile) { "No recording is in progress." }

        try {
            activeRecorder.stop()
        } catch (e: Exception) {
            // A stop failure (e.g. a recording too short to finalize) leaves an
            // unusable file; clean it up and report the failure.
            runCatching { activeRecorder.release() }
            recorder = null
            currentFile = null
            activeFile.delete()
            throw AudioRecordingException("Failed to finalize audio recording.", e)
        }

        activeRecorder.release()
        recorder = null
        currentFile = null
        activeFile.absolutePath
    }

    override suspend fun cancel() {
        withContext(ioDispatcher) {
            val activeRecorder = recorder ?: return@withContext
            runCatching { activeRecorder.stop() }
            runCatching { activeRecorder.release() }
            currentFile?.delete()
            recorder = null
            currentFile = null
        }
    }

    /**
     * Builds a [MediaRecorder] using the API-appropriate constructor. The
     * context-taking constructor is required on API 31+ (`minSdk` is 26).
     */
    private fun newMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private companion object {
        /** App-internal subdirectory holding recorded voice-journal audio. */
        const val VOICE_JOURNAL_DIR = "voice_journal"

        /** File extension for the MPEG-4/AAC recordings. */
        const val FILE_EXTENSION = "m4a"

        /** AAC encoding bit rate (128 kbps) — good quality for spoken audio. */
        const val AUDIO_ENCODING_BIT_RATE = 128_000

        /** Sampling rate (44.1 kHz) suitable for voice capture. */
        const val AUDIO_SAMPLING_RATE = 44_100
    }
}
