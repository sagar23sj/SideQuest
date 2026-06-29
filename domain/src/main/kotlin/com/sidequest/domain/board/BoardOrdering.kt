package com.sidequest.domain.board

/**
 * Pure, total ordering of board [BoardGroup]s for display.
 *
 * The board surfaces the buckets a user engages with most, while still opening
 * on a sensible curated order for a brand-new user. [orderByActivity] sorts by,
 * in priority:
 *
 *  1. **Content volume** — buckets holding more items rank higher (the "amount
 *     of content you keep here" signal).
 *  2. **Recency** — among equal volumes, the bucket touched most recently
 *     (largest `createdAt` across its items) ranks higher, so freshly-used
 *     buckets rise.
 *  3. **Curated default order** — the position of the bucket's name in
 *     [defaultOrder]. For a new user every bucket is empty, so volume and
 *     recency tie and this preserves the curated starting order exactly.
 *  4. **Name** — a final stable tiebreak for custom buckets not in the default
 *     list (and for fully-deterministic output).
 *
 * The sort is stable and never mutates [groups]; unknown/custom buckets simply
 * fall to the end of the curated tier (then alphabetical), never lost.
 */
object BoardOrdering {

    fun orderByActivity(
        groups: List<BoardGroup>,
        defaultOrder: List<String>,
    ): List<BoardGroup> {
        val priority: Map<String, Int> =
            defaultOrder.withIndex().associate { (index, name) -> name to index }

        return groups.sortedWith(
            compareByDescending<BoardGroup> { it.items.size }
                .thenByDescending { lastActivity(it) }
                .thenBy { priority[it.bucket.name] ?: Int.MAX_VALUE }
                .thenBy { it.bucket.name },
        )
    }

    /** The most recent `createdAt` among a group's items, or a floor when empty. */
    private fun lastActivity(group: BoardGroup): Long =
        group.items.maxOfOrNull { it.item.createdAt } ?: Long.MIN_VALUE
}
