package com.sidequest.ui.voice

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidequest.R
import com.sidequest.ui.components.SecondaryPillButton
import com.sidequest.ui.components.SoftCard

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
 * Stateless voice-journal content (Req 10.1, 10.2): a compact record/stop
 * control, recording/transcription status (announced as live regions), the
 * permission-denied explanation, and a scrolling list of past journals grouped
 * by day (newest first) with each entry's time shown.
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(
                        text = stringResource(R.string.voice_journal_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        },
    ) { innerPadding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp,
                vertical = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "hero") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.voice_journal_hero_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.voice_journal_hero_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )

                    if (state.showPermissionRationale) {
                        PermissionDeniedCard(onOpenSettings = onOpenSettings)
                    }

                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    RecordControl(
                        isRecording = state.isRecording,
                        onRecordClick = onRecordClick,
                        onStopClick = onStopClick,
                    )

                    if (state.isRecording) {
                        Text(
                            text = stringResource(R.string.voice_journal_recording),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (state.isTranscribing) {
                        Text(
                            text = stringResource(R.string.voice_journal_transcribing),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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

            if (state.journalGroups.isNotEmpty()) {
                item(key = "journals-header") {
                    com.sidequest.ui.components.SectionHeader(
                        title = stringResource(R.string.voice_journal_recent),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                state.journalGroups.forEach { group ->
                    item(key = "date-${group.dateLabel}") {
                        Text(
                            text = group.dateLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    items(group.entries, key = { it.id }) { entry ->
                        JournalEntryCard(
                            entry = entry,
                            onExtract = { onReviewEntry(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A past journal entry card (Stitch "Recent Logs"): a time chip, a quoted
 * transcript with a tertiary left accent, a play control, and an "Extract
 * actions" pill that opens the review/extraction flow.
 */
@Composable
private fun JournalEntryCard(
    entry: com.sidequest.domain.model.VoiceJournalEntry,
    onExtract: () -> Unit,
) {
    val time = remember(entry.createdAt) {
        java.time.Instant.ofEpochMilli(entry.createdAt)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
    }
    com.sidequest.ui.components.SoftCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            // Quoted transcript with a tertiary left accent bar.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                )
                Text(
                    text = entry.transcript?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.voice_journal_no_transcript_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp),
                    maxLines = 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (entry.audioRef.isNotBlank()) {
                    AudioPlayerButton(audioPath = entry.audioRef)
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Surface(
                    onClick = onExtract,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.voice_journal_extract),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The single large circular record/stop control — the centerpiece of the voice
 * screen. While recording, the button pulses gently (spring-like) and shows a
 * stop icon; otherwise it's a coral mic button. Label and content description
 * reflect the state for screen-reader users (Req 10.2).
 */
@Composable
private fun RecordControl(
    isRecording: Boolean,
    onRecordClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "recordPulse")
    // Expanding ring while recording (the "reflective" pulse from the design).
    val ringScale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(animation = tween(2200), repeatMode = RepeatMode.Restart),
        label = "ringScale",
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(2200), repeatMode = RepeatMode.Restart),
        label = "ringAlpha",
    )
    // Violet record cluster (matches the Stitch voice screen).
    val container = MaterialTheme.colorScheme.secondary
    val onContainer = MaterialTheme.colorScheme.onSecondary
    val description = stringResource(
        if (isRecording) R.string.voice_journal_stop_desc else R.string.voice_journal_record_desc,
    )

    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(ringScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = ringAlpha)),
            )
        }
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
        )
        Surface(
            onClick = { if (isRecording) onStopClick() else onRecordClick() },
            shape = CircleShape,
            color = container,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(96.dp)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

/**
 * Explanation shown when the microphone permission is denied, with a button
 * that deep-links to the app's OS settings so the user can grant it (Req 10.1).
 */
@Composable
private fun PermissionDeniedCard(onOpenSettings: () -> Unit) {
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.voice_journal_permission_denied),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            SecondaryPillButton(
                text = stringResource(R.string.voice_journal_open_settings),
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            )
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
    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = stringResource(R.string.voice_journal_transcription_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        )
    }
}

/** Transient recording-error surface with a dismiss action. */
@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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
