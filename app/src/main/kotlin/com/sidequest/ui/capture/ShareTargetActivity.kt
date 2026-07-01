package com.sidequest.ui.capture

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidequest.R
import com.sidequest.domain.capture.SharedIntentData
import com.sidequest.ui.theme.SideQuestTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Share-sheet entry point (Req 1.1, 1.2). Receives `ACTION_SEND` /
 * `ACTION_SEND_MULTIPLE` intents declared in the manifest, maps the payload to a
 * platform-neutral [SharedIntentData], and hands it to [CaptureViewModel].
 *
 * The activity is a thin host: it renders [CaptureUiState] from the view model
 * as either the "content type not supported" message with a discard action
 * (Req 1.4) or the bucket + timeframe categorization sheet (Req 1.3). On a
 * confirmed save (Req 1.5) it finishes so the user returns to the sharing app.
 */
@AndroidEntryPoint
class ShareTargetActivity : ComponentActivity() {

    private val viewModel: CaptureViewModel by viewModels()

    @javax.inject.Inject
    lateinit var userPreferences: com.sidequest.data.local.UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val manual = intent?.getBooleanExtra(EXTRA_MANUAL, false) == true
        if (manual) {
            viewModel.startManual(intent?.getStringExtra(EXTRA_BUCKET_ID))
        } else {
            viewModel.onShared(parseSharedIntent(intent))
        }

        setContent {
            val useSystemColors by userPreferences.useSystemColors.collectAsStateWithLifecycle()
            SideQuestTheme(dynamicColor = useSystemColors) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                CaptureScreen(
                    state = state,
                    onBucketSelected = viewModel::onBucketSelected,
                    onTimeframeSelected = viewModel::onTimeframeSelected,
                    onSpecificDatePicked = viewModel::onSpecificDatePicked,
                    onTitleChange = viewModel::onTitleChange,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    onLinkChange = viewModel::onLinkChange,
                    onCreateBucket = viewModel::createAndSelectBucket,
                    onConfirm = viewModel::onConfirm,
                    onDismiss = ::finish,
                )
            }
        }
    }

    /**
     * Maps an incoming share [intent] into [SharedIntentData].
     *
     * For `ACTION_SEND` the text comes from `EXTRA_TEXT` and any media from
     * `EXTRA_STREAM`; for `ACTION_SEND_MULTIPLE` the first streamed item is used
     * (multi-item capture is a later enhancement). The intent's MIME type is
     * preserved so the domain classifier can accept or reject it (Req 1.2, 1.4).
     */
    private fun parseSharedIntent(intent: Intent?): SharedIntentData {
        if (intent == null) return SharedIntentData()

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> intent.parcelableStream()
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableStreams()?.firstOrNull()
            else -> null
        }

        return SharedIntentData(
            mimeType = intent.type,
            text = text,
            uri = uri?.toString(),
        )
    }

    private fun Intent.parcelableStream(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun Intent.parcelableStreams(): List<Uri>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }

    companion object {
        /** Intent extra flag: open the capture flow as a manual "new task" entry. */
        const val EXTRA_MANUAL = "com.sidequest.extra.MANUAL"

        /** Intent extra: pre-select this bucket id for a manual task entry. */
        const val EXTRA_BUCKET_ID = "com.sidequest.extra.BUCKET_ID"
    }
}

/**
 * Top-level capture UI that switches on [state]. Stateless: every interaction is
 * forwarded through the callbacks to the view model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureScreen(
    state: CaptureUiState,
    onBucketSelected: (String) -> Unit,
    onTimeframeSelected: (TimeframeOption) -> Unit,
    onSpecificDatePicked: (java.time.LocalDate) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLinkChange: (String) -> Unit,
    onCreateBucket: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        CaptureUiState.Loading ->
            CenteredProgress(message = stringResource(R.string.capture_loading))

        CaptureUiState.Unsupported ->
            UnsupportedContent(onDiscard = onDismiss)

        CaptureUiState.Saved ->
            // Persisted; close the flow and return to the sharing app (Req 1.5).
            LaunchedEffect(Unit) { onDismiss() }

        is CaptureUiState.Categorizing -> {
            var showDatePicker by remember { mutableStateOf(false) }
            // Resist accidental swipe-down: the sheet won't dismiss on a drag to
            // hidden. The user dismisses intentionally via the system back
            // gesture or the explicit close (✕) button, so an in-progress form
            // isn't lost by a stray finger slide.
            val sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden },
            )

            // Back reliably closes the form regardless of the drag lock above.
            androidx.activity.compose.BackHandler(enabled = true) { onDismiss() }

            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                CategorizationSheetContent(
                    state = state,
                    onBucketSelected = onBucketSelected,
                    onTimeframeSelected = onTimeframeSelected,
                    onTitleChange = onTitleChange,
                    onDescriptionChange = onDescriptionChange,
                    onLinkChange = onLinkChange,
                    onCreateBucket = onCreateBucket,
                    onPickDate = { showDatePicker = true },
                    onConfirm = onConfirm,
                    onClose = onDismiss,
                )
            }

            if (showDatePicker) {
                CaptureDatePickerDialog(
                    onDismiss = { showDatePicker = false },
                    onDateSelected = { epochMillis ->
                        showDatePicker = false
                        onSpecificDatePicked(epochMillisToLocalDate(epochMillis))
                    },
                )
            }
        }
    }
}

@Composable
private fun CenteredProgress(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * "Content type not supported" message with a discard action that closes the
 * flow without persisting anything (Req 1.4).
 */
@Composable
private fun UnsupportedContent(onDiscard: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.capture_unsupported_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.capture_unsupported_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onDiscard) {
                Text(text = stringResource(R.string.capture_discard))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureDatePickerDialog(
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit,
) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let(onDateSelected) ?: onDismiss()
                },
            ) {
                Text(text = stringResource(R.string.capture_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.capture_close))
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}
