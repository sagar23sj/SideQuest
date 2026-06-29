package com.sidequest.ui.capture

import com.sidequest.data.auth.TokenStore
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

    /**
     * The account id used to scope locally created data. This intentionally
     * stays a single stable local id even after a silent backup account is
     * provisioned: the backup account (a server identity used purely for
     * cloud backup auth) must not reshuffle which rows the local UI shows.
     * Cloud backups are keyed server-side by the authenticated account, so
     * local scoping and backup identity are cleanly decoupled. Email "harden"
     * later attaches an email to that backup account without touching this.
     */
    fun currentAccountId(): String = LOCAL_ACCOUNT_ID

    companion object {
        /** Placeholder local account used while signed out (offline-first). */
        const val LOCAL_ACCOUNT_ID: String = "local-account"
    }
}
