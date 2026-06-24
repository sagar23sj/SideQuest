package com.actiontracker.ui.reminder

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actiontracker.R
import com.actiontracker.domain.reminder.ReminderTime

/**
 * Stateful entry point for the daily-reminder settings screen (Req 6.2, 6.3,
 * 6.5).
 *
 * Collects [ReminderSettingsViewModel] state with lifecycle awareness and
 * reflects the current OS notification-permission state into the view model so
 * the permission-denied explanation can surface (Req 6.5). The first-launch
 * POST_NOTIFICATIONS request (Req 6.1) is owned by the app root, so this screen
 * never re-prompts on open; instead it offers a deep link to system settings
 * when permission is missing (Req 6.5). Rendering is delegated to the stateless
 * [ReminderSettingsContent].
 */
@Composable
fun ReminderSettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    viewModel: ReminderSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Reflect the live permission state so the denied explanation can show
    // (Req 6.5). The first-launch request itself is handled by the app root, so
    // opening this screen never re-prompts.
    LaunchedEffect(Unit) {
        viewModel.onPermissionResult(NotificationPermission.isGranted(context))
    }

    ReminderSettingsContent(
        state = state,
        onEnabledChange = viewModel::onEnabledChange,
        onTimeChange = viewModel::onTimeChange,
        onOpenSettings = {
            context.startActivity(
                NotificationPermission.appNotificationSettingsIntent(context)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * Stateless reminder-settings content (Req 6.2, 6.3, 6.5):
 *  - an enable/disable [Switch] for daily reminders (Req 6.2),
 *  - a Material 3 [TimePicker] for the reminder time of day (Req 6.3),
 *  - a permission-denied explanation + open-settings button shown when the OS
 *    permission is missing while reminders are enabled (Req 6.5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsContent(
    state: ReminderSettingsUiState,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminder_settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(stringResource(R.string.reminder_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ReminderToggleRow(
                enabled = state.settings.enabled,
                onEnabledChange = onEnabledChange,
            )

            if (state.showPermissionDeniedExplanation) {
                PermissionDeniedCard(onOpenSettings = onOpenSettings)
            }

            ReminderTimePicker(
                time = state.settings.time,
                enabled = state.settings.enabled,
                onTimeChange = onTimeChange,
            )
        }
    }
}

@Composable
private fun ReminderToggleRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val description = stringResource(R.string.reminder_enable_desc)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reminder_enable_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePicker(
    time: ReminderTime,
    enabled: Boolean,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.reminder_time_label),
            style = MaterialTheme.typography.titleMedium,
        )

        val timePickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
        )

        // Persist the chosen time whenever the picker's value changes (Req 6.3).
        LaunchedEffect(timePickerState.hour, timePickerState.minute) {
            if (timePickerState.hour != time.hour || timePickerState.minute != time.minute) {
                onTimeChange(timePickerState.hour, timePickerState.minute)
            }
        }

        val timeDescription = stringResource(
            R.string.reminder_time_desc,
            timePickerState.hour,
            timePickerState.minute,
        )
        TimePicker(
            state = timePickerState,
            modifier = Modifier.semantics { contentDescription = timeDescription },
        )

        if (!enabled) {
            Text(
                text = stringResource(R.string.reminder_disabled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Explanation shown when notification permission is denied while reminders are
 * enabled, with a button that deep-links to the OS notification settings
 * (Req 6.5).
 */
@Composable
private fun PermissionDeniedCard(onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.reminder_permission_denied),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.reminder_open_settings))
            }
        }
    }
}
