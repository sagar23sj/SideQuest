package com.sidequest.data.sync

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT

/**
 * Retrofit contract for the backend snapshot backup endpoints. Both require a
 * valid access token (carried by the authenticated OkHttp client); the server
 * scopes reads/writes to the token's account.
 */
interface BackupApi {

    /** Uploads (replaces) the account's snapshot. 204 on success. */
    @PUT("backup")
    suspend fun put(
        @Body snapshot: BackupSnapshot,
        @Header("X-Device-Id") deviceId: String,
    ): Response<Unit>

    /** Returns the account's snapshot, or HTTP 204 when none exists yet. */
    @GET("backup")
    suspend fun get(): Response<BackupSnapshot>
}
