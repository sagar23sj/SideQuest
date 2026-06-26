package com.actiontracker.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.actiontracker.R
import com.actiontracker.ui.theme.LocalGameColors

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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.word_guess_title)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(stringResource(R.string.reminder_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GuessGrid(answer = answer, guesses = guesses, current = current)

            if (finished) {
                Text(
                    text = if (solved) {
                        stringResource(R.string.word_guess_won, guesses.size)
                    } else {
                        stringResource(R.string.word_guess_lost, answer)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
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
private fun GuessGrid(answer: String, guesses: List<String>, current: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until MAX_ATTEMPTS) {
            val guess = guesses.getOrNull(row)
            val isCurrentRow = guess == null && row == guesses.size
            val text = guess ?: if (isCurrentRow) current else ""
            val rowDesc = stringResource(R.string.word_guess_row_desc, row + 1)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics { contentDescription = rowDesc },
            ) {
                for (col in 0 until WORD_LENGTH) {
                    val letter = text.getOrNull(col)
                    val feedback = if (guess != null && letter != null) {
                        feedbackFor(answer, guess, col)
                    } else {
                        LetterFeedback.EMPTY
                    }
                    GuessTile(letter = letter, feedback = feedback)
                }
            }
        }
    }
}

@Composable
private fun GuessTile(letter: Char?, feedback: LetterFeedback) {
    val gameColors = LocalGameColors.current
    val container = when (feedback) {
        LetterFeedback.CORRECT -> gameColors.guessCorrect
        LetterFeedback.PRESENT -> gameColors.guessPresent
        LetterFeedback.ABSENT -> gameColors.guessAbsent
        LetterFeedback.EMPTY -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val onContainer = if (feedback == LetterFeedback.EMPTY) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color.White
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter?.toString() ?: "",
            color = onContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
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
    ) {
        rows.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (index == 2) {
                    KeyCap(label = stringResource(R.string.word_guess_submit), onClick = onEnter, wide = true)
                }
                row.forEach { c ->
                    KeyCap(label = c.toString(), feedback = keyState[c], onClick = { onKey(c) })
                }
                if (index == 2) {
                    KeyCap(label = "⌫", onClick = onBackspace, wide = true)
                }
            }
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    feedback: LetterFeedback? = null,
    wide: Boolean = false,
    onClick: () -> Unit,
) {
    val gameColors = LocalGameColors.current
    val container = when (feedback) {
        LetterFeedback.CORRECT -> gameColors.guessCorrect
        LetterFeedback.PRESENT -> gameColors.guessPresent
        LetterFeedback.ABSENT -> gameColors.guessAbsent
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val onContainer = if (feedback == null || feedback == LetterFeedback.EMPTY) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color.White
    }
    Box(
        modifier = Modifier
            .size(width = if (wide) 56.dp else 32.dp, height = 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = onContainer, fontWeight = FontWeight.Medium, fontSize = 14.sp)
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
