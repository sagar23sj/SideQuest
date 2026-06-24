package com.actiontracker.data.repository

import com.actiontracker.data.local.dao.ActionItemDao
import com.actiontracker.data.local.dao.BucketDao
import com.actiontracker.data.local.entity.toActionItems
import com.actiontracker.data.local.entity.toBuckets
import com.actiontracker.data.local.entity.toDomain
import com.actiontracker.data.local.entity.toEntity
import com.actiontracker.domain.bucket.BucketDeletionOutcome
import com.actiontracker.domain.bucket.BucketDeletionStrategy
import com.actiontracker.domain.bucket.BucketOperations
import com.actiontracker.domain.bucket.BucketResult
import com.actiontracker.domain.model.Bucket
import com.actiontracker.domain.model.SyncMeta
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Outcome of deleting a bucket through [BucketRepository].
 *
 * Deleting an empty bucket succeeds immediately ([Deleted]). Deleting a bucket
 * that still contains Action_Items requires the reassign-or-delete decision
 * (Req 2.5), which is implemented in a later task; this repository reports that
 * case as [NotEmpty] rather than destroying items.
 */
sealed interface BucketDeleteResult {

    /** The empty bucket was deleted (tombstoned) (Req 2.4). */
    data object Deleted : BucketDeleteResult

    /** No bucket with the given id exists for the account. */
    data object NotFound : BucketDeleteResult

    /**
     * The bucket still contains [itemCount] Action_Items, so deletion needs the
     * reassign-or-delete flow (Req 2.5). Run [BucketRepository.deleteNonEmptyBucket]
     * with the user's chosen [BucketDeletionStrategy] to complete the deletion.
     */
    data class NotEmpty(val itemCount: Int) : BucketDeleteResult
}

/**
 * Repository for bucket CRUD (Req 2.1–2.4, 2.6).
 *
 * The repository is intentionally thin: all name-uniqueness validation lives in
 * the pure `:domain` [BucketOperations] so it is portable and property-tested
 * without Android. Here we read the account's existing buckets from
 * [BucketDao], apply the domain create/rename/delete checks, and persist the
 * result via the DAO. Writes mark the row dirty so the offline-first sync layer
 * pushes the change.
 *
 * The reassign-or-delete flow for non-empty buckets (Req 2.5) is implemented in
 * a later task; [deleteBucket] only deletes empty buckets and reports a
 * non-empty bucket back to the caller.
 */
