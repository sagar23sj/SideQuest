package com.actiontracker.ui.capture

import com.actiontracker.data.auth.JwtClaims
import com.actiontracker.data.auth.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the id of the currently signed-in account so locally created data
 * (Action_Items, Buckets, Voice_Journal_Entries, Game results) is associated
 * with that account (Req 13.3).
 *
 * When signed in, the account id is the `sub` claim of the stored access token
 * (the server is authoritative — see [JwtClaims]). When signed out (no stored
 * token, or a malformed one), it falls back to [LOCAL_ACCOUNT_ID] so the
 * offline capture flow (Req 1.x, 14.4) keeps working before sign-in and the
 * data syncs to the real account once the user authenticates.
 */
@Singleton
class CurrentAccountProvider @Inject constructor(
    private val tokenStore: TokenStore,
) {

    /** The account id to associate captured content with. */
    fun currentAccountId(): String {
        val accessToken = tokenStore.tokens()?.accessToken ?: return LOCAL_ACCOUNT_ID
        return JwtClaims.subject(accessToken) ?: LOCAL_ACCOUNT_ID
    }

    companion object {
        /** Placeholder local account used while signed out (offline-first). */
        const val LOCAL_ACCOUNT_ID: String = "local-account"
    }
}
