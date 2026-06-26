package com.actiontracker.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.actiontracker.R

/** A single leaderboard standing. */
data class LeaderboardEntry(val rank: Int, val name: String, val points: Int)

/** The three leaderboard periods (Req 12): daily, weekly, monthly. */
private enum class LeaderboardPeriod(val labelRes: Int) {
    DAILY(R.string.leaderboard_daily),
    WEEKLY(R.string.leaderboard_weekly),
    MONTHLY(R.string.leaderboard_monthly),
}

/**
 * Leaderboard (Req 12): three period tabs (Daily / Weekly / Monthly) over a
 * ranked, descending list. A user not in any organization sees a join prompt
 * instead of a board.
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
    var selectedPeriod by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.leaderboard_title)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(stringResource(R.string.reminder_back))
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

        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedPeriod) {
                LeaderboardPeriod.entries.forEachIndexed { index, period ->
                    Tab(
                        selected = selectedPeriod == index,
                        onClick = { selectedPeriod = index },
                        text = { Text(stringResource(period.labelRes)) },
                    )
                }
            }

            val entries = sampleEntries(LeaderboardPeriod.entries[selectedPeriod])
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(entries, key = { _, e -> e.rank }) { _, entry ->
                    LeaderboardRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
    val desc = stringResource(R.string.leaderboard_rank_desc, entry.rank, entry.name, entry.points)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = desc },
        color = if (entry.rank <= 3) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.rank.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = entry.points.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.leaderboard_no_org_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.leaderboard_no_org_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onJoinOrganization) {
                Text(stringResource(R.string.leaderboard_join_org))
            }
        }
    }
}

/** A small local sample so the board renders; the backend supplies the real data. */
private fun sampleEntries(period: LeaderboardPeriod): List<LeaderboardEntry> {
    val base = when (period) {
        LeaderboardPeriod.DAILY -> 40
        LeaderboardPeriod.WEEKLY -> 220
        LeaderboardPeriod.MONTHLY -> 980
    }
    val names = listOf("Riley", "Sam", "Avery", "Jordan", "Casey", "Quinn", "Devon")
    return names.mapIndexed { index, name ->
        LeaderboardEntry(rank = index + 1, name = name, points = base - index * 7)
    }
}
