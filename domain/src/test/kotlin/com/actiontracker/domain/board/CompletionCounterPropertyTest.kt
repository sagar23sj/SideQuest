package com.actiontracker.domain.board

import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.Bucket
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.SyncMeta
import com.actiontracker.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * Property-based test for the completion counter (Property 11).
 *
 * For any set of Action_Items and any sequence of status changes applied to
 * them, the Completion_Counter equals the count of items whose status is
 * "completed" (Req 5.2, 5.3, 5.4).
 *
 * The test generates a set of items with random statuses and a sequence of
 * status changes (each change picks an existing item id and a new
 * [ActionStatus]). The changes are applied in order; after *each* applied change
 * the counter is recomputed and compared against the actual count of completed
 * items, which exercises increment-on-completing (5.2) and
 * decrement-on-un-completing (5.3) across an arbitrary change sequence. The
 * final set is then checked against both [BoardAggregation.completionCount] and
 * [BoardAggregation.buildBoard] (Req 5.4).
 *
 * _Requirements: 5.2, 5.3, 5.4_
 */
class CompletionCounterPropertyTest : StringSpec({

    val accountId = "account-1"

    val bucketIds = listOf("travel", "cooking", "stocks", "shopping", "reading")

    fun syncMeta(): SyncMeta =
        SyncMeta(updatedAt = 0L, version = 1L, deleted = false, dirty = false)

    fun bucket(id: String): Bucket =
        Bucket(
            id = id,
            accountId = accountId,
            name = id,
            isShopping = false,
            notStartedColor = "#NS_$id",
            inProgressColor = "#IP_$id",
            completedColor = "#CO_$id",
            sync = syncMeta(),
        )

    val buckets: List<Bucket> = bucketIds.map { bucket(it) }

    // Items have unique ids ("item-0", "item-1", ...) assigned positionally when
    // building the set, so status changes can target an item unambiguously by id.
    val arbItem: Arb<ActionItem> = Arb.bind(
        Arb.int(bucketIds.indices),
        Arb.long(0L, 1_000L),
        Arb.enum<ActionStatus>(),
    ) { bucketIndex, createdAt, status ->
        ActionItem(
            id = "placeholder", // replaced with a unique id when the set is built
            accountId = accountId,
            bucketId = bucketIds[bucketIndex],
            title = "Item",
            contentType = ContentType.TEXT,
            timeframe = Timeframe.Today,
            status = status,
            createdAt = createdAt,
            isWishlistItem = false,
            sync = syncMeta(),
        )
    }

    // A non-empty set of items with unique ids.
    val arbItems: Arb<List<ActionItem>> =
        Arb.list(arbItem, 1..30).map { raw ->
            raw.mapIndexed { index, item -> item.copy(id = "item-$index") }
        }

    /** A status change: an index into the item set plus the new status to apply. */
    data class StatusChange(val itemIndex: Int, val newStatus: ActionStatus)

    fun actualCompleted(items: List<ActionItem>): Int =
        items.count { it.status == ActionStatus.COMPLETED }

    // Feature: action-tracker-app, Property 11: The completion counter equals the number of completed items
    "Property 11: the completion counter equals the number of completed items after any sequence of status changes" {
        checkAll(
            100,
            arbItems,
            Arb.list(Arb.bind(Arb.int(0..1_000), Arb.enum<ActionStatus>()) { idxSeed, status ->
                StatusChange(idxSeed, status)
            }, 0..40),
        ) { items, rawChanges ->
            // Initial state: the counter equals the count of completed items.
            BoardAggregation.completionCount(items) shouldBe actualCompleted(items)

            // Map each raw change onto an actual item id (modulo set size) and
            // apply the changes in order, recomputing the counter after each step.
            var current = items
            rawChanges.forEach { change ->
                val targetIndex = change.itemIndex % current.size
                val targetId = current[targetIndex].id
                current = current.map { item ->
                    if (item.id == targetId) item.copy(status = change.newStatus) else item
                }

                // After each applied change the counter must equal the running
                // count of completed items (covers increment on completing and
                // decrement on un-completing, Req 5.2/5.3).
                BoardAggregation.completionCount(current) shouldBe actualCompleted(current)
            }

            // Final set: standalone counter and the board's completionCount agree
            // with the true completed count (Req 5.4).
            val expected = actualCompleted(current)
            BoardAggregation.completionCount(current) shouldBe expected
            BoardAggregation.buildBoard(current, buckets).completionCount shouldBe expected
        }
    }
})
