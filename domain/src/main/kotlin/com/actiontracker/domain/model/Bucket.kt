package com.actiontracker.domain.model

import kotlinx.serialization.Serializable

/**
 * A user-defined grouping of [ActionItem]s. The [name] is unique per account.
 *
 * Status indicator colors are stored per bucket so the board can render an
 * item's color from its current [ActionStatus]. When [isShopping] is true the
 * bucket's items are wishlist items.
 */
@Serializable
data class Bucket(
    val id: String,
    val accountId: String,
    val name: String,
    val isShopping: Boolean,
    val notStartedColor: String,
    val inProgressColor: String,
    val completedColor: String,
    val sync: SyncMeta,
)
