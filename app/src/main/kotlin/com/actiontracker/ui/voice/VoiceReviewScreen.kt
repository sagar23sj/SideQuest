package com.actiontracker.ui.voice

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actiontracker.R

/**
 * Voice-journal review screen (Req 10.5–10.7): shows the transcript and the
 * LLM-suggested action items as a confirmable checklist. The user ticks the
 * actions to keep, assigns each a bucket, and confirms — only then are
 * Action_Items created. Suggestions are fail-soft: an unavailable LLM shows an
 * "unavailable" notice instead of an error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceReviewScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    viewModel: VoiceReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) {
        if (state.done) onNavigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_review_title)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(stringResource(R.string.reminder_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.voice_review_loading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "transcript") {
                TranscriptCard(transcript = state.transcript)
            }

            item(key = "extracted-label") {
                Text(
                    text = stringResource(R.string.voice_review_extracted_label),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (state.suggestionsUnavailable) {
                item(key = "unavailable") {
                    Text(
                        text = stringResource(R.string.voice_review_extracted_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (state.actions.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.voice_review_extracted_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            itemsIndexed(state.actions, key = { index, _ -> "action-$index" }) { index, reviewAction ->
                ReviewActionRow(
                    reviewAction = reviewAction,
                    buckets = state.buckets,
                    onToggle = { viewModel.toggleSelected(index) },
                    onBucketSelected = { viewModel.setBucket(index, it) },
                )
            }

            item(key = "confirm") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = onNavigateBack, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.voice_review_skip))
                    }
                    Button(onClick = viewModel::confirm, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.voice_review_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptCard(transcript: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.voice_review_transcript_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = transcript?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.voice_review_no_transcript),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewActionRow(
    reviewAction: ReviewAction,
    buckets: List<com.actiontracker.domain.model.Bucket>,
    onToggle: () -> Unit,
    onBucketSelected: (String) -> Unit,
) {
    val selectDesc = stringResource(R.string.voice_review_select_desc, reviewAction.action.title)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = reviewAction.selected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics { contentDescription = selectDesc },
                )
                Text(
                    text = reviewAction.action.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            if (reviewAction.selected && buckets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    buckets.forEach { bucket ->
                        FilterChip(
                            selected = reviewAction.bucketId == bucket.id,
                            onClick = { onBucketSelected(bucket.id) },
                            label = { Text(bucket.name) },
                        )
                    }
                }
            }
        }
    }
}
