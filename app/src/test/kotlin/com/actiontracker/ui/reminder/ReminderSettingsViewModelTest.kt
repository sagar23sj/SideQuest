package com.actiontracker.ui.reminder

import com.actiontracker.data.reminder.ReminderScheduler
import com.actiontracker.data.reminder.ReminderSettingsStore
import com.actiontracker.domain.reminder.ReminderSettings
import com.actiontracker.domain.reminder.ReminderTime
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Example tests proving reminder settings persist and (re)schedule (Req 6.2,
 * 6.3).
 *
 * Enabling or changing the time must persist the new [ReminderSettings] through
 * the store and immediately re-apply scheduling; disabling must cancel. The
 * view model is driven directly with an in-memory store and a recording
 * scheduler so the test stays pure-JVM (no WorkManager / Android runtime). The
 * recorded [ReminderScheduler.apply] arguments stand in for "the daily reminder
 * was (re)scheduled / cancelled".
 *
 * _Requirements: 6.2, 6.3_
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSettingsViewModelTest : StringSpec({

    beforeTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    afterTest {
        Dispatchers.resetMain()
    }

    "enabling reminders persists and schedules" {
        val store = FakeStore()
        val scheduler = RecordingScheduler()
        val viewModel = ReminderSettingsViewModel(store, scheduler)
        // Subscribe so the combined uiState is active and reflects store writes.
        viewModel.uiState.value

        viewModel.onEnabledChange(enabled = true)

        store.current.enabled shouldBe true
        scheduler.applied.shouldContain(store.current.copy(enabled = true))
        scheduler.applied.last().enabled shouldBe true
    }

    "disabling reminders persists and cancels (applies disabled settings)" {
        val store = FakeStore(initial = ReminderSettings(enabled = true))
        val scheduler = RecordingScheduler()
        val viewModel = ReminderSettingsViewModel(store, scheduler)
        viewModel.uiState.value

        viewModel.onEnabledChange(enabled = false)

        store.current.enabled shouldBe false
        scheduler.applied.last().enabled shouldBe false
    }

    "changing the time persists and reschedules for the new local time" {
        val store = FakeStore(initial = ReminderSettings(enabled = true))
        val scheduler = RecordingScheduler()
        val viewModel = ReminderSettingsViewModel(store, scheduler)
        viewModel.uiState.value

        viewModel.onTimeChange(hour = 7, minute = 45)

        store.current.time shouldBe ReminderTime(hour = 7, minute = 45)
        scheduler.applied.last().time shouldBe ReminderTime(hour = 7, minute = 45)
    }
})

private class RecordingScheduler : ReminderScheduler {
    val applied = mutableListOf<ReminderSettings>()
    override fun apply(settings: ReminderSettings) {
        applied += settings
    }
}

private class FakeStore(
    initial: ReminderSettings = ReminderSettings.DEFAULT,
) : ReminderSettingsStore {

    private val settingsState = MutableStateFlow(initial)
    private val permissionRequestedState = MutableStateFlow(false)

    val current: ReminderSettings get() = settingsState.value

    override val settings: Flow<ReminderSettings> = settingsState.asStateFlow()
    override val permissionRequested: Flow<Boolean> = permissionRequestedState.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(enabled = enabled)
    }

    override suspend fun setReminderTime(time: ReminderTime) {
        settingsState.value = settingsState.value.copy(time = time)
    }

    override suspend fun markPermissionRequested() {
        permissionRequestedState.value = true
    }
}
