package com.sidequest.data.transcription

import com.sidequest.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Hilt bindings for the Transcription_Service client (Req 10.3).
 *
 * Binds the [TranscriptionService] seam to [RetrofitTranscriptionService] and
 * provides the [TranscriptionProxyApi] Retrofit contract it depends on. The
 * shared [OkHttpClient] is provided by `PreviewModule`. Per-call timeouts are
 * enforced by [RetrofitTranscriptionService] (Req 10.8), so this base
 * configuration only wires the JSON converter and base URL.
 *
 * The backend Transcription Proxy is implemented in Milestone E;
 * [TRANSCRIPTION_PROXY_BASE_URL] is a placeholder so the client compiles and is
 * injectable now, ahead of the real proxy deployment.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TranscriptionModule {

    @Binds
    @Singleton
    abstract fun bindTranscriptionService(
        impl: RetrofitTranscriptionService,
    ): TranscriptionService

    companion object {

        /**
         * Backend Transcription Proxy base URL, sourced from
         * [BuildConfig.API_BASE_URL] so debug builds hit the local backend and
         * release uses the deployed endpoint.
         */
        val TRANSCRIPTION_PROXY_BASE_URL: String = BuildConfig.API_BASE_URL

        @Provides
        @Singleton
        fun provideTranscriptionProxyApi(client: OkHttpClient): TranscriptionProxyApi {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            return Retrofit.Builder()
                .baseUrl(TRANSCRIPTION_PROXY_BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TranscriptionProxyApi::class.java)
        }
    }
}
