import Foundation

// MARK: - Bucket deletion: decision model + accounting (task 7.1)
//
// Pure, portable logic for deleting a bucket. Mirrors the Android client's
// `com.sidequest.domain.bucket` types (`BucketDeletionStrategy`,
// `BucketDeletionOutcome`, `BucketOperations.applyBucketDeletion`) so the iOS
// Swift implementation produces field-by-field equivalent results
// (Req 3.3, cross-implementation equivalence). Living in `Domain` keeps it
// I/O-free and host-testable, and lets the reused sibling **Property 6** test
// (task 7.2) target the accounting function directly.
//
// Two concerns are modeled here, both pure:
//   * The *decision* of what prompt (if any) a delete requires — direct delete
//     for an empty bucket, reassign-or-delete for a non-empty bucket when
//     another bucket exists (Req 9.4), or confirm-delete-contained-items when
//     it is the last bucket (Req 9.5).
//   * The *accounting* of applying a chosen strategy to the item list, used by
//     the `BucketManagementService` to persist exactly the right changes and by
//     Property 6 to assert no item is lost.

// MARK: - Strategy

/// The choice a user makes when deleting a bucket that still contains
/// Action_Items (Req 9.4, 9.5). Mirrors the Android `BucketDeletionStrategy`.
public enum BucketDeletionStrategy: Equatable {

    /// Move every Action_Item in the bucket being deleted to the bucket
    /// identified by `targetBucketId`. No item is lost; the deleted bucket ends
    /// up empty. The target must differ from the bucket being deleted —
    /// otherwise the move is a no-op and the source is not emptied.
    case reassign(targetBucketId: String)

    /// Delete exactly the Action_Items contained in the bucket being deleted.
    case deleteItems
}

// MARK: - Accounting outcome

/// Result of applying a ``BucketDeletionStrategy`` to a list of Action_Items
/// via ``Domain/applyBucketDeletion(items:bucketId:strategy:)``. Mirrors the
/// Android `BucketDeletionOutcome`.
///
/// `items` is the full updated item list after the strategy is applied: for
/// ``BucketDeletionStrategy/reassign(targetBucketId:)`` it has the same size as
/// the input (items are moved, none lost), and for
/// ``BucketDeletionStrategy/deleteItems`` it is the remaining items after
/// removing the bucket's contents. `reassignedItemIds` and `deletedItemIds` let
/// the repository persist exactly the changes (update moved items' `bucketId`,
/// or tombstone removed items) and let Property 6 assert total item accounting
/// is preserved.
public struct BucketDeletionOutcome: Equatable {

    /// The full item list after applying the strategy.
    public var items: [ActionItem]

    /// Ids of items moved to the reassign target, in their original order.
    public var reassignedItemIds: [String]

    /// Ids of items deleted, in their original order.
    public var deletedItemIds: [String]

    public init(
        items: [ActionItem],
        reassignedItemIds: [String],
        deletedItemIds: [String]
    ) {
        self.items = items
        self.reassignedItemIds = reassignedItemIds
        self.deletedItemIds = deletedItemIds
    }
}

// MARK: - Delete decision

/// What a delete of a given bucket requires before it can be applied. Produced
/// by ``Domain/bucketDeleteDecision(bucketId:buckets:items:)`` so the UI knows
/// which prompt to show; the bucket is only mutated once the user confirms via
/// ``BucketManagementService``.
public enum BucketDeleteDecision: Equatable {

    /// The bucket is empty: it can be deleted directly with no further prompt.
    case deleteDirectly

    /// The bucket is non-empty and at least one other bucket exists for the
    /// account: the user must choose to reassign the contained items to one of
    /// `targetCandidates` or delete them, and confirm, before applying
    /// (Req 9.4). `itemCount` is the number of contained Action_Items.
    case requiresReassignOrDelete(itemCount: Int, targetCandidates: [Bucket])

    /// The bucket is non-empty and is the last bucket for the account: the user
    /// must confirm deleting the contained items before applying (Req 9.5).
    /// `itemCount` is the number of contained Action_Items.
    case requiresConfirmDeleteItems(itemCount: Int)
}

