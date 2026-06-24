package com.actiontracker.data.auth

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Qualifier for the authenticated [OkHttpClient] that carries the bearer token
 * and performs silent refresh. Kept distinct from the plain client provided by
 * `PreviewModule` so unauthenticated enrichment calls (link previews) are not
 * coupled to the auth stack.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedClient

/**
 * Hilt bindings for client-side auth (Req 13.1, 13.2, 13.3).
 *
 * Wires the [TokenStore] seam to [EncryptedTokenStore], provides the
 * [AuthenticatedClient] OkHttp stack (request interceptor + 401 silent-refresh
 * authenticator), and builds the [AuthApi] Retrofit contract. The base URL is a
 * placeholder until the backend deploys (mirrors `LlmModule`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: EncryptedTokenStore): TokenStore

    companion object {

        /** Placeholder base URL for the backend API. Replaced on deployment. */
        const val API_BASE_URL: String = "https://api.actiontracker.invalid/"

        @Provides
        @Singleton
        @AuthenticatedClient
        fun provideAuthenticatedClient(
            authHeaderInterceptor: AuthHeaderInterceptor,
            tokenAuthenticator: TokenAuthenticator,
        ): OkHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(authHeaderInterceptor)
                .authenticator(tokenAuthenticator)
                .build()

        @Provides
        @Singleton
        fun provideAuthApi(@AuthenticatedClient client: OkHttpClient): AuthApi {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            return Retrofit.Builder()
                .baseUrl(API_BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(AuthApi::class.java)
        }
    }
}
