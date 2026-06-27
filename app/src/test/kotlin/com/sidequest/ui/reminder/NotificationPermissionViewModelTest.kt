package com.sidequest.ui.reminder

import app.cash.turbine.test
import com.sidequest.data.reminder.ReminderSettingsStore
import com.sidequest.domain.reminder.ReminderSettings
import com.sidequest.domain.reminder.ReminderTime
import io.kotest.core.spec.style.StringSpec
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
 * Example tests for the first-launch notification-permission gating (Req 6.1).
 *
 * The permission request must fire exactly once on first launch: only when the
 * app has never requested it before AND it is not already granted.
 * [NotificationPermissionViewModel] derives
 * [NotificationPermissionUiState.shouldRequestPermission] from the persisted
 * "already requested" flag (in [ReminderSettingsStore]) combined with the live
 * grant state fed in by the UI. These tests drive the view model directly with
 * an in-memory store so they stay pure-JVM (no Android/Compose runtime).
 *
 * _Requirements: 6.1_
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPermissionViewModelTest : StringSpec({

    beforeTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    afterTest {
        Dispatchers.resetMain()
    }

    "requests permission on first launch when never requested and not granted" {
        val store = FakeReminderSettingsStore(permissionAlreadyRequested = false)
        val viewModel = NotificationPermissionViewModel(store)

        viewModel.uiState.test {
            // Seed the live grant state as "not granted".
            viewModel.onPermissionStateKnown(granted = false)
            skipToLatest(this) { it.shouldRequestPermission }
        }
    }

    "does not request when the permission is already granted" {
        val store = FakeReminderSettingsStore(permissionAlreadyRequested = false)
        val viewModel = NotificationPermissionViewModel(store)

        viewModel.uiState.test {
            viewModel.onPermissionStateKnown(granted = true)
            skipToLatest(this) { !it.shouldRequestPermission }
        }
    }

    "does not request again once it has been requested on a previous launch" {
        val store = FakeReminderSettingsStore(permissionAlreadyRequested = true)
        val viewModel = NotificationPermissionViewModel(store)

        viewModel.uiState.test {
            viewModel.onPermissionStateKnown(granted = false)
            skipToLatest(this) { !it.shouldRequestPermission }
        }
    }

    "recording a result marks the request as made so it never fires again" {
        val store = FakeReminderSettingsStore(permissionAlreadyRequested = false)
        val viewModel = NotificationPermissionViewModel(store)

        viewModel.onPermissionResult(granted = false)

        store.permissionRequestedValue shouldBe true
    }
})

/**
 * Awaits items from the [NotificationPermissionUiState] flow until [predicate]
 * holds, asserting it eventually does within a bounded number of emissions.
 * Tolerates the combined flow emitting an intermediate value before the seeded
 * grant state propagates.
 */
private suspend fun skipToLatest(
    turbine: app.cash.turbine.ReceiveTurbine<NotificationPermissionUiState>,
    predicate: (NotificationPermissionUiState) -> Boolean,
) {
    repeat(4) {
        if (predicate(turbine.awaitItem())) {
            return
        }
    }
    throw AssertionError("predicate never held within the observed emissions")
}

/**
 * In-memory [ReminderSettingsStore] for the permission tests. Only the
 * permission-requested flag matters here; the settings flow is held at its
 * default so the store is fully usable without Android dependencies.
 */
private class FakeReminderSettingsStore(
    permissionAlreadyRequested: Boolean,
) : ReminderSettingsStore {

    private val settingsState = MutableStateFlow(ReminderSettings.DEFAULT)
    private val permissionRequestedState = MutableStateFlow(permissionAlreadyRequested)

    var permissionRequestedValue: Boolean = permissionAlreadyRequested
        private set

    override val settings: Flow<ReminderSettings> = settingsState.asStateFlow()
    override val permissionRequested: Flow<Boolean> = permissionRequestedState.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(enabled = enabled)
    }

    override suspend fun setReminderTime(time: ReminderTime) {
        settingsState.value = settingsState.value.copy(time = time)
    }

    override suspend fun markPermissionRequested() {
        permissionRequestedValue = true
        permissionRequestedState.value = true
    }
}
