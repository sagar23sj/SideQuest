package com.actiontracker.ui.board

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actiontracker.R
import com.actiontracker.domain.board.BoardGroup
import com.actiontracker.domain.board.BoardItem
import com.actiontracker.domain.board.BoardState
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.ContentType

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
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BoardContent(
        state = state,
        onStatusChange = viewModel::onStatusChange,
        onOpenReminderSettings = onOpenReminderSettings,
        onOpenItem = onOpenItem,
        onManageBuckets = onManageBuckets,
        modifier = modifier,
    )
}

/**
 * Stateless board content (Req 4.1–4.7, 5.1). Renders:
 * - the Completion_Counter prominently at the top of the board (Req 5.1),
 * - each bucket as a section header with its [BoardItem] rows beneath (Req 4.1),
 * - per-item status color indicators that open a status menu on tap (Req 4.6).
 *
 * Within each group the rows preserve the ascending-by-creation order already
 * established by the domain aggregation (Req 4.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardContent(
    state: BoardUiState,
    onStatusChange: (itemId: String, newStatus: ActionStatus) -> Unit,
    modifier: Modifier = Modifier,
    onOpenReminderSettings: () -> Unit = {},
    onOpenItem: (String) -> Unit = {},
    onManageBuckets: () -> Unit = {},
) {
    val openSettingsDescription = stringResource(R.string.reminder_open_board_settings_desc)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.board_title)) },
                actions = {
                    TextButton(onClick = onManageBuckets) {
                        Text(text = stringResource(R.string.buckets_title))
                    }
                    TextButton(
                        onClick = onOpenReminderSettings,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.reminder_open_board_settings_label,
                            ),
                            modifier = Modifier.semantics {
                                contentDescription = openSettingsDescription
                            },
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
                onStatusChange = onStatusChange,
                onOpenItem = onOpenItem,
                contentPadding = innerPadding,
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
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.board_loading),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ReadyBoard(
    board: BoardState,
    onStatusChange: (itemId: String, newStatus: ActionStatus) -> Unit,
    onOpenItem: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Completion_Counter is rendered first so it sits at the top of the
        // board (Req 5.1).
        item(key = "completion-counter") {
            CompletionCounter(count = board.completionCount)
        }

        if (board.groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.board_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        board.groups.forEach { group ->
            item(key = "header-${group.bucket.id}") {
                BucketHeader(group = group)
            }
            items(
                items = group.items,
                key = { it.item.id },
            ) { boardItem ->
                ActionItemRow(
                    boardItem = boardItem,
                    onStatusChange = { newStatus ->
                        onStatusChange(boardItem.item.id, newStatus)
                    },
                    onOpenItem = { onOpenItem(boardItem.item.id) },
                )
            }
        }
    }
}

/**
 * The Completion_Counter shown at the top of the board (Req 5.1). Carries a
 * single, descriptive content description so screen readers announce the count
 * as a unit rather than reading the label and number separately.
 */
@Composable
private fun CompletionCounter(count: Int) {
    val description = stringResource(R.string.board_completion_count_desc, count)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clearAndSetSemantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.board_completion_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun BucketHeader(group: BoardGroup) {
    Text(
        text = group.bucket.name,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * A single Action_Item row: a tappable status color indicator (Req 4.3, 4.6),
 * the content title, and either a resolved link preview (title + thumbnail) or
 * the raw source content/link as a fallback.
 *
 * The display decision is computed by the pure [previewDisplay] helper: for a
 * LINK item with a resolved [com.actiontracker.domain.model.LinkPreview] the
 * row shows the preview title and its thumbnail (Req 1a.3); when the preview is
 * absent or unresolved the row falls back to the raw link / source content
 * (Req 1a.4). Image loading libraries (e.g. Coil) are not on the classpath, so
 * the thumbnail is represented by a placeholder [Box] that still carries a
 * content description naming the thumbnail URL — keeping the thumbnail both
 * accessible and assertable. Swapping in an `AsyncImage` later only changes
 * this leaf, not the decision logic.
 */
@Composable
private fun ActionItemRow(
    boardItem: BoardItem,
    onStatusChange: (ActionStatus) -> Unit,
    onOpenItem: () -> Unit = {},
) {
    val display = previewDisplay(boardItem)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenItem),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusIndicator(
            statusColor = boardItem.statusColor,
            status = boardItem.item.status,
            onStatusChange = onStatusChange,
        )
        display.thumbnailUrl?.let { thumbnailUrl ->
            LinkThumbnail(thumbnailUrl = thumbnailUrl)
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = display.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Raw link / source content shown only when no resolved preview
            // replaces it (Req 1a.4).
            display.rawSource?.let { source ->
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (boardItem.item.contentType == ContentType.LINK) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Renders a resolved link preview's thumbnail (Req 1a.3). With no image-loading
 * dependency available this is a colored placeholder [Box]; it exposes a
 * content description naming the thumbnail URL so screen readers announce the
 * thumbnail and tests can assert it is present. Swapping in an `AsyncImage`
 * that loads [thumbnailUrl] later only changes this leaf.
 */
@Composable
private fun LinkThumbnail(thumbnailUrl: String) {
    val description = stringResource(R.string.board_link_thumbnail_desc, thumbnailUrl)
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .size(48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
    )
}

/**
 * A small colored dot representing the item's [ActionStatus] (Req 4.3). Tapping
 * it opens a menu of statuses; selecting one emits [onStatusChange] (Req 4.6),
 * after which the dot's color updates reactively through the board flow
 * (Req 4.7). The indicator exposes a content description naming the current
 * status for accessibility.
 */
@Composable
private fun StatusIndicator(
    statusColor: String,
    status: ActionStatus,
    onStatusChange: (ActionStatus) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val fallback = MaterialTheme.colorScheme.outline
    val color = parseStatusColor(statusColor, fallback)
    val statusLabel = stringResource(status.labelRes())
    val indicatorDescription = stringResource(R.string.board_status_indicator_desc, statusLabel)

    Box {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(CircleShape)
                .clickable(
                    onClickLabel = stringResource(R.string.board_change_status),
                    role = Role.Button,
                    onClick = { menuExpanded = true },
                )
                .size(16.dp)
                .background(color)
                .semantics { contentDescription = indicatorDescription },
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            ActionStatus.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = {
                        menuExpanded = false
                        onStatusChange(option)
                    },
                )
            }
        }
    }
}

/** Maps an [ActionStatus] to its user-facing display label resource. */
@StringRes
private fun ActionStatus.labelRes(): Int = when (this) {
    ActionStatus.NOT_STARTED -> R.string.status_not_started
    ActionStatus.IN_PROGRESS -> R.string.status_in_progress
    ActionStatus.COMPLETED -> R.string.status_completed
}
