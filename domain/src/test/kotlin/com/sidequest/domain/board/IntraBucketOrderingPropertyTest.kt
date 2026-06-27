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
 * Property-based test for intra-bucket ordering (Property 9).
 *
 * For any set of Action_Items, each bucket group produced by
 * [BoardAggregation.buildBoard] is sorted in non-decreasing order of
 * `createdAt` (Req 4.2).
 *
 * The generator produces items with random creation times spread across
 * several buckets so the ordering invariant is checked within every group.
 *
 * _Requirements: 4.2_
 */
class IntraBucketOrderingPropertyTest : StringSpec({

    val accountId = "account-1"

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

    val buckets: List<Bucket> = bucketIds.map { bucket(it) }

    // Items get random createdAt values (including collisions) across buckets so
    // the non-decreasing ordering within each group is genuinely exercised.
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

    // Feature: action-tracker-app, Property 9: Items within a bucket are ordered by ascending creation time
    "Property 9: each bucket group is sorted in non-decreasing createdAt order" {
        checkAll(100, Arb.list(arbItem, 0..40)) { items ->
            val state = BoardAggregation.buildBoard(items, buckets)

            state.groups.forEach { group ->
                val createdAts = group.items.map { it.item.createdAt }
                createdAts shouldBe createdAts.sorted()
            }
        }
    }
})
