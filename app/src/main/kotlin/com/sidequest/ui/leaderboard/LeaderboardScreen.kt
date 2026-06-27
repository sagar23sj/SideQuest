package com.sidequest.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidequest.R
import com.sidequest.ui.components.PillButton

/** A single leaderboard standing. */
data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val points: Int,
    val isCurrentUser: Boolean = false,
)

/** The three leaderboard periods (Req 12): daily, weekly, monthly. */
private enum class LeaderboardPeriod(val labelRes: Int) {
    DAILY(R.string.leaderboard_period_day),
    WEEKLY(R.string.leaderboard_period_week),
    MONTHLY(R.string.leaderboard_period_month),
}

/**
 * Leaderboard (Req 12): three period tabs (Day / Week / Month) over a ranked,
 * descending standing — a top-three podium followed by the remaining ranks, with
 * the current user's row highlighted. A user not in any organization sees a join
 * prompt instead of a board.
 *
 * Aggregation, period rollover (monthly reset, daily/weekly boundaries), and org
 * membership are computed by the Go backend; there is no client leaderboard
 * repository yet, so this screen renders from a local sample and a local
 * `inOrganization` flag. Wiring later means feeding the ranked entries and the
 * membership flag from a repository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onJoinOrganization: () -> Unit = {},
) {
    // Local stand-ins until a client leaderboard repository exists.
    val inOrganization by remember { mutableStateOf(true) }
    var selectedPeriod by remember { mutableIntStateOf(1) }

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
                        text = stringResource(R.string.leaderboard_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.reminder_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!inOrganization) {
            NoOrgPrompt(
                modifier = Modifier.padding(innerPadding),
                onJoinOrganization = onJoinOrganization,
            )
            return@Scaffold
        }

        val entries = sampleEntries(LeaderboardPeriod.entries[selectedPeriod])
        val podium = entries.take(3)
        val rest = entries.drop(3)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.leaderboard_subtitle),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    PeriodSelector(
                        selectedIndex = selectedPeriod,
                        onSelect = { selectedPeriod = it },
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Podium(podium)
                Spacer(Modifier.height(8.dp))
            }

            items(rest, key = { it.rank }) { entry ->
                LeaderboardRow(entry)
            }

            item {
                Spacer(Modifier.height(12.dp))
                CtaBanner(onExplore = onJoinOrganization)
            }
        }
    }
}

/**
 * A pill-shaped segmented control for the three periods, matching the app's
 * rounded design language (in place of the stock underlined TabRow).
 */
@Composable
private fun PeriodSelector(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .width(320.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            LeaderboardPeriod.entries.forEachIndexed { index, period ->
                val selected = index == selectedIndex
                Surface(
                    onClick = { onSelect(index) },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(period.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * The top-three podium: rank 1 elevated and centered with a trophy crown, rank 2
 * to the left and rank 3 to the right, each with a tonal avatar ring and a rank
 * badge — the celebratory hero of the board.
 */
@Composable
private fun Podium(top: List<LeaderboardEntry>) {
    val first = top.getOrNull(0)
    val second = top.getOrNull(1)
    val third = top.getOrNull(2)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
            second?.let { PodiumColumn(it, avatarSize = 64.dp, crowned = false) }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
            first?.let { PodiumColumn(it, avatarSize = 96.dp, crowned = true) }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
            third?.let { PodiumColumn(it, avatarSize = 56.dp, crowned = false) }
        }
    }
}

@Composable
private fun PodiumColumn(
    entry: LeaderboardEntry,
    avatarSize: androidx.compose.ui.unit.Dp,
    crowned: Boolean,
) {
    val desc = stringResource(R.string.leaderboard_rank_desc, entry.rank, entry.name, entry.points)
    val ringBrush = when (entry.rank) {
        1 -> Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer),
        )
        2 -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.secondaryContainer,
            ),
        )
        else -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics { contentDescription = desc },
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            if (crowned) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(34.dp)
                        .offset(y = -(avatarSize + 6.dp)),
                )
            }
            Box(contentAlignment = Alignment.BottomCenter) {
                // Gradient ring with initials avatar.
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(ringBrush)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    InitialAvatar(
                        name = entry.name,
                        size = avatarSize - 6.dp,
                        background = MaterialTheme.colorScheme.surfaceContainerLowest,
                    )
                }
                // Rank badge.
                Box(
                    modifier = Modifier
                        .offset(y = 8.dp)
                        .size(if (crowned) 28.dp else 24.dp)
                        .clip(CircleShape)
                        .background(
                            if (crowned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = entry.rank.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (crowned) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.name,
            style = if (crowned) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.leaderboard_points_short, entry.points),
            style = if (crowned) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (entry.rank <= 2) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
    val you = entry.isCurrentUser
    val displayName = if (you) stringResource(R.string.leaderboard_you) else entry.name
    val desc = stringResource(R.string.leaderboard_rank_desc, entry.rank, displayName, entry.points)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = desc },
        shape = RoundedCornerShape(20.dp),
        color = if (you) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = entry.rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (you) FontWeight.Bold else FontWeight.Medium,
                color = if (you) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
            Box(contentAlignment = Alignment.BottomEnd) {
                InitialAvatar(
                    name = entry.name,
                    size = 48.dp,
                    background = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                if (you) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (you) FontWeight.Bold else FontWeight.Medium,
                color = if (you) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.leaderboard_points_short, entry.points),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (you) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** A circular avatar showing the person's initial on a tonal background. */
@Composable
private fun InitialAvatar(
    name: String,
    size: androidx.compose.ui.unit.Dp,
    background: Color,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CtaBanner(onExplore: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.GroupAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = stringResource(R.string.leaderboard_cta_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = stringResource(R.string.leaderboard_cta_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                onClick = onExplore,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.leaderboard_cta_button),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiary,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoOrgPrompt(
    modifier: Modifier = Modifier,
    onJoinOrganization: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.leaderboard_no_org_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.leaderboard_no_org_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            PillButton(
                text = stringResource(R.string.leaderboard_join_org),
                onClick = onJoinOrganization,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A small local sample so the board renders; the backend supplies the real data. */
private fun sampleEntries(period: LeaderboardPeriod): List<LeaderboardEntry> {
    val base = when (period) {
        LeaderboardPeriod.DAILY -> 380
        LeaderboardPeriod.WEEKLY -> 1850
        LeaderboardPeriod.MONTHLY -> 7400
    }
    val names = listOf("Sarah M.", "Alex T.", "David P.", "Emily R.", "You", "Marcus J.", "Chloe W.")
    return names.mapIndexed { index, name ->
        LeaderboardEntry(
            rank = index + 1,
            name = name,
            points = base - index * (base / 18),
            isCurrentUser = name == "You",
        )
    }
}
