package com.actiontracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actiontracker.ui.board.BoardScreen
import com.actiontracker.ui.reminder.NotificationPermission
import com.actiontracker.ui.reminder.NotificationPermissionViewModel
import com.actiontracker.ui.reminder.ReminderSettingsScreen
import com.actiontracker.ui.theme.ActionTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the Compose UI. The Action Board is the default
 * screen shown on launch (Req 4.1, 5.1); capture happens through the separate
 * share-target activity.
 *
 * On first launch the app requests OS permission to post notifications
 * (Req 6.1) via [ActionTrackerRoot], and the Board's reminder action opens the
 * daily-reminder settings screen (Req 6.2, 6.3, 6.5).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ActionTrackerTheme {
                ActionTrackerRoot(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** The screens reachable from the single activity. */
private enum class RootScreen { BOARD, REMINDER_SETTINGS }

/**
 * App root that owns the first-launch notification-permission request (Req 6.1)
 * and the simple Board ↔ reminder-settings navigation (Req 6.2, 6.3, 6.5).
 *
 * The runtime POST_NOTIFICATIONS request is launched from here via the Activity
 * Result API (the side effect lives in a [LaunchedEffect], not in composition)
 * and is gated by [NotificationPermissionViewModel] so it fires exactly once
 * across launches. Screen selection is hoisted into saveable state so it
 * survives configuration changes without pulling in a navigation library.
 */
@Composable
private fun ActionTrackerRoot(
    modifier: Modifier = Modifier,
    permissionViewModel: NotificationPermissionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val permissionState by permissionViewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionViewModel.onPermissionResult(granted)
    }

    // First-launch permission request (Req 6.1): seed the live grant state, then
    // request exactly once on devices that need the runtime grant. Marking the
    // request as made (even when no runtime request is needed) keeps it
    // one-shot across launches.
    LaunchedEffect(permissionState.shouldRequestPermission) {
        permissionViewModel.onPermissionStateKnown(NotificationPermission.isGranted(context))
        if (permissionState.shouldRequestPermission) {
            if (NotificationPermission.requiresRuntimeRequest()) {
                permissionLauncher.launch(NotificationPermission.PERMISSION)
            } else {
                permissionViewModel.markRequested()
            }
        }
    }

    var currentScreen by rememberSaveable { mutableStateOf(RootScreen.BOARD) }

    when (currentScreen) {
        RootScreen.BOARD -> BoardScreen(
            modifier = modifier,
            onOpenReminderSettings = { currentScreen = RootScreen.REMINDER_SETTINGS },
        )

        RootScreen.REMINDER_SETTINGS -> ReminderSettingsScreen(
            modifier = modifier,
            onNavigateBack = { currentScreen = RootScreen.BOARD },
        )
    }
}
