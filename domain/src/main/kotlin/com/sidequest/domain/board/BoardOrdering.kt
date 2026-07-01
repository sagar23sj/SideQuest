package com.sidequest.domain.board

import com.sidequest.domain.model.Bucket

/**
 * Pure, total, **stable** ordering of buckets for display — identical on the
 * board and the capture bucket picker so the two never disagree.
 *
 * There is intentionally no usage/count-based dynamic reordering: buckets must
 * not move around as quests are added or completed (that breaks muscle memory
 * and is disorienting). The order is deterministic and user-controllable:
 *
 *  1. **[Bucket.position]** — explicit display order. Curated default buckets
 *     are seeded with ascending positions, new buckets are appended, and the
 *     user can reorder them; that's the primary key.
 *  2. **Curated index** — for buckets that share a position (e.g. legacy data
 *     migrated with position 0), fall back to their place in [curatedOrder] so
 *     a fresh-but-unreordered list still reads in the intended order.
 *  3. **Name** — a final stable, case-insensitive tiebreak.
 *
 * Functions never mutate inputs and never throw.
 */
object BoardOrdering {

    fun orderBuckets(buckets: List<Bucket>, curatedOrder: List<String>): List<Bucket> {
        val curated = curatedIndex(curatedOrder)
        return buckets.sortedWith(
            compareBy<Bucket> { it.position }
                .thenBy { curated[it.name] ?: Int.MAX_VALUE }
                .thenBy { it.name.lowercase() },
        )
    }

    fun orderGroups(groups: List<BoardGroup>, curatedOrder: List<String>): List<BoardGroup> {
        val curated = curatedIndex(curatedOrder)
        return groups.sortedWith(
            compareBy<BoardGroup> { it.bucket.position }
                .thenBy { curated[it.bucket.name] ?: Int.MAX_VALUE }
                .thenBy { it.bucket.name.lowercase() },
        )
    }

    private fun curatedIndex(curatedOrder: List<String>): Map<String, Int> =
        curatedOrder.withIndex().associate { (index, name) -> name to index }
}
