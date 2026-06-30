import Foundation

// MARK: - Board aggregation and ordering (Req 8.1)
//
// Pure, portable board aggregation logic. Mirrors the Android client's
// `com.sidequest.domain.board.BoardAggregation` so the iOS Swift implementation
// produces field-by-field, ordering-exact equivalent results (Req 3.3,
// cross-implementation equivalence validated by task 4.19).
//
// Scope of task 4.6: partition Action_Items by bucket without loss and order
// each bucket's items by ascending `createdAt`, tie-broken by ascending `id`
// (Req 8.1, Properties 8 and 9).
//
// Scope of task 4.9 (this file's later additions): resolve each item's status
// indicator color from its bucket's per-status color map (Req 8.2, 8.3,
// Properties 10 and 17) and compute the completion counter as the number of
// "completed" items, clamped at zero (Req 8.5, 8.6, reused Property 11). The
// pure mapping/counter functions live in `StatusColor.swift`; this file wires
// their results into `BoardState`/`BoardGroup` so the UI never re-derives them.

/// An `ActionItem` paired with the indicator color resolved from its bucket's
/// configured color for the item's current ``ActionStatus`` (Req 8.2, 8.3).
///
/// The color is resolved once at aggregation time (via
/// ``Domain/statusColor(for:in:)``) so the UI never has to re-derive it, and it
/// always matches the item's current status (reused Property 10). This mirrors
/// the Android client's `com.sidequest.domain.board.BoardItem` so both clients
/// produce field-by-field equivalent boards (Req 3.3, task 4.19).
public struct BoardItem: Equatable {

    /// The underlying action item.
    public var item: ActionItem

    /// The indicator color for `item.status`, drawn from the group's bucket
    /// per-status color map.
    public var statusColor: String

    public init(item: ActionItem, statusColor: String) {
        self.item = item
        self.statusColor = statusColor
    }
}

/// A `Bucket` together with the ``BoardItem``s assigned to it, ordered ascending
/// by `createdAt` and tie-broken by ascending `id` (Req 8.1). Every item in
/// `items` has a `bucketId` equal to `bucket.id` (Property 8).
public struct BoardGroup: Equatable {

    /// The bucket this group represents. For items whose `bucketId` has no
    /// matching bucket, this is a synthetic placeholder bucket (see
    /// `Domain.buildBoard`) so that no item is ever lost.
    public var bucket: Bucket

    /// The bucket's items in board order: ascending `createdAt`, then ascending
    /// `id` for items that share a `createdAt`, each carrying its resolved
    /// status indicator color (Req 8.2, 8.3).
    public var items: [BoardItem]

    public init(bucket: Bucket, items: [BoardItem]) {
        self.bucket = bucket
        self.items = items
    }
}

/// The aggregated board read by the UI: ``BoardItem``s grouped by `Bucket`
/// (Req 8.1) with resolved per-status colors (Req 8.2, 8.3), plus the
/// ``completionCount`` of items whose status is "completed" (Req 8.5, 8.6).
/// Groups follow the order of the supplied buckets, with any groups for unknown
/// bucket ids appended afterward in first-seen order.
///
/// Mirrors the Android client's `BoardState` so both clients produce
/// field-by-field, ordering-exact equivalent boards (Req 3.3, task 4.19).
public struct BoardState: Equatable {

    public var groups: [BoardGroup]

    /// The Completion_Counter shown at the top of the board: the number of
    /// items across all groups whose status is "completed", clamped at zero
    /// (Req 8.5, 8.6, reused Property 11). Computed via
    /// ``Domain/completionCounter(items:)``.
    public var completionCount: Int

    public init(groups: [BoardGroup], completionCount: Int) {
        self.groups = groups
        self.completionCount = completionCount
    }
}

extension Domain {

