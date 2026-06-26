package com.actiontracker.domain.bucket

import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.SyncMeta
import com.actiontracker.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * Property-based test for deleting a non-empty bucket (Req 2.5).
 *
 * For any bucket containing items, choosing "reassign" moves every contained
 * item to the target bucket (none lost, source emptied) and choosing "delete"
 * removes exactly those items; total item accounting is preserved in both
 * cases. Items not in the source bucket are always returned unchanged and in
 * their original order.
 *
 * The generator spreads Action_Items across several distinct bucketIds, always
 * guaranteeing the chosen source bucket contains at least one item and that the
 * reassign target is distinct from the source.
 *
 * _Requirements: 2.5_
 */
class NonEmptyBucketDeletionPropertyTest : StringSpec({

    val accountId = "account-1"
    val sourceBucket = "bucket-source"
    val targetBucket = "bucket-target"

    // Buckets, other than the source, that items may live in. The reassign
    // target is intentionally included so reassigning into an already-populated
    // bucket is exercised too.
    val otherBuckets = listOf(targetBucket, "bucket-x", "bucket-y", "bucket-z")

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

    // Each generated int picks a bucket for one item: 0 => source bucket,
    // otherwise one of the non-source buckets. Items get stable, unique ids by
    // their index so order can be asserted precisely.
    fun buildItems(assignments: List<Int>): List<ActionItem> =
        assignments.mapIndexed { index, choice ->
            val bucketId = if (choice == 0) {
                sourceBucket
            } else {
                otherBuckets[(choice - 1) % otherBuckets.size]
            }
            item(id = "item-$index", bucketId = bucketId)
        }

    // A non-empty list of bucket assignments that always contains at least one
    // source-bucket item (the first slot is forced to the source bucket), so the
    // bucket being deleted is genuinely non-empty.
    val itemsArb: Arb<List<ActionItem>> =
        Arb.list(Arb.int(0..otherBuckets.size), 0..15).map { rest ->
            buildItems(listOf(0) + rest)
        }

    // Feature: action-tracker-app, Property 6: Deleting a non-empty bucket reassigns or deletes all of its items
    "Property 6: reassign moves every contained item to the target, losing none and emptying the source" {
        checkAll(100, itemsArb) { items ->
            val sourceIds = items.filter { it.bucketId == sourceBucket }.map { it.id }
            sourceIds.isEmpty() shouldBe false

            val outcome = BucketOperations.applyBucketDeletion(
                items = items,
                bucketId = sourceBucket,
                strategy = BucketDeletionStrategy.Reassign(targetBucket),
            )

            // No item is lost: total accounting preserved.
            outcome.items.size shouldBe items.size
            // The source bucket is empty afterwards.
            outcome.items.none { it.bucketId == sourceBucket } shouldBe true
            // Every item originally in the source now lives in the target.
            outcome.items.filter { it.id in sourceIds }
                .all { it.bucketId == targetBucket } shouldBe true
            // Items not in the source are unchanged (same bucket) and in order.
            val expectedUntouched = items.filter { it.bucketId != sourceBucket }
            outcome.items.filter { it.id !in sourceIds } shouldContainExactly expectedUntouched
            // The reassigned ids are exactly the source's item ids, in order.
            outcome.reassignedItemIds shouldContainExactly sourceIds
            outcome.deletedItemIds shouldBe emptyList()
        }
    }

    // Feature: action-tracker-app, Property 6: Deleting a non-empty bucket reassigns or deletes all of its items
    "Property 6: delete removes exactly the contained items, preserving the rest" {
        checkAll(100, itemsArb) { items ->
            val sourceIds = items.filter { it.bucketId == sourceBucket }.map { it.id }
            sourceIds.isEmpty() shouldBe false

            val outcome = BucketOperations.applyBucketDeletion(
                items = items,
                bucketId = sourceBucket,
                strategy = BucketDeletionStrategy.DeleteItems,
            )

            // Count drops by exactly the number of items in the source bucket.
            outcome.items.size shouldBe items.size - sourceIds.size
            // None of the source items remain.
            outcome.items.none { it.id in sourceIds } shouldBe true
            // Items not in the source are unchanged and in their original order.
            val expectedUntouched = items.filter { it.bucketId != sourceBucket }
            outcome.items shouldContainExactly expectedUntouched
            // The deleted ids are exactly the source's item ids, in order.
            outcome.deletedItemIds shouldContainExactly sourceIds
            outcome.reassignedItemIds shouldBe emptyList()
        }
    }
})
