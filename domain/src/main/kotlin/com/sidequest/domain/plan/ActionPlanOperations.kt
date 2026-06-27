package com.sidequest.domain.plan

import com.sidequest.domain.model.ActionPlan
import com.sidequest.domain.model.SubAction

/**
 * Pure Action_Plan logic (Req 9.1–9.5). Lives in `:domain` so it is portable
 * and validated with the shared Correctness Properties (16, 17, 18) without any
 * Android/Room dependency; the app's plan repository feeds it the current plan,
 * applies an operation, and persists the returned plan.
 *
 * Every function here is pure and total: it never mutates its inputs and never
 * throws for any input.
 *
 * ## Ordering convention
 * Sub-actions within a plan use **contiguous order indices starting at 0**: a
 * plan with `n` sub-actions has `order` values forming the set `0..n-1`, and the
 * list is kept sorted so element `i` has `order == i`. Every operation that adds
 * or rearranges sub-actions ([addSubAction], [reorder]) re-establishes this
 * invariant on the value it returns, so callers can rely on contiguous,
 * gap-free ordering regardless of the input plan's ordering.
 */
object ActionPlanOperations {

    /**
     * Appends a new [SubAction] to the end of [plan] (Req 9.1).
     *
     * The new sub-action is created with the given [text], `completed == false`,
     * an id from [idGenerator], and `order == plan.subActions.size` (the next
     * contiguous index after the existing 0-based sequence). The returned plan's
     * sub-actions are re-normalized to contiguous `0..n` ordering so the
     * convention holds even if the input plan had non-contiguous orders.
     *
     * @param plan the plan to append to; not mutated.
     * @param text the text of the new step.
     * @param idGenerator supplies the client-generated id for the new sub-action;
     *   injected so the computation stays deterministic and testable.
     */
    fun addSubAction(plan: ActionPlan, text: String, idGenerator: () -> String): ActionPlan {
        val normalized = normalizedOrder(plan.subActions)
        val appended = normalized + SubAction(
            id = idGenerator(),
            text = text,
            order = normalized.size,
            completed = false,
        )
        return plan.copy(subActions = appended)
    }

    /**
     * Sets the [completed] flag of the sub-action identified by [subActionId]
     * within [plan] (Req 9.2).
     *
     * Order and the relative position of every sub-action are preserved. If no
     * sub-action has the given id the plan is returned unchanged, keeping the
     * function total.
     */
    fun markSubAction(plan: ActionPlan, subActionId: String, completed: Boolean): ActionPlan {
        val updated = plan.subActions.map { subAction ->
            if (subAction.id == subActionId) subAction.copy(completed = completed) else subAction
        }
        return plan.copy(subActions = updated)
    }

    /**
     * Computes the [Progress] of [plan] (Req 9.3, Property 16):
     * `completed` is the number of completed sub-actions and `total` is the
     * number of sub-actions, so `0 <= completed <= total` always holds.
     */
    fun progress(plan: ActionPlan): Progress = Progress(
        completed = plan.subActions.count { it.completed },
        total = plan.subActions.size,
    )

    /**
     * Whether the UI should prompt the user to mark the parent Action_Item
     * completed (Req 9.4, Property 17).
     *
     * Returns true iff the plan is **non-empty** and **every** sub-action is
     * completed. An empty plan returns false: there is nothing to have completed,
     * so no prompt is surfaced.
     */
    fun shouldPromptParentComplete(plan: ActionPlan): Boolean =
        plan.subActions.isNotEmpty() && plan.subActions.all { it.completed }

    /**
     * Reorders the sub-actions of [plan] to follow [orderedSubActionIds]
     * (Req 9.5, Property 18).
     *
     * The result is guaranteed to be a permutation of the existing sub-actions
     * with contiguous ordering regardless of the input, by this total contract:
     * 1. Sub-actions whose ids appear in [orderedSubActionIds] are placed first,
     *    in the order their ids are listed. Ids that do not match any existing
     *    sub-action are ignored, and duplicate ids in the list select a
     *    sub-action only once (first occurrence).
     * 2. Any existing sub-actions not mentioned in [orderedSubActionIds] are
     *    appended afterward, preserving their original relative order, so none
     *    are ever lost.
     * 3. The `order` field of every sub-action is reassigned to its final
     *    position, yielding a contiguous `0..n-1` sequence.
     *
     * Because step 2 reattaches any omitted sub-actions and no new sub-actions
     * are introduced, the returned plan always contains exactly the same set of
     * sub-actions as [plan].
     */
    fun reorder(plan: ActionPlan, orderedSubActionIds: List<String>): ActionPlan {
        val byId = plan.subActions.associateBy { it.id }

        val mentioned = LinkedHashSet<String>()
        val ordered = mutableListOf<SubAction>()
        for (id in orderedSubActionIds) {
            if (id in byId && mentioned.add(id)) {
                ordered += byId.getValue(id)
            }
        }

        // Append existing sub-actions not mentioned, preserving relative order.
        for (subAction in plan.subActions) {
            if (subAction.id !in mentioned) {
                ordered += subAction
            }
        }

        return plan.copy(subActions = reindex(ordered))
    }

    /**
     * Returns [subActions] sorted by their current [SubAction.order] and then
     * reindexed to a contiguous `0..n-1` sequence, establishing the ordering
     * convention without changing the existing relative sequence.
     */
    private fun normalizedOrder(subActions: List<SubAction>): List<SubAction> =
        reindex(subActions.sortedBy { it.order })

    /**
     * Reassigns each sub-action's [SubAction.order] to its index in the list,
     * producing a contiguous `0..n-1` ordering in list order.
     */
    private fun reindex(subActions: List<SubAction>): List<SubAction> =
        subActions.mapIndexed { index, subAction -> subAction.copy(order = index) }
}