@Singleton
class BucketRepository(
    private val bucketDao: BucketDao,
    private val actionItemDao: ActionItemDao,
    private val clock: () -> Long,
    private val idGenerator: () -> String,
) {

    /**
     * Hilt-visible constructor. Hilt can only supply the injectable DAOs, so it
     * delegates to the primary constructor with the real wall-clock and UUID
     * generators. Tests use the primary constructor to inject deterministic
     * [clock]/[idGenerator] functions.
     */
    @Inject
    constructor(
        bucketDao: BucketDao,
        actionItemDao: ActionItemDao,
    ) : this(
        bucketDao = bucketDao,
        actionItemDao = actionItemDao,
        clock = System::currentTimeMillis,
        idGenerator = { UUID.randomUUID().toString() },
    )

    /**
     * Observes the live (non-tombstoned) buckets for [accountId] as a reactive
     * stream of domain [Bucket]s, ordered by name (Req 2.2). The categorization
     * sheet collects this so newly created buckets become selectable without a
     * manual refresh.
     */
    fun observeBuckets(accountId: String): Flow<List<Bucket>> =
        bucketDao.observeByAccount(accountId).map { it.toBuckets() }

    /**
     * Creates a bucket for [accountId] with the user-provided [name] (Req 2.1,
     * 2.2). The name is validated for per-account uniqueness (normalized, trim +
     * case-insensitive) before writing; a duplicate is rejected with an in-use
     * message (Req 2.6) and nothing is persisted.
     */
    suspend fun createBucket(
        accountId: String,
        name: String,
        isShopping: Boolean = false,
        notStartedColor: String,
        inProgressColor: String,
        completedColor: String,
    ): BucketResult {
        val existing = bucketDao.getByAccount(accountId).toBuckets()
        val now = clock()
        val candidate = Bucket(
            id = idGenerator(),
            accountId = accountId,
            name = name,
            isShopping = isShopping,
            notStartedColor = notStartedColor,
            inProgressColor = inProgressColor,
            completedColor = completedColor,
            sync = SyncMeta(
                updatedAt = now,
                version = 1,
                deleted = false,
                dirty = true,
            ),
        )

        val result = BucketOperations.createBucket(existing, candidate)
        if (result is BucketResult.Created) {
            bucketDao.upsert(result.bucket.toEntity())
        }
        return result
    }

    /**
     * Renames the bucket identified by [bucketId] to [newName] (Req 2.3).
     *
     * The new name is validated for per-account uniqueness, excluding the
     * bucket itself so it may keep its name or change only casing; a collision
     * with another bucket is rejected with an in-use message (Req 2.6) and no
     * change is persisted. Returns null when no live bucket with [bucketId] exists.
     */
    suspend fun renameBucket(bucketId: String, newName: String): BucketResult? {
        val current = bucketDao.getById(bucketId)?.takeIf { !it.sync.deleted } ?: return null
        val existing = bucketDao.getByAccount(current.accountId).toBuckets()
        val bucket = current.toDomain()

        val result = BucketOperations.renameBucket(existing, bucket, newName)
        if (result is BucketResult.Renamed) {
            val updated = result.bucket.copy(
                sync = result.bucket.sync.copy(
                    updatedAt = clock(),
                    version = result.bucket.sync.version + 1,
                    dirty = true,
                ),
            )
            bucketDao.upsert(updated.toEntity())
            return BucketResult.Renamed(updated)
        }
        return result
    }

    /**
     * Designates the bucket identified by [bucketId] as a shopping bucket, or
     * clears that designation, by updating its `isShopping` flag (Req 8.1).
     *
     * The change bumps the bucket's sync metadata (updatedAt, version + 1,
     * dirty) so it propagates through sync. Toggling membership of the bucket's
     * existing items into/out of wishlist items (Req 8.2) is applied by
     * [WishlistRepository] per item via the pure
     * [com.actiontracker.domain.wishlist.WishlistOperations]; this method only
     * flips the bucket flag. Returns the updated [Bucket], or null when no live
     * bucket with [bucketId] exists.
     */
    suspend fun setShopping(bucketId: String, isShopping: Boolean): Bucket? {
        val current = bucketDao.getById(bucketId)?.takeIf { !it.sync.deleted } ?: return null
        if (current.isShopping == isShopping) {
            return current.toDomain()
        }
        val updated = current.copy(
            isShopping = isShopping,
            sync = current.sync.copy(
                updatedAt = clock(),
                version = current.sync.version + 1,
                dirty = true,
            ),
        )
        bucketDao.upsert(updated)
        return updated.toDomain()
    }

    /**
     * Deletes the bucket identified by [bucketId] when it is empty (Req 2.4).
     *
     * Deletion tombstones the bucket (sets the sync deleted flag and marks it
     * dirty) so the deletion propagates through sync. A bucket that still
     * contains Action_Items is not deleted here; it returns
     * [BucketDeleteResult.NotEmpty] so the caller can run the reassign-or-delete
     * flow (Req 2.5), which is implemented in a later task.
     */
    suspend fun deleteBucket(bucketId: String): BucketDeleteResult {
        val current = bucketDao.getById(bucketId)?.takeIf { !it.sync.deleted }
            ?: return BucketDeleteResult.NotFound

        val itemCount = actionItemDao.countByBucket(bucketId)
        if (itemCount > 0) {
            return BucketDeleteResult.NotEmpty(itemCount)
        }

        val tombstoned = current.copy(
            sync = current.sync.copy(
                updatedAt = clock(),
                version = current.sync.version + 1,
                deleted = true,
                dirty = true,
            ),
        )
        bucketDao.upsert(tombstoned)
        return BucketDeleteResult.Deleted
    }

    /**
     * Deletes a non-empty bucket by first resolving its contained Action_Items
     * with the user's chosen [strategy] (Req 2.5), then tombstoning the now-empty
     * bucket. The reassign/delete computation is the pure
     * [BucketOperations.applyBucketDeletion]; this method only loads the bucket's
     * items, persists the resulting changes, and removes the bucket.
     *
     * For [BucketDeletionStrategy.Reassign] the moved items have their
     * `bucketId` updated and are upserted (dirty, version bumped) so the move
     * syncs; for [BucketDeletionStrategy.DeleteItems] the contained items are
     * tombstoned. In both cases total item accounting is preserved by the domain
     * outcome. A [BucketDeletionStrategy.Reassign] whose target is missing, the
     * same bucket, or a tombstoned bucket is rejected with
     * [BucketDeleteResult.NotFound] so items are never orphaned.
     *
     * Returns [BucketDeleteResult.NotFound] when no live bucket with [bucketId]
     * exists, or [BucketDeleteResult.Deleted] once the items are reassigned or
     * deleted and the bucket is tombstoned.
     */
    suspend fun deleteNonEmptyBucket(
        bucketId: String,
        strategy: BucketDeletionStrategy,
    ): BucketDeleteResult {
        val current = bucketDao.getById(bucketId)?.takeIf { !it.sync.deleted }
            ?: return BucketDeleteResult.NotFound

        // Guard the reassign target: it must be a live, different bucket for the
        // same account, otherwise the contained items would be orphaned.
        if (strategy is BucketDeletionStrategy.Reassign) {
            if (strategy.targetBucketId == bucketId) {
                return BucketDeleteResult.NotFound
            }
            val target = bucketDao.getById(strategy.targetBucketId)
                ?.takeIf { !it.sync.deleted && it.accountId == current.accountId }
                ?: return BucketDeleteResult.NotFound
        }

        val items = actionItemDao.getByBucket(bucketId).toActionItems()
        val outcome: BucketDeletionOutcome =
            BucketOperations.applyBucketDeletion(items, bucketId, strategy)

        val byId = items.associateBy { it.id }

        when (strategy) {
            is BucketDeletionStrategy.Reassign -> {
                val reassigned = outcome.items.filter { it.id in outcome.reassignedItemIds }
                val updatedEntities = reassigned.map { moved ->
                    moved.copy(
                        sync = moved.sync.copy(
                            updatedAt = clock(),
                            version = moved.sync.version + 1,
                            dirty = true,
                        ),
                    ).toEntity()
                }
                if (updatedEntities.isNotEmpty()) {
                    actionItemDao.upsertAll(updatedEntities)
                }
            }

            BucketDeletionStrategy.DeleteItems -> {
                val tombstonedItems = outcome.deletedItemIds.mapNotNull { id ->
                    byId[id]?.let { original ->
                        original.copy(
                            sync = original.sync.copy(
                                updatedAt = clock(),
                                version = original.sync.version + 1,
                                deleted = true,
                                dirty = true,
                            ),
                        ).toEntity()
                    }
                }
                if (tombstonedItems.isNotEmpty()) {
                    actionItemDao.upsertAll(tombstonedItems)
                }
            }
        }

        val tombstoned = current.copy(
            sync = current.sync.copy(
                updatedAt = clock(),
                version = current.sync.version + 1,
                deleted = true,
                dirty = true,
            ),
        )
        bucketDao.upsert(tombstoned)
        return BucketDeleteResult.Deleted
    }
}
