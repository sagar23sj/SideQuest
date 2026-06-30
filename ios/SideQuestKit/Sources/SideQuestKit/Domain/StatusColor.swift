import Foundation

// MARK: - Status-to-color mapping and completion counter (Req 8.2, 8.3, 8.5, 8.6)
//
// Pure, portable status-color resolution and completion counting. Mirrors the
// Android client's `com.sidequest.domain.board.BoardAggregation.statusColor`
// and `completionCount` so the iOS Swift implementation produces field-by-field
// equivalent results (Req 3.3, cross-implementation equivalence validated by
// task 4.19).
//
// Scope of task 4.9:
//   * Map each `ActionStatus` to a color from the bucket's per-status color map
//     such that distinct statuses map to distinct colors (Req 8.2, 8.3); the
//     mapping is injective per bucket whenever the bucket's three colors are
//     distinct (Property 17), and `statusColorsAreInjective(in:)` lets callers
//     (e.g. bucket creation/validation in task 7.1) and the property tests
//     (task 4.11) detect/validate that invariant.
//   * Compute the completion counter as the number of "completed" items,
//     clamped at zero (Req 8.5, 8.6, reused Property 11).
//
// These functions are pure and total: they never mutate their inputs and never
// throw for any input.

extension Domain {

    /// Resolves the indicator color for `status` from `bucket`'s configured
    /// per-status color map (Req 8.2, 8.3):
    ///
    /// - ``ActionStatus/notStarted`` → ``Bucket/notStartedColor``
    /// - ``ActionStatus/inProgress`` → ``Bucket/inProgressColor``
    /// - ``ActionStatus/completed`` → ``Bucket/completedColor``
    ///
    /// Because each status reads a distinct field of the bucket, the resolved
    /// color always matches the item's *current* status (reused Property 10):
    /// re-resolving after any status change yields the field for the new
    /// status. The mapping is injective whenever the bucket's three colors are
    /// distinct — see ``statusColorsAreInjective(in:)`` (Property 17).
    public static func statusColor(for status: ActionStatus, in bucket: Bucket) -> String {
        switch status {
        case .notStarted: return bucket.notStartedColor
        case .inProgress: return bucket.inProgressColor
        case .completed: return bucket.completedColor
        }
    }

    /// The full per-status color map for `bucket`: every ``ActionStatus`` paired
    /// with the color ``statusColor(for:in:)`` resolves for it (Req 8.2).
    ///
    /// Useful for rendering a legend and for validating the injectivity
    /// invariant (Property 17) without re-deriving the cases at the call site.
    public static func statusColors(in bucket: Bucket) -> [ActionStatus: String] {
        var colors: [ActionStatus: String] = [:]
        for status in ActionStatus.allCases {
            colors[status] = statusColor(for: status, in: bucket)
        }
        return colors
    }

    /// Whether `bucket`'s per-status color map is injective: no two distinct
    /// ``ActionStatus`` values resolve to the same color (Req 8.2,
    /// Property 17).
    ///
    /// The mapping itself always sends each status to a distinct *field* of the
    /// bucket; this check reports whether those fields hold distinct *values*,
    /// which is the invariant bucket creation/rename must enforce so the board
    /// can show a distinct indicator per status. Comparison is exact on the raw
    /// color strings, matching how the colors are stored and synced.
    public static func statusColorsAreInjective(in bucket: Bucket) -> Bool {
        let colors = ActionStatus.allCases.map { statusColor(for: $0, in: bucket) }
        return Set(colors).count == colors.count
    }

    /// The Completion_Counter: the number of `items` whose status is
    /// ``ActionStatus/completed``, clamped so it is never negative (Req 8.5,
    /// 8.6, reused Property 11).
    ///
    /// This is the single source of truth for the counter — it reflects the
    /// *current* set of statuses rather than a running tally, so applying any
    /// sequence of status changes and recomputing always yields the exact count
    /// of completed items. A plain count is already non-negative; the
    /// `max(0,)` makes the "never less than zero" guarantee explicit and total.
    public static func completionCounter(items: [ActionItem]) -> Int {
        let completed = items.lazy.filter { $0.status == .completed }.count
        return max(0, completed)
    }
}
