package com.actiontracker.data.transcription

import com.actiontracker.domain.transcription.TranscriptionResult
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit

/**
 * Integration test for the Transcription Proxy network seam (Req 10.3).
 *
 * Unlike the unit tests that mock [TranscriptionProxyApi], this test stands up a
 * real HTTP stack: a [MockWebServer] plays the role of the backend Transcription
 * Proxy while a real [RetrofitTranscriptionService] (built over Retrofit +
 * OkHttp + the kotlinx.serialization converter, mirroring `TranscriptionModule`)
 * uploads a sample audio file to it. This exercises the multipart upload, the
 * `transcription/transcribe` route, JSON deserialization, and the
 * success/failure mapping end-to-end against a mock proxy — no device, emulator,
 * or live backend required.
 *
 * _Requirements: 10.3_
 */
class TranscriptionProxyIntegrationTest : StringSpec({

    /**
     * Builds a [RetrofitTranscriptionService] whose Retrofit/JSON wiring matches
     * the production `TranscriptionModule`, but pointed at the [server] base URL.
     */
    fun serviceFor(server: MockWebServer): RetrofitTranscriptionService {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TranscriptionProxyApi::class.java)
        return RetrofitTranscriptionService(api)
    }

    /** Writes a small temp file to stand in for the recorded audio. */
    fun sampleAudioFile(): File =
        File.createTempFile("sample-audio", ".m4a").apply {
            writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
            deleteOnExit()
        }

    "uploading sample audio returns the transcript the proxy responds with" {
        runTest {
            val server = MockWebServer()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"transcript":"hello world"}"""),
            )
            server.start()
            val audio = sampleAudioFile()
            try {
                val result = serviceFor(server).transcribe(audio.absolutePath)

                result shouldBe TranscriptionResult.Success("hello world")

                // The upload must hit the proxy contract: the transcribe route
                // via a multipart POST (Req 10.3).
                val recorded = server.takeRequest()
                recorded.method shouldBe "POST"
                recorded.path shouldBe "/transcription/transcribe"
                (recorded.getHeader("Content-Type") ?: "") shouldContain "multipart/form-data"
            } finally {
                audio.delete()
                server.shutdown()
            }
        }
    }

    "a proxy error response is mapped to a transcription failure" {
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(500))
            server.start()
            val audio = sampleAudioFile()
            try {
                val result = serviceFor(server).transcribe(audio.absolutePath)

                // The client fails soft on a non-2xx proxy response (Req 10.8),
                // so the caller can retain the audio rather than throwing.
                result shouldBe TranscriptionResult.Failure
            } finally {
                audio.delete()
                server.shutdown()
            }
        }
    }
})
