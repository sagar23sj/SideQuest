package com.sidequest.data.sync

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sidequest.data.auth.AuthModule
import com.sidequest.data.auth.AuthenticatedClient
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
 * Hilt bindings for offline-first backup sync. The [BackupApi] uses the
 * authenticated OkHttp client (bearer token + silent refresh) so backups are
 * scoped to the device/user account on the server.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideBackupApi(@AuthenticatedClient client: OkHttpClient): BackupApi {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        return Retrofit.Builder()
            .baseUrl(AuthModule.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BackupApi::class.java)
    }
}
