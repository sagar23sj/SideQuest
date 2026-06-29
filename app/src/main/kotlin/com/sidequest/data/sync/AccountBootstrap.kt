package com.sidequest.data.sync

import com.sidequest.data.auth.AuthTokens
import com.sidequest.data.auth.AuthApi
import com.sidequest.data.auth.DeviceAccountRequest
import com.sidequest.data.auth.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ensures the app has a backup account before syncing. On first run it silently
 * provisions a password-less device account and stores its tokens; on later
 * runs it's a no-op. Fully fail-soft: when offline it returns false and the
 * caller simply tries again next time (the local-first UX is never blocked).
 *
 * This account is the *backup identity* only — local data scoping is unchanged.
 * The account can later be hardened by attaching an email/password.
 */
@Singleton
class AccountBootstrap @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApi: AuthApi,
    private val deviceIdentity: DeviceIdentity,
) {
    /** True when an account/token is available (already present or just provisioned). */
    suspend fun ensureAccount(): Boolean {
        if (tokenStore.tokens() != null) return true
        return runCatching {
            val resp = authApi.deviceAccount(DeviceAccountRequest(deviceIdentity.deviceId()))
            tokenStore.save(AuthTokens(resp.tokens.accessToken, resp.tokens.refreshToken))
            true
        }.getOrDefault(false)
    }
}