extension Domain {

    /// Classifies what deleting the bucket `bucketId` requires, given the live
    /// `buckets` and `items` for the account (Req 9.4, 9.5). Pure and total: it
    /// never mutates its inputs and never throws.
    ///
    /// Tombstoned (`sync.deleted`) buckets and items are ignored, so a bucket
    /// already pending deletion does not count as "another bucket" and an
    /// item pending deletion does not make a bucket "non-empty".
    ///
    /// - Returns:
    ///   - `nil` when no live bucket with `bucketId` exists (nothing to delete);
    ///   - ``BucketDeleteDecision/deleteDirectly`` when the bucket holds no
    ///     live items;
    ///   - ``BucketDeleteDecision/requiresReassignOrDelete(itemCount:targetCandidates:)``
    ///     when the bucket is non-empty and another live bucket exists for the
    ///     same account (Req 9.4);
    ///   - ``BucketDeleteDecision/requiresConfirmDeleteItems(itemCount:)`` when
    ///     the bucket is non-empty and is the last live bucket for the account
    ///     (Req 9.5).
    public static func bucketDeleteDecision(
        bucketId: String,
        buckets: [Bucket],
        items: [ActionItem]
    ) -> BucketDeleteDecision? {
        guard let bucket = buckets.first(where: {
            $0.id == bucketId && !$0.sync.deleted
        }) else {
            return nil
        }

        let contained = items.filter {
            $0.bucketId == bucketId && !$0.sync.deleted
        }
        guard !contained.isEmpty else {
            return .deleteDirectly
        }

        let otherBuckets = buckets.filter {
            $0.accountId == bucket.accountId
                && $0.id != bucketId
                && !$0.sync.deleted
        }
        if otherBuckets.isEmpty {
            return .requiresConfirmDeleteItems(itemCount: contained.count)
        }
        return .requiresReassignOrDelete(
            itemCount: contained.count,
            targetCandidates: otherBuckets
        )
    }

    /// Computes the result of deleting the bucket `bucketId` that contains
    /// Action_Items, given the `strategy` chosen by the user (Req 9.4, 9.5).
    /// Pure and total: it does not mutate `items` and never throws; the caller
    /// persists the returned changes and tombstones the now-empty bucket.
    /// Mirrors the Android `BucketOperations.applyBucketDeletion`.
    ///
    /// Total item accounting is preserved (reused Property 6):
    /// - ``BucketDeletionStrategy/reassign(targetBucketId:)``: every item whose
    ///   `bucketId` equals `bucketId` is moved to the target bucket (its
    ///   `bucketId` updated), so the returned `items` has the same size as
    ///   `items`, no item is lost, and no item remains in the deleted bucket.
    ///   Moving to the same bucket is a no-op, so when the target equals
    ///   `bucketId` the items are left untouched and reported as reassigned for
    ///   accounting.
    /// - ``BucketDeletionStrategy/deleteItems``: exactly the items in `bucketId`
    ///   are removed, so the count decreases by precisely the bucket's item
    ///   count.
    ///
    /// Items not in `bucketId` are always returned unchanged and in their
    /// original order.
    public static func applyBucketDeletion(
        items: [ActionItem],
        bucketId: String,
        strategy: BucketDeletionStrategy
    ) -> BucketDeletionOutcome {
        let containedIds = items
            .filter { $0.bucketId == bucketId }
            .map { $0.id }

        switch strategy {
        case .reassign(let targetBucketId):
            let updated = items.map { item -> ActionItem in
                guard item.bucketId == bucketId else { return item }
                var moved = item
                moved.bucketId = targetBucketId
                return moved
            }
            return BucketDeletionOutcome(
                items: updated,
                reassignedItemIds: containedIds,
                deletedItemIds: []
            )

        case .deleteItems:
            let remaining = items.filter { $0.bucketId != bucketId }
            return BucketDeletionOutcome(
                items: remaining,
                reassignedItemIds: [],
                deletedItemIds: containedIds
            )
        }
    }
}
