import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Reused Property 11 — Completion counter equals the
/// number of completed items** (iOS design "Reused properties" table; sibling
/// `action-tracker-app` Property 11; task 4.12).
///
/// **Validates: Requirements 8.5, 8.6**
///
/// Property 11 statement (as it applies to the iOS Swift domain logic): for any
/// list of `ActionItem`s, the Completion_Counter equals the number of items
/// whose `status` is `.completed` and is always `>= 0` (Req 8.5). The counter is
/// a function of the *current* set of statuses, so toggling a single item into
/// or out of the completed state changes the counter by exactly one (Req 8.6).
///
/// Subjects under test:
///   * `Domain.completionCounter(items:)` — the pure counter.
///   * `Domain.buildBoard(items:buckets:).completionCount` — the same count
///     surfaced on the aggregated board, which must agree with the pure counter.
///
/// ## Strategy
///
/// Items are generated with randomly varied statuses (drawn uniformly from all
/// `ActionStatus` cases) so completed and non-completed items are both common.
/// An independent oracle — `items.filter { $0.status == .completed }.count` —
/// expresses the property directly rather than mirroring the implementation.
/// Each property runs ≥100 iterations (the design mandates a minimum of 100; we
/// configure 200 for extra coverage).
final class CompletionCounterPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    // MARK: - Fixed pools

    private static let accountIds = ["acct-A", "acct-B", "acct-C"]
    private static let bucketIds = ["bucket-1", "bucket-2", "bucket-3"]
    private static let epoch = Date(timeIntervalSince1970: 1_600_000_000)

    // MARK: - Generators

    /// Whole-second instants so `createdAt` values vary without affecting the
    /// count (the counter ignores ordering and time entirely).
    private static let createdAtGen: Gen<Date> =
        Gen<Int>.choose((1_600_000_000, 1_700_000_000))
            .map { Date(timeIntervalSince1970: TimeInterval($0)) }

    private static let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)

    /// A single `ActionItem` with a varied status, account, bucket, and
    /// creation time. The `id` is a placeholder reassigned per-index so the
    /// board's tie-break ordering is never confused by duplicate ids.
    private static var itemGen: Gen<ActionItem> {
        Gen.zip(
            Gen.fromElements(of: accountIds),
            Gen.fromElements(of: bucketIds),
            statusGen,
            createdAtGen
        ).map { account, bucket, status, createdAt in
            makeItem(id: "tmp", account: account, bucketId: bucket, status: status, createdAt: createdAt)
        }
    }

    /// A bounded-length list of items with distinct, stable ids. Lengths
    /// include the empty list so the `0` edge case is exercised.
    private static var itemListGen: Gen<[ActionItem]> {
        Gen<Int>.choose((0, 12)).flatMap { count in
            Gen.sequence(Array(repeating: itemGen, count: count))
        }.map(assignUniqueIds)
    }

    /// A small set of buckets covering the ids items are drawn from, so
    /// `buildBoard` can resolve every item into a known group. Color values are
    /// distinct per status (not relevant to the counter, but realistic).
    private static var bucketsGen: Gen<[Bucket]> {
        Gen.pure(bucketIds.enumerated().map { index, id in
            makeBucket(id: id, account: accountIds[index % accountIds.count])
        })
    }

    // MARK: - Helpers

    private static func makeItem(
        id: String,
        account: String,
        bucketId: String,
        status: ActionStatus,
        createdAt: Date
    ) -> ActionItem {
        ActionItem(
            id: id,
            accountId: account,
            bucketId: bucketId,
            title: "item-\(id)",
            contentType: .text,
            timeframe: .today,
            status: status,
            createdAt: createdAt,
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false, dirty: false)
        )
    }

    private static func makeBucket(id: String, account: String) -> Bucket {
        Bucket(
            id: id,
            accountId: account,
            name: "bucket-\(id)",
            notStartedColor: "#100000",
            inProgressColor: "#001000",
            completedColor: "#000010",
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false, dirty: false)
        )
    }

    /// Re-keys items with unique, stable ids so the board ordering and the
    /// per-index flip address a single, well-defined item.
    private static func assignUniqueIds(_ items: [ActionItem]) -> [ActionItem] {
        items.enumerated().map { index, item in
            var copy = item
            copy.id = "i-\(index)"
            return copy
        }
    }

    /// Independent oracle for the completed count, expressed directly from the
    /// Property 11 statement.
    private static func completedCount(_ items: [ActionItem]) -> Int {
        items.filter { $0.status == .completed }.count
    }

    // MARK: - Property 11: counter equals completed count and is never negative (Req 8.5)

    /// `completionCounter(items:)` equals the number of completed items and is
    /// always `>= 0`, for any list of items.
    func testCounterEqualsCompletedCountAndIsNonNegative() {
        property("completionCounter == completed count, >= 0 (Property 11, Req 8.5)",
                 arguments: Self.checkArgs)
            <- forAll(Self.itemListGen) { (items: [ActionItem]) in
                let counter = Domain.completionCounter(items: items)
                let expected = Self.completedCount(items)
                return (counter == expected) ^&&^ (counter >= 0)
            }
    }

    // MARK: - Property 11: board completion count agrees with the pure counter (Req 8.5)

    /// `buildBoard(...).completionCount` equals `completionCounter(items:)`,
    /// which equals the number of completed items — the board surfaces the same
    /// count the pure function computes.
    func testBoardCompletionCountMatchesCounter() {
        let scenarioGen = Gen.zip(Self.itemListGen, Self.bucketsGen)

        property("buildBoard.completionCount == completionCounter == completed count (Property 11, Req 8.5)",
                 arguments: Self.checkArgs)
            <- forAll(scenarioGen) { (items: [ActionItem], buckets: [Bucket]) in
                let board = Domain.buildBoard(items: items, buckets: buckets)
                let counter = Domain.completionCounter(items: items)
                let expected = Self.completedCount(items)
                return (board.completionCount == counter) ^&&^ (board.completionCount == expected)
            }
    }

    // MARK: - Property 11: toggling one item changes the counter by exactly one (Req 8.6)

    /// Flipping a single item into or out of the completed state changes the
    /// counter by exactly one in the corresponding direction; all other items
    /// are untouched, so no other change to the count can occur.
    func testTogglingOneItemChangesCounterByExactlyOne() {
        // Generate a non-empty list plus an index selecting the item to flip.
        let scenarioGen: Gen<(items: [ActionItem], index: Int)> =
            Gen<Int>.choose((1, 12)).flatMap { count in
                Gen.sequence(Array(repeating: Self.itemGen, count: count))
                    .map(Self.assignUniqueIds)
            }.flatMap { items in
                Gen<Int>.choose((0, items.count - 1)).map { (items, $0) }
            }

        property("toggling completed changes the counter by exactly 1 (Property 11, Req 8.6)",
                 arguments: Self.checkArgs)
            <- forAll(scenarioGen) { scenario in
                let items = scenario.items
                let index = scenario.index
                let before = Domain.completionCounter(items: items)

                var flipped = items
                let wasCompleted = flipped[index].status == .completed
                // Toggle: completed -> notStarted, otherwise -> completed.
                flipped[index].status = wasCompleted ? .notStarted : .completed
                let after = Domain.completionCounter(items: flipped)

                let expectedDelta = wasCompleted ? -1 : 1
                return (after - before) == expectedDelta
            }
    }

    // MARK: - Property 11: counter is invariant under reordering (Req 8.5)

    /// The counter depends only on the multiset of statuses, not on item order:
    /// reversing the list leaves the count unchanged.
    func testCounterInvariantUnderReordering() {
        property("completionCounter is order-independent (Property 11, Req 8.5)",
                 arguments: Self.checkArgs)
            <- forAll(Self.itemListGen) { (items: [ActionItem]) in
                Domain.completionCounter(items: items)
                    == Domain.completionCounter(items: items.reversed())
            }
    }
}