    /// Builds the ``BoardState`` from `items` and `buckets` (Req 8.1).
    ///
    /// - Partitions `items` by ``ActionItem/bucketId`` so every input item
    ///   appears exactly once and, within each group, every item's `bucketId`
    ///   equals the group's bucket id (Property 8). Items whose `bucketId` has
    ///   no matching bucket are surfaced in their own groups keyed by a
    ///   synthetic placeholder bucket so no item is ever lost.
    /// - Orders each group ascending by ``ActionItem/createdAt`` and breaks ties
    ///   by ascending ``ActionItem/id`` (Req 8.1, Property 9), which is total
    ///   and deterministic regardless of input order.
    /// - Resolves each item's indicator color from its group's bucket for the
    ///   item's current status (Req 8.2, 8.3, reused Property 10).
    /// - Computes ``BoardState/completionCount`` as the number of items whose
    ///   status is "completed", clamped at zero (Req 8.5, 8.6, reused
    ///   Property 11).
    ///
    /// Group ordering follows the order of `buckets`, with groups for unknown
    /// bucket ids appended afterward in first-seen order (the order in which
    /// their items first appear in `items`). Buckets with no items appear as
    /// empty groups.
    ///
    /// This function is pure and total: it never mutates its inputs and never
    /// throws for any input.
    public static func buildBoard(items: [ActionItem], buckets: [Bucket]) -> BoardState {
        let bucketIds = Set(buckets.map(\.id))
        let itemsByBucketId = groupByBucketIdPreservingOrder(items)

        var groups: [BoardGroup] = []

        // Emit a group per known bucket, preserving the order of `buckets`.
        for bucket in buckets {
            let bucketItems = itemsByBucketId[bucket.id] ?? []
            groups.append(BoardGroup(bucket: bucket, items: boardItems(bucketItems, in: bucket)))
        }

        // Surface items whose bucketId has no matching bucket so none are lost,
        // in the order those unknown bucket ids were first seen in `items`.
        for bucketId in firstSeenUnknownBucketIds(items, knownBucketIds: bucketIds) {
            let bucketItems = itemsByBucketId[bucketId] ?? []
            let placeholder = placeholderBucket(id: bucketId, accountId: bucketItems.first?.accountId ?? "")
            groups.append(BoardGroup(bucket: placeholder, items: boardItems(bucketItems, in: placeholder)))
        }

        return BoardState(groups: groups, completionCount: completionCounter(items: items))
    }

    // MARK: - Helpers

    /// Orders `items` ascending by `createdAt` (tie-broken by ascending `id`)
    /// and pairs each with its indicator color resolved from `bucket` for the
    /// item's current status (Req 8.1, 8.2, 8.3).
    private static func boardItems(_ items: [ActionItem], in bucket: Bucket) -> [BoardItem] {
        sortedForBoard(items).map { item in
            BoardItem(item: item, statusColor: statusColor(for: item.status, in: bucket))
        }
    }

    /// Orders `items` ascending by `createdAt`, breaking ties by ascending `id`
    /// (Req 8.1). Using `id` as the tie-breaker makes the order total and
    /// independent of the input order.
    private static func sortedForBoard(_ items: [ActionItem]) -> [ActionItem] {
        items.sorted { lhs, rhs in
            if lhs.createdAt != rhs.createdAt {
                return lhs.createdAt < rhs.createdAt
            }
            return lhs.id < rhs.id
        }
    }

    /// Groups `items` by `bucketId` while preserving the first-seen order of
    /// each bucket id and the relative order of items within a bucket. Unlike
    /// `Dictionary(grouping:)`, this keeps deterministic ordering for the
    /// unknown-bucket surfacing step.
    private static func groupByBucketIdPreservingOrder(
        _ items: [ActionItem]
    ) -> [String: [ActionItem]] {
        var grouped: [String: [ActionItem]] = [:]
        for item in items {
            grouped[item.bucketId, default: []].append(item)
        }
        return grouped
    }

    /// Returns the bucket ids referenced by `items` that are not in
    /// `knownBucketIds`, in the order they first appear in `items`.
    private static func firstSeenUnknownBucketIds(
        _ items: [ActionItem],
        knownBucketIds: Set<String>
    ) -> [String] {
        var seen: Set<String> = []
        var ordered: [String] = []
        for item in items where !knownBucketIds.contains(item.bucketId) {
            if seen.insert(item.bucketId).inserted {
                ordered.append(item.bucketId)
            }
        }
        return ordered
    }

    /// A synthetic bucket holding items that reference a bucket id absent from
    /// the supplied bucket list, ensuring no item is ever dropped (Property 8).
    /// Its color fields are empty since no configured colors are available;
    /// this mirrors the Android `placeholderBucket`.
    private static func placeholderBucket(id: String, accountId: String) -> Bucket {
        Bucket(
            id: id,
            accountId: accountId,
            name: "",
            notStartedColor: "",
            inProgressColor: "",
            completedColor: "",
            sync: SyncMeta(updatedAt: Date(timeIntervalSince1970: 0), version: 0, deleted: false, dirty: false)
        )
    }
}
