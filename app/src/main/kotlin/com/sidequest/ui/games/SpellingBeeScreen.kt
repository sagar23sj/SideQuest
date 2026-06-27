package com.sidequest.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidequest.R

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
 * the backend later means feeding state from a games repository and replacing
 * [isAcceptable] with the server word-list check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellingBeeScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    // Local placeholder puzzle. The center letter is required in every word.
    val centerLetter = 'A'
    var outerLetters by remember { mutableStateOf(listOf('R', 'T', 'L', 'N', 'E', 'C')) }

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.spelling_bee_title)) },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            // Current word being built.
            Box(
                modifier = Modifier.height(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (current.isEmpty()) "—" else current.toCharArray().joinToString(" "),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (current.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            Text(
                text = if (error != null) {
                    stringResource(R.string.spelling_bee_invalid)
                } else {
                    " "
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            Spacer(Modifier.height(8.dp))

            Honeycomb(
                centerLetter = centerLetter,
                outerLetters = outerLetters,
                onLetter = { current += it; error = null },
            )

            Spacer(Modifier.height(24.dp))

            // Action row: Delete · Shuffle · Enter.
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                PillButton(
                    label = stringResource(R.string.spelling_bee_delete),
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    onClick = { if (current.isNotEmpty()) current = current.dropLast(1) },
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            outerLetters = outerLetters.shuffled()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.spelling_bee_shuffle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PillButton(
                    label = stringResource(R.string.spelling_bee_submit),
                    container = MaterialTheme.colorScheme.tertiary,
                    content = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.weight(1f),
                    onClick = { submit() },
                )
            }

            Spacer(Modifier.height(20.dp))

            FoundWordsSheet(found = found, score = score)
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The honeycomb letter pad: six outer hexagons arranged around a highlighted
 * required center hexagon, in three overlapping rows (2 / 3 / 2) for the classic
 * spelling-bee shape. The center letter must appear in every accepted word.
 */
@Composable
private fun Honeycomb(
    centerLetter: Char,
    outerLetters: List<Char>,
    onLetter: (Char) -> Unit,
) {
    val safe = (outerLetters + List(6) { ' ' }).take(6)
    // Vertical overlap so rows interlock like a honeycomb.
    val rowOverlap = (-22).dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HexLetter(safe[0], isCenter = false, onLetter)
            HexLetter(safe[1], isCenter = false, onLetter)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.offset(y = rowOverlap),
        ) {
            HexLetter(safe[2], isCenter = false, onLetter)
            HexLetter(centerLetter, isCenter = true, onLetter)
            HexLetter(safe[3], isCenter = false, onLetter)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.offset(y = rowOverlap * 2),
        ) {
            HexLetter(safe[4], isCenter = false, onLetter)
            HexLetter(safe[5], isCenter = false, onLetter)
        }
    }
}

@Composable
private fun HexLetter(
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
            .width(80.dp)
            .height(92.dp)
            .clip(HexagonShape)
            .background(container)
            .clickable(enabled = letter != ' ') { onLetter(letter) }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter.toString(),
            color = onContainer,
            fontWeight = FontWeight.Bold,
            fontSize = if (isCenter) 34.sp else 30.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FoundWordsSheet(found: List<String>, score: Int) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.spelling_bee_found_label, found.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.games_score_label, score),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (found.isEmpty()) {
                Text(
                    text = stringResource(R.string.spelling_bee_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(found, key = { it }) { word ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ) {
                            Text(
                                text = word,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A pointy-top hexagon matching the design's `clip-hexagon` polygon. */
private val HexagonShape: Shape = object : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.25f)
            lineTo(w, h * 0.75f)
            lineTo(w * 0.5f, h)
            lineTo(0f, h * 0.75f)
            lineTo(0f, h * 0.25f)
            close()
        }
        return Outline.Generic(path)
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
