package com.sidequest.data.repository

import com.sidequest.data.local.dao.ActionPlanDao
import com.sidequest.data.local.entity.toDomain
import com.sidequest.data.local.entity.toEntity
import com.sidequest.domain.model.ActionPlan
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.SyncMeta
import com.sidequest.domain.plan.ActionPlanOperations
import com.sidequest.domain.plan.Progress
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for an Action_Item's Action_Plan (Req 9.1–9.5).
 *
 * The repository is intentionally thin: all sub-action logic — appending,
 * marking complete, progress counting, the parent-complete prompt, and
 * reordering — lives in the pure `:domain` [ActionPlanOperations] so it stays
 * portable and is validated by the shared Correctness Properties (16, 17, 18)
 * without any Android dependency. Here we load (or lazily create) the plan for
 * a given Action_Item from [ActionPlanDao], apply a domain operation, bump the
 * plan's sync metadata, and persist. Writes mark the row dirty so the
 * offline-first sync layer pushes the change.
 *
 * A plan is created lazily on the first [addSubAction] for an item that has no
 * plan yet, so an Action_Item carries a plan row only once the user starts
 * breaking it into steps.
 */
@Singleton
class ActionPlanRepository(
    private val actionPlanDao: ActionPlanDao,
    private val boardRepository: BoardRepository,
    private val clock: () -> Long,
    private val idGenerator: () -> String,
) {

    /**
     * Hilt-visible constructor. Hilt can only supply the injectable DAO and the
     * [BoardRepository], so it delegates to the primary constructor with the
     * real wall-clock and UUID generators. Tests use the primary constructor to
     * inject deterministic [clock]/[idGenerator] functions.
     */
    @Inject
    constructor(
        actionPlanDao: ActionPlanDao,
        boardRepository: BoardRepository,
    ) : this(
        actionPlanDao = actionPlanDao,
        boardRepository = boardRepository,
        clock = System::currentTimeMillis,
        idGenerator = { UUID.randomUUID().toString() },
    )

    /**
     * Observes the live Action_Plan for [actionItemId] as a reactive stream of
     * the domain [ActionPlan], or null when the item has no plan yet. Because
     * the source is a Room-backed [Flow], the detail screen re-renders whenever
     * a sub-action is added, toggled, or reordered.
     */
    fun observePlan(actionItemId: String): Flow<ActionPlan?> =
        actionPlanDao.observeByActionItem(actionItemId).map { it?.toDomain() }

    /**
     * Appends a new sub-action with [text] to [actionItemId]'s plan (Req 9.1).
     *
     * When the item has no plan yet a fresh [ActionPlan] is created with a
     * generated id, the [actionItemId], an empty sub-action list, and fresh
     * dirty [SyncMeta]; the new step is then appended via
     * [ActionPlanOperations.addSubAction]. The plan's sync metadata is bumped
     * and the plan is upserted. Returns the persisted plan.
     */
    suspend fun addSubAction(actionItemId: String, text: String): ActionPlan {
        val current = loadOrCreate(actionItemId)
        val updated = ActionPlanOperations.addSubAction(
            plan = current,
            text = text,
            idGenerator = idGenerator,
        )
        return persist(updated)
    }

    /**
     * Sets the [completed] flag of the sub-action [subActionId] within
     * [actionItemId]'s plan (Req 9.2) and persists the change. Returns the
     * persisted plan, or null when the item has no plan to update.
     */
    suspend fun setSubActionCompleted(
        actionItemId: String,
        subActionId: String,
        completed: Boolean,
    ): ActionPlan? {
        val current = actionPlanDao.getByActionItem(actionItemId)?.toDomain() ?: return null
        val updated = ActionPlanOperations.markSubAction(
            plan = current,
            subActionId = subActionId,
            completed = completed,
        )
        return persist(updated)
    }

    /**
     * Reorders [actionItemId]'s sub-actions to follow [orderedIds] (Req 9.5) and
     * persists the change. The reorder is a permutation with contiguous ordering
     * computed by [ActionPlanOperations.reorder]. Returns the persisted plan, or
     * null when the item has no plan to reorder.
     */
    suspend fun reorderSubActions(
        actionItemId: String,
        orderedIds: List<String>,
    ): ActionPlan? {
        val current = actionPlanDao.getByActionItem(actionItemId)?.toDomain() ?: return null
        val updated = ActionPlanOperations.reorder(
            plan = current,
            orderedSubActionIds = orderedIds,
        )
        return persist(updated)
    }

    /**
     * Computes the [Progress] (completed / total) of [actionItemId]'s plan
     * (Req 9.3). An item with no plan yields zero progress.
     */
    suspend fun progress(actionItemId: String): Progress {
        val plan = actionPlanDao.getByActionItem(actionItemId)?.toDomain()
            ?: return Progress(completed = 0, total = 0)
        return ActionPlanOperations.progress(plan)
    }

    /**
     * Marks the parent Action_Item completed (Req 9.4) by delegating to
     * [BoardRepository.changeStatus] so the board's indicator color and
     * Completion_Counter update reactively. This is the action behind the
     * "all sub-actions done — mark complete?" prompt.
     */
    suspend fun markParentComplete(actionItemId: String) {
        boardRepository.changeStatus(actionItemId, ActionStatus.COMPLETED)
    }

    /**
     * Loads the existing plan for [actionItemId] or builds a new, empty plan
     * with fresh dirty [SyncMeta] when none exists yet.
     */
    private suspend fun loadOrCreate(actionItemId: String): ActionPlan =
        actionPlanDao.getByActionItem(actionItemId)?.toDomain()
            ?: ActionPlan(
                id = idGenerator(),
                actionItemId = actionItemId,
                subActions = emptyList(),
                sync = SyncMeta(
                    updatedAt = clock(),
                    version = 1,
                    deleted = false,
                    dirty = true,
                ),
            )

    /**
     * Bumps the plan's sync metadata (updatedAt to the current clock, version
     * + 1, dirty) and upserts it so the change propagates through sync. Returns
     * the persisted plan.
     */
    private suspend fun persist(plan: ActionPlan): ActionPlan {
        val bumped = plan.copy(
            sync = plan.sync.copy(
                updatedAt = clock(),
                version = plan.sync.version + 1,
                dirty = true,
            ),
        )
        actionPlanDao.upsert(bumped.toEntity())
        return bumped
    }
}
