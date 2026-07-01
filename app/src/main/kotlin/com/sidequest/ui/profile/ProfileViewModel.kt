package com.sidequest.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the Profile screen's local identity: the player's display name and
 * profile photo, captured and stored on-device via [UserPreferences] even when
 * the user has not signed in (so they can personalize the app and appear by
 * name later). The picked photo is copied into app-internal storage so it
 * survives the source content URI being revoked.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** The chosen display name, or null when not yet set. */
    val displayName: StateFlow<String?> = userPreferences.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), userPreferences.displayName.value)

    /** Local file path of the player's profile photo, or null when not set. */
    val avatarRef: StateFlow<String?> = userPreferences.avatarRef
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), userPreferences.avatarRef.value)

    /** Whether dynamic (Material You) colors override the brand theme. */
    val useSystemColors: StateFlow<Boolean> = userPreferences.useSystemColors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), userPreferences.useSystemColors.value)

    /** Whether to show the first-run "what should we call you?" prompt. */
    val shouldPromptForName: Boolean
        get() = !userPreferences.hasSeenNamePrompt

    fun setDisplayName(name: String) = userPreferences.setDisplayName(name)

    /** Toggles whether dynamic/system colors override the brand theme. */
    fun setUseSystemColors(enabled: Boolean) = userPreferences.setUseSystemColors(enabled)

    /** Picks one of the built-in [AVATAR_PRESETS] as the avatar. */
    fun setAvatarPreset(index: Int) = userPreferences.setAvatarRef(avatarRefForPreset(index))

    fun dismissNamePrompt() = userPreferences.markNamePromptSeen()

    /**
     * Copies the picked [uri] into app-internal storage and stores its path as
     * the profile photo. A null [uri] clears the photo.
     */
    fun onAvatarPicked(uri: Uri?) {
        if (uri == null) {
            userPreferences.setAvatarRef(null)
            return
        }
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { copyAvatarToStorage(uri) }
            if (path != null) userPreferences.setAvatarRef(path)
        }
    }

    private fun copyAvatarToStorage(uri: Uri): String? = runCatching {
        val dir = File(appContext.filesDir, "profile").apply { mkdirs() }
        val dest = File(dir, "avatar_${UUID.randomUUID()}.jpg")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        dest.absolutePath
    }.getOrNull()
}
