package com.sidequest.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidequest.R
import com.sidequest.ui.board.bucketIconFor
import com.sidequest.ui.components.ProgressRing
import com.sidequest.ui.components.SoftCard

private val StatGreen = Color(0xFF2E7D32)

/**
 * The progress dashboard, opened from the board's progress banner. It surfaces
 * encouraging, momentum-first statistics — total quests completed, completion
 * rate, and an open/done breakdown per bucket — without any overdue or pressure
 * framing, so checking it feels motivating rather than stressful.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                        text = stringResource(R.string.stats_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.stats_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!state.loading && state.total == 0) {
            EmptyStats(innerPadding)
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "hero") { StatsHero(state) }
            item(key = "chips") { StatChips(state) }
            if (state.buckets.any { it.total > 0 }) {
                item(key = "by-bucket") {
                    Text(
                        text = stringResource(R.string.stats_by_bucket),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                items(state.buckets.filter { it.total > 0 }, key = { it.name }) { bucket ->
                    BucketStatRow(bucket)
                }
            }
        }
    }
}

@Composable
private fun StatsHero(state: StatsUiState) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = headline(state),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = encouragement(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ProgressRing(progress = state.percent / 100f, label = "${state.percent}%")
        }
    }
}

@Composable
private fun StatChips(state: StatsUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatChip(value = state.totalDone, label = stringResource(R.string.stats_done_label), tint = StatGreen, modifier = Modifier.weight(1f))
        StatChip(value = state.totalOpen, label = stringResource(R.string.stats_open_label), tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
        StatChip(value = state.percent, label = stringResource(R.string.stats_rate_label), suffix = "%", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(
    value: Int,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    suffix: String = "",
) {
    SoftCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "$value$suffix",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BucketStatRow(bucket: BucketStat) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = bucketIconFor(bucket.name),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = bucket.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LinearProgressIndicator(
                    progress = { bucket.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = StatGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = stringResource(R.string.stats_bucket_breakdown, bucket.done, bucket.open),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyStats(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        ) {
            Text(
                text = stringResource(R.string.stats_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/** A momentum-first headline; never scolds about what's left. */
private fun headline(state: StatsUiState): String = when {
    state.totalDone == 0 -> "Your journey starts now"
    state.totalDone == 1 -> "1 quest complete 🎉"
    else -> "${state.totalDone} quests complete 🎉"
}

/** Warm, pressure-free encouragement tuned to the player's momentum. */
private fun encouragement(state: StatsUiState): String {
    val top = state.topBucket
    return when {
        state.total == 0 -> "Add a quest and your progress lights up here."
        state.totalDone == 0 -> "Every quest you finish shows up here. You've got this."
        top != null -> "Brightest spot: ${top.name}, with ${top.done} done. Keep the momentum."
        else -> "Steady progress beats perfect. Nice work so far."
    }
}
