package com.sidequest.ui.bucket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidequest.R
import com.sidequest.data.repository.BucketDeleteResult
import com.sidequest.domain.bucket.BucketDeletionStrategy
import com.sidequest.domain.model.Bucket
import com.sidequest.ui.board.parseStatusColor

/**
 * Bucket management list (Req 2.1–2.6). Lists the account's buckets, each with
 * its status-color swatches and an overflow menu (edit / delete). Deleting an
 * empty bucket removes it immediately (Req 2.4); deleting a non-empty bucket
 * opens the reassign-or-delete dialog (Req 2.5). The FAB opens the create form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BucketManagementScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onCreateBucket: () -> Unit = {},
    onEditBucket: (String) -> Unit = {},
    viewModel: BucketManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The bucket pending a reassign-or-delete decision (non-empty delete).
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.buckets_title)) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateBucket,
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.buckets_add_desc),
                )
            }
        },
    ) { innerPadding ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.buckets.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.buckets_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.buckets, key = { it.id }) { bucket ->
                    BucketRow(
                        bucket = bucket,
                        onEdit = { onEditBucket(bucket.id) },
                        onDelete = {
                            viewModel.deleteBucket(bucket.id) { result ->
                                if (result is BucketDeleteResult.NotEmpty) {
                                    pendingDelete = PendingDelete(bucket, result.itemCount)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { pending ->
        ReassignOrDeleteDialog(
            pending = pending,
            otherBuckets = state.buckets.filter { it.id != pending.bucket.id },
            onDismiss = { pendingDelete = null },
            onConfirm = { strategy ->
                viewModel.deleteNonEmptyBucket(pending.bucket.id, strategy) {
                    pendingDelete = null
                }
            },
        )
    }
}

private data class PendingDelete(val bucket: Bucket, val itemCount: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BucketRow(
    bucket: Bucket,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = bucket.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColorDot(bucket.notStartedColor)
                    ColorDot(bucket.inProgressColor)
                    ColorDot(bucket.completedColor)
                }
            }

            Box {
                val overflowDesc = stringResource(R.string.buckets_overflow_desc, bucket.name)
                TextButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.semantics { contentDescription = overflowDesc },
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.buckets_edit)) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.buckets_delete)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorDot(colorHex: String) {
    val fallback = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(parseStatusColor(colorHex, fallback)),
    )
}

/**
 * The reassign-or-delete dialog shown when deleting a non-empty bucket
 * (Req 2.5). The user either moves the items to another bucket or deletes them
 * along with the bucket. Reassign is only offered when another bucket exists.
 */
@Composable
private fun ReassignOrDeleteDialog(
    pending: PendingDelete,
    otherBuckets: List<Bucket>,
    onDismiss: () -> Unit,
    onConfirm: (BucketDeletionStrategy) -> Unit,
) {
    var deleteItems by remember { mutableStateOf(otherBuckets.isEmpty()) }
    var targetBucketId by remember { mutableStateOf(otherBuckets.firstOrNull()?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.buckets_delete_dialog_title, pending.bucket.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.buckets_delete_nonempty_message, pending.itemCount))

                if (otherBuckets.isNotEmpty()) {
                    OptionRow(
                        selected = !deleteItems,
                        label = stringResource(R.string.buckets_delete_reassign),
                        onClick = { deleteItems = false },
                    )
                    if (!deleteItems) {
                        Column(modifier = Modifier.padding(start = 32.dp)) {
                            otherBuckets.forEach { target ->
                                OptionRow(
                                    selected = targetBucketId == target.id,
                                    label = target.name,
                                    onClick = { targetBucketId = target.id },
                                )
                            }
                        }
                    }
                }

                OptionRow(
                    selected = deleteItems,
                    label = stringResource(R.string.buckets_delete_items),
                    onClick = { deleteItems = true },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val strategy = if (deleteItems || targetBucketId == null) {
                        BucketDeletionStrategy.DeleteItems
                    } else {
                        BucketDeletionStrategy.Reassign(targetBucketId!!)
                    }
                    onConfirm(strategy)
                },
            ) {
                Text(stringResource(R.string.buckets_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.buckets_delete_cancel))
            }
        },
    )
}

@Composable
private fun OptionRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
