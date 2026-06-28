package com.sidequest.ui.bucket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onAddTask: (String) -> Unit = {},
    viewModel: BucketDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val confetti = rememberConfettiController()
    val bucketId = state.bucketId
    var showCompleted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

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
                    actions = {
                        if (bucketId != null) {
                            IconButton(onClick = { onAddTask(bucketId) }) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.nav_capture_desc),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
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
                    item(key = "cover") {
                        com.sidequest.ui.board.BucketCover(
                            name = state.group!!.bucket.name,
                            imageRef = state.group!!.bucket.imageRef,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            iconSize = 56.dp,
                        )
                    }

                    // Open quests first (oldest-created first), then a
                    // collapsible "Completed" section so finished work stays out
                    // of the way until asked for.
                    val allItems = state.group!!.items.sortedBy { it.item.createdAt }
                    val openItems = allItems.filter { it.item.status != ActionStatus.COMPLETED }
                    val completedItems = allItems.filter { it.item.status == ActionStatus.COMPLETED }

                    items(openItems, key = { it.item.id }) { boardItem ->
                        TaskCard(
                            boardItem = boardItem,
                            onComplete = {
                                confetti.celebrate()
                                com.sidequest.ui.components.CompletionSound.play()
                                viewModel.complete(boardItem.item.id)
                            },
                            onUndo = { viewModel.uncomplete(boardItem.item.id) },
                            onOpenItem = { onOpenItem(boardItem.item.id) },
                        )
                    }

                    if (completedItems.isNotEmpty()) {
                        item(key = "completed-toggle") {
                            CompletedSectionToggle(
                                count = completedItems.size,
                                expanded = showCompleted,
                                onToggle = { showCompleted = !showCompleted },
                            )
                        }
                        if (showCompleted) {
                            items(completedItems, key = { it.item.id }) { boardItem ->
                                TaskCard(
                                    boardItem = boardItem,
                                    onComplete = {
                                        confetti.celebrate()
                                        com.sidequest.ui.components.CompletionSound.play()
                                        viewModel.complete(boardItem.item.id)
                                    },
                                    onUndo = { viewModel.uncomplete(boardItem.item.id) },
                                    onOpenItem = { onOpenItem(boardItem.item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
        ConfettiOverlay(controller = confetti)
    }
}

/**
 * A tappable "Show / Hide completed" row separating finished quests from the
 * open ones, so completed work is available but out of the way by default.
 */
@Composable
private fun CompletedSectionToggle(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(
                    if (expanded) R.string.bucket_hide_completed else R.string.bucket_show_completed,
                    count,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskCard(
    boardItem: BoardItem,
    onComplete: () -> Unit,
    onUndo: () -> Unit,
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
        completed = isCompleted,
        onClick = onOpenItem,
        onHoldComplete = onComplete,
        onUndo = onUndo,
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

