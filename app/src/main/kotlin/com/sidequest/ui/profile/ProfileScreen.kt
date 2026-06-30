package com.sidequest.ui.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CorporateFare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidequest.R
import com.sidequest.ui.components.SecondaryPillButton
import com.sidequest.ui.components.SettingsRow

/**
 * Profile / settings (Req 13), styled to the SideQuest design: a centered hero
 * avatar with an organization badge, a grouped settings list with circular
 * tonal icon badges, and a sign-out action. Account/org details come from the
 * auth/sync layer; this screen shows offline placeholders and navigation entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    onManageBuckets: () -> Unit = {},
    onCreateBucket: () -> Unit = {},
    onJoinOrganization: () -> Unit = {},
    onSignIn: () -> Unit = {},
    viewModel: ProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    var useSystemColors by remember { mutableStateOf(false) }
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val avatarRef by viewModel.avatarRef.collectAsStateWithLifecycle()
    var showNameDialog by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }

    // Plain photo picker (no external crop activity — avoids the AppCompat-theme
    // crash). The picked image is copied to internal storage as the avatar.
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> viewModel.onAvatarPicked(uri) }

    // First-run: gently ask for a name so the experience feels personal even
    // before signing in.
    LaunchedEffect(Unit) {
        if (viewModel.shouldPromptForName) {
            showNameDialog = true
            viewModel.dismissNamePrompt()
        }
    }

    if (showNameDialog) {
        NameDialog(
            initial = displayName.orEmpty(),
            onConfirm = { name ->
                if (name.isNotBlank()) viewModel.setDisplayName(name)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false },
        )
    }

    if (showAvatarPicker) {
        AvatarPickerDialog(
            onPreset = { index ->
                viewModel.setAvatarPreset(index)
                showAvatarPicker = false
            },
            onChoosePhoto = {
                showAvatarPicker = false
                avatarPicker.launch("image/*")
            },
            onDismiss = { showAvatarPicker = false },
        )
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
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileHero(
                displayName = displayName,
                avatarRef = avatarRef,
                onEditName = { showNameDialog = true },
                onChangePhoto = { showAvatarPicker = true },
            )

            SettingsGroup {
                if (com.sidequest.FeatureFlags.GUILDS_ENABLED) {
                    SettingsRow(
                        title = stringResource(R.string.profile_organization_label),
                        subtitle = stringResource(R.string.profile_no_organization),
                        icon = Icons.Filled.CorporateFare,
                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                        onIconContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = onJoinOrganization,
                        trailing = { JoinPill(onClick = onJoinOrganization) },
                    )
                    RowDivider()
                }
                SettingsRow(
                    title = stringResource(R.string.profile_reminders),
                    subtitle = stringResource(R.string.profile_reminders_subtitle),
                    icon = Icons.Filled.NotificationsActive,
                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    onIconContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onOpenReminders,
                    trailing = { Chevron() },
                )
                RowDivider()
                SettingsRow(
                    title = stringResource(R.string.profile_buckets),
                    subtitle = stringResource(R.string.profile_buckets_subtitle),
                    icon = Icons.Filled.Palette,
                    iconContainer = MaterialTheme.colorScheme.primaryContainer,
                    onIconContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onManageBuckets,
                    trailing = { Chevron() },
                )
                RowDivider()
                SettingsRow(
                    title = stringResource(R.string.profile_use_system_colors),
                    subtitle = stringResource(R.string.profile_use_system_colors_desc),
                    icon = Icons.Filled.Palette,
                    iconContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
                    onIconContainer = MaterialTheme.colorScheme.onSurfaceVariant,
                    trailing = {
                        Switch(checked = useSystemColors, onCheckedChange = { useSystemColors = it })
                    },
                )
            }

            SecondaryPillButton(
                text = stringResource(R.string.login_sign_in),
                onClick = onSignIn,
                icon = Icons.AutoMirrored.Filled.Login,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProfileHero(
    displayName: String?,
    avatarRef: String?,
    onEditName: () -> Unit,
    onChangePhoto: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                onClick = onChangePhoto,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(112.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    UserAvatar(
                        avatarRef = avatarRef,
                        displayName = displayName,
                        modifier = Modifier.fillMaxSize(),
                        emojiSize = 64.dp,
                    )
                }
            }
            // Small camera badge overlapping the avatar to invite changing it.
            Surface(
                onClick = onChangePhoto,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = stringResource(R.string.profile_change_photo),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(7.dp).size(18.dp),
                )
            }
        }
        // Tappable name — edit it any time, signed in or not.
        Surface(
            onClick = onEditName,
            shape = CircleShape,
            color = androidx.compose.ui.graphics.Color.Transparent,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = displayName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.profile_set_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.profile_edit_name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.profile_signed_out),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A simple dialog to capture/edit the player's display name. */
@Composable
private fun NameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_name_dialog_title)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.profile_name_dialog_label)) },
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.profile_save_name))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.create_bucket_cancel))
            }
        },
    )
}

/**
 * Avatar chooser: a grid of built-in preset avatars (the crash-free default
 * path) plus an option to use a personal photo. No external crop activity is
 * involved, so it can't crash on theme mismatch.
 */
@Composable
private fun AvatarPickerDialog(
    onPreset: (Int) -> Unit,
    onChoosePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_avatar_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AVATAR_PRESETS.indices.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { index ->
                            Surface(
                                onClick = { onPreset(index) },
                                shape = CircleShape,
                                color = androidx.compose.ui.graphics.Color.Transparent,
                                modifier = Modifier.size(56.dp),
                            ) {
                                UserAvatar(
                                    avatarRef = avatarRefForPreset(index),
                                    displayName = null,
                                    modifier = Modifier.fillMaxSize(),
                                    emojiSize = 30.dp,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onChoosePhoto) {
                Text(stringResource(R.string.profile_choose_photo))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.create_bucket_cancel))
            }
        },
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(4.dp)) { content() }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
    )
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A circular "+" button on the Buckets row to create a new bucket. */
@Composable
private fun JoinPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = stringResource(R.string.leaderboard_join_org),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
