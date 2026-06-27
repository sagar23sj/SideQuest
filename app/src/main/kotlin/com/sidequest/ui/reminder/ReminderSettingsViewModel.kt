package com.sidequest.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.data.reminder.ReminderScheduler
import com.sidequest.data.reminder.ReminderSettingsStore
import com.sidequest.domain.reminder.ReminderSettings
import com.sidequest.domain.reminder.ReminderTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the daily-reminder settings screen (Req 6.2, 6.3, 6.5).
 *
 * Observes the persisted [ReminderSettings] from [ReminderSettingsStore] and
 * exposes them — combined with the latest notification-permission state — as a
 * [StateFlow] of [ReminderSettingsUiState]. User intents persist through the
 * store and immediately re-apply scheduling via [ReminderScheduler] so a
 * toggle (Req 6.2) or time change (Req 6.3) reschedules the daily reminder at
 * once. When the OS permission is denied, the UI state surfaces the
 * explanation + open-settings affordance (Req 6.5).
 */
@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    private val settingsStore: ReminderSettingsStore,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    /**
     * Whether the OS notification permission is currently granted. The UI feeds
     * this in via [onPermissionResult] (and on resume) since permission state
     * lives outside the settings store.
     */
    private val permissionGranted = MutableStateFlow(true)

    val uiState: StateFlow<ReminderSettingsUiState> =
        combine(settingsStore.settings, permissionGranted) { settings, granted ->
            ReminderSettingsUiState(
                settings = settings,
                // Only nag about permission once the user actually wants
                // reminders (Req 6.5).
                showPermissionDeniedExplanation = settings.enabled && !granted,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ReminderSettingsUiState(),
        )

    /**
     * Enables or disables daily reminders (Req 6.2). Persists the change, then
     * reschedules: enabling schedules the daily reminder, disabling cancels it.
     */
    fun onEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setEnabled(enabled)
            scheduler.apply(currentSettings().copy(enabled = enabled))
        }
    }

    /**
     * Sets the reminder time of day (Req 6.3). Persists the change, then
     * reschedules the daily reminder for the new local time.
     */
    fun onTimeChange(hour: Int, minute: Int) {
        viewModelScope.launch {
            val time = ReminderTime(hour = hour, minute = minute)
            settingsStore.setReminderTime(time)
            scheduler.apply(currentSettings().copy(time = time))
        }
    }

    /**
     * Updates the cached OS notification-permission state (Req 6.5). Called with
     * the launcher result on first launch and whenever the screen re-checks the
     * permission (e.g. after returning from system settings).
     */
    fun onPermissionResult(granted: Boolean) {
        permissionGranted.value = granted
    }

    private fun currentSettings(): ReminderSettings = uiState.value.settings

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
