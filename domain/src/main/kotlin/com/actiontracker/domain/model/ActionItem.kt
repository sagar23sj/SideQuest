package com.actiontracker.domain.model

import kotlinx.serialization.Serializable

/**
 * A tracked, actionable task captured from shared content.
 *
 * IDs are client-generated UUIDs so items can be created offline without a
 * server round-trip. [preview] is present for [ContentType.LINK] items, and
 * [wishlist] is present when the item lives in a shopping bucket.
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
    val isWishlistItem: Boolean,
    val wishlist: WishlistFields? = null,
    val sync: SyncMeta,
)
