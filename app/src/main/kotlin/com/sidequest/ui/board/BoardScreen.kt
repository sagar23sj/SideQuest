package com.sidequest.ui.board

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidequest.R
import com.sidequest.domain.board.BoardItem
import com.sidequest.domain.board.BoardState
import com.sidequest.domain.model.ActionStatus
import com.sidequest.ui.components.HoldToCompleteButton
import com.sidequest.ui.components.ProgressRing
import com.sidequest.ui.components.QuestCard
import com.sidequest.ui.components.RichTaskCard
import com.sidequest.ui.components.SectionHeader

/**
 * Stateful entry point for the Action Board. Collects [BoardViewModel] state
 * with lifecycle awareness and forwards status-change intents back to the view
 * model (Req 4.6). All rendering is delegated to the stateless [BoardContent].
 */
@Composable
fun BoardScreen(
    modifier: Modifier = Modifier,
    onOpenReminderSettings: () -> Unit = {},
    onOpenItem: (String) -> Unit = {},
    onManageBuckets: () -> Unit = {},
    onOpenBucket: (String) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BoardContent(
        state = state,
        onComplete = { id -> viewModel.onStatusChange(id, ActionStatus.COMPLETED) },
        onOpenItem = onOpenItem,
        onManageBuckets = onManageBuckets,
        onOpenBucket = onOpenBucket,
        onOpenProfile = onOpenProfile,
        onOpenLeaderboard = onOpenLeaderboard,
        modifier = modifier,
    )
}

/**
 * Stateless board content (Req 4.1–4.7, 5.1), styled to the SideQuest
 * "Expressive" design: an avatar + centered wordmark header, a progress-ring
 * hero celebrating completed actions (Req 5.1), a horizontal "Your Quests"
 * bucket carousel, and rich task cards per bucket with a press-and-hold
 * complete control (Req 6c).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardContent(
    state: BoardUiState,
    onComplete: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenItem: (String) -> Unit = {},
    onManageBuckets: () -> Unit = {},
    onOpenBucket: (String) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
) {
    val confetti = com.sidequest.ui.components.rememberConfettiController()
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
                            text = "SideQuest",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    navigationIcon = {
                        AvatarButton(onClick = onOpenProfile)
                    },
                    actions = {
                        IconButton(onClick = onOpenLeaderboard) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = stringResource(R.string.leaderboard_title),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            when (state) {
                BoardUiState.Loading -> LoadingBoard(innerPadding)
                is BoardUiState.Ready -> ReadyBoard(
                    board = state.board,
                    onComplete = { id ->
                        confetti.celebrate()
                        onComplete(id)
                    },
                    onOpenItem = onOpenItem,
                    onManageBuckets = onManageBuckets,
                    onOpenBucket = onOpenBucket,
                    contentPadding = innerPadding,
                )
            }
        }
        com.sidequest.ui.components.ConfettiOverlay(controller = confetti)
    }
}

@Composable
private fun AvatarButton(onClick: () -> Unit) {
    val desc = stringResource(R.string.profile_title)
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .semantics { contentDescription = desc },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun LoadingBoard(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.board_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadyBoard(
    board: BoardState,
    onComplete: (itemId: String) -> Unit,
    onOpenItem: (String) -> Unit,
    onManageBuckets: () -> Unit,
    onOpenBucket: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val totalItems = board.groups.sumOf { it.items.size }
    val progress = if (totalItems == 0) 0f else board.completionCount.toFloat() / totalItems

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "progress-hero") {
            ProgressHero(
                completionCount = board.completionCount,
                progress = progress,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        if (board.groups.isEmpty()) {
            item(key = "empty") {
                EmptyBoard(modifier = Modifier.padding(horizontal = 20.dp))
            }
        } else {
            // "Your Quests" — horizontal carousel of bucket cover cards.
            item(key = "quests") {
                QuestsCarousel(
                    board = board,
                    onManageBuckets = onManageBuckets,
                    onOpenBucket = onOpenBucket,
                )
            }

            // Each bucket then lists its rich task cards under a section header.
            board.groups.forEach { group ->
                item(key = "section-${group.bucket.id}") {
                    SectionHeader(
                        title = group.bucket.name,
                        actionLabel = stringResource(R.string.board_view_all),
                        onAction = { onOpenBucket(group.bucket.id) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                items(
                    items = group.items,
                    key = { it.item.id },
                ) { boardItem ->
                    ActionItemRow(
                        boardItem = boardItem,
                        onComplete = { onComplete(boardItem.item.id) },
                        onOpenItem = { onOpenItem(boardItem.item.id) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}

/**
 * The hero "progress overview" card (Req 5.1): an encouraging headline plus a
 * thick progress ring celebrating completed actions. Announced as a single unit
 * for screen readers.
 */
