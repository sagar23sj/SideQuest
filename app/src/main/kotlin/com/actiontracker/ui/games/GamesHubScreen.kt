package com.actiontracker.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.actiontracker.R

/**
 * Games Hub (Req 11): entry point to the two daily games and the leaderboard.
 *
 * The game *logic* (puzzle generation, scoring, replay guard) lives on the Go
 * backend; there is no client-side game repository yet. This screen is therefore
 * presentational — it surfaces the two games and routes into them. When a client
 * games repository is added, replace the static "play / completed" affordance
 * with the day's real play state.
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.games_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.games_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GameCard(
                title = stringResource(R.string.games_spelling_bee),
                description = stringResource(R.string.games_spelling_bee_desc),
                onPlay = onPlaySpellingBee,
            )
            GameCard(
                title = stringResource(R.string.games_word_guess),
                description = stringResource(R.string.games_word_guess_desc),
                onPlay = onPlayWordGuess,
            )

            OutlinedButton(
                onClick = onOpenLeaderboard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null)
                Text(
                    text = stringResource(R.string.games_view_leaderboard),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GameCard(
    title: String,
    description: String,
    onPlay: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onPlay) {
                    Text(stringResource(R.string.games_play))
                }
            }
        }
    }
}
