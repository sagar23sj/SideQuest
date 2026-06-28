package com.sidequest.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the Profile screen's local identity: the player's display name, which
 * is captured and stored on-device via [UserPreferences] even when the user has
 * not signed in (so they can personalize the app and appear by name later).
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    /** The chosen display name, or null when not yet set. */
    val displayName: StateFlow<String?> = userPreferences.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), userPreferences.displayName.value)

    /** Whether to show the first-run "what should we call you?" prompt. */
    val shouldPromptForName: Boolean
        get() = !userPreferences.hasSeenNamePrompt

    fun setDisplayName(name: String) = userPreferences.setDisplayName(name)

    fun dismissNamePrompt() = userPreferences.markNamePromptSeen()
}
