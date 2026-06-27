package com.sidequest.domain.board

import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.Bucket
import com.sidequest.domain.model.ContentType
import com.sidequest.domain.model.SyncMeta
import com.sidequest.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * Property-based test for the status indicator color (Property 10).
 *
 * For any Action_Item, the displayed indicator color equals the bucket's
 * configured color for the item's current status — including immediately after
 * a status change (Req 4.3, 4.4, 4.5, 4.7).
 *
 * The first property checks that every [BoardItem.statusColor] resolved by
 * [BoardAggregation.buildBoard] matches `statusColor(group.bucket, item.status)`.
 * The second property simulates a status change: for each generated item it
 * rewrites the status to every [ActionStatus], rebuilds the board, and asserts
 * the resolved color matches the new status's configured color, covering the
 * "immediately after a status change" clause (Req 4.7).
 *
 * Buckets are generated with distinct per-status colors so a wrong mapping
 * (e.g. returning the in-progress color for a completed item) is detectable.
 *
 * _Requirements: 4.3, 4.4, 4.5, 4.7_
 */
class StatusIndicatorColorPropertyTest : StringSpec({

    val accountId = "account-1"

    val bucketIds = listOf("travel", "cooking", "stocks", "shopping", "reading")

    fun syncMeta(): SyncMeta =
        SyncMeta(updatedAt = 0L, version = 1L, deleted = false, dirty = false)

    // Distinct colors per bucket and per status so every (bucket, status) pair
    // maps to a unique color string.
    fun bucket(id: String): Bucket =
        Bucket(
            id = id,
            accountId = accountId,
            name = id,
            notStartedColor = "#NS_$id",
            inProgressColor = "#IP_$id",
            completedColor = "#CO_$id",
            sync = syncMeta(),
        )

    val buckets: List<Bucket> = bucketIds.map { bucket(it) }

    val arbItem: Arb<ActionItem> = Arb.bind(
        Arb.int(0..1_000_000),
        Arb.int(bucketIds.indices),
        Arb.long(0L, 1_000L),
        Arb.enum<ActionStatus>(),
    ) { idSeed, bucketIndex, createdAt, status ->
        ActionItem(
            id = "item-$idSeed",
            accountId = accountId,
            bucketId = bucketIds[bucketIndex],
            title = "Item $idSeed",
            contentType = ContentType.TEXT,
            timeframe = Timeframe.Today,
            status = status,
            createdAt = createdAt,
            sync = syncMeta(),
        )
    }

    // Feature: action-tracker-app, Property 10: The status indicator color always matches the item's current status
    "Property 10: each board item's color equals its bucket's color for its current status" {
        checkAll(100, Arb.list(arbItem, 0..40)) { items ->
            val state = BoardAggregation.buildBoard(items, buckets)

            state.groups.forEach { group ->
                group.items.forEach { boardItem ->
                    boardItem.statusColor shouldBe
                        BoardAggregation.statusColor(group.bucket, boardItem.item.status)
                }
            }
        }
    }

    // Feature: action-tracker-app, Property 10: The status indicator color always matches the item's current status
    "Property 10: color tracks the new status immediately after a status change" {
        checkAll(100, Arb.list(arbItem, 1..40)) { items ->
            // For every status an item could transition to, rewrite the whole set
            // to that status, rebuild, and assert each board item's color matches
            // the new status's configured color for its bucket (Req 4.7).
            ActionStatus.entries.forEach { newStatus ->
                val changed = items.map { it.copy(status = newStatus) }
                val state = BoardAggregation.buildBoard(changed, buckets)

                state.groups.forEach { group ->
                    group.items.forEach { boardItem ->
                        boardItem.item.status shouldBe newStatus
                        boardItem.statusColor shouldBe
                            BoardAggregation.statusColor(group.bucket, newStatus)
                    }
                }
            }

            // Also exercise changing a single item's status (the others kept as
            // generated) so per-item transitions are covered, not just bulk ones.
            // Target the first item whose id is unique in the set so the lookup
            // below is unambiguous even if the generator produced a repeated id.
            val target = items.firstOrNull { candidate ->
                items.count { it.id == candidate.id } == 1
            } ?: return@checkAll
            ActionStatus.entries.forEach { newStatus ->
                val changed = items.map { item ->
                    if (item.id == target.id) item.copy(status = newStatus) else item
                }
                val state = BoardAggregation.buildBoard(changed, buckets)
                val resolved = state.groups
                    .flatMap { group -> group.items.map { group.bucket to it } }
                    .first { (_, boardItem) -> boardItem.item.id == target.id }

                val (groupBucket, boardItem) = resolved
                boardItem.statusColor shouldBe
                    BoardAggregation.statusColor(groupBucket, newStatus)
            }
        }
    }
})
