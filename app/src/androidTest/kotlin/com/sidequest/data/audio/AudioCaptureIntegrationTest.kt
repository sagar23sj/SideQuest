package com.sidequest.data.audio

import android.Manifest
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Integration test for audio capture (Req 10.2).
 *
 * Exercises the real [MediaRecorderAudioRecorder] against the instrumentation
 * context to verify that a start/stop recording cycle yields a *playable* file
 * reference: the returned `audioRef` points at a non-empty file that a media
 * framework component ([MediaMetadataRetriever]) can open and read duration
 * metadata from, confirming it is a valid media container rather than an empty
 * or corrupt stub.
 *
 * This is an instrumentation test: it requires a device or emulator with a
 * microphone and the Android SDK, so it runs from the `androidTest` source set
 * (`./gradlew connectedAndroidTest`) and cannot execute as a local JVM unit
 * test.
 */
@RunWith(AndroidJUnit4::class)
class AudioCaptureIntegrationTest {

    /**
     * Grants RECORD_AUDIO for the test process so [MediaRecorder] can open the
     * microphone without an interactive permission prompt (Req 10.1 covers the
     * runtime request; this test focuses on capture in Req 10.2).
     */
    @get:Rule
    val microphonePermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private var recordedFile: File? = null

    @After
    fun cleanUp() {
        // Keep the test idempotent: remove the captured file so repeat runs
        // start clean and no recording is left in app-internal storage.
        recordedFile?.delete()
        recordedFile = null
    }

    @Test
    fun startThenStopYieldsPlayableAudioReference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val recorder = MediaRecorderAudioRecorder(context, Dispatchers.IO)

        val startedRef = recorder.start()
        // Capture a brief window of audio so the encoder writes a finalizable
        // MPEG-4/AAC container; stopping too quickly can yield an empty file.
        delay(RECORD_DURATION_MS)
        val audioRef = recorder.stop()

        recordedFile = File(audioRef)

        // start() and stop() reference the same recording file.
        assertTrue(
            "stop() should return the path returned by start()",
            startedRef == audioRef,
        )

        // The audioRef points at a real, non-empty file.
        assertTrue("audio file should exist at $audioRef", recordedFile!!.exists())
        assertTrue("audio file should be non-empty", recordedFile!!.length() > 0L)

        // The reference is *playable*: a media framework component can open it
        // and read back duration metadata, proving it is a valid media file.
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(audioRef)
            val duration =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            assertNotNull("playable audio should expose duration metadata", duration)
            assertTrue(
                "playable audio should report a positive duration",
                (duration?.toLongOrNull() ?: 0L) > 0L,
            )
        } finally {
            retriever.release()
        }
    }

    private companion object {
        /** Brief capture window so the recorder produces a finalizable file. */
        const val RECORD_DURATION_MS = 500L
    }
}
