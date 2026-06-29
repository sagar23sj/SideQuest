package com.sidequest.data.seed

/**
 * The canonical set of starter buckets every new SideQuest user gets, shared by
 * both the release-safe [DefaultBucketSeeder] (buckets only) and the debug-only
 * [PreviewSeeder] (buckets plus sample quests).
 *
 * Each entry carries the bucket name and its three-color tonal status palette
 * (not-started / in-progress / completed), drawn from the SideQuest brand
 * scheme so the board reads clearly and each life area feels distinct. The
 * names match the keyword mappings in
 * [com.sidequest.ui.board.BucketVisuals] so every default bucket ships with a
 * topical cover photo and icon.
 */
data class DefaultBucketSpec(
    val name: String,
    val notStartedColor: String,
    val inProgressColor: String,
    val completedColor: String,
)

/** The default starter buckets, in display order. */
val DEFAULT_BUCKETS: List<DefaultBucketSpec> = listOf(
    DefaultBucketSpec("Travel", "#FFB59E", "#FF8A65", "#9F4122"),
    DefaultBucketSpec("Cooking", "#FFD8A8", "#FF922B", "#B85C00"),
    DefaultBucketSpec("Shopping", "#C5A3FF", "#9775FA", "#6D4EA2"),
    DefaultBucketSpec("Daily Rituals", "#8EF4E9", "#53BBB1", "#006A63"),
    DefaultBucketSpec("Learning", "#A5D8FF", "#4DABF7", "#1971C2"),
    DefaultBucketSpec("Vault", "#B0BEC5", "#78909C", "#37474F"),
    DefaultBucketSpec("Movies & Shows", "#B39DDB", "#7E57C2", "#4527A0"),
    DefaultBucketSpec("Appointments", "#90CAF9", "#6FA8DC", "#2B6CB0"),
    DefaultBucketSpec("Bills", "#E6CB6E", "#D4B45A", "#8A6D1F"),
)
