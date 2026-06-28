package com.sidequest.ui.games

import android.content.Context
import java.time.LocalDate

/**
 * Lightweight, client-side daily persistence for the games: it both enforces the
 * once-per-day rule and saves in-progress state so a player can leave and resume
 * exactly where they left off (and an exhausted round stays exhausted).
 *
 * The authoritative replay guard, shared daily puzzle, and scoring belong to the
 * Go backend (one shared puzzle per org per day, results stored server-side).
 * Until a client games repository exists, this records the local day's progress
 * in SharedPreferences. The stored day is an epoch-day, so a new calendar day
 * naturally unlocks the next puzzle.
 *
 * Elapsed time is tracked as *active* play seconds (only accumulated while the
 * game screen is open and the round is unfinished), so pausing/resuming doesn't
 * inflate the timer. It is captured for a future time-as-tiebreaker ranking.
 */
private const val PREFS_NAME = "sidequest_daily_games"
private const val LIST_SEP = "|"

/** Today's local epoch-day, used to scope a single day's play. */
internal fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

/** Maximum Word Guess attempts per day (shared by the game and the hub). */
internal const val WORD_GUESS_MAX_ATTEMPTS = 5

private fun prefs(context: Context) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

private fun encode(list: List<String>): String = list.joinToString(LIST_SEP)

private fun decode(raw: String?): List<String> =
    if (raw.isNullOrEmpty()) emptyList() else raw.split(LIST_SEP)

// --- Word Guess -------------------------------------------------------------

/**
 * Resumable Word Guess progress. [solved], [attempts] and [exhausted] are
 * derived from [guesses] against [answer].
 */
internal data class WordGuessProgress(
    val day: Long,
    val answer: String,
    val guesses: List<String>,
    val elapsedSeconds: Int,
    val finished: Boolean,
) {
    val solved: Boolean get() = guesses.lastOrNull() == answer
    val attempts: Int get() = guesses.size
}

internal fun loadWordGuessProgress(context: Context): WordGuessProgress? {
    val p = prefs(context)
    val day = p.getLong("wg_day", -1L)
    if (day < 0L) return null
    val answer = p.getString("wg_answer", null) ?: return null
    return WordGuessProgress(
        day = day,
        answer = answer,
        guesses = decode(p.getString("wg_guesses", "")),
        elapsedSeconds = p.getInt("wg_elapsed", 0),
        finished = p.getBoolean("wg_finished", false),
    )
}

internal fun saveWordGuessProgress(context: Context, progress: WordGuessProgress) {
    prefs(context).edit()
        .putLong("wg_day", progress.day)
        .putString("wg_answer", progress.answer)
        .putString("wg_guesses", encode(progress.guesses))
        .putInt("wg_elapsed", progress.elapsedSeconds)
        .putBoolean("wg_finished", progress.finished)
        .apply()
}

/** Today's Word Guess progress, or null if none has been saved for today. */
internal fun todayWordGuessProgress(context: Context): WordGuessProgress? =
    loadWordGuessProgress(context)?.takeIf { it.day == todayEpochDay() }

// --- Spelling Bee -----------------------------------------------------------

/** Resumable Spelling Bee progress for the day. */
internal data class SpellingBeeProgress(
    val day: Long,
    val found: List<String>,
    val score: Int,
    val elapsedSeconds: Int,
)

internal fun loadSpellingBeeProgress(context: Context): SpellingBeeProgress? {
    val p = prefs(context)
    val day = p.getLong("sb_day", -1L)
    if (day < 0L) return null
    return SpellingBeeProgress(
        day = day,
        found = decode(p.getString("sb_found", "")),
        score = p.getInt("sb_score", 0),
        elapsedSeconds = p.getInt("sb_elapsed", 0),
    )
}

internal fun saveSpellingBeeProgress(context: Context, progress: SpellingBeeProgress) {
    prefs(context).edit()
        .putLong("sb_day", progress.day)
        .putString("sb_found", encode(progress.found))
        .putInt("sb_score", progress.score)
        .putInt("sb_elapsed", progress.elapsedSeconds)
        .apply()
}

/** Today's Spelling Bee progress, or null if none has been saved for today. */
internal fun todaySpellingBeeProgress(context: Context): SpellingBeeProgress? =
    loadSpellingBeeProgress(context)?.takeIf { it.day == todayEpochDay() }

// --- Daily puzzles ----------------------------------------------------------

/**
 * The deterministic word of the day. Mixes 5- and 6-letter words for variety;
 * the board sizes itself to the chosen word's length. Selection is by epoch-day
 * so every player gets the same word on the same calendar day (matching the
 * backend's "one shared word per day" intent).
 */
internal fun dailyWord(epochDay: Long): String {
    val words = listOf(
        // 5-letter
        "QUEST", "FOCUS", "HABIT", "GOALS", "SPARK",
        "BRAVE", "SHINE", "DREAM", "POWER", "LEARN",
        // 6-letter
        "ROCKET", "GROWTH", "ACTION", "THRIVE", "WONDER",
        "ENERGY", "MASTER", "INTENT", "STREAK", "REWARD",
    ).filter { it.length in 5..6 }
    val index = Math.floorMod(epochDay, words.size.toLong()).toInt()
    return words[index]
}

/** A Spelling Bee puzzle: a required center letter plus six outer letters. */
internal data class SpellingBeePuzzle(val center: Char, val outer: List<Char>)

/**
 * The deterministic Spelling Bee puzzle of the day, picked by epoch-day so the
 * letter set is shared and stable for resuming. Curated sets that can form
 * several 4+ letter words including the center letter.
 */
internal fun dailySpellingBee(epochDay: Long): SpellingBeePuzzle {
    val puzzles = listOf(
        SpellingBeePuzzle('A', listOf('R', 'T', 'L', 'N', 'E', 'C')),
        SpellingBeePuzzle('T', listOf('R', 'A', 'I', 'N', 'E', 'S')),
        SpellingBeePuzzle('O', listOf('C', 'P', 'I', 'T', 'R', 'S')),
        SpellingBeePuzzle('E', listOf('R', 'A', 'D', 'L', 'T', 'S')),
        SpellingBeePuzzle('R', listOf('A', 'E', 'D', 'O', 'T', 'S')),
        SpellingBeePuzzle('N', listOf('A', 'E', 'I', 'T', 'R', 'D')),
        SpellingBeePuzzle('I', listOf('R', 'A', 'N', 'T', 'S', 'E')),
    )
    val index = Math.floorMod(epochDay, puzzles.size.toLong()).toInt()
    return puzzles[index]
}
