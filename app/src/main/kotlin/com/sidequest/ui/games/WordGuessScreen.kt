package com.sidequest.ui.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidequest.R
import com.sidequest.ui.theme.LocalGameColors

private const val WORD_LENGTH = 5
private const val MAX_ATTEMPTS = 6

/** Per-letter feedback for a guessed tile (Req 11.2). */
private enum class LetterFeedback { CORRECT, PRESENT, ABSENT, EMPTY }

/**
 * Word Guess (Req 11.2): guess a hidden 5-letter word within six attempts, with
 * per-letter feedback — correct (right letter and position), present (in the
 * word, wrong position), or absent.
 *
 * The hidden word, word-list validation, scoring, and the once-per-day replay
 * guard are owned by the Go backend (one shared word per org per day). With no
 * client games repository yet, this screen plays against a fixed local answer
 * and computes feedback locally as a faithful UI stand-in. Wiring to the backend
 * later means sourcing the answer/feedback from a games repository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordGuessScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val answer = "QUEST"

    var guesses by remember { mutableStateOf(listOf<String>()) }
    var current by remember { mutableStateOf("") }

    val solved = guesses.lastOrNull() == answer
    val finished = solved || guesses.size >= MAX_ATTEMPTS

    fun onKey(c: Char) {
        if (finished) return
        if (current.length < WORD_LENGTH) current += c
    }
    fun onBackspace() {
        if (current.isNotEmpty()) current = current.dropLast(1)
    }
    fun onEnter() {
        if (current.length == WORD_LENGTH && !finished) {
            guesses = guesses + current
            current = ""
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.word_guess_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProgressHeader(attempt = guesses.size, finished = finished)

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                GuessGrid(answer = answer, guesses = guesses, current = current)
            }

            if (finished) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (solved) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ) {
                    Text(
                        text = if (solved) {
                            stringResource(R.string.word_guess_won, guesses.size)
                        } else {
                            stringResource(R.string.word_guess_lost, answer)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (solved) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }

            Keyboard(
                answer = answer,
                guesses = guesses,
                onKey = ::onKey,
                onEnter = ::onEnter,
                onBackspace = ::onBackspace,
            )
        }
    }
}

@Composable
private fun ProgressHeader(attempt: Int, finished: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(
                        R.string.word_guess_attempt,
                        (attempt + if (finished) 0 else 1).coerceAtMost(MAX_ATTEMPTS),
                        MAX_ATTEMPTS,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun GuessGrid(answer: String, guesses: List<String>, current: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 340.dp),
    ) {
        for (row in 0 until MAX_ATTEMPTS) {
            val guess = guesses.getOrNull(row)
            val isCurrentRow = guess == null && row == guesses.size
            val text = guess ?: if (isCurrentRow) current else ""
            val rowDesc = stringResource(R.string.word_guess_row_desc, row + 1)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = rowDesc },
            ) {
                for (col in 0 until WORD_LENGTH) {
                    val letter = text.getOrNull(col)
                    val feedback = if (guess != null && letter != null) {
                        feedbackFor(answer, guess, col)
                    } else {
                        LetterFeedback.EMPTY
                    }
                    GuessTile(
                        letter = letter,
                        feedback = feedback,
                        isActiveRow = isCurrentRow,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun GuessTile(
    letter: Char?,
    feedback: LetterFeedback,
    isActiveRow: Boolean,
    modifier: Modifier = Modifier,
) {
    val gameColors = LocalGameColors.current
    val scored = feedback != LetterFeedback.EMPTY
    val container = when (feedback) {
        LetterFeedback.CORRECT -> gameColors.guessCorrect
        LetterFeedback.PRESENT -> gameColors.guessPresent
        LetterFeedback.ABSENT -> gameColors.guessAbsent
        LetterFeedback.EMPTY ->
            if (isActiveRow) MaterialTheme.colorScheme.surfaceContainerLowest
            else MaterialTheme.colorScheme.surfaceContainer
    }
    val animatedContainer by animateColorAsState(container, label = "tileColor")
    val onContainer = if (scored) Color.White else MaterialTheme.colorScheme.onSurface

    // Filled-but-unscored cells in the active row get a primary border; empty
    // active cells get a soft outline, matching the design's entry row.
    val borderColor = when {
        scored -> Color.Transparent
        isActiveRow && letter != null -> MaterialTheme.colorScheme.primary
        isActiveRow -> MaterialTheme.colorScheme.outlineVariant
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(animatedContainer)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter?.toString() ?: "",
            color = onContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
        )
    }
}

@Composable
private fun Keyboard(
    answer: String,
    guesses: List<String>,
    onKey: (Char) -> Unit,
    onEnter: () -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    val keyState = remember(guesses) { computeKeyStates(answer, guesses) }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (index == 1) Spacer(Modifier.weight(0.5f))
                if (index == 2) {
                    KeyCap(
                        label = stringResource(R.string.word_guess_submit),
                        onClick = onEnter,
                        modifier = Modifier.weight(1.6f),
                    )
                }
                row.forEach { c ->
                    KeyCap(
                        label = c.toString(),
                        feedback = keyState[c],
                        onClick = { onKey(c) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (index == 2) {
                    KeyCap(
                        icon = true,
                        label = "⌫",
                        onClick = onBackspace,
                        modifier = Modifier.weight(1.6f),
                    )
                }
                if (index == 1) Spacer(Modifier.weight(0.5f))
            }
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    modifier: Modifier = Modifier,
    feedback: LetterFeedback? = null,
    icon: Boolean = false,
    onClick: () -> Unit,
) {
    val gameColors = LocalGameColors.current
    val container = when (feedback) {
        LetterFeedback.CORRECT -> gameColors.guessCorrect
        LetterFeedback.PRESENT -> gameColors.guessPresent
        LetterFeedback.ABSENT -> gameColors.guessAbsent.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val animatedContainer by animateColorAsState(container, label = "keyColor")
    val onContainer = when (feedback) {
        null, LetterFeedback.EMPTY -> MaterialTheme.colorScheme.onSurface
        LetterFeedback.ABSENT -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color.White
    }
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(animatedContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = stringResource(R.string.word_guess_backspace),
                tint = onContainer,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Text(
                text = label,
                color = onContainer,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (label.length > 1) 13.sp else 16.sp,
            )
        }
    }
}

/**
 * Computes per-letter feedback for [col] of [guess] against [answer]: CORRECT
 * when the letter matches in position, PRESENT when it occurs elsewhere in the
 * answer, ABSENT otherwise.
 */
private fun feedbackFor(answer: String, guess: String, col: Int): LetterFeedback {
    val c = guess[col]
    return when {
        answer.getOrNull(col) == c -> LetterFeedback.CORRECT
        answer.contains(c) -> LetterFeedback.PRESENT
        else -> LetterFeedback.ABSENT
    }
}

/** Best-known feedback per keyboard letter across all guesses (CORRECT wins). */
private fun computeKeyStates(answer: String, guesses: List<String>): Map<Char, LetterFeedback> {
    val state = mutableMapOf<Char, LetterFeedback>()
    guesses.forEach { guess ->
        guess.forEachIndexed { col, c ->
            val fb = feedbackFor(answer, guess, col)
            val existing = state[c]
            if (existing == null || fb.rank() > existing.rank()) {
                state[c] = fb
            }
        }
    }
    return state
}

private fun LetterFeedback.rank(): Int = when (this) {
    LetterFeedback.CORRECT -> 3
    LetterFeedback.PRESENT -> 2
    LetterFeedback.ABSENT -> 1
    LetterFeedback.EMPTY -> 0
}
