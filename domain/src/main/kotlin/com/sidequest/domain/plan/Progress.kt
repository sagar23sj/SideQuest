package com.sidequest.domain.plan

/**
 * The progress of an [com.sidequest.domain.model.ActionPlan]: how many of
 * its sub-actions are completed out of the total (Req 9.3).
 *
 * The invariant `0 <= completed <= total` always holds for a value produced by
 * [ActionPlanOperations.progress] (Property 16): [completed] counts completed
 * sub-actions and [total] is the number of sub-actions, so [completed] can
 * never exceed [total] and neither is negative.
 */
data class Progress(
    val completed: Int,
    val total: Int,
)
