package com.sidequest.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CorporateFare
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
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
    onOpenReminders: () -> Unit = {},
    onManageBuckets: () -> Unit = {},
    onJoinOrganization: () -> Unit = {},
    onSignIn: () -> Unit = {},
) {
    var useSystemColors by remember { mutableStateOf(false) }

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
            ProfileHero(onJoinOrganization = onJoinOrganization)

            SettingsGroup {
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
private fun ProfileHero(onJoinOrganization: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.profile_signed_out),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.profile_local_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
