package com.sidequest.data.auth

import kotlinx.serialization.Serializable
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit contract for the backend auth endpoints (Req 13.1, 13.2, 13.3).
 *
 * [refresh] is declared as a blocking [Call] (not `suspend`) because it is
 * invoked from [TokenAuthenticator.authenticate], which runs on OkHttp's
 * synchronous dispatch path. The signup/login endpoints used by the future
 * sign-in UI are `suspend`.
 */
interface AuthApi {

    /** Creates an account, optionally joining/creating an org (Req 13.1, 13.2). */
    @POST("accounts")
    suspend fun createAccount(@Body request: CreateAccountRequest): AuthResultResponse

    /** Authenticates a credential pair and returns the account + tokens. */
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResultResponse

    /**
     * Exchanges a refresh token for a fresh pair (silent refresh). Synchronous
     * so it can run inside the OkHttp authenticator without a nested suspend.
     */
    @POST("auth/refresh")
    fun refresh(@Body request: RefreshRequest): Call<TokenPairResponse>

    /**
     * Provisions (or re-attaches to) a silent, password-less account for this
     * device and returns a token pair. No credentials required — this is the
     * zero-friction onboarding path that enables background backup.
     */
    @POST("accounts/device")
    suspend fun deviceAccount(@Body request: DeviceAccountRequest): DeviceAccountResponse
}

@Serializable
data class DeviceAccountRequest(
    val deviceId: String,
)

@Serializable
data class DeviceAccountResponse(
    val accountId: String,
    val tokens: TokenPairResponse,
)

@Serializable
data class CreateAccountRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val joinOrgId: String? = null,
    val newOrgName: String? = null,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class TokenPairResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: String,
    val refreshExpiresAt: String,
)

@Serializable
data class AccountResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val orgId: String? = null,
    val createdAt: String,
)

@Serializable
data class AuthResultResponse(
    val account: AccountResponse,
    val tokens: TokenPairResponse,
)
