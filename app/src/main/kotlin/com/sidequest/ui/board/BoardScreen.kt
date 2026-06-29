package com.sidequest.ui.board

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
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
    onAddTask: () -> Unit = {},
    onOpenItem: (String) -> Unit = {},
    onManageBuckets: () -> Unit = {},
    onOpenBucket: (String) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BoardContent(
        state = state,
        onComplete = { id -> viewModel.onStatusChange(id, ActionStatus.COMPLETED) },
        onUndo = { id -> viewModel.onStatusChange(id, ActionStatus.NOT_STARTED) },
        onAddTask = onAddTask,
        onOpenItem = onOpenItem,
        onManageBuckets = onManageBuckets,
        onOpenBucket = onOpenBucket,
        onOpenProfile = onOpenProfile,
        onOpenLeaderboard = onOpenLeaderboard,
        onOpenStats = onOpenStats,
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
    onUndo: (itemId: String) -> Unit = {},
    onAddTask: () -> Unit = {},
    onOpenItem: (String) -> Unit = {},
    onManageBuckets: () -> Unit = {},
    onOpenBucket: (String) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    onOpenStats: () -> Unit = {},
) {
    val confetti = com.sidequest.ui.components.rememberConfettiController()
    val context = androidx.compose.ui.platform.LocalContext.current
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
                )
            },
        ) { innerPadding ->
            when (state) {
                BoardUiState.Loading -> LoadingBoard(innerPadding)
                is BoardUiState.Ready -> ReadyBoard(
                    board = state.board,
                    onComplete = { id ->
                        confetti.celebrate()
                        com.sidequest.ui.components.CompletionSound.play(context)
                        onComplete(id)
                    },
                    onUndo = onUndo,
                    onOpenItem = onOpenItem,
                    onManageBuckets = onManageBuckets,
                    onOpenBucket = onOpenBucket,
                    onOpenStats = onOpenStats,
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
    onUndo: (itemId: String) -> Unit,
    onOpenItem: (String) -> Unit,
    onManageBuckets: () -> Unit,
    onOpenBucket: (String) -> Unit,
    onOpenStats: () -> Unit,
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
                totalCount = totalItems,
                progress = progress,
                onClick = onOpenStats,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        if (board.groups.isEmpty()) {
            item(key = "empty") {
                EmptyBoard(modifier = Modifier.padding(horizontal = 20.dp))
            }
        } else {
            // "Your Quests" — browse every bucket (including empty ones) and
            // jump into any bucket's page.
            item(key = "quests") {
                QuestsCarousel(
                    board = board,
                    onManageBuckets = onManageBuckets,
                    onOpenBucket = onOpenBucket,
                )
            }

            // One shelf per bucket that has open tasks, organized by the user's
            // own categories — the simplest, lowest-friction way to navigate
            // (no time/status taxonomy to learn). The header opens the bucket's
            // full page; cards are colored tiles so they read distinctly from
            // the bucket covers above. Dynamic order (most-used first) via
            // BoardOrdering.
            board.groups.forEach { group ->
                val openItems = group.items.filter {
                    it.item.status != ActionStatus.COMPLETED
                }
                if (openItems.isNotEmpty()) {
                    item(key = "shelf-${group.bucket.id}") {
                        BucketShelf(
                            bucketName = group.bucket.name,
                            openItems = openItems,
                            onOpenBucket = { onOpenBucket(group.bucket.id) },
                            onOpenItem = onOpenItem,
                            onComplete = onComplete,
                            onUndo = onUndo,
                        )
                    }
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
    totalCount: Int,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openCount = (totalCount - completionCount).coerceAtLeast(0)
    val percent = (progress * 100).toInt()
    val description = stringResource(R.string.board_completion_count_desc, completionCount)
    Surface(
        onClick = onClick,
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
                        "$completionCount quests done ✨"
                    } else {
                        "Ready when you are"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (totalCount > 0) {
                        "$completionCount of $totalCount complete · $openCount to go"
                    } else {
                        "Add a quest to get started."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Tap for your stats",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Your Quests",
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(board.groups, key = { _, g -> g.bucket.id }) { index, group ->
                QuestCard(
                    name = group.bucket.name,
                    itemCountLabel = "${group.items.size} items",
                    onClick = { onOpenBucket(group.bucket.id) },
                    cover = {
                        BucketCover(
                            name = group.bucket.name,
                            imageRef = group.bucket.imageRef,
                            modifier = Modifier.fillMaxSize(),
                            iconSize = 44.dp,
                        )
                    },
                )
            }
        }
    }
}

/**
 * A per-bucket shelf: a tappable header (bucket name + open count + chevron that
 * opens the bucket's full page) above a row of task posters for that bucket's
 * open quests. Responsive — 1–3 items stretch to fill the row, 4+ scroll.
 */
@Composable
private fun BucketShelf(
    bucketName: String,
    openItems: List<BoardItem>,
    onOpenBucket: () -> Unit,
    onOpenItem: (String) -> Unit,
    onComplete: (String) -> Unit,
    onUndo: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenBucket)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bucketName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${openItems.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (openItems.size <= FILL_SHELF_THRESHOLD) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                openItems.forEach { boardItem ->
                    TaskPoster(
                        boardItem = boardItem,
                        bucketName = bucketName,
                        onOpenItem = onOpenItem,
                        onComplete = onComplete,
                        onUndo = onUndo,
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp),
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = openItems, key = { it.item.id }) { boardItem ->
                    TaskPoster(
                        boardItem = boardItem,
                        bucketName = bucketName,
                        onOpenItem = onOpenItem,
                        onComplete = onComplete,
                        onUndo = onUndo,
                        modifier = Modifier
                            .width(150.dp)
                            .height(200.dp),
                    )
                }
            }
        }
    }
}

/** Below this many tasks, a shelf fills the row instead of scrolling. */
private const val FILL_SHELF_THRESHOLD = 3

/**
 * A single board poster: resolves the item's display (title, thumbnail, status)
 * and renders a [com.sidequest.ui.components.TaskPosterCard] backed by the
 * item's own thumbnail when present, else a colored tile derived from its
 * bucket (never the bucket's cover photo, so tasks don't echo bucket cards).
 * Sized by the caller via [modifier].
 */
@Composable
private fun TaskPoster(
    boardItem: BoardItem,
    bucketName: String,
    onOpenItem: (String) -> Unit,
    onComplete: (String) -> Unit,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = previewDisplay(boardItem)
    val fallback = MaterialTheme.colorScheme.outline
    val statusColor = parseStatusColor(boardItem.statusColor, fallback)
    val statusLabel = stringResource(boardItem.item.status.labelRes())
    val isCompleted = boardItem.item.status == ActionStatus.COMPLETED
    com.sidequest.ui.components.TaskPosterCard(
        title = display.title,
        statusLabel = statusLabel,
        statusColor = statusColor,
        completed = isCompleted,
        onClick = { onOpenItem(boardItem.item.id) },
        onHoldComplete = { onComplete(boardItem.item.id) },
        onUndo = { onUndo(boardItem.item.id) },
        modifier = modifier,
        cover = {
            if (!display.thumbnailUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = display.thumbnailUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                TaskTileBackdrop(
                    name = bucketName,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )
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
    onUndo: () -> Unit,
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
        completed = isCompleted,
        onClick = onOpenItem,
        onHoldComplete = onComplete,
        onUndo = onUndo,
        modifier = modifier,
    )
}

/** Maps an [ActionStatus] to its user-facing display label resource. */
@StringRes
private fun ActionStatus.labelRes(): Int = when (this) {
    ActionStatus.NOT_STARTED -> R.string.status_not_started
    ActionStatus.IN_PROGRESS -> R.string.status_in_progress
    ActionStatus.COMPLETED -> R.string.status_completed
}
