package com.sidequest.data.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * App-level DataStore Preferences instance for reminder settings.
 *
 * Declared as a top-level property delegate (the recommended pattern) so the
 * process holds a single [DataStore] for the `reminder_settings` file; the Hilt
 * provider below exposes it for injection into
 * [DataStoreReminderSettingsStore].
 */
private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reminder_settings",
)

/**
 * Hilt bindings for daily-reminder permission, settings, and scheduling
 * (Req 6.1, 6.2, 6.3).
 *
 * Binds the [ReminderSettingsStore] seam to [DataStoreReminderSettingsStore]
 * and the [ReminderScheduler] seam to [WorkManagerReminderScheduler], and
 * provides the [DataStore] and [Clock] those depend on. The [WorkManager] used
 * by the scheduler is provided by `PreviewModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    @Binds
    @Singleton
    abstract fun bindReminderSettingsStore(
        impl: DataStoreReminderSettingsStore,
    ): ReminderSettingsStore

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(
        impl: WorkManagerReminderScheduler,
    ): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindReminderNotifier(
        impl: NotificationManagerReminderNotifier,
    ): ReminderNotifier

    companion object {

        @Provides
        @Singleton
        fun providePreferencesDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = context.reminderDataStore

        /** The system clock in the device's default zone, used for scheduling. */
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}
