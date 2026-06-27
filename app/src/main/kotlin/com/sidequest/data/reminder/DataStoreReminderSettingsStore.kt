package com.sidequest.data.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.sidequest.domain.reminder.ReminderSettings
import com.sidequest.domain.reminder.ReminderTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [ReminderSettingsStore] backed by DataStore Preferences.
 *
 * Each preference is read defensively so a missing or out-of-range stored value
 * falls back to the [ReminderSettings.DEFAULT] / [ReminderTime.DEFAULT] values
 * rather than throwing — the [ReminderTime] constructor validates its range, so
 * persisted minutes/hours are clamped back into range on read to keep the flow
 * total. Writes use [DataStore.edit], which is transactional and observable, so
 * every setter emits a fresh [ReminderSettings] to collectors (the ViewModel and
 * the scheduler).
 */
@Singleton
class DataStoreReminderSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReminderSettingsStore {

    override val settings: Flow<ReminderSettings> =
        dataStore.data.map { prefs -> prefs.toReminderSettings() }

    override val permissionRequested: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_PERMISSION_REQUESTED] ?: false }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ENABLED] = enabled }
    }

    override suspend fun setReminderTime(time: ReminderTime) {
        dataStore.edit { prefs ->
            prefs[KEY_HOUR] = time.hour
            prefs[KEY_MINUTE] = time.minute
        }
    }

    override suspend fun markPermissionRequested() {
        dataStore.edit { prefs -> prefs[KEY_PERMISSION_REQUESTED] = true }
    }

    private fun Preferences.toReminderSettings(): ReminderSettings {
        val enabled = this[KEY_ENABLED] ?: ReminderSettings.DEFAULT_ENABLED
        val hour = (this[KEY_HOUR] ?: ReminderTime.DEFAULT.hour).coerceIn(0, 23)
        val minute = (this[KEY_MINUTE] ?: ReminderTime.DEFAULT.minute).coerceIn(0, 59)
        return ReminderSettings(
            enabled = enabled,
            time = ReminderTime(hour = hour, minute = minute),
        )
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_HOUR = intPreferencesKey("reminder_hour")
        val KEY_MINUTE = intPreferencesKey("reminder_minute")
        val KEY_PERMISSION_REQUESTED = booleanPreferencesKey("notification_permission_requested")
    }
}
