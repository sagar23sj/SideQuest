package com.sidequest.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small local store for lightweight, device-local user preferences that don't
 * belong in the synced Room database — currently the player's chosen display
 * name. This lets someone use SideQuest (and appear on a future leaderboard as
 * a friendly name rather than "Adventurer") without signing in; when they later
 * create an account, the name can seed their profile.
 *
 * Backed by SharedPreferences and exposed as a [StateFlow] so the UI updates
 * reactively when the name changes.
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _displayName = MutableStateFlow(prefs.getString(KEY_NAME, null))
    /** The chosen display name, or null when not yet set. */
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    private val _avatarRef = MutableStateFlow(prefs.getString(KEY_AVATAR, null))
    /** Local file path of the chosen profile photo, or null when not set. */
    val avatarRef: StateFlow<String?> = _avatarRef.asStateFlow()

    private val _useSystemColors = MutableStateFlow(prefs.getBoolean(KEY_SYSTEM_COLORS, false))
    /** Whether to use the device's dynamic (Material You) colors over the brand. */
    val useSystemColors: StateFlow<Boolean> = _useSystemColors.asStateFlow()

    /** Whether the user has been asked for their name at least once. */
    val hasSeenNamePrompt: Boolean
        get() = prefs.getBoolean(KEY_NAME_PROMPTED, false)

    fun setDisplayName(name: String) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        prefs.edit().putString(KEY_NAME, trimmed).putBoolean(KEY_NAME_PROMPTED, true).apply()
        _displayName.value = trimmed
    }

    /** Persists whether dynamic/system colors should override the brand theme. */
    fun setUseSystemColors(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYSTEM_COLORS, enabled).apply()
        _useSystemColors.value = enabled
    }

    /** Stores the local file [path] of the player's profile photo. */
    fun setAvatarRef(path: String?) {
        prefs.edit().putString(KEY_AVATAR, path).apply()
        _avatarRef.value = path
    }

    fun markNamePromptSeen() {
        prefs.edit().putBoolean(KEY_NAME_PROMPTED, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "sidequest_user_prefs"
        const val KEY_NAME = "display_name"
        const val KEY_AVATAR = "avatar_ref"
        const val KEY_NAME_PROMPTED = "name_prompted"
        const val KEY_SYSTEM_COLORS = "use_system_colors"
        const val MAX_NAME_LENGTH = 40
    }
}
