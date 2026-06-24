package com.actiontracker.ui.reminder

import com.actiontracker.domain.reminder.ReminderSettings

/**
 * Screen state for the daily-reminder settings screen, exposed as a single
 * immutable value from [ReminderSettingsViewModel].
 *
 * The screen renders the enable/disable toggle (Req 6.2) and the reminder-time
 * picker (Req 6.3) from [settings], and surfaces the permission-denied
 * explanation + deep link to OS settings when [showPermissionDeniedExplanation]
 * is true (Req 6.5).
 *
 * @property settings the current reminder preferences driving the toggle and
 *   time picker.
 * @property showPermissionDeniedExplanation whether to show the "reminders are
 *   unavailable" explanation and the open-settings action because the OS
 *   notification permission is not granted (Req 6.5).
 */
data class ReminderSettingsUiState(
    val settings: ReminderSettings = ReminderSettings.DEFAULT,
    val showPermissionDeniedExplanation: Boolean = false,
)
