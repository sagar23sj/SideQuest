package com.actiontracker.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actiontracker.data.reminder.ReminderSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the first-launch notification-permission request (Req 6.1).
 *
 * The OS permission request must be made exactly once on first launch, so this
 * view model combines two facts: whether the app has already requested the
 * permission on a previous launch (persisted in [ReminderSettingsStore]) and
 * the latest known grant state (fed in by the UI, since grant state lives
 * outside the store). It exposes a [StateFlow] of
 * [NotificationPermissionUiState] whose [NotificationPermissionUiState.shouldRequestPermission]
 * is true only when no prior request has been made and the permission is not
 * already granted.
 *
 * The app root collects this state and, when it should request, launches the
 * runtime request via the Activity Result API and reports the outcome back
 * through [onPermissionResult]. The store flag is marked the first time a
 * request is launched ([markRequested]) so the request never fires again on
 * later launches.
 */
@HiltViewModel
class NotificationPermissionViewModel @Inject constructor(
    private val settingsStore: ReminderSettingsStore,
) : ViewModel() {

    /**
     * The latest known grant state of the OS notification permission. Seeded by
     * the UI (which can read the live permission) and updated with the runtime
     * request result via [onPermissionResult].
     */
    private val permissionGranted = MutableStateFlow(false)

    val uiState: StateFlow<NotificationPermissionUiState> =
        combine(
            settingsStore.permissionRequested,
            permissionGranted,
        ) { alreadyRequested, granted ->
            NotificationPermissionUiState(
                // Request exactly once: only when we have never asked and the
                // permission is not already granted (Req 6.1).
                shouldRequestPermission = !alreadyRequested && !granted,
                permissionGranted = granted,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = NotificationPermissionUiState(),
        )

    /**
     * Reflects the live OS permission state into the flow. Called by the UI on
     * start so an already-granted permission suppresses the first-launch
     * request (Req 6.1).
     */
    fun onPermissionStateKnown(granted: Boolean) {
        permissionGranted.value = granted
    }

    /**
     * Records that the first-launch permission request has been made (Req 6.1)
     * so it is never requested again, and stores the request outcome.
     */
    fun onPermissionResult(granted: Boolean) {
        permissionGranted.value = granted
        markRequested()
    }

    /**
     * Persists the "permission already requested" flag so the first-launch
     * request fires only once across launches (Req 6.1).
     */
    fun markRequested() {
        viewModelScope.launch {
            settingsStore.markPermissionRequested()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
