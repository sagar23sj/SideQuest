package com.actiontracker

import android.content.Intent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actiontracker.ui.capture.ShareTargetActivity
import com.actiontracker.ui.navigation.ActionTrackerNavHost
import com.actiontracker.ui.reminder.NotificationPermission
import com.actiontracker.ui.reminder.NotificationPermissionViewModel
import com.actiontracker.ui.theme.ActionTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the Compose UI. Hosts the SideQuest navigation shell
 * ([ActionTrackerNavHost]) whose start destination is the Action Board (Req 4.1,
 * 5.1); external capture happens through the separate share-target activity,
 * and the shell's capture FAB launches that same activity for in-app capture.
 *
 * On first launch the app requests OS permission to post notifications
 * (Req 6.1) via [ActionTrackerRoot].
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

/**
 * App root that owns the first-launch notification-permission request (Req 6.1)
 * and hosts the navigation shell.
 *
 * The runtime POST_NOTIFICATIONS request is launched from here via the Activity
 * Result API (the side effect lives in a [LaunchedEffect], not in composition)
 * and is gated by [NotificationPermissionViewModel] so it fires exactly once
 * across launches.
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

    ActionTrackerNavHost(
        onAddItem = {
            // The capture FAB reuses the share-target activity's categorization
            // flow for in-app adds (the OS share sheet remains the primary
            // external capture path).
            context.startActivity(Intent(context, ShareTargetActivity::class.java))
        },
        modifier = modifier,
    )
}
