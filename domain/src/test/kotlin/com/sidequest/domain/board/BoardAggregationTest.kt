package com.sidequest.domain.board

import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.Bucket
import com.sidequest.domain.model.ContentType
import com.sidequest.domain.model.SyncMeta
import com.sidequest.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * Example/unit tests for [BoardAggregation] covering grouping, intra-bucket
 * ascending ordering, and per-status color resolution (Req 4.1, 4.2, 4.3, 4.4,
 * 4.5). The universal no-loss/membership, ordering, and color properties are
 * covered by their dedicated property tests (Properties 8, 9, 10); this spec
 * exercises concrete branches.
 *
 * _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
 */
class BoardAggregationTest : StringSpec({

    val accountId = "account-1"

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

    fun item(id: String, bucketId: String, createdAt: Long, status: ActionStatus): ActionItem =
        ActionItem(
            id = id,
            accountId = accountId,
            bucketId = bucketId,
            title = "Item $id",
            contentType = ContentType.TEXT,
            timeframe = Timeframe.Today,
            status = status,
            createdAt = createdAt,
            sync = syncMeta(),
        )

    // ---- statusColor helper (Req 4.3, 4.4, 4.5) ----

    "statusColor resolves each status to the bucket's configured color" {
        val b = bucket("travel")
        BoardAggregation.statusColor(b, ActionStatus.NOT_STARTED) shouldBe "#NS_travel"
        BoardAggregation.statusColor(b, ActionStatus.IN_PROGRESS) shouldBe "#IP_travel"
        BoardAggregation.statusColor(b, ActionStatus.COMPLETED) shouldBe "#CO_travel"
    }

    // ---- Grouping (Req 4.1) ----

    "buildBoard groups items by bucket with no loss" {
        val buckets = listOf(bucket("travel"), bucket("cooking"))
        val items = listOf(
            item("a", "travel", 1L, ActionStatus.NOT_STARTED),
            item("b", "cooking", 2L, ActionStatus.IN_PROGRESS),
            item("c", "travel", 3L, ActionStatus.COMPLETED),
        )

        val state = BoardAggregation.buildBoard(items, buckets)

        val travelGroup = state.groups.first { it.bucket.id == "travel" }
        val cookingGroup = state.groups.first { it.bucket.id == "cooking" }
        travelGroup.items.map { it.item.id } shouldContainExactly listOf("a", "c")
        cookingGroup.items.map { it.item.id } shouldContainExactly listOf("b")
        // Every item appears exactly once.
        state.groups.flatMap { it.items }.size shouldBe 3
    }

    "buildBoard includes buckets with no items as empty groups" {
        val buckets = listOf(bucket("travel"), bucket("empty"))
        val items = listOf(item("a", "travel", 1L, ActionStatus.NOT_STARTED))

        val state = BoardAggregation.buildBoard(items, buckets)

        state.groups.first { it.bucket.id == "empty" }.items shouldBe emptyList()
    }

    "buildBoard surfaces items whose bucket is unknown so none are lost" {
        val buckets = listOf(bucket("travel"))
        val items = listOf(
            item("a", "travel", 1L, ActionStatus.NOT_STARTED),
            item("orphan", "ghost", 2L, ActionStatus.NOT_STARTED),
        )

        val state = BoardAggregation.buildBoard(items, buckets)

        // Both items still appear across the groups.
        state.groups.flatMap { it.items }.map { it.item.id } shouldContainExactly
            listOf("a", "orphan")
    }

    // ---- Ordering (Req 4.2) ----

    "buildBoard sorts items ascending by createdAt within a group" {
        val buckets = listOf(bucket("travel"))
        val items = listOf(
            item("c", "travel", 30L, ActionStatus.NOT_STARTED),
            item("a", "travel", 10L, ActionStatus.NOT_STARTED),
            item("b", "travel", 20L, ActionStatus.NOT_STARTED),
        )

        val state = BoardAggregation.buildBoard(items, buckets)

        state.groups.first().items.map { it.item.id } shouldContainExactly listOf("a", "b", "c")
    }

    "buildBoard uses a stable sort for equal createdAt" {
        val buckets = listOf(bucket("travel"))
        val items = listOf(
            item("first", "travel", 5L, ActionStatus.NOT_STARTED),
            item("second", "travel", 5L, ActionStatus.NOT_STARTED),
            item("third", "travel", 5L, ActionStatus.NOT_STARTED),
        )

        val state = BoardAggregation.buildBoard(items, buckets)

        state.groups.first().items.map { it.item.id } shouldContainExactly
            listOf("first", "second", "third")
    }

    // ---- Color resolution (Req 4.3, 4.4, 4.5) ----

    "buildBoard resolves each item's status color from its bucket" {
        val buckets = listOf(bucket("travel"))
        val items = listOf(
            item("ns", "travel", 1L, ActionStatus.NOT_STARTED),
            item("ip", "travel", 2L, ActionStatus.IN_PROGRESS),
            item("co", "travel", 3L, ActionStatus.COMPLETED),
        )

        val state = BoardAggregation.buildBoard(items, buckets)
        val byId = state.groups.flatMap { it.items }.associateBy { it.item.id }

        byId.getValue("ns").statusColor shouldBe "#NS_travel"
        byId.getValue("ip").statusColor shouldBe "#IP_travel"
        byId.getValue("co").statusColor shouldBe "#CO_travel"
    }

    // ---- Completion count (Req 5.4, forward-compatible) ----

    "buildBoard counts completed items across all groups" {
        val buckets = listOf(bucket("travel"), bucket("cooking"))
        val items = listOf(
            item("a", "travel", 1L, ActionStatus.COMPLETED),
            item("b", "travel", 2L, ActionStatus.NOT_STARTED),
            item("c", "cooking", 3L, ActionStatus.COMPLETED),
            item("d", "cooking", 4L, ActionStatus.IN_PROGRESS),
        )

        val state = BoardAggregation.buildBoard(items, buckets)

        state.completionCount shouldBe 2
    }

    "buildBoard on empty inputs yields no groups and zero completion count" {
        val state = BoardAggregation.buildBoard(emptyList(), emptyList())

        state.groups shouldBe emptyList()
        state.completionCount shouldBe 0
    }
})
