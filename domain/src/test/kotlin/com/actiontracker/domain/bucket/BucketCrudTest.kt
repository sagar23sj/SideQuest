package com.actiontracker.domain.bucket

import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.Bucket
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.SyncMeta
import com.actiontracker.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Example/unit tests for the bucket CRUD branches of [BucketOperations]
 * (Req 2.1, 2.2, 2.3, 2.4). These cover the concrete create, rename, and
 * empty-bucket delete paths with specific inputs; the universal name-uniqueness
 * rule (Property 5) and non-empty deletion accounting (Property 6) are covered
 * by their dedicated property tests, and the non-empty/reassign delete branches
 * are covered by [BucketDeletionTest]. This spec focuses on the create/rename/
 * empty-delete CRUD branches per 2.1–2.4.
 *
 * _Requirements: 2.1, 2.2, 2.3, 2.4_
 */
class BucketCrudTest : StringSpec({

    val accountId = "account-1"

    fun syncMeta(deleted: Boolean = false): SyncMeta =
        SyncMeta(updatedAt = 0L, version = 1L, deleted = deleted, dirty = false)

    fun bucket(id: String, name: String, deleted: Boolean = false): Bucket =
        Bucket(
            id = id,
            accountId = accountId,
            name = name,
            isShopping = false,
            notStartedColor = "#000000",
            inProgressColor = "#777777",
            completedColor = "#00FF00",
            sync = syncMeta(deleted = deleted),
        )

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
            isWishlistItem = false,
            sync = syncMeta(),
        )

    // ---- Create (Req 2.1, 2.2) ----

    "create: a bucket with a fresh name is Created" {
        val existing = listOf(bucket("bucket-travel", "Travel"))
        val candidate = bucket("bucket-cooking", "Cooking")

        val result = BucketOperations.createBucket(existing, candidate)

        result.shouldBeInstanceOf<BucketResult.Created>()
        result.bucket.name shouldBe "Cooking"
        result.bucket.id shouldBe "bucket-cooking"
    }

    "create: the created bucket's name is trimmed" {
        val candidate = bucket("bucket-stocks", "  Stocks  ")

        val result = BucketOperations.createBucket(emptyList(), candidate)

        result.shouldBeInstanceOf<BucketResult.Created>()
        result.bucket.name shouldBe "Stocks"
    }

    "create: a created bucket's name is no longer available for selection" {
        val candidate = bucket("bucket-shopping", "Shopping")

        val result = BucketOperations.createBucket(emptyList(), candidate)

        result.shouldBeInstanceOf<BucketResult.Created>()
        // Once created and stored, the same name is taken (Req 2.2).
        val afterCreate = listOf(result.bucket)
        BucketOperations.isNameAvailable(afterCreate, accountId, "Shopping") shouldBe false
        // Normalization: differing case/whitespace is also unavailable.
        BucketOperations.isNameAvailable(afterCreate, accountId, "  shopping ") shouldBe false
    }

    "create: a colliding name is rejected as DuplicateName and leaves buckets unchanged" {
        val existing = listOf(bucket("bucket-travel", "Travel"))
        val candidate = bucket("bucket-dup", " travel ")

        val result = BucketOperations.createBucket(existing, candidate)

        result.shouldBeInstanceOf<BucketResult.DuplicateName>()
        // Existing set is unchanged (single Travel bucket still present).
        existing.map { it.id } shouldContainExactly listOf("bucket-travel")
    }

    // ---- Rename (Req 2.3) ----

    "rename: changing to a fresh name returns Renamed with the trimmed name" {
        val travel = bucket("bucket-travel", "Travel")
        val existing = listOf(travel, bucket("bucket-cooking", "Cooking"))

        val result = BucketOperations.renameBucket(existing, travel, "  Trips  ")

        result.shouldBeInstanceOf<BucketResult.Renamed>()
        result.bucket.id shouldBe "bucket-travel"
        result.bucket.name shouldBe "Trips"
    }

    "rename: renaming a bucket to its own name is allowed (self is excluded)" {
        val travel = bucket("bucket-travel", "Travel")
        val existing = listOf(travel)

        val result = BucketOperations.renameBucket(existing, travel, "Travel")

        result.shouldBeInstanceOf<BucketResult.Renamed>()
        result.bucket.name shouldBe "Travel"
    }

    "rename: changing only the casing of a bucket's own name is allowed" {
        val travel = bucket("bucket-travel", "Travel")
        val existing = listOf(travel)

        val result = BucketOperations.renameBucket(existing, travel, "TRAVEL")

        result.shouldBeInstanceOf<BucketResult.Renamed>()
        result.bucket.name shouldBe "TRAVEL"
    }

    "rename: colliding with another bucket's name is rejected as DuplicateName" {
        val travel = bucket("bucket-travel", "Travel")
        val cooking = bucket("bucket-cooking", "Cooking")
        val existing = listOf(travel, cooking)

        val result = BucketOperations.renameBucket(existing, travel, " cooking ")

        result.shouldBeInstanceOf<BucketResult.DuplicateName>()
    }

    // ---- Delete empty bucket (Req 2.4) ----

    "delete empty bucket: deleting items of an empty bucket yields no item changes" {
        val items = listOf(
            item("a", "bucket-other"),
            item("b", "bucket-other"),
        )

        val outcome = BucketOperations.applyBucketDeletion(
            items = items,
            bucketId = "bucket-empty",
            strategy = BucketDeletionStrategy.DeleteItems,
        )

        // Items unchanged; nothing deleted or reassigned (Req 2.4).
        outcome.items shouldContainExactly items
        outcome.deletedItemIds shouldBe emptyList()
        outcome.reassignedItemIds shouldBe emptyList()
    }

    "delete empty bucket: with no items at all the outcome is empty and unchanged" {
        val outcome = BucketOperations.applyBucketDeletion(
            items = emptyList(),
            bucketId = "bucket-empty",
            strategy = BucketDeletionStrategy.DeleteItems,
        )

        outcome.items shouldBe emptyList()
        outcome.deletedItemIds shouldBe emptyList()
        outcome.reassignedItemIds shouldBe emptyList()
    }
})
