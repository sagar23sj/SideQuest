package com.sidequest.data.llm

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
 * Hilt bindings for the LLM_Service client (Req 7).
 *
 * Binds the [LlmService] seam to [RetrofitLlmService] and provides the
 * [LlmProxyApi] Retrofit contract it depends on. The shared [OkHttpClient] is
 * provided by `PreviewModule`. Per-call timeouts are enforced by
 * [RetrofitLlmService] (Req 7.5), so this base configuration only wires the
 * JSON converter and base URL.
 *
 * The backend LLM Proxy is implemented in Milestone E; [LLM_PROXY_BASE_URL] is a
 * placeholder so the client compiles and is injectable now, ahead of the real
 * proxy deployment.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @Singleton
    abstract fun bindLlmService(impl: RetrofitLlmService): LlmService

    companion object {

        /**
         * Backend LLM Proxy base URL, sourced from [BuildConfig.API_BASE_URL]
         * so debug builds hit the local backend and release uses the deployed
         * endpoint.
         */
        val LLM_PROXY_BASE_URL: String = BuildConfig.API_BASE_URL

        @Provides
        @Singleton
        fun provideLlmProxyApi(client: OkHttpClient): LlmProxyApi {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            return Retrofit.Builder()
                .baseUrl(LLM_PROXY_BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(LlmProxyApi::class.java)
        }
    }
}
