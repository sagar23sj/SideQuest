package com.sidequest.ui.bucket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidequest.R
import com.sidequest.domain.board.BoardItem
import com.sidequest.domain.model.ActionStatus
import com.sidequest.ui.board.parseStatusColor
import com.sidequest.ui.board.previewDisplay
import com.sidequest.ui.components.ConfettiOverlay
import com.sidequest.ui.components.HoldToCompleteButton
import com.sidequest.ui.components.RichTaskCard
import com.sidequest.ui.components.rememberConfettiController

/**
 * Shows the Action_Items that belong to a single bucket. This is what opens when
 * a bucket is tapped on the board (fixing the earlier bug where the bucket list
 * was shown). Each task is a rich card with a press-and-hold complete control
 * and a confetti celebration on completion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BucketDetailScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onOpenItem: (String) -> Unit = {},
    viewModel: BucketDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val confetti = rememberConfettiController()

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = {
                        Text(
                            text = state.bucketName.ifBlank { stringResource(R.string.buckets_title) },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.reminder_back),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            when {
                state.loading -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

                state.group == null || state.group!!.items.isEmpty() -> EmptyBucket(innerPadding)

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.group!!.items, key = { it.item.id }) { boardItem ->
                        TaskCard(
                            boardItem = boardItem,
                            onComplete = {
                                confetti.celebrate()
                                viewModel.complete(boardItem.item.id)
                            },
                            onOpenItem = { onOpenItem(boardItem.item.id) },
                        )
                    }
                }
            }
        }
        ConfettiOverlay(controller = confetti)
    }
}

@Composable
private fun TaskCard(
    boardItem: BoardItem,
    onComplete: () -> Unit,
    onOpenItem: () -> Unit,
) {
    val display = previewDisplay(boardItem)
    val fallback = MaterialTheme.colorScheme.outline
    val statusColor = parseStatusColor(boardItem.statusColor, fallback)
    val statusLabel = stringResource(boardItem.item.status.statusLabelRes())
    val isCompleted = boardItem.item.status == ActionStatus.COMPLETED

    RichTaskCard(
        title = display.title,
        subtitle = display.rawSource,
        statusLabel = statusLabel,
        statusColor = statusColor,
        icon = Icons.Filled.Bolt,
        thumbnailUrl = display.thumbnailUrl,
        onClick = onOpenItem,
        trailing = {
            HoldToCompleteButton(completed = isCompleted, onCompleted = onComplete)
        },
    )
}

@Composable
private fun EmptyBucket(innerPadding: PaddingValues) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "No quests here yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Share something into SideQuest and file it in this bucket.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Status label resource for an [ActionStatus] (kept local to this screen). */
private fun ActionStatus.statusLabelRes(): Int = when (this) {
    ActionStatus.NOT_STARTED -> R.string.status_not_started
    ActionStatus.IN_PROGRESS -> R.string.status_in_progress
    ActionStatus.COMPLETED -> R.string.status_completed
}
