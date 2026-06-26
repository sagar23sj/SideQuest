package com.actiontracker.domain.bucket

import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.SyncMeta
import com.actiontracker.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Example/unit tests for the reassign-or-delete flow used when deleting a
 * non-empty bucket (Req 2.5). These cover the concrete branches of
 * [BucketOperations.applyBucketDeletion]; the universal accounting property is
 * covered by the property test in task 5.4 (Property 6).
 */
class BucketDeletionTest : StringSpec({

    val accountId = "account-1"

    fun item(id: String, bucketId: String): ActionItem =
        ActionItem(
            id = id,
            accountId = accountId,
            bucketId = bucketId,
            title = "Item $id",
            contentType = ContentType.TEXT,
            timeframe = Timeframe.Today,
            status = ActionStatus.NOT_STARTED,
            createdAt = 0L,
            sync = SyncMeta(updatedAt = 0L, version = 1L, deleted = false, dirty = false),
        )

    val items = listOf(
        item("a", "bucket-source"),
        item("b", "bucket-other"),
        item("c", "bucket-source"),
        item("d", "bucket-other"),
    )

    "reassign moves all contained items to the target bucket and empties the source" {
        val outcome = BucketOperations.applyBucketDeletion(
            items = items,
            bucketId = "bucket-source",
            strategy = BucketDeletionStrategy.Reassign("bucket-target"),
        )

        // No item lost: same total count preserved.
        outcome.items.size shouldBe items.size
        // Source bucket is empty afterwards.
        outcome.items.none { it.bucketId == "bucket-source" } shouldBe true
        // The previously contained items now live in the target bucket.
        outcome.items.filter { it.id in listOf("a", "c") }
            .all { it.bucketId == "bucket-target" } shouldBe true
        // Untouched items keep their bucket and order.
        outcome.items.map { it.id } shouldContainExactly listOf("a", "b", "c", "d")
        outcome.reassignedItemIds shouldContainExactlyInAnyOrder listOf("a", "c")
        outcome.deletedItemIds shouldBe emptyList()
    }

    "delete removes exactly the contained items and decreases the count by that amount" {
        val outcome = BucketOperations.applyBucketDeletion(
            items = items,
            bucketId = "bucket-source",
            strategy = BucketDeletionStrategy.DeleteItems,
        )

        // Count decreases by exactly the bucket's item count (2).
        outcome.items.size shouldBe items.size - 2
        outcome.items.map { it.id } shouldContainExactly listOf("b", "d")
        outcome.deletedItemIds shouldContainExactlyInAnyOrder listOf("a", "c")
        outcome.reassignedItemIds shouldBe emptyList()
    }

    "deleting an empty bucket leaves all items unchanged" {
        val outcome = BucketOperations.applyBucketDeletion(
            items = items,
            bucketId = "bucket-empty",
            strategy = BucketDeletionStrategy.DeleteItems,
        )

        outcome.items shouldContainExactly items
        outcome.deletedItemIds shouldBe emptyList()
        outcome.reassignedItemIds shouldBe emptyList()
    }

    "reassigning a bucket with no items is a no-op" {
        val outcome = BucketOperations.applyBucketDeletion(
            items = items,
            bucketId = "bucket-empty",
            strategy = BucketDeletionStrategy.Reassign("bucket-target"),
        )

        outcome.items shouldContainExactly items
        outcome.reassignedItemIds shouldBe emptyList()
        outcome.deletedItemIds shouldBe emptyList()
    }
})
