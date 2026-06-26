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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actiontracker.R

/**
 * Spelling Bee (Req 11.1): form words from a fixed set of letters that must
 * include the required center letter. A word is accepted only if it uses the
 * allowed letters and includes the center letter; the score grows per accepted
 * word.
 *
 * The authoritative puzzle (today's letters + word list) and scoring live on the
 * Go backend, with one shared puzzle per org per day and a replay guard. There
 * is no client games repository yet, so this screen uses a fixed local letter
 * set and a length-based acceptance rule as a faithful UI stand-in. Wiring it to
 * the backend later means feeding [SpellingBeeState] from a games repository and
 * replacing [isAcceptable] with the server word-list check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellingBeeScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    // Local placeholder puzzle. The center letter is required in every word.
    val centerLetter = 'A'
    val outerLetters = remember { listOf('R', 'T', 'L', 'N', 'E', 'C') }

    var current by remember { mutableStateOf("") }
    var found by remember { mutableStateOf(listOf<String>()) }
    var score by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val allowed = remember(outerLetters) { (outerLetters + centerLetter).toSet() }

    fun submit() {
        val word = current.uppercase()
        if (isAcceptable(word, allowed, centerLetter) && word !in found) {
            found = listOf(word) + found
            score += scoreFor(word)
            current = ""
            error = null
        } else {
            error = "invalid"
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.spelling_bee_title)) },
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
            Text(
                text = stringResource(R.string.games_score_label, score),
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = current.ifEmpty { "—" },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (error != null) {
                Text(
                    text = stringResource(R.string.spelling_bee_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            LetterPad(
                centerLetter = centerLetter,
                outerLetters = outerLetters,
                onLetter = { current += it },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (current.isNotEmpty()) current = current.dropLast(1) }) {
                    Text(stringResource(R.string.spelling_bee_delete))
                }
                Button(onClick = { submit() }, enabled = current.isNotEmpty()) {
                    Text(stringResource(R.string.spelling_bee_submit))
                }
            }

            Text(
                text = stringResource(R.string.spelling_bee_found_label, found.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(found, key = { it }) { word ->
                    Text(text = word, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * The letter pad: six outer letters around a highlighted required center letter.
 * Rendered as two rows with the center letter centered for a hex-like layout
 * without a custom layout dependency.
 */
@Composable
private fun LetterPad(
    centerLetter: Char,
    outerLetters: List<Char>,
    onLetter: (Char) -> Unit,
) {
    val top = outerLetters.take(3)
    val bottom = outerLetters.drop(3)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LetterRow(top, onLetter)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LetterHex(centerLetter, isCenter = true, onLetter = onLetter)
        }
        LetterRow(bottom, onLetter)
    }
}

@Composable
private fun LetterRow(letters: List<Char>, onLetter: (Char) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        letters.forEach { LetterHex(it, isCenter = false, onLetter = onLetter) }
    }
}

@Composable
private fun LetterHex(
    letter: Char,
    isCenter: Boolean,
    onLetter: (Char) -> Unit,
) {
    val container = if (isCenter) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val onContainer = if (isCenter) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val desc = if (isCenter) {
        stringResource(R.string.spelling_bee_center_letter_desc, letter.toString())
    } else {
        stringResource(R.string.spelling_bee_letter_desc, letter.toString())
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(container)
            .clickable { onLetter(letter) }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter.toString(),
            color = onContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Client-side acceptance stand-in: a word is acceptable when it is at least four
 * letters, uses only [allowed] letters, and contains the required [centerLetter].
 * The authoritative word-list membership check is the backend's responsibility.
 */
private fun isAcceptable(word: String, allowed: Set<Char>, centerLetter: Char): Boolean =
    word.length >= 4 &&
        word.all { it in allowed } &&
        word.contains(centerLetter)

/** Simple local score: 1 point for a 4-letter word, +1 per extra letter. */
private fun scoreFor(word: String): Int = (word.length - 3).coerceAtLeast(1)
