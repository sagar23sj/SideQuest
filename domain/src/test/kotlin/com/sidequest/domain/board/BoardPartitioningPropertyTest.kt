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
 * Property-based test for board partitioning (Property 8).
 *
 * For any set of Action_Items, [BoardAggregation.buildBoard]'s grouped output
 * contains every input item exactly once, and within each group every item's
 * `bucketId` equals that group's bucket id (Req 4.1).
 *
 * The generator spreads items across several distinct bucketIds and always
 * supplies a buckets list covering those ids, so every item lands in a known
 * group.
 *
 * _Requirements: 4.1_
 */
class BoardPartitioningPropertyTest : StringSpec({

    val accountId = "account-1"

    // A fixed pool of bucket ids items can be assigned to.
    val bucketIds = listOf("travel", "cooking", "stocks", "shopping", "reading")

    fun syncMeta(): SyncMeta =
        SyncMeta(updatedAt = 0L, version = 1L, deleted = false, dirty = false)

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

    // The buckets list covers every id in the pool.
    val buckets: List<Bucket> = bucketIds.map { bucket(it) }

    // An arbitrary Action_Item assigned to one of the known bucket ids, with a
    // random id, creation time, and status so partitioning is exercised across
    // varied inputs.
    val arbItem: Arb<ActionItem> = Arb.bind(
        Arb.int(0..1_000_000),
        Arb.int(bucketIds.indices),
        Arb.long(0L, Long.MAX_VALUE),
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

    // Feature: action-tracker-app, Property 8: The board partitions items by bucket without loss
    "Property 8: every input item appears exactly once and matches its group's bucket" {
        checkAll(100, Arb.list(arbItem, 0..40)) { items ->
            val state = BoardAggregation.buildBoard(items, buckets)

            // Within each group, every item's bucketId equals the group's bucket id.
            state.groups.forEach { group ->
                group.items.forEach { boardItem ->
                    boardItem.item.bucketId shouldBe group.bucket.id
                }
            }

            // Every input item appears exactly once across all groups: compare by
            // id-count multisets so duplicates or losses are caught even when the
            // generator happens to produce repeated ids.
            val inputCounts = items.groupingBy { it.id }.eachCount()
            val outputCounts = state.groups
                .flatMap { it.items }
                .groupingBy { it.item.id }
                .eachCount()
            outputCounts shouldBe inputCounts

            // Total count is preserved (no loss, no duplication).
            state.groups.sumOf { it.items.size } shouldBe items.size
        }
    }
})
