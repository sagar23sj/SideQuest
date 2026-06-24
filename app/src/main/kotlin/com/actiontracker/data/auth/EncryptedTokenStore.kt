package com.actiontracker.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TokenStore] backed by `EncryptedSharedPreferences` (Jetpack Security).
 *
 * The auth tokens are encrypted at rest with a master key held in the Android
 * Keystore, so they are not readable from a plain preferences file even on a
 * rooted device backup. Values are encrypted with AES-256-GCM and keys with
 * AES-256-SIV per the recommended scheme.
 *
 * The underlying [SharedPreferences] is created lazily and only once; its
 * read/write operations are individually synchronized, satisfying the
 * [TokenStore] concurrency contract relied on by [TokenAuthenticator].
 */
@Singleton
class EncryptedTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : TokenStore {

    // Lazily initialized: building the master key touches the Keystore, which we
    // want to defer until the first token access rather than at graph creation.
    private val prefs: SharedPreferences by lazy { createEncryptedPrefs() }

    override fun tokens(): AuthTokens? {
        val access = prefs.getString(KEY_ACCESS, null)
        val refresh = prefs.getString(KEY_REFRESH, null)
        if (access.isNullOrEmpty() || refresh.isNullOrEmpty()) {
            return null
        }
        return AuthTokens(accessToken = access, refreshToken = refresh)
    }

    override fun save(tokens: AuthTokens) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .apply()
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .apply()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val PREFS_FILE = "actiontracker_auth_tokens"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
