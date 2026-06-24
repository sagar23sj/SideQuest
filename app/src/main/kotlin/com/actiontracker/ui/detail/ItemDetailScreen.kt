package com.actiontracker.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actiontracker.R
import com.actiontracker.domain.model.SubAction
import com.actiontracker.domain.plan.Progress

/**
 * Stateful entry point for the Action_Item detail / Action_Plan screen. Collects
 * [ItemDetailViewModel] state with lifecycle awareness and forwards plan intents
 * back to the view model (Req 9.1–9.5). All rendering is delegated to the
 * stateless [ItemDetailContent].
 *
 * TODO(nav): There is no navigation graph yet, so the Board does not yet open
 * this screen with a selected item id. Once a NavHost exists, pass the item id
 * as a route argument (read via SavedStateHandle under
 * [ItemDetailViewModel.KEY_ACTION_ITEM_ID]); callers without a route can seed it
 * with [ItemDetailViewModel.setActionItemId].
 */
@Composable
fun ItemDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ItemDetailContent(
        state = state,
        onAddSubAction = viewModel::onAddSubAction,
        onToggleSubAction = viewModel::onToggleSubAction,
        onReorder = viewModel::onReorder,
        onMarkParentComplete = viewModel::onMarkParentComplete,
        modifier = modifier,
    )
}

/**
 * Stateless Action_Plan content (Req 9.1–9.5). Renders:
 * - the progress "completed / total" display and a progress bar (Req 9.3),
 * - the parent-complete prompt when every sub-action is done (Req 9.4),
 * - an add-sub-action text input (Req 9.1),
 * - the ordered sub-actions with completion checkboxes (Req 9.2) and move
 *   up / down reorder controls (Req 9.5).
 *
 * Reorder controls build the new id order from the current sub-action list and
 * emit it through [onReorder]; full drag-and-drop is intentionally out of scope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailContent(
    state: ItemDetailUiState,
    onAddSubAction: (String) -> Unit,
    onToggleSubAction: (subActionId: String, completed: Boolean) -> Unit,
    onReorder: (orderedIds: List<String>) -> Unit,
    onMarkParentComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.plan_title)) })
        },
    ) { innerPadding ->
        when (state) {
            ItemDetailUiState.Loading -> LoadingPlan(innerPadding)
            is ItemDetailUiState.Ready -> ReadyPlan(
                state = state,
                onAddSubAction = onAddSubAction,
                onToggleSubAction = onToggleSubAction,
                onReorder = onReorder,
                onMarkParentComplete = onMarkParentComplete,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun LoadingPlan(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ReadyPlan(
    state: ItemDetailUiState.Ready,
    onAddSubAction: (String) -> Unit,
    onToggleSubAction: (subActionId: String, completed: Boolean) -> Unit,
    onReorder: (orderedIds: List<String>) -> Unit,
    onMarkParentComplete: () -> Unit,
    contentPadding: PaddingValues,
) {
    val subActions = state.subActions
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "progress") {
            PlanProgress(progress = state.progress)
        }

        if (state.showParentCompletePrompt) {
            item(key = "parent-complete-prompt") {
                ParentCompletePrompt(onMarkParentComplete = onMarkParentComplete)
            }
        }

        item(key = "add-sub-action") {
            AddSubActionInput(onAddSubAction = onAddSubAction)
        }

        if (subActions.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.plan_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        itemsIndexed(
            items = subActions,
            key = { _, subAction -> subAction.id },
        ) { index, subAction ->
            SubActionRow(
                subAction = subAction,
                canMoveUp = index > 0,
                canMoveDown = index < subActions.size - 1,
                onToggle = { completed -> onToggleSubAction(subAction.id, completed) },
                onMoveUp = { onReorder(moveUp(subActions, index)) },
                onMoveDown = { onReorder(moveDown(subActions, index)) },
            )
        }
    }
}

/**
 * The completed / total progress display (Req 9.3): a "x / y" label with a
 * matching progress bar. The label carries a single descriptive content
 * description so screen readers announce the progress as a unit.
 */
@Composable
private fun PlanProgress(progress: Progress) {
    val description = stringResource(
        R.string.plan_progress_desc,
        progress.completed,
        progress.total,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(
                R.string.plan_progress_label,
                progress.completed,
                progress.total,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(
            progress = {
                if (progress.total == 0) 0f else progress.completed.toFloat() / progress.total
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Prompt shown when every sub-action is completed, inviting the user to mark the
 * parent Action_Item completed (Req 9.4).
 */
@Composable
private fun ParentCompletePrompt(onMarkParentComplete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.plan_parent_complete_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(
                onClick = onMarkParentComplete,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.plan_mark_parent_complete))
            }
        }
    }
}

/**
 * The add-sub-action input (Req 9.1): a text field plus an add button. Adding
 * clears the field; blank input is ignored by the view model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSubActionInput(onAddSubAction: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.plan_add_sub_action_label)) },
            singleLine = true,
        )
        TextButton(
            onClick = {
                onAddSubAction(text)
                text = ""
            },
            enabled = text.isNotBlank(),
        ) {
            Text(stringResource(R.string.plan_add_sub_action))
        }
    }
}

/**
 * A single sub-action row: a completion checkbox (Req 9.2), the step text
 * (struck through when completed), and move up / down reorder controls
 * (Req 9.5). The checkbox and reorder buttons carry content descriptions naming
 * the step for accessibility.
 */
@Composable
private fun SubActionRow(
    subAction: SubAction,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val toggleDescription = stringResource(
        R.string.plan_sub_action_toggle_desc,
        subAction.text,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = subAction.completed,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = toggleDescription },
        )
        Text(
            text = subAction.text,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (subAction.completed) TextDecoration.LineThrough else null,
            color = if (subAction.completed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        IconReorderButton(
            label = stringResource(R.string.plan_move_up),
            description = stringResource(R.string.plan_move_up_desc, subAction.text),
            enabled = canMoveUp,
            onClick = onMoveUp,
        )
        IconReorderButton(
            label = stringResource(R.string.plan_move_down),
            description = stringResource(R.string.plan_move_down_desc, subAction.text),
            enabled = canMoveDown,
            onClick = onMoveDown,
        )
    }
}

/**
 * A compact reorder control rendered as a text button (move up / down). Carries
 * a descriptive [description] naming the affected step for accessibility, while
 * showing a short arrow [label] visually.
 */
@Composable
private fun IconReorderButton(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(label)
    }
}

/**
 * Returns the list of sub-action ids with the item at [index] moved one
 * position earlier. The caller guarantees `index > 0`.
 */
private fun moveUp(subActions: List<SubAction>, index: Int): List<String> {
    val ids = subActions.map { it.id }.toMutableList()
    val moved = ids.removeAt(index)
    ids.add(index - 1, moved)
    return ids
}

/**
 * Returns the list of sub-action ids with the item at [index] moved one
 * position later. The caller guarantees `index < subActions.size - 1`.
 */
private fun moveDown(subActions: List<SubAction>, index: Int): List<String> {
    val ids = subActions.map { it.id }.toMutableList()
    val moved = ids.removeAt(index)
    ids.add(index + 1, moved)
    return ids
}
