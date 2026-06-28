package com.sidequest.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sidequest.R
import com.sidequest.domain.model.Bucket
import com.sidequest.ui.board.bucketIconFor
import com.sidequest.ui.components.GradientPillButton
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(
                if (state.isManual) R.string.capture_new_task_title else R.string.capture_title,
            ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        // Name (required).
        androidx.compose.material3.OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.capture_task_title_label)) },
            singleLine = true,
            shape = RoundedCornerShapeMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        // Optional details.
        androidx.compose.material3.OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.capture_task_desc_label)) },
            minLines = 2,
            shape = RoundedCornerShapeMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        // Optional link (prefilled when sharing a URL).
        androidx.compose.material3.OutlinedTextField(
            value = state.link,
            onValueChange = onLinkChange,
            label = { Text(stringResource(R.string.capture_task_link_label)) },
            singleLine = true,
            shape = RoundedCornerShapeMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeaderRow(
            icon = Icons.Filled.FolderOpen,
            text = stringResource(R.string.capture_select_bucket),
        )
        BucketChips(
            buckets = state.buckets,
            selectedBucketId = state.selectedBucketId,
            onBucketSelected = onBucketSelected,
            onCreateBucket = onCreateBucket,
        )

        SectionHeaderRow(
            icon = Icons.Filled.NotificationsActive,
            text = stringResource(R.string.capture_select_timeframe),
        )
        TimeframeSelector(
            selected = state.selectedTimeframe,
            dateError = state.dateError,
            onTimeframeSelected = onTimeframeSelected,
            onPickDate = onPickDate,
        )

        GradientPillButton(
            text = stringResource(R.string.capture_add_to_actions),
            onClick = onConfirm,
            startColor = MaterialTheme.colorScheme.primary,
            endColor = MaterialTheme.colorScheme.surfaceTint,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = Icons.Filled.BookmarkAdd,
            enabled = state.canConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
        )
    }
}

private val RoundedCornerShapeMedium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

/** A section header: a small primary icon plus a semibold label. */
@Composable
private fun SectionHeaderRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BucketChips(
    buckets: List<Bucket>,
    selectedBucketId: String?,
    onBucketSelected: (String) -> Unit,
    onCreateBucket: (String) -> Unit,
) {
    var showNewBucketDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        buckets.forEach { bucket ->
            BucketChip(
                name = bucket.name,
                selected = bucket.id == selectedBucketId,
                onClick = { onBucketSelected(bucket.id) },
            )
        }
        Surface(
            onClick = { showNewBucketDialog = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.capture_new_bucket),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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

/** A single bucket chip: a circular topical-icon avatar plus the bucket name. */
@Composable
private fun BucketChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = bucketIconFor(name),
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
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
