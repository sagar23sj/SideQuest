package com.actiontracker.data.reminder

import com.actiontracker.domain.reminder.ReminderSettings
import com.actiontracker.domain.reminder.ReminderTime
import kotlinx.coroutines.flow.Flow

/**
 * Persists the user's daily-reminder preferences (Req 6.2, 6.3) and the
 * one-time notification-permission-request flag (Req 6.1).
 *
 * Extracting this seam keeps the reminder ViewModel and the permission flow
 * free of any storage dependency: they observe [settings] / [permissionRequested]
 * and call the suspend setters. The default [DataStoreReminderSettingsStore]
 * backs this with DataStore Preferences; tests can substitute an in-memory
 * implementation.
 */
interface ReminderSettingsStore {

    /**
     * The current [ReminderSettings] as a cold [Flow] that re-emits on every
     * change. Emits [ReminderSettings.DEFAULT] before the user sets anything.
     */
    val settings: Flow<ReminderSettings>

    /**
     * Whether the app has already requested the POST_NOTIFICATIONS permission
     * on a previous launch (Req 6.1). Used to request the permission exactly
     * once on first launch. Emits `false` until [markPermissionRequested] runs.
     */
    val permissionRequested: Flow<Boolean>

    /** Enables or disables daily reminders (Req 6.2). */
    suspend fun setEnabled(enabled: Boolean)

    /** Sets the local time of day the reminder fires (Req 6.3). */
    suspend fun setReminderTime(time: ReminderTime)

    /**
     * Records that the first-launch notification-permission request has been
     * made (Req 6.1) so it is not requested again on later launches.
     */
    suspend fun markPermissionRequested()
}
