package com.actiontracker.ui.voice

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actiontracker.R

/**
 * Stateful entry point for the voice-journal recording screen (Req 10.1, 10.2,
 * 10.4).
 *
 * Owns the microphone-permission request: tapping record requests RECORD_AUDIO
 * via the Activity Result API on first use; if granted, recording starts, and
 * if denied, recording is blocked and an explanation with a deep link to app
 * settings is shown (Req 10.1). Recording/persistence is delegated to
 * [VoiceJournalViewModel]; rendering is delegated to the stateless
 * [VoiceJournalContent].
 */
@Composable
fun VoiceJournalScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onReviewEntry: (String) -> Unit = {},
    viewModel: VoiceJournalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onPermissionGranted()
            viewModel.startRecording()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    VoiceJournalContent(
        state = state,
        onRecordClick = {
            // Request the microphone permission on first record; start
            // immediately if already granted (Req 10.1, 10.2).
            if (MicrophonePermission.isGranted(context)) {
                viewModel.onPermissionGranted()
                viewModel.startRecording()
            } else {
                permissionLauncher.launch(MicrophonePermission.PERMISSION)
            }
        },
        onStopClick = viewModel::stopRecording,
        onOpenSettings = {
            context.startActivity(
                MicrophonePermission.appSettingsIntent(context)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onErrorDismiss = viewModel::clearError,
        onNavigateBack = onNavigateBack,
        onReviewEntry = onReviewEntry,
        modifier = modifier,
    )
}

/**
 * Stateless voice-journal content (Req 10.1, 10.2):
 *  - a record/stop control whose label and content description reflect the
 *    recording state for assistive technologies,
 *  - a recording-in-progress indicator announced as a live region,
 *  - a permission-denied explanation + open-settings button shown when the
 *    microphone permission is missing (Req 10.1),
 *  - a transient error surface for recording failures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceJournalContent(
    state: VoiceJournalUiState,
    onRecordClick: () -> Unit,
    onStopClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onReviewEntry: (String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_journal_title)) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.voice_journal_hint),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (state.showPermissionRationale) {
                PermissionDeniedCard(onOpenSettings = onOpenSettings)
            }

            RecordControl(
                isRecording = state.isRecording,
                onRecordClick = onRecordClick,
                onStopClick = onStopClick,
            )

            if (state.isRecording) {
                Text(
                    text = stringResource(R.string.voice_journal_recording),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }

            if (state.lastSavedEntryId != null && !state.isRecording) {
                Text(
                    text = stringResource(R.string.voice_journal_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                TextButton(onClick = { onReviewEntry(state.lastSavedEntryId) }) {
                    Text(stringResource(R.string.voice_review_title))
                }
            }

            if (state.isTranscribing) {
                Text(
                    text = stringResource(R.string.voice_journal_transcribing),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }

            if (state.transcriptionFailed && !state.isTranscribing) {
                TranscriptionFailedCard()
            }

            state.errorMessage?.let { error ->
                ErrorCard(message = error, onDismiss = onErrorDismiss)
            }
        }
    }
}

/**
 * The single record/stop control. The visible label and the content
 * description both reflect the current recording state so screen-reader users
 * know whether tapping starts or stops a recording (Req 10.2).
 */
@Composable
private fun RecordControl(
    isRecording: Boolean,
    onRecordClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    if (isRecording) {
        val stopDescription = stringResource(R.string.voice_journal_stop_desc)
        Button(
            onClick = onStopClick,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = stopDescription },
        ) {
            Text(stringResource(R.string.voice_journal_stop))
        }
    } else {
        val recordDescription = stringResource(R.string.voice_journal_record_desc)
        Button(
            onClick = onRecordClick,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = recordDescription },
        ) {
            Text(stringResource(R.string.voice_journal_record))
        }
    }
}

/**
 * Explanation shown when the microphone permission is denied, with a button
 * that deep-links to the app's OS settings so the user can grant it (Req 10.1).
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
                text = stringResource(R.string.voice_journal_permission_denied),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.voice_journal_open_settings))
            }
        }
    }
}

/**
 * Message shown when transcription failed (Req 10.8). The audio recording is
 * retained, so this explains the user can try again later. Announced as a polite
 * live region for screen-reader users.
 */
@Composable
private fun TranscriptionFailedCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = stringResource(R.string.voice_journal_transcription_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

/** Transient recording-error surface with a dismiss action. */
@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
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
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.voice_journal_error_dismiss))
            }
        }
    }
}
