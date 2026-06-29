package com.sidequest.data.transcription

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * [LiveTranscriber] backed by Android's [SpeechRecognizer]. It listens to the
 * microphone while a journal is recording and accumulates finalized phrases into
 * a single transcript, restarting after each utterance so longer entries are
 * captured. It prefers the on-device recognizer when the device has it, but
 * falls back to the default recognizer (which on most phones still works,
 * on-device or via Google) so transcription is available broadly.
 *
 * Everything is defensive and fail-soft: the recognizer is created and driven on
 * the main thread (required by the platform), every platform call is wrapped, and
 * any failure simply yields a null transcript so the caller falls back to the
 * file-based backend [TranscriptionService]. Diagnostic logging is emitted under
 * the [TAG] tag.
 */
@Singleton
class SpeechRecognizerLiveTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
) : LiveTranscriber {

    private val transcript = StringBuilder()
    private var lastPartial: String = ""
    private var recognizer: SpeechRecognizer? = null

    @Volatile private var stopped = false

    override val isAvailable: Boolean
        get() = runCatching {
            val available = SpeechRecognizer.isRecognitionAvailable(context)
            val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            Log.w(TAG, "isAvailable: recognitionAvailable=$available onDevice=$onDevice sdk=${Build.VERSION.SDK_INT}")
            available
        }.getOrElse {
            Log.w(TAG, "isAvailable check threw", it)
            false
        }

    override suspend fun start() {
        if (!isAvailable) {
            Log.w(TAG, "start: recognition not available; skipping live transcription")
            return
        }
        withContext(Dispatchers.Main) {
            runCatching {
                stopped = false
                transcript.setLength(0)
                lastPartial = ""
                recognizer = createRecognizer().apply { setRecognitionListener(listener) }
                Log.w(TAG, "start: listening")
                beginListening()
            }.onFailure { Log.w(TAG, "start failed", it) }
        }
    }

    override suspend fun stop(): String? {
        val rec = recognizer ?: return transcript.toString().trim().ifBlank { null }
        withContext(Dispatchers.Main) {
            stopped = true
            runCatching { rec.stopListening() }
        }
        delay(FINALIZE_DELAY_MS)
        withContext(Dispatchers.Main) {
            commitPartial()
            runCatching { rec.destroy() }
            recognizer = null
        }
        val result = transcript.toString().trim().ifBlank { null }
        Log.w(TAG, "stop: transcript=${result?.take(80) ?: "<null>"}")
        return result
    }

    override suspend fun cancel() {
        val rec = recognizer ?: return
        withContext(Dispatchers.Main) {
            stopped = true
            runCatching { rec.cancel() }
            runCatching { rec.destroy() }
            recognizer = null
            transcript.setLength(0)
            lastPartial = ""
        }
    }

    /** Prefer the on-device recognizer when present; otherwise the default one. */
    private fun createRecognizer(): SpeechRecognizer {
        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        return if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.w(TAG, "createRecognizer: on-device")
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            Log.w(TAG, "createRecognizer: default")
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    /** Must be called on the main thread. */
    private fun beginListening() {
        runCatching { recognizer?.startListening(recognizerIntent()) }
            .onFailure { Log.w(TAG, "startListening failed", it) }
    }

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    private fun append(text: String) {
        val phrase = text.trim()
        if (phrase.isEmpty()) return
        if (transcript.isNotEmpty()) transcript.append(' ')
        transcript.append(phrase)
        lastPartial = ""
    }

    private fun commitPartial() {
        if (lastPartial.isNotBlank()) append(lastPartial)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            Log.w(TAG, "onResults: '${text.take(80)}'")
            append(text)
            if (!stopped) restart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) lastPartial = text
        }

        override fun onError(error: Int) {
            Log.w(TAG, "onError: $error (${errorName(error)}) stopped=$stopped")
            if (stopped) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                -> restart()
                else -> Unit
            }
        }

        override fun onReadyForSpeech(params: Bundle?) { Log.w(TAG, "onReadyForSpeech") }
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun restart() {
        if (stopped) return
        runCatching { recognizer?.startListening(recognizerIntent()) }
    }

    private fun errorName(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
        SpeechRecognizer.ERROR_SERVER -> "SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
        else -> "OTHER"
    }

    private companion object {
        const val TAG = "SQVoice"
        const val FINALIZE_DELAY_MS = 600L
    }
}
