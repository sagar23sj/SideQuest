package com.sidequest.ui.detail

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidequest.R
import com.sidequest.domain.model.SubAction
import com.sidequest.domain.model.TaskReminder
import com.sidequest.domain.plan.Progress
import com.sidequest.ui.components.PillButton
import com.sidequest.ui.components.SecondaryPillButton
import com.sidequest.ui.components.SoftCard

/**
 * Stateful entry point for the Action_Item detail screen. The item's optional
 * checklist (Action_Plan) lets the User break a task into steps they can tick
 * off one by one (Req 9.1–9.5) — but a checklist is entirely optional, so the
 * screen leads with an "add checklist" affordance and only shows the list once
 * steps exist.
 */
@Composable
fun ItemDetailScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ItemDetailContent(
        state = state,
        onAddSubAction = viewModel::onAddSubAction,
        onToggleSubAction = viewModel::onToggleSubAction,
        onReorder = viewModel::onReorder,
        onMarkParentComplete = {
            // Mark the parent done, then return to the board where the
            // completion is reflected — otherwise the action looks like a no-op.
            viewModel.onMarkParentComplete()
            onNavigateBack()
        },
        onSetReminder = viewModel::onSetReminder,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailContent(
    state: ItemDetailUiState,
    onAddSubAction: (String) -> Unit,
    onToggleSubAction: (subActionId: String, completed: Boolean) -> Unit,
    onReorder: (orderedIds: List<String>) -> Unit,
    onMarkParentComplete: () -> Unit,
    onSetReminder: (com.sidequest.domain.model.TaskReminder?) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.plan_title)) },
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
        when (state) {
            ItemDetailUiState.Loading -> LoadingPlan(innerPadding)
            is ItemDetailUiState.Ready -> ReadyPlan(
                state = state,
                onAddSubAction = onAddSubAction,
                onToggleSubAction = onToggleSubAction,
                onReorder = onReorder,
                onMarkParentComplete = onMarkParentComplete,
                onSetReminder = onSetReminder,
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
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ReadyPlan(
    state: ItemDetailUiState.Ready,
    onAddSubAction: (String) -> Unit,
    onToggleSubAction: (subActionId: String, completed: Boolean) -> Unit,
    onReorder: (orderedIds: List<String>) -> Unit,
    onMarkParentComplete: () -> Unit,
    onSetReminder: (com.sidequest.domain.model.TaskReminder?) -> Unit,
    contentPadding: PaddingValues,
) {
    val subActions = state.subActions
    val hasChecklist = subActions.isNotEmpty()
    var showAddField by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            ItemHeaderCard(
                item = state.item,
                bucketName = state.bucketName,
                bucketImageRef = state.bucketImageRef,
            )
        }

        item(key = "reminder") {
            ReminderCard(reminder = state.reminder, onSetReminder = onSetReminder)
        }

        if (hasChecklist) {
            item(key = "progress") {
                ChecklistProgressCard(progress = state.progress)
            }
        }

        if (state.showParentCompletePrompt) {
            item(key = "parent-complete-prompt") {
                ParentCompletePrompt(onMarkParentComplete = onMarkParentComplete)
            }
        }

        // Section header for the optional checklist.
        item(key = "checklist-header") {
            ChecklistHeader(
                hasChecklist = hasChecklist,
                onAddClick = { showAddField = true },
            )
        }

        if (showAddField || hasChecklist) {
            item(key = "add-sub-action") {
                AddSubActionInput(
                    onAddSubAction = {
                        onAddSubAction(it)
                        showAddField = true
                    },
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
 * The item header: a domain-tinted icon "cover", the title, a bucket chip and
 * status, an optional description, and — for link items — a tappable preview
 * (thumbnail, source, and link). This is the context the screen previously
 * lacked: title, description, bucket, and links.
 */
@Composable
private fun ItemHeaderCard(
    item: com.sidequest.domain.model.ActionItem?,
    bucketName: String?,
    bucketImageRef: String?,
) {
    if (item == null) return
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val preview = item.preview
    val statusLabel = when (item.status) {
        com.sidequest.domain.model.ActionStatus.NOT_STARTED -> stringResource(R.string.status_not_started)
        com.sidequest.domain.model.ActionStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
        com.sidequest.domain.model.ActionStatus.COMPLETED -> stringResource(R.string.status_completed)
    }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cover: the link's thumbnail when present, else the bucket's
            // custom image, else a domain-themed cover.
            com.sidequest.ui.board.BucketCover(
                name = bucketName ?: item.title,
                imageRef = preview?.thumbnailUrl ?: bucketImageRef,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                iconSize = 48.dp,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Bucket chip + status.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!bucketName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = bucketName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (!item.description.isNullOrBlank()) {
                    Text(
                        text = item.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Link preview / source link.
                val link = preview?.rawUrl ?: item.sourceContent
                if (!link.isNullOrBlank() && link.startsWith("http")) {
                    Surface(
                        onClick = { runCatching { uriHandler.openUri(link) } },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (!preview?.sourceName.isNullOrBlank()) {
                                Text(
                                    text = preview!!.sourceName!!,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (!preview?.title.isNullOrBlank()) {
                                Text(
                                    text = preview!!.title!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = link,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else if (!item.sourceContent.isNullOrBlank()) {
                    // Plain text content.
                    Text(
                        text = item.sourceContent!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The checklist progress card: a "x / y" label and a thick rounded progress bar,
 * shown only when the item has a checklist (Req 9.3).
 */
@Composable
private fun ChecklistProgressCard(progress: Progress) {
    val description = stringResource(
        R.string.plan_progress_desc,
        progress.completed,
        progress.total,
    )
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.plan_progress_label,
                    progress.completed,
                    progress.total,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LinearProgressIndicator(
                progress = {
                    if (progress.total == 0) 0f else progress.completed.toFloat() / progress.total
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                color = MaterialTheme.colorScheme.primary,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/**
 * The checklist section header. When the item has no checklist yet, it presents
 * the optional "add a checklist" call-to-action (Req 6c.5 / 9.1); once steps
 * exist, it's a simple section title.
 */
@Composable
private fun ChecklistHeader(hasChecklist: Boolean, onAddClick: () -> Unit) {
    if (hasChecklist) {
        Text(
            text = stringResource(R.string.plan_checklist_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    } else {
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.Checklist,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.plan_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                PillButton(
                    text = stringResource(R.string.plan_add_checklist),
                    onClick = onAddClick,
                    icon = Icons.Filled.Add,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ParentCompletePrompt(onMarkParentComplete: () -> Unit) {
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.plan_parent_complete_prompt),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            PillButton(
                text = stringResource(R.string.plan_mark_parent_complete),
                onClick = onMarkParentComplete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

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
            shape = RoundedCornerShape(16.dp),
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
 * A single checklist row inside a soft card: a completion checkbox (Req 9.2),
 * the step text (struck through when done), and compact up/down reorder controls
 * (Req 9.5).
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
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
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
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.plan_move_up_desc, subAction.text),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.plan_move_down_desc, subAction.text),
                )
            }
        }
    }
}

private fun moveUp(subActions: List<SubAction>, index: Int): List<String> {
    val ids = subActions.map { it.id }.toMutableList()
    val moved = ids.removeAt(index)
    ids.add(index - 1, moved)
    return ids
}

private fun moveDown(subActions: List<SubAction>, index: Int): List<String> {
    val ids = subActions.map { it.id }.toMutableList()
    val moved = ids.removeAt(index)
    ids.add(index + 1, moved)
    return ids
}

/**
 * The optional per-task reminder card (Req 6.2, 6.5–6.8). When no reminder is
 * set it offers an "add reminder" action; once set it shows the time, the
 * until-date, and whether it recurs, with edit and remove controls. Editing
 * opens a time picker then a date picker, and a recurrence toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderCard(
    reminder: TaskReminder?,
    onSetReminder: (TaskReminder?) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var draftHour by remember { mutableStateOf(9) }
    var draftMinute by remember { mutableStateOf(0) }
    var recurring by remember { mutableStateOf(reminder?.recurring ?: false) }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.reminder_task_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (reminder != null) {
                    IconButton(onClick = { onSetReminder(null) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.reminder_task_remove),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (reminder == null) {
                Text(
                    text = stringResource(R.string.reminder_task_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PillButton(
                    text = stringResource(R.string.reminder_task_add),
                    onClick = {
                        draftHour = 9
                        draftMinute = 0
                        showTimePicker = true
                    },
                    icon = Icons.Filled.Add,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val timeText = "%02d:%02d".format(reminder.hour, reminder.minute)
                Text(
                    text = stringResource(
                        if (reminder.recurring) R.string.reminder_task_summary_recurring
                        else R.string.reminder_task_summary_once,
                        timeText,
                        reminder.untilDate.toString(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecondaryPillButton(
                    text = stringResource(R.string.reminder_task_edit),
                    onClick = {
                        draftHour = reminder.hour
                        draftMinute = reminder.minute
                        recurring = reminder.recurring
                        showTimePicker = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Step 1: pick the time.
    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = draftHour, initialMinute = draftMinute)
        androidx.compose.ui.window.Dialog(onDismissRequest = { showTimePicker = false }) {
            SoftCard(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.reminder_task_pick_time),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TimePicker(state = timeState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.create_bucket_cancel))
                        }
                        TextButton(onClick = {
                            draftHour = timeState.hour
                            draftMinute = timeState.minute
                            showTimePicker = false
                            showDatePicker = true
                        }) {
                            Text(stringResource(R.string.reminder_task_next))
                        }
                    }
                }
            }
        }
    }

    // Step 2: pick the until-date (today or later) + recurrence.
    if (showDatePicker) {
        val todayMillis = System.currentTimeMillis()
        val dateState = rememberDatePickerState(initialSelectedDateMillis = todayMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = dateState.selectedDateMillis ?: todayMillis
                    val date = java.time.Instant.ofEpochMilli(millis)
                        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    onSetReminder(
                        TaskReminder(
                            hour = draftHour,
                            minute = draftMinute,
                            untilDate = date,
                            recurring = recurring,
                        ),
                    )
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.reminder_task_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.create_bucket_cancel))
                }
            },
        ) {
            Column {
                DatePicker(state = dateState, title = null)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.reminder_task_recurring),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = recurring, onCheckedChange = { recurring = it })
                }
            }
        }
    }
}
