package com.sidequest.domain.board

import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.Bucket

/**
 * An [ActionItem] paired with the indicator color resolved from its bucket's
 * configured color for the item's current [com.sidequest.domain.model.ActionStatus]
 * (Req 4.3, 4.4, 4.5). The color is resolved once at aggregation time so the UI
 * never has to re-derive it, and it always matches the item's current status
 * (Property 10).
 */
data class BoardItem(
    val item: ActionItem,
    val statusColor: String,
)

/**
 * A [Bucket] together with its [BoardItem]s, sorted in non-decreasing order of
 * [ActionItem.createdAt] (Req 4.2, Property 9). Every item in [items] has a
 * `bucketId` equal to [bucket]'s id (Property 8).
 */
data class BoardGroup(
    val bucket: Bucket,
    val items: List<BoardItem>,
)

/**
 * The aggregated board read by the UI: items grouped by bucket (Req 4.1) with
 * resolved status colors, plus the [completionCount] of items whose status is
 * completed (Req 5.4). The completion count is forward-compatible with the
 * status-change/counter flow (task 7.5) and equals the number of completed
 * items across all groups (Property 11).
 */
data class BoardState(
    val groups: List<BoardGroup>,
    val completionCount: Int,
)
