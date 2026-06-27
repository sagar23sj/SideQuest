package com.sidequest.ui.navigation

/**
 * Central registry of navigation routes for the single-activity app.
 *
 * Routes are split into two groups:
 * - [TopLevelDestination]s are the bottom-navigation tabs (Board, Games, Voice,
 *   Profile). They form the persistent nav shell.
 * - The remaining `const` routes are pushed on top of a tab (item detail,
 *   bucket management, create/edit bucket, leaderboard, reminder settings,
 *   voice review, the games, and the auth flow). They are reached via
 *   `navController.navigate(...)` and show a back affordance.
 *
 * Keeping route strings here (rather than scattered string literals) makes the
 * graph in [SideQuestNavHost] easy to audit and avoids typos between the
 * destination definition and its navigation calls.
 */
object Routes {
    const val BOARD = "board"
    const val GAMES = "games"
    const val VOICE = "voice"
    const val PROFILE = "profile"

    const val ITEM_DETAIL = "item_detail"
    const val ITEM_DETAIL_ARG = "actionItemId"
    const val ITEM_DETAIL_PATTERN = "$ITEM_DETAIL/{$ITEM_DETAIL_ARG}"

    const val BUCKETS = "buckets"
    const val BUCKET_DETAIL = "bucket_detail"
    const val BUCKET_DETAIL_ARG = "bucketId"
    const val BUCKET_DETAIL_PATTERN = "$BUCKET_DETAIL/{$BUCKET_DETAIL_ARG}"
    const val CREATE_BUCKET = "create_bucket"
    const val EDIT_BUCKET = "edit_bucket"
    const val EDIT_BUCKET_ARG = "bucketId"
    const val EDIT_BUCKET_PATTERN = "$EDIT_BUCKET/{$EDIT_BUCKET_ARG}"

    const val VOICE_REVIEW = "voice_review"
    const val VOICE_REVIEW_ARG = "entryId"
    const val VOICE_REVIEW_PATTERN = "$VOICE_REVIEW/{$VOICE_REVIEW_ARG}"

    const val REMINDER_SETTINGS = "reminder_settings"

    const val SPELLING_BEE = "spelling_bee"
    const val WORD_GUESS = "word_guess"
    const val LEADERBOARD = "leaderboard"

    const val LOGIN = "login"
    const val JOIN_ORG = "join_org"

    /** Builds a concrete item-detail route for [itemId]. */
    fun itemDetail(itemId: String): String = "$ITEM_DETAIL/$itemId"

    /** Builds a concrete edit-bucket route for [bucketId]. */
    fun editBucket(bucketId: String): String = "$EDIT_BUCKET/$bucketId"

    /** Builds a concrete bucket-detail route for [bucketId]. */
    fun bucketDetail(bucketId: String): String = "$BUCKET_DETAIL/$bucketId"

    /** Builds a concrete voice-review route for [entryId]. */
    fun voiceReview(entryId: String): String = "$VOICE_REVIEW/$entryId"
}
