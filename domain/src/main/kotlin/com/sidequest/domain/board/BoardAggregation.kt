package com.sidequest.domain.board

import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.Bucket
import com.sidequest.domain.model.SyncMeta

/**
 * Pure board aggregation logic (Req 4.1–4.5). Lives in `:domain` so it is
 * portable and validated with the shared Correctness Properties (8, 9, 10, 11)
 * without any Android/Room dependency; the app's board repository feeds it the
 * current items and buckets and exposes the resulting [BoardState] as a Flow.
 *
 * The functions here are pure and total: they never mutate their inputs and
 * never throw for any input.
 */
object BoardAggregation {

    /**
     * Resolves the indicator color for [status] from [bucket]'s configured
     * per-status colors (Req 4.3, 4.4, 4.5):
     * - [ActionStatus.NOT_STARTED] -> [Bucket.notStartedColor]
     * - [ActionStatus.IN_PROGRESS] -> [Bucket.inProgressColor]
     * - [ActionStatus.COMPLETED] -> [Bucket.completedColor]
     */
    fun statusColor(bucket: Bucket, status: ActionStatus): String = when (status) {
        ActionStatus.NOT_STARTED -> bucket.notStartedColor
        ActionStatus.IN_PROGRESS -> bucket.inProgressColor
        ActionStatus.COMPLETED -> bucket.completedColor
    }

    /**
     * The Completion_Counter: the number of [items] whose status is
     * [ActionStatus.COMPLETED] (Req 5.4). This is the single source of truth for
     * the counter — it reflects the *current* set of statuses rather than a
     * running tally, so applying any sequence of status changes and recomputing
     * always yields the exact count of completed items (Req 5.2, 5.3, Property
     * 11). [buildBoard] uses this for [BoardState.completionCount]; it is also
     * exposed standalone for reuse by callers that only need the count.
     */
    fun completionCount(items: List<ActionItem>): Int =
        items.count { it.status == ActionStatus.COMPLETED }

    /**
     * Builds the [BoardState] from [items] and [buckets].
     *
     * - Groups [items] by [ActionItem.bucketId] so every input item appears
     *   exactly once and, within each group, every item's `bucketId` equals the
     *   group's bucket (Req 4.1, Property 8). Items whose `bucketId` has no
     *   matching bucket are dropped from no group — they are surfaced in their
     *   own [BoardGroup]s keyed by a synthetic placeholder bucket so no item is
     *   ever lost.
     * - Sorts each group in non-decreasing order of [ActionItem.createdAt] using
     *   a stable sort, so items with equal timestamps keep their input order
     *   (Req 4.2, Property 9).
     * - Resolves each item's indicator color from its group's bucket for the
     *   item's current status (Req 4.3, 4.4, 4.5, Property 10).
     * - Computes [BoardState.completionCount] as the number of items whose
     *   status is [ActionStatus.COMPLETED] (Req 5.4, Property 11).
     *
     * Group ordering follows the order of [buckets], with any groups for unknown
     * bucket ids appended afterward in first-seen order. Buckets with no items
     * appear as empty groups, which is acceptable and does not affect the
     * no-loss property.
     */
    fun buildBoard(items: List<ActionItem>, buckets: List<Bucket>): BoardState {
        val bucketsById = buckets.associateBy { it.id }
        val itemsByBucketId = items.groupBy { it.bucketId }

        val groups = mutableListOf<BoardGroup>()

        // Emit a group per known bucket, preserving the order of [buckets].
        for (bucket in buckets) {
            val bucketItems = itemsByBucketId[bucket.id].orEmpty()
            groups += BoardGroup(bucket = bucket, items = toSortedBoardItems(bucketItems, bucket))
        }

        // Surface items whose bucketId has no matching bucket so none are lost.
        for ((bucketId, bucketItems) in itemsByBucketId) {
            if (bucketId !in bucketsById) {
                val placeholder = placeholderBucket(bucketId, bucketItems.first().accountId)
                groups += BoardGroup(
                    bucket = placeholder,
                    items = toSortedBoardItems(bucketItems, placeholder),
                )
            }
        }

        val completionCount = completionCount(items)

        return BoardState(groups = groups, completionCount = completionCount)
    }

    /**
     * Sorts [items] ascending by [ActionItem.createdAt] (stable) and resolves
     * each item's status color from [bucket].
     */
    private fun toSortedBoardItems(items: List<ActionItem>, bucket: Bucket): List<BoardItem> =
        items
            .sortedBy { it.createdAt }
            .map { BoardItem(item = it, statusColor = statusColor(bucket, it.status)) }

    /**
     * A synthetic bucket used to hold items that reference a bucket id not
     * present in the supplied bucket list, ensuring no item is ever dropped. Its
     * status colors are empty strings since no configured colors are available.
     */
    private fun placeholderBucket(bucketId: String, accountId: String): Bucket = Bucket(
        id = bucketId,
        accountId = accountId,
        name = "",
        notStartedColor = "",
        inProgressColor = "",
        completedColor = "",
        sync = SyncMeta(
            updatedAt = 0L,
            version = 0L,
            deleted = false,
            dirty = false,
        ),
    )
}
