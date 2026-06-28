package com.sidequest.domain.model

import kotlinx.serialization.Serializable

/**
 * A user-defined grouping of [ActionItem]s. The [name] is unique per account.
 *
 * Status indicator colors are stored per bucket so the board can render an
 * item's color from its current [ActionStatus]. A "wishlist" is just a bucket
 * the user named — there is no special shopping flag.
 */
@Serializable
data class Bucket(
    val id: String,
    val accountId: String,
    val name: String,
    val notStartedColor: String,
    val inProgressColor: String,
    val completedColor: String,
    /**
     * Optional cover image for the bucket: a local file path (or URI) to a
     * user-chosen photo. When null, the UI shows a domain-themed cover derived
     * from the bucket [name].
     */
    val imageRef: String? = null,
    val sync: SyncMeta,
)
