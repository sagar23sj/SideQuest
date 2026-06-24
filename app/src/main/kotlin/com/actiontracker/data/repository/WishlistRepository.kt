package com.actiontracker.data.repository

import com.actiontracker.data.local.dao.ActionItemDao
import com.actiontracker.data.local.entity.toDomain
import com.actiontracker.data.local.entity.toEntity
import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.wishlist.WishlistOperations
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for shopping wishlist items (Req 8.2–8.5).
 *
 * The repository is intentionally thin: all wishlist logic — the shopping
 * membership invariant, recording product fields, and the purchased→completed
 * transition — lives in the pure `:domain` [WishlistOperations] so it stays
 * portable and is validated by the shared Correctness Properties (14, 15)
 * without any Android dependency. Here we load the item from [ActionItemDao],
 * apply a domain operation, bump the item's sync metadata, and persist. Writes
 * mark the row dirty so the offline-first sync layer pushes the change.
 */
@Singleton
class WishlistRepository(
    private val actionItemDao: ActionItemDao,
    private val clock: () -> Long,
) {

    /**
     * Hilt-visible constructor. Hilt can only supply the injectable DAO, so it
     * delegates to the primary constructor with the real wall-clock generator.
     * Tests use the primary constructor to inject a deterministic [clock].
     */
    @Inject
    constructor(actionItemDao: ActionItemDao) : this(
        actionItemDao = actionItemDao,
        clock = System::currentTimeMillis,
    )

    /**
     * Records or updates the product name and optional source link for the
     * wishlist item identified by [actionItemId] (Req 8.3), then persists it.
     *
     * Delegates to [WishlistOperations.recordWishlistFields], which is a no-op
     * for non-wishlist items; in that case the item is returned unchanged and
     * not re-persisted. Returns the updated [ActionItem], or null when no live
     * item with [actionItemId] exists.
     */
    suspend fun recordFields(
        actionItemId: String,
        productName: String,
        sourceLink: String?,
    ): ActionItem? {
        val current = actionItemDao.getById(actionItemId)?.takeIf { !it.sync.deleted }?.toDomain()
            ?: return null
        val updated = WishlistOperations.recordWishlistFields(current, productName, sourceLink)
        if (updated == current) {
            return current
        }
        return persist(updated)
    }

    /**
     * Marks the wishlist item identified by [actionItemId] as purchased
     * (Req 8.4), which also sets its status to completed (Req 8.5), then
     * persists it. Delegates to [WishlistOperations.markPurchased]. Returns the
     * updated [ActionItem], or null when no live item with [actionItemId] exists.
     */
    suspend fun markPurchased(actionItemId: String): ActionItem? {
        val current = actionItemDao.getById(actionItemId)?.takeIf { !it.sync.deleted }?.toDomain()
            ?: return null
        return persist(WishlistOperations.markPurchased(current))
    }

    /**
     * Bumps the item's sync metadata (updatedAt to the current clock, version
     * + 1, dirty) and upserts it so the change propagates through sync. Returns
     * the persisted item.
     */
    private suspend fun persist(item: ActionItem): ActionItem {
        val bumped = item.copy(
            sync = item.sync.copy(
                updatedAt = clock(),
                version = item.sync.version + 1,
                dirty = true,
            ),
        )
        actionItemDao.upsert(bumped.toEntity())
        return bumped
    }
}