@Composable
private fun ProgressHero(
    completionCount: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.board_completion_count_desc, completionCount)
    val percent = (progress * 100).toInt()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (completionCount > 0) {
                        "$completionCount done today! ✨"
                    } else {
                        "Ready when you are"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (completionCount > 0) {
                        "You're on a great streak. Keep exploring!"
                    } else {
                        "Complete a quest to start your streak."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ProgressRing(progress = progress, label = "$percent%")
        }
    }
}

/**
 * The "Your Quests" horizontal carousel: one cover card per bucket, tinted with
 * an alternating tonal palette and a topical icon (see [bucketVisual]).
 */
@Composable
private fun QuestsCarousel(
    board: BoardState,
    onManageBuckets: () -> Unit,
    onOpenBucket: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Your Quests",
            actionLabel = stringResource(R.string.buckets_title),
            onAction = onManageBuckets,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(board.groups, key = { _, g -> g.bucket.id }) { index, group ->
                val visual = bucketVisual(group.bucket.name, index, scheme)
                QuestCard(
                    name = group.bucket.name,
                    itemCountLabel = "${group.items.size} items",
                    icon = visual.icon,
                    container = visual.container,
                    onContainer = visual.onContainer,
                    iconContainer = visual.iconContainer,
                    onIconContainer = visual.onIconContainer,
                    onClick = { onOpenBucket(group.bucket.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyBoard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Text(
            text = stringResource(R.string.board_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        )
    }
}

/**
 * A single Action_Item rendered as a rich task card (Req 4.3) with a
 * press-and-hold complete control (Req 6c). The card opens the item detail;
 * holding the trailing control marks the item completed with a confetti
 * celebration handled inside [HoldToCompleteButton].
 */
@Composable
private fun ActionItemRow(
    boardItem: BoardItem,
    onComplete: () -> Unit,
    onOpenItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = previewDisplay(boardItem)
    val fallback = MaterialTheme.colorScheme.outline
    val statusColor = parseStatusColor(boardItem.statusColor, fallback)
    val statusLabel = stringResource(boardItem.item.status.labelRes())
    val isCompleted = boardItem.item.status == ActionStatus.COMPLETED

    RichTaskCard(
        title = display.title,
        subtitle = display.rawSource,
        statusLabel = statusLabel,
        statusColor = statusColor,
        icon = Icons.Filled.Bolt,
        thumbnailUrl = display.thumbnailUrl,
        onClick = onOpenItem,
        modifier = modifier,
        trailing = {
            HoldToCompleteButton(
                completed = isCompleted,
                onCompleted = onComplete,
            )
        },
    )
}

/** Maps an [ActionStatus] to its user-facing display label resource. */
@StringRes
private fun ActionStatus.labelRes(): Int = when (this) {
    ActionStatus.NOT_STARTED -> R.string.status_not_started
    ActionStatus.IN_PROGRESS -> R.string.status_in_progress
    ActionStatus.COMPLETED -> R.string.status_completed
}
