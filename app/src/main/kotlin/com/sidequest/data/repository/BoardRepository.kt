package com.sidequest.data.repository

import com.sidequest.data.local.dao.ActionItemDao
import com.sidequest.data.local.dao.BucketDao
import com.sidequest.data.reminder.TaskReminderScheduler
import com.sidequest.data.local.entity.toActionItems
import com.sidequest.data.local.entity.toBuckets
import com.sidequest.data.local.entity.toDomain
import com.sidequest.data.local.entity.toEntity
import com.sidequest.data.seed.DEFAULT_BUCKETS
import com.sidequest.domain.board.BoardAggregation
import com.sidequest.domain.board.BoardOrdering
import com.sidequest.domain.board.BoardState
import com.sidequest.domain.model.ActionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Outcome of changing an Action_Item's status through [BoardRepository].
 *
 * A successful change returns [Changed] with the persisted new status; a
 * request for an id that has no live (non-tombstoned) item returns [NotFound]
 * so the caller can surface the stale reference rather than silently no-op.
 */
sealed interface StatusChangeResult {

    /** The item's status was updated to [newStatus] and persisted (Req 4.6). */
    data class Changed(val itemId: String, val newStatus: ActionStatus) : StatusChangeResult

    /** No live Action_Item with the given id exists. */
    data object NotFound : StatusChangeResult
}

/**
 * Repository for the reactive Action Board and Action_Item status changes
 * (Req 4.6, 4.7, 5.2, 5.3, 5.4).
 *
 * The repository is intentionally thin: the board assembly and the
 * completion-counter derivation are the pure `:domain`
 * [BoardAggregation.buildBoard], which groups items by bucket, sorts them,
 * resolves status colors, and derives [BoardState.completionCount] from the
 * count of completed items (Properties 8–11). Here we only combine the
 * Room-backed [Flow]s and map entities to domain so the board — including the
 * completion counter and each item's indicator color — recomputes reactively
 * whenever an item's status (or any item/bucket) changes (Req 5.2, 5.3, 5.4,
 * 4.7). Writes mark the row dirty so the offline-first sync layer pushes the
 * change.
 */
@Singleton
class BoardRepository(
    private val actionItemDao: ActionItemDao,
    private val bucketDao: BucketDao,
    private val taskReminderScheduler: TaskReminderScheduler,
    private val clock: () -> Long,
) {

    /**
     * Hilt-visible constructor. Hilt can only supply the injectable DAOs, so it
     * delegates to the primary constructor with the real wall-clock generator.
     * Tests use the primary constructor to inject a deterministic [clock].
     */
    @Inject
    constructor(
        actionItemDao: ActionItemDao,
        bucketDao: BucketDao,
        taskReminderScheduler: TaskReminderScheduler,
    ) : this(
        actionItemDao = actionItemDao,
        bucketDao = bucketDao,
        taskReminderScheduler = taskReminderScheduler,
        clock = System::currentTimeMillis,
    )

    /**
     * Observes the live [BoardState] for [accountId] (Req 4.1–4.5, 5.4).
     *
     * Combines the account's live Action_Items and buckets from Room and feeds
     * them to the pure [BoardAggregation.buildBoard]. Because both inputs are
     * Room-backed [Flow]s, the board re-emits whenever an item's status changes
     * (via [changeStatus]) or items/buckets are added, edited, or removed — so
     * the indicator colors (Req 4.7) and the Completion_Counter (Req 5.2, 5.3,
     * 5.4) always reflect the current data without any manual recompute.
     */
    fun observeBoard(accountId: String): Flow<BoardState> =
        combine(
            actionItemDao.observeByAccount(accountId),
            bucketDao.observeByAccount(accountId),
        ) { itemEntities, bucketEntities ->
            val board = BoardAggregation.buildBoard(
                items = itemEntities.toActionItems(),
                buckets = bucketEntities.toBuckets(),
            )
            // Order buckets dynamically by how much they're used (content
            // volume, then recency), falling back to the curated default order
            // for a brand-new user whose buckets are all empty.
            board.copy(
                groups = BoardOrdering.orderByActivity(
                    groups = board.groups,
                    defaultOrder = DEFAULT_BUCKETS.map { it.name },
                ),
            )
        }

    /**
     * Changes the Action_Status of the item identified by [itemId] to
     * [newStatus] and persists it (Req 4.6).
     *
     * The item's sync metadata is bumped (updatedAt to the current clock,
     * version + 1, dirty) so the change propagates through the offline-first
     * sync layer. Changing the status naturally updates the item's indicator
     * color (Req 4.7) and the board's Completion_Counter (Req 5.2, 5.3, 5.4)
     * through the reactive [observeBoard] flow, which re-runs
     * [BoardAggregation.buildBoard] on the new data.
     *
     * Returns [StatusChangeResult.NotFound] when no live item with [itemId]
     * exists, or [StatusChangeResult.Changed] once the new status is persisted.
     * A no-op change (status already equal to [newStatus]) is still persisted
     * and reported as [StatusChangeResult.Changed].
     */
    suspend fun changeStatus(itemId: String, newStatus: ActionStatus): StatusChangeResult {
        val current = actionItemDao.getById(itemId)?.takeIf { !it.sync.deleted }
            ?: return StatusChangeResult.NotFound

        val updated = current.toDomain().let { item ->
            item.copy(
                status = newStatus,
                sync = item.sync.copy(
                    updatedAt = clock(),
                    version = item.sync.version + 1,
                    dirty = true,
                ),
            )
        }

        actionItemDao.upsert(updated.toEntity())
        // Cancel any pending per-task reminder once completed (Req 6.7); a
        // change away from completed re-evaluates scheduling.
        taskReminderScheduler.schedule(updated)
        return StatusChangeResult.Changed(itemId = itemId, newStatus = newStatus)
    }

    /**
     * Sets (or clears, when [reminder] is null) the per-task reminder for the
     * item [itemId] and arms/cancels its alarm accordingly (Req 6.2, 6.5–6.8).
     * Returns the updated item, or null when no live item exists.
     */
    suspend fun setReminder(
        itemId: String,
        reminder: com.sidequest.domain.model.TaskReminder?,
    ): com.sidequest.domain.model.ActionItem? {
        val current = actionItemDao.getById(itemId)?.takeIf { !it.sync.deleted } ?: return null
        val updated = current.toDomain().let { item ->
            item.copy(
                reminder = reminder,
                sync = item.sync.copy(
                    updatedAt = clock(),
                    version = item.sync.version + 1,
                    dirty = true,
                ),
            )
        }
        actionItemDao.upsert(updated.toEntity())
        taskReminderScheduler.schedule(updated)
        return updated
    }
}
