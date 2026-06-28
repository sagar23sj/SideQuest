package com.sidequest.data.transcription

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * [LiveTranscriber] backed by Android's on-device [SpeechRecognizer]
 * (Android 12+ `createOnDeviceSpeechRecognizer`). It listens to the microphone
 * while a journal is recording and accumulates finalized phrases into a single
 * transcript, restarting after each utterance so longer entries are captured.
 *
 * Everything is defensive and fail-soft: the recognizer is created and driven on
 * the main thread (required by the platform), every platform call is wrapped, and
 * any failure simply yields a null transcript so the caller falls back to the
 * file-based backend [TranscriptionService]. It never touches the recorded audio
 * file, so the audio capture path is unaffected even if recognition fails.
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
            if (!SpeechRecognizer.isRecognitionAvailable(context)) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            } else {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            }
        }.getOrDefault(false)

    override suspend fun start() {
        if (!isAvailable) return
        withContext(Dispatchers.Main) {
            runCatching {
                stopped = false
                transcript.setLength(0)
                lastPartial = ""
                recognizer = createRecognizer().apply {
                    setRecognitionListener(listener)
                }
                beginListening()
            }
        }
    }

    override suspend fun stop(): String? {
        val rec = recognizer ?: return transcript.toString().trim().ifBlank { null }
        withContext(Dispatchers.Main) {
            stopped = true
            runCatching { rec.stopListening() }
        }
        // Give the recognizer a brief window to deliver its final results.
        delay(FINALIZE_DELAY_MS)
        withContext(Dispatchers.Main) {
            commitPartial()
            runCatching { rec.destroy() }
            recognizer = null
        }
        return transcript.toString().trim().ifBlank { null }
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

    private fun createRecognizer(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    /** Must be called on the main thread. */
    private fun beginListening() {
        runCatching { recognizer?.startListening(recognizerIntent()) }
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

    /** Folds the last partial hypothesis into the transcript when finalizing. */
    private fun commitPartial() {
        if (lastPartial.isNotBlank()) {
            append(lastPartial)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            append(text)
            if (!stopped) restart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) lastPartial = text
        }

        override fun onError(error: Int) {
            // Transient end-of-utterance/silence errors just mean "start the next
            // phrase"; keep listening until the caller stops. Other errors end
            // the session gracefully (the caller falls back).
            if (stopped) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                -> restart()
                else -> Unit
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /** Restart a fresh utterance; guarded so a destroyed recognizer is ignored. */
    private fun restart() {
        if (stopped) return
        runCatching { recognizer?.startListening(recognizerIntent()) }
    }

    private companion object {
        const val FINALIZE_DELAY_MS = 600L
    }
}
