package com.sidequest.data.auth

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp [Authenticator] that performs **silent refresh** when an authenticated
 * request comes back `401 Unauthorized` (Req 13.3 / design "Auth" note).
 *
 * Flow:
 * 1. OkHttp calls [authenticate] after a 401 on a request that carried a bearer
 *    token.
 * 2. If the failed request already used the current stored access token, the
 *    refresh token is exchanged for a fresh pair via [AuthApi.refresh]; the new
 *    pair is persisted and the original request is retried with the new access
 *    token.
 * 3. If there are no stored tokens, the refresh fails, or we have already
 *    retried with the latest token, it returns `null` to give up — the caller
 *    surfaces the 401 and the app routes to re-authentication.
 *
 * [AuthApi] is injected via [Lazy] to break the dependency cycle: the API is
 * built on the same OkHttpClient that installs this authenticator.
 *
 * The method is `@Synchronized` so concurrent 401s trigger only one refresh;
 * later callers observe the already-refreshed token and skip a redundant
 * exchange (the [alreadyRefreshed] check).
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApi: Lazy<AuthApi>,
) : Authenticator {

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        val current = tokenStore.tokens() ?: return null

        val usedToken = response.request.header(AUTHORIZATION)?.removePrefix(BEARER_PREFIX)

        // Another thread may have refreshed while this 401 was in flight. If the
        // stored access token already differs from the one this request used,
        // retry with the current token instead of refreshing again.
        if (usedToken != null && usedToken != current.accessToken) {
            return retryWith(response.request, current.accessToken)
        }

        // Stop retrying the same token to avoid an infinite 401 loop.
        if (countPriorRetries(response) >= MAX_RETRIES) {
            return null
        }

        val refreshed = refresh(current.refreshToken) ?: run {
            // Refresh failed (expired/invalid refresh token): sign out so the
            // app routes to re-authentication without losing local data.
            tokenStore.clear()
            return null
        }

        tokenStore.save(refreshed)
        return retryWith(response.request, refreshed.accessToken)
    }

    /** Calls the refresh endpoint synchronously; returns null on any failure. */
    private fun refresh(refreshToken: String): AuthTokens? {
        return try {
            val result = authApi.get().refresh(RefreshRequest(refreshToken)).execute()
            val body = result.body()
            if (!result.isSuccessful || body == null) {
                null
            } else {
                AuthTokens(accessToken = body.accessToken, refreshToken = body.refreshToken)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun retryWith(request: Request, accessToken: String): Request =
        request.newBuilder()
            .header(AUTHORIZATION, BEARER_PREFIX + accessToken)
            .build()

    /** Counts how many times OkHttp has already replayed this request. */
    private fun countPriorRetries(response: Response): Int {
        var count = 0
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val MAX_RETRIES = 1
    }
}
