package com.sidequest.data.auth

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the stored access token as a bearer credential on outbound requests
 * (Req 13.3) so the server can stamp `account_id` from the validated token.
 *
 * Requests that already carry an `Authorization` header are left untouched (so
 * the unauthenticated signup/login/refresh calls are not overwritten), and when
 * signed out no header is added. Pairs with [TokenAuthenticator], which renews
 * the token on a 401.
 */
@Singleton
class AuthHeaderInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null) {
            return chain.proceed(request)
        }
        val accessToken = tokenStore.tokens()?.accessToken
        if (accessToken.isNullOrEmpty()) {
            return chain.proceed(request)
        }
        val authenticated = request.newBuilder()
            .header(AUTHORIZATION, BEARER_PREFIX + accessToken)
            .build()
        return chain.proceed(authenticated)
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
