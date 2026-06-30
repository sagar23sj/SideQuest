package com.sidequest

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidequest.ui.capture.ShareTargetActivity
import com.sidequest.ui.loading.LoadingScreen
import com.sidequest.ui.navigation.SideQuestNavHost
import com.sidequest.ui.reminder.NotificationPermission
import com.sidequest.ui.reminder.NotificationPermissionViewModel
import com.sidequest.ui.theme.SideQuestTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

/**
 * Single-activity host for the Compose UI. Hosts the SideQuest navigation shell
 * ([SideQuestNavHost]) whose start destination is the Action Board (Req 4.1,
 * 5.1); external capture happens through the separate share-target activity,
 * and the shell's capture FAB launches that same activity for in-app capture.
 *
 * On first launch the app requests OS permission to post notifications
 * (Req 6.1) via [SideQuestRoot].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // The task id to deep-link to when launched from a reminder notification
    // (Req 6.5). A StateFlow so a tap while the app is already running
    // (onNewIntent) re-triggers navigation.
    private val deepLinkItemId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkItemId.value = intent?.getStringExtra(EXTRA_OPEN_ITEM_ID)
        setContent {
            SideQuestTheme {
                SideQuestRoot(
                    deepLinkItemId = deepLinkItemId,
                    onDeepLinkHandled = { deepLinkItemId.value = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_ITEM_ID)?.let { deepLinkItemId.value = it }
    }

    companion object {
        /** Intent extra: open this task's detail page on launch (reminder tap). */
        const val EXTRA_OPEN_ITEM_ID = "com.sidequest.extra.OPEN_ITEM_ID"
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
private fun SideQuestRoot(
    modifier: Modifier = Modifier,
    deepLinkItemId: kotlinx.coroutines.flow.StateFlow<String?> =
        kotlinx.coroutines.flow.MutableStateFlow(null),
    onDeepLinkHandled: () -> Unit = {},
    permissionViewModel: NotificationPermissionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val permissionState by permissionViewModel.uiState.collectAsStateWithLifecycle()
    val pendingItemId by deepLinkItemId.collectAsStateWithLifecycle()

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

    // Show the branded loading screen with the thought of the day (Req 6d) long
    // enough to comfortably read the quote before revealing the nav shell.
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(4000)
        loading = false
    }

    if (loading) {
        LoadingScreen(modifier = modifier, onSkip = { loading = false })
        return
    }

    SideQuestNavHost(
        deepLinkItemId = pendingItemId,
        onDeepLinkHandled = onDeepLinkHandled,
        onAddTask = { bucketId ->
            // The capture FAB's "add task" opens the manual capture entry (no
            // shared content). When launched from a bucket, that bucket is
            // pre-selected.
            context.startActivity(
                Intent(context, ShareTargetActivity::class.java)
                    .putExtra(ShareTargetActivity.EXTRA_MANUAL, true)
                    .apply { if (bucketId != null) putExtra(ShareTargetActivity.EXTRA_BUCKET_ID, bucketId) },
            )
        },
        modifier = modifier,
    )
}
