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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidequest.R
import com.sidequest.ui.board.parseStatusColor
import com.sidequest.ui.components.PillButton

/**
 * Create / edit bucket form (Req 2.1, 2.3, 2.6). Captures the name, the three
 * per-status indicator colors, and the shopping-bucket toggle — the single
 * switch that turns the bucket into a "shopping bucket". On save the screen
 * pops back; a duplicate name surfaces the in-use error inline (Req 2.6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBucketScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    viewModel: CreateBucketViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onNavigateBack()
    }

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
                        stringResource(
                            if (state.isEditing) R.string.edit_bucket_title else R.string.create_bucket_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.create_bucket_cancel),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.create_bucket_name_label)) },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.create_bucket_colors_label),
                style = MaterialTheme.typography.titleMedium,
            )
            ColorSwatchRow(
                label = stringResource(R.string.create_bucket_color_not_started),
                colorHex = state.notStartedColor,
                onColorChange = viewModel::onNotStartedColorChange,
            )
            ColorSwatchRow(
                label = stringResource(R.string.create_bucket_color_in_progress),
                colorHex = state.inProgressColor,
                onColorChange = viewModel::onInProgressColorChange,
            )
            ColorSwatchRow(
                label = stringResource(R.string.create_bucket_color_completed),
                colorHex = state.completedColor,
                onColorChange = viewModel::onCompletedColorChange,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.create_bucket_shopping_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.create_bucket_shopping_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.shoppingEnabled,
                    onCheckedChange = viewModel::onShoppingToggle,
                )
            }

            PillButton(
                text = stringResource(R.string.create_bucket_save),
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A single status-color row: a live color swatch (parsed from the hex via the
 * board's [parseStatusColor]) plus an editable hex field, so a color can be
 * tuned without a full color-picker dependency. A real HSV picker can replace
 * the text field later without changing the surrounding form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSwatchRow(
    label: String,
    colorHex: String,
    onColorChange: (String) -> Unit,
) {
    val fallback = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(parseStatusColor(colorHex, fallback)),
        )
        OutlinedTextField(
            value = colorHex,
            onValueChange = onColorChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}
