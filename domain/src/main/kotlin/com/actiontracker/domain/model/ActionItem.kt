package com.actiontracker.domain.model

import kotlinx.serialization.Serializable

/**
 * A tracked, actionable task captured from shared content.
 *
 * IDs are client-generated UUIDs so items can be created offline without a
 * server round-trip. [preview] is present for [ContentType.LINK] items.
 *
 * A "wishlist" is not a special kind of item — it is simply a [Bucket] the user
 * created (e.g. named "Wishlist"). Items in such a bucket are ordinary action
 * items, so there is no dedicated wishlist flag or fields here.
 */
@Serializable
data class ActionItem(
    val id: String,
    val accountId: String,
    val bucketId: String,
    val title: String,
    val description: String? = null,
    val contentType: ContentType,
    val sourceContent: String? = null,
    val preview: LinkPreview? = null,
    val timeframe: Timeframe,
    val status: ActionStatus,
    val createdAt: Long,
    val sync: SyncMeta,
)
