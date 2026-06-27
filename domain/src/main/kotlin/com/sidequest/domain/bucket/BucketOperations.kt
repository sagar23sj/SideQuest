package com.sidequest.domain.bucket

import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.Bucket

/**
 * Pure bucket CRUD validation logic, primarily the per-account name-uniqueness
 * rule (Req 2.6). Lives in `:domain` so it is portable and validated with the
 * shared Correctness Properties without any Android/Room dependency; the app's
 * `BucketRepository` reads existing buckets, applies these checks, and writes.
 *
 * Names are compared after normalization (trim + case-insensitive) so that
 * "Travel", "travel ", and " TRAVEL" are treated as the same name. The stored
 * name keeps the user's casing (trimmed) while comparison is normalized.
 */
object BucketOperations {

    /**
     * Builds the message shown when a candidate bucket name is already in use
     * for the account (Req 2.6). The trimmed [name] is echoed back to the user.
     */
    fun nameInUseMessage(name: String): String =
        "A bucket named \"${name.trim()}\" already exists."

    /**
     * Normalizes a bucket name for uniqueness comparison: trims surrounding
     * whitespace and lowercases so comparison is case-insensitive. Lowercasing
     * uses the root locale to keep the rule deterministic across devices.
     */
    fun normalizeName(name: String): String = name.trim().lowercase()

    /**
     * Returns true when [name] is available for [accountId] — that is, no
     * non-deleted bucket belonging to that account has the same normalized
     * name. [excludingBucketId] lets a rename ignore the bucket being renamed
     * so keeping its own name (or only changing case) is not a self-collision.
     *
     * Tombstoned buckets ([Bucket.sync] deleted) do not reserve their name.
     */
    fun isNameAvailable(
        existing: List<Bucket>,
        accountId: String,
        name: String,
        excludingBucketId: String? = null,
    ): Boolean {
        val normalized = normalizeName(name)
        return existing.none { bucket ->
            bucket.accountId == accountId &&
                !bucket.sync.deleted &&
                bucket.id != excludingBucketId &&
                normalizeName(bucket.name) == normalized
        }
    }

    /**
     * Validates creating [candidate] against [existing] (Req 2.1, 2.2, 2.6).
     *
     * Returns [BucketResult.Created] with the candidate's name trimmed when the
     * normalized name is unique for the candidate's account, otherwise
     * [BucketResult.DuplicateName] and the bucket set is left unchanged.
     */
    fun createBucket(existing: List<Bucket>, candidate: Bucket): BucketResult {
        val trimmedName = candidate.name.trim()
        return if (isNameAvailable(existing, candidate.accountId, trimmedName)) {
            BucketResult.Created(candidate.copy(name = trimmedName))
        } else {
            BucketResult.DuplicateName(nameInUseMessage(trimmedName))
        }
    }

    /**
     * Validates renaming [bucket] to [newName] against [existing] (Req 2.3, 2.6).
     *
     * The bucket being renamed is excluded from the uniqueness check so it may
     * keep its own name or change only its casing. Returns
     * [BucketResult.Renamed] with the trimmed new name when available, otherwise
     * [BucketResult.DuplicateName].
     */
    fun renameBucket(existing: List<Bucket>, bucket: Bucket, newName: String): BucketResult {
        val trimmedName = newName.trim()
        return if (
            isNameAvailable(
                existing = existing,
                accountId = bucket.accountId,
                name = trimmedName,
                excludingBucketId = bucket.id,
            )
        ) {
            BucketResult.Renamed(bucket.copy(name = trimmedName))
        } else {
            BucketResult.DuplicateName(nameInUseMessage(trimmedName))
        }
    }

    /**
     * Computes the result of deleting the bucket [bucketId] that still contains
     * Action_Items, given the [strategy] chosen by the user (Req 2.5). Pure and
     * total: it does not mutate [items] and never throws; the caller persists
     * the returned changes and removes the now-empty bucket.
     *
     * Total item accounting is preserved (Property 6):
     * - [BucketDeletionStrategy.Reassign]: every item whose `bucketId` equals
     *   [bucketId] is moved to the target bucket (its `bucketId` updated), so the
     *   returned [BucketDeletionOutcome.items] has the same size as [items], no
     *   item is lost, and no item remains in the deleted bucket. Moving to the
     *   same bucket would be a no-op, so when the target equals [bucketId] the
     *   items are left untouched and reported as reassigned for accounting.
     * - [BucketDeletionStrategy.DeleteItems]: exactly the items in [bucketId]
     *   are removed, so the count decreases by precisely the bucket's item count.
     *
     * Items not in [bucketId] are always returned unchanged and in their
     * original order.
     */
    fun applyBucketDeletion(
        items: List<ActionItem>,
        bucketId: String,
        strategy: BucketDeletionStrategy,
    ): BucketDeletionOutcome {
        val containedIds = items.filter { it.bucketId == bucketId }.map { it.id }
        return when (strategy) {
            is BucketDeletionStrategy.Reassign -> {
                val updated = items.map { item ->
                    if (item.bucketId == bucketId) {
                        item.copy(bucketId = strategy.targetBucketId)
                    } else {
                        item
                    }
                }
                BucketDeletionOutcome(
                    items = updated,
                    reassignedItemIds = containedIds,
                    deletedItemIds = emptyList(),
                )
            }

            BucketDeletionStrategy.DeleteItems -> {
                val remaining = items.filter { it.bucketId != bucketId }
                BucketDeletionOutcome(
                    items = remaining,
                    reassignedItemIds = emptyList(),
                    deletedItemIds = containedIds,
                )
            }
        }
    }
}
