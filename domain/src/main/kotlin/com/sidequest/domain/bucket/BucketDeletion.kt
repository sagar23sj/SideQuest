package com.sidequest.domain.bucket

import com.sidequest.domain.model.ActionItem

/**
 * Strategy chosen by the user when deleting a bucket that still contains
 * Action_Items (Req 2.5). The user is prompted to either reassign the contained
 * items to another bucket or delete them outright.
 */
sealed interface BucketDeletionStrategy {

    /**
     * Move every Action_Item in the bucket being deleted to the bucket
     * identified by [targetBucketId]. No item is lost; the deleted bucket ends
     * up empty. The caller must supply a [targetBucketId] that differs from the
     * bucket being deleted (otherwise the move is a no-op and the source is not
     * emptied).
     */
    data class Reassign(val targetBucketId: String) : BucketDeletionStrategy

    /** Delete exactly the Action_Items contained in the bucket being deleted. */
    data object DeleteItems : BucketDeletionStrategy
}

/**
 * Result of applying a [BucketDeletionStrategy] to a list of Action_Items via
 * [BucketOperations.applyBucketDeletion].
 *
 * [items] is the full updated item list after the strategy is applied: for
 * [BucketDeletionStrategy.Reassign] it has the same size as the input (items
 * are moved, none lost), and for [BucketDeletionStrategy.DeleteItems] it is the
 * remaining items after removing the bucket's contents. [reassignedItemIds] and
 * [deletedItemIds] let the repository persist exactly the changes (update moved
 * items' `bucketId`, or tombstone removed items) and let tests assert that total
 * item accounting is preserved (Property 6).
 */
data class BucketDeletionOutcome(
    val items: List<ActionItem>,
    val reassignedItemIds: List<String>,
    val deletedItemIds: List<String>,
)
