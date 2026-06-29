package com.sidequest

/**
 * Staged-rollout feature switches. While SideQuest focuses on its core planner
 * experience, secondary features are hidden behind these flags rather than
 * removed — flip a flag to `true` to surface the feature again, no other change
 * needed. The underlying screens, routes, and data all remain in the codebase.
 */
object FeatureFlags {

    /** Daily games (Word Guess, Spelling Bee) hub + its leaderboard entry. */
    const val GAMES_ENABLED: Boolean = false

    /** Voice journaling (record + transcribe + extract actions). */
    const val VOICE_JOURNAL_ENABLED: Boolean = false

    /** Guilds / organizations (join-by-code, shared leaderboards). */
    const val GUILDS_ENABLED: Boolean = false

    /** Shopping buckets (the product/source/purchased toggle on a bucket). */
    const val SHOPPING_BUCKETS_ENABLED: Boolean = false
}
