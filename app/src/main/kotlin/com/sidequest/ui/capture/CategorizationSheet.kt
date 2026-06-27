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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onPickDate: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.capture_title),
            style = MaterialTheme.typography.titleLarge,
        )

        state.draft.title.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        BucketSelector(
            buckets = state.buckets,
            selectedBucketId = state.selectedBucketId,
            onBucketSelected = onBucketSelected,
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
) {
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
    }
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
