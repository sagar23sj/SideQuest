package com.sidequest.domain.voice

import com.sidequest.domain.llm.ExtractedAction

/**
 * A pure, on-device fallback that extracts candidate [ExtractedAction]s from a
 * voice-journal transcript without any LLM or network — used when the
 * LLM_Service is unavailable so action extraction still works fully offline
 * (Req 10.5/10.6, on-device analysis).
 *
 * The heuristic is deliberately simple and explainable: it splits the transcript
 * into clauses, strips conversational lead-ins ("I need to", "remember to", …),
 * and keeps clauses that read like tasks (a stripped lead-in, or an opening
 * action verb). Each kept clause becomes a tidy, title-cased candidate the user
 * still confirms before any Action_Item is created — so a loose heuristic is
 * safe: the user is the final filter.
 *
 * It lives in `:domain` with no Android dependency so it is unit-testable and
 * reusable, and so a future on-device LLM (e.g. Gemini Nano) can replace it
 * behind the same [ExtractedAction] shape.
 */
object HeuristicActionExtraction {

    /** Conversational lead-ins stripped from the front of a clause to form a title. */
    private val LEAD_INS: List<String> = listOf(
        "i really need to", "i also need to", "i need to", "i have to", "i've got to",
        "i want to", "i would like to", "i'd like to", "i should", "i must",
        "i'm going to", "im going to", "i am going to", "i plan to", "i intend to",
        "remember to", "don't forget to", "dont forget to", "make sure to",
        "make sure i", "let me", "gotta", "got to", "need to", "have to", "want to",
        "to do", "todo", "please", "and then", "then", "also", "and",
    )

    /** Opening verbs that mark a clause as a task even without a lead-in. */
    private val ACTION_VERBS: Set<String> = setOf(
        "buy", "call", "book", "finish", "send", "schedule", "plan", "order", "try",
        "review", "renew", "email", "text", "pay", "clean", "write", "read", "watch",
        "check", "fix", "update", "prepare", "cook", "visit", "return", "cancel",
        "research", "draft", "organize", "organise", "install", "download", "submit",
        "complete", "start", "contact", "message", "reply", "book", "pack", "wash",
        "fill", "sign", "print", "share", "post", "upload", "back", "set",
    )

    /** Keyword → suggested bucket name, mirroring the app's domain buckets. */
    private val BUCKET_HINTS: List<Pair<List<String>, String>> = listOf(
        listOf("flight", "trip", "travel", "passport", "hotel", "pack") to "Travel",
        listOf("cook", "recipe", "dinner", "lunch", "meal", "pasta", "bake") to "Cooking",
        listOf("buy", "order", "shop", "purchase", "headphones", "groceries") to "Shopping",
        listOf("read", "study", "learn", "course", "article", "book") to "Learning",
        listOf("clean", "house", "home", "laundry", "fix", "repair") to "Home",
    )

    private const val MAX_CANDIDATES = 6
    private const val MAX_TITLE_LENGTH = 70

    /**
     * Extracts up to [MAX_CANDIDATES] candidate actions from [transcript].
     *
     * Returns an empty list for a blank transcript. When no clause reads like a
     * task, it falls back to a single candidate built from the first sentence so
     * the review screen is never empty for a non-blank transcript.
     */
    fun extract(transcript: String): List<ExtractedAction> {
        if (transcript.isBlank()) return emptyList()

        val clauses = splitClauses(transcript)
        val candidates = mutableListOf<ExtractedAction>()
        val seen = mutableSetOf<String>()

        for (clause in clauses) {
            val (title, wasStripped) = stripLeadIn(clause)
            if (title.isBlank()) continue
            val looksLikeTask = wasStripped || startsWithActionVerb(title)
            if (!looksLikeTask) continue
            val tidy = titleCase(title)
            val key = tidy.lowercase()
            if (tidy.length < 3 || key in seen) continue
            seen += key
            candidates += ExtractedAction(title = tidy, suggestedBucketName = bucketHintFor(clause))
            if (candidates.size >= MAX_CANDIDATES) break
        }

        if (candidates.isNotEmpty()) return candidates

        // Nothing read like an explicit task: offer the first sentence so the
        // user still has something to confirm or discard.
        val first = clauses.firstOrNull()?.let { stripLeadIn(it).first }?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return listOf(ExtractedAction(title = titleCase(first), suggestedBucketName = bucketHintFor(first)))
    }

    /** Splits a transcript into trimmed clauses on sentence and connector boundaries. */
    private fun splitClauses(transcript: String): List<String> =
        transcript
            .split(Regex("[.!?\\n;,]+|\\b(?:and then|and|then)\\b", RegexOption.IGNORE_CASE))
            .map { it.trim().trim(',', '-', ' ') }
            .filter { it.isNotBlank() }

    /**
     * Strips a leading conversational lead-in from [clause]. Returns the
     * remaining text and whether any lead-in was removed (a strong "this is a
     * task" signal).
     */
    private fun stripLeadIn(clause: String): Pair<String, Boolean> {
        var text = clause.trim()
        var stripped = false
        var changed = true
        while (changed) {
            changed = false
            val lower = text.lowercase()
            for (lead in LEAD_INS) {
                if (lower == lead) {
                    return "" to stripped
                }
                if (lower.startsWith("$lead ")) {
                    text = text.substring(lead.length).trim()
                    stripped = true
                    changed = true
                    break
                }
            }
        }
        return text to stripped
    }

    private fun startsWithActionVerb(text: String): Boolean {
        val firstWord = text.substringBefore(' ').lowercase().trim(',', '.', '!', '?')
        return firstWord in ACTION_VERBS
    }

    private fun bucketHintFor(clause: String): String? {
        val lower = clause.lowercase()
        return BUCKET_HINTS.firstOrNull { (keywords, _) ->
            keywords.any { lower.contains(it) }
        }?.second
    }

    /** Tidies a clause into a concise, capitalized title. */
    private fun titleCase(text: String): String {
        val trimmed = text.trim().trim(',', '.', '!', '?', ' ').take(MAX_TITLE_LENGTH)
        if (trimmed.isEmpty()) return trimmed
        return trimmed.replaceFirstChar { it.uppercaseChar() }
    }
}
