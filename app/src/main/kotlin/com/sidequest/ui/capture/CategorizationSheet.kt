package com.sidequest.ui.capture

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sidequest.R
import com.sidequest.domain.model.Bucket
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Stateless categorization sheet content: pick a [Bucket] and a
 * [TimeframeOption], then confirm to save (Req 1.3, 3.1). All state is passed in
 * via [state] and all interactions are emitted through the callbacks so the
 * composable can be previewed and tested in isolation.
 */
@Composable
fun CategorizationSheetContent(
    state: CaptureUiState.Categorizing,
    onBucketSelected: (String) -> Unit,
    onTimeframeSelected: (TimeframeOption) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLinkChange: (String) -> Unit,
    onCreateBucket: (String) -> Unit,
    onPickDate: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(
                if (state.isManual) R.string.capture_new_task_title else R.string.capture_title,
            ),
            style = MaterialTheme.typography.titleLarge,
        )

        // Name (required) — the user names the task rather than the link/raw
        // payload becoming the title.
        androidx.compose.material3.OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.capture_task_title_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Optional details / description.
        androidx.compose.material3.OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.capture_task_desc_label)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        // Optional link (prefilled when sharing a URL); metadata is fetched in
        // the background after saving.
        androidx.compose.material3.OutlinedTextField(
            value = state.link,
            onValueChange = onLinkChange,
            label = { Text(stringResource(R.string.capture_task_link_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        BucketSelector(
            buckets = state.buckets,
            selectedBucketId = state.selectedBucketId,
            onBucketSelected = onBucketSelected,
            onCreateBucket = onCreateBucket,
        )

        TimeframeSelector(
            selected = state.selectedTimeframe,
            dateError = state.dateError,
            onTimeframeSelected = onTimeframeSelected,
            onPickDate = onPickDate,
        )

        Button(
            onClick = onConfirm,
            enabled = state.canConfirm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(text = stringResource(R.string.capture_confirm))
        }
    }
}

@Composable
private fun BucketSelector(
    buckets: List<Bucket>,
    selectedBucketId: String?,
    onBucketSelected: (String) -> Unit,
    onCreateBucket: (String) -> Unit,
) {
    var showNewBucketDialog by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.capture_select_bucket),
            style = MaterialTheme.typography.titleMedium,
        )
        if (buckets.isEmpty()) {
            Text(
                text = stringResource(R.string.capture_no_buckets),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(modifier = Modifier.selectableGroup()) {
                buckets.forEach { bucket ->
                    val selected = bucket.id == selectedBucketId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = { onBucketSelected(bucket.id) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            // The Row handles the click for accessibility.
                            onClick = null,
                        )
                        Text(
                            text = bucket.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
        // Inline "create a new bucket" option so the user isn't limited to the
        // existing buckets while adding a task.
        androidx.compose.material3.TextButton(onClick = { showNewBucketDialog = true }) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(stringResource(R.string.capture_new_bucket))
        }
    }

    if (showNewBucketDialog) {
        NewBucketDialog(
            onConfirm = { name ->
                showNewBucketDialog = false
                onCreateBucket(name)
            },
            onDismiss = { showNewBucketDialog = false },
        )
    }
}

@Composable
private fun NewBucketDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_bucket_title)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.create_bucket_name_label)) },
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.create_bucket_save))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.create_bucket_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeframeSelector(
    selected: TimeframeOption,
    dateError: String?,
    onTimeframeSelected: (TimeframeOption) -> Unit,
    onPickDate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.capture_select_timeframe),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeframeChip(
                label = stringResource(R.string.capture_timeframe_today),
                selected = selected is TimeframeOption.Today,
                onClick = { onTimeframeSelected(TimeframeOption.Today) },
            )
            TimeframeChip(
                label = stringResource(R.string.capture_timeframe_within_a_day),
                selected = selected is TimeframeOption.WithinADay,
                onClick = { onTimeframeSelected(TimeframeOption.WithinADay) },
            )
            TimeframeChip(
                label = stringResource(R.string.capture_timeframe_within_a_week),
                selected = selected is TimeframeOption.WithinAWeek,
                onClick = { onTimeframeSelected(TimeframeOption.WithinAWeek) },
            )
            TimeframeChip(
                label = specificDateLabel(selected),
                selected = selected is TimeframeOption.SpecificDate,
                onClick = onPickDate,
            )
        }
        if (dateError != null) {
            Text(
                text = dateError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeframeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun specificDateLabel(selected: TimeframeOption): String {
    val base = stringResource(R.string.capture_timeframe_specific_date)
    return if (selected is TimeframeOption.SpecificDate && selected.date != null) {
        selected.date.toString()
    } else {
        base
    }
}

/** Converts epoch millis from a date picker (UTC midnight) into a [LocalDate]. */
fun epochMillisToLocalDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()
