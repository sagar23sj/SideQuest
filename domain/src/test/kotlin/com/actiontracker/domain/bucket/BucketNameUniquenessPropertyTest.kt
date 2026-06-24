package com.actiontracker.domain.bucket

import com.actiontracker.domain.model.Bucket
import com.actiontracker.domain.model.SyncMeta
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll

/**
 * Property-based test for per-account bucket name uniqueness.
 *
 * For any set of existing buckets, attempting to create a bucket whose
 * normalized (trimmed + case-insensitive) name already exists for the same
 * account is rejected with [BucketResult.DuplicateName], and the set of buckets
 * is left unchanged (no [BucketResult.Created] is produced). To make the iff
 * meaningful the test also asserts the positive direction: a genuinely new
 * normalized name is accepted with [BucketResult.Created]. Case and surrounding
 * whitespace are varied to exercise normalization.
 *
 * _Requirements: 2.6_
 */
class BucketNameUniquenessPropertyTest : StringSpec({

    val accountId = "account-1"

    fun syncMeta(): SyncMeta =
        SyncMeta(updatedAt = 0L, version = 1L, deleted = false, dirty = false)

    fun bucket(id: String, name: String): Bucket =
        Bucket(
            id = id,
            accountId = accountId,
            name = name,
            isShopping = false,
            notStartedColor = "#000000",
            inProgressColor = "#777777",
            completedColor = "#00FF00",
            sync = syncMeta(),
        )

    // Non-blank base names (letters/digits) used for bucket names.
    val baseName: Arb<String> = Arb.stringPattern("[a-zA-Z0-9]{1,12}")

    // A set of existing buckets with distinct normalized names, each given a
    // unique id. Names are kept distinct so the existing set is itself valid.
    val existingBuckets: Arb<List<Bucket>> =
        Arb.list(baseName, 1..8).map { names ->
            names
                .distinctBy { it.lowercase() }
                .mapIndexed { index, name -> bucket(id = "bucket-$index", name = name) }
        }

    // Vary case and surrounding whitespace to exercise normalization.
    fun varyCaseAndWhitespace(name: String, upper: Boolean, leftPad: Boolean, rightPad: Boolean): String {
        val cased = if (upper) name.uppercase() else name.lowercase()
        val left = if (leftPad) "  " else ""
        val right = if (rightPad) "  " else ""
        return "$left$cased$right"
    }

    // Feature: action-tracker-app, Property 5: Bucket names are unique per account
    "Property 5: creating a bucket with an existing normalized name is rejected and leaves buckets unchanged" {
        checkAll(
            100,
            existingBuckets,
            Arb.long(0L, 1_000_000L),
            Arb.boolean(),
            Arb.boolean(),
            Arb.boolean(),
        ) { buckets, pickSeed, upper, leftPad, rightPad ->
            // Pick an existing bucket and build a candidate whose name collides
            // after normalization, varying case and whitespace.
            val target = buckets[(pickSeed % buckets.size).toInt()]
            val collidingName = varyCaseAndWhitespace(target.name, upper, leftPad, rightPad)
            val candidate = bucket(id = "candidate-new", name = collidingName)

            val result = BucketOperations.createBucket(buckets, candidate)

            // Rejected as a duplicate; no Created bucket is produced, so the
            // existing set of buckets is unchanged, and the name is unavailable.
            result.shouldBeInstanceOf<BucketResult.DuplicateName>()
            BucketOperations.isNameAvailable(buckets, accountId, collidingName) shouldBe false
        }
    }

    // Feature: action-tracker-app, Property 5: Bucket names are unique per account
    "Property 5: creating a bucket with a genuinely new normalized name is accepted" {
        checkAll(100, existingBuckets, baseName) { buckets, newName ->
            // Only exercise the positive direction when the name is genuinely
            // new for the account (its normalized form is not already taken).
            val normalized = newName.trim().lowercase()
            val isNew = buckets.none { it.name.trim().lowercase() == normalized }

            if (isNew) {
                val candidate = bucket(id = "candidate-new", name = newName)
                val result = BucketOperations.createBucket(buckets, candidate)

                result.shouldBeInstanceOf<BucketResult.Created>()
                result.bucket.name shouldBe newName.trim()
                BucketOperations.isNameAvailable(buckets, accountId, newName) shouldBe true
            }
        }
    }
})
