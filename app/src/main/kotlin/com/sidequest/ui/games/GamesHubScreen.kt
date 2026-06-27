package com.sidequest.ui.games

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sidequest.R
import com.sidequest.ui.components.GradientPillButton
import com.sidequest.ui.components.SecondaryPillButton

/**
 * Games Hub (Req 11), styled to the SideQuest design: a centered header with a
 * daily-reset chip, two vibrant game cards (each with an icon badge, a status
 * line, and a gradient "play" button), and a streak card linking to the
 * leaderboard. Game logic lives on the Go backend; this screen routes into the
 * games.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesHubScreen(
    modifier: Modifier = Modifier,
    onPlaySpellingBee: () -> Unit = {},
    onPlayWordGuess: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
) {
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
                        text = stringResource(R.string.games_title),
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DailyHeader()

            GameCard(
                title = stringResource(R.string.games_word_guess),
                tagline = stringResource(R.string.games_word_guess_tag),
                statusLine = stringResource(R.string.games_word_guess_desc),
                icon = Icons.Filled.Grid4x4,
                badgeContainer = MaterialTheme.colorScheme.primary,
                onBadge = MaterialTheme.colorScheme.onPrimary,
                cardContainer = MaterialTheme.colorScheme.surfaceContainerHigh,
                gradientStart = MaterialTheme.colorScheme.primary,
                gradientEnd = MaterialTheme.colorScheme.surfaceTint,
                onGradient = MaterialTheme.colorScheme.onPrimary,
                onPlay = onPlayWordGuess,
            )

            GameCard(
                title = stringResource(R.string.games_spelling_bee),
                tagline = stringResource(R.string.games_spelling_bee_tag),
                statusLine = stringResource(R.string.games_spelling_bee_desc),
                icon = Icons.Filled.Spellcheck,
                badgeContainer = MaterialTheme.colorScheme.secondary,
                onBadge = MaterialTheme.colorScheme.onSecondary,
                cardContainer = MaterialTheme.colorScheme.surfaceContainerHigh,
                gradientStart = MaterialTheme.colorScheme.secondary,
                gradientEnd = MaterialTheme.colorScheme.onSecondaryContainer,
                onGradient = MaterialTheme.colorScheme.onSecondary,
                onPlay = onPlaySpellingBee,
            )

            StreakCard(onOpenLeaderboard = onOpenLeaderboard)
        }
    }
}

@Composable
private fun DailyHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.games_today_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.games_resets_daily),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Text(
            text = stringResource(R.string.games_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GameCard(
    title: String,
    tagline: String,
    statusLine: String,
    icon: ImageVector,
    badgeContainer: Color,
    onBadge: Color,
    cardContainer: Color,
    gradientStart: Color,
    gradientEnd: Color,
    onGradient: Color,
    onPlay: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = cardContainer,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(badgeContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = onBadge)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = tagline.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GradientPillButton(
                text = stringResource(R.string.games_play),
                onClick = onPlay,
                startColor = gradientStart,
                endColor = gradientEnd,
                contentColor = onGradient,
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StreakCard(onOpenLeaderboard: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.games_streak_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.games_streak_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SecondaryPillButton(
                text = stringResource(R.string.games_view_leaderboard),
                onClick = onOpenLeaderboard,
                icon = Icons.Filled.EmojiEvents,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
