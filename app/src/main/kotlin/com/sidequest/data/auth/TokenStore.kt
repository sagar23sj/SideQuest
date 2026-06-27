package com.sidequest.data.auth

/**
 * Persists the signed-in account's JWT access + refresh tokens (Req 13.3).
 *
 * Tokens are sensitive credentials, so the production implementation
 * ([EncryptedTokenStore]) stores them in `EncryptedSharedPreferences`. The
 * interface is the seam the rest of the app depends on: the
 * [com.sidequest.ui.capture.CurrentAccountProvider] reads the account id
 * from the stored access token, and [TokenAuthenticator] reads/replaces the
 * pair during silent refresh.
 *
 * Implementations MUST be safe for concurrent use: OkHttp may invoke the
 * authenticator from multiple call threads.
 */
interface TokenStore {

    /** The persisted token pair, or `null` when signed out. */
    fun tokens(): AuthTokens?

    /** Persists [tokens], replacing any previously stored pair. */
    fun save(tokens: AuthTokens)

    /** Clears the stored tokens (sign-out, or after a failed refresh). */
    fun clear()
}

/**
 * A stored JWT pair. [accessToken] is short-lived and sent as the bearer
 * credential; [refreshToken] is longer-lived and exchanged for a new pair via
 * `/auth/refresh` during silent refresh.
 */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)
