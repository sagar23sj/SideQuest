package com.sidequest.domain.model

import kotlinx.serialization.Serializable

/**
 * An ordered breakdown of an [ActionItem] into smaller steps.
 */
@Serializable
data class ActionPlan(
    val id: String,
    val actionItemId: String,
    val subActions: List<SubAction>,
    val sync: SyncMeta,
)

/**
 * A single step within an [ActionPlan]. [order] defines its position in the
 * sequence; [completed] tracks whether the step is done.
 */
@Serializable
data class SubAction(
    val id: String,
    val text: String,
    val order: Int,
    val completed: Boolean,
)
