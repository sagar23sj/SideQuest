import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Reused Property 9 — Items within a bucket ordered
/// by ascending creation time** (iOS design "Reused properties" table; sibling
/// `action-tracker-app` Property 9).
///
/// **Validates: Requirements 8.1**
///
/// Property 9 statement (as it applies to the iOS Swift board aggregation): for
/// every ``BoardGroup`` produced by ``Domain/buildBoard(items:buckets:)``, the
/// group's items are sorted ascending by ``ActionItem/createdAt`` and, for items
/// that share a `createdAt`, ascending by ``ActionItem/id``. Concretely, for any
/// two consecutive items `a, b` in a group:
///
/// ```
/// (a.createdAt < b.createdAt) || (a.createdAt == b.createdAt && a.id < b.id)
/// ```
///
/// This order is total and deterministic regardless of the order items are
/// supplied in, so a shuffled input must yield the same board order.
///
/// Subject under test: ``Domain/buildBoard(items:buckets:)`` in
/// `Sources/SideQuestKit/Domain/Board.swift`.
///
/// The generators below deliberately draw `createdAt` from a small pool of
/// instants and `bucketId` from a small pool of buckets so that:
///   * ties on `createdAt` (with differing ids) occur frequently, exercising the
///     id tie-breaker, and
///   * multiple items land in the same bucket, exercising intra-group ordering.
/// Item ids are made unique per list so the `id` tie-breaker is total. Each
/// property runs ≥100 iterations (the design mandates a minimum of 100; we
/// configure 200 for extra coverage).
final class BoardOrderingPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    // MARK: - Fixed pools (small, so ties and shared buckets occur frequently)

    private static let accountId = "acct-board"

    /// Known bucket ids. Items also occasionally reference an unknown id so the
    /// synthetic-placeholder groups are ordered too.
    private static let knownBucketIds = ["bucket-1", "bucket-2", "bucket-3"]

    /// Includes one id with no matching bucket so unknown-bucket groups are
    /// exercised by the ordering property as well.
    private static let candidateBucketIds = knownBucketIds + ["bucket-unknown"]

    private static let epoch = Date(timeIntervalSince1970: 0)

    /// A small pool of creation instants so generated items frequently collide
    /// on `createdAt`, forcing the `id` tie-breaker to decide their order.
    private static let createdAtPool: [Date] = (0..<4).map { offset in
        Date(timeIntervalSince1970: TimeInterval(offset * 86_400))
    }

    // MARK: - Generators

    private static var statusGen: Gen<ActionStatus> {
        Gen.fromElements(of: ActionStatus.allCases)
    }

    private static var bucketIdGen: Gen<String> {
        Gen.fromElements(of: candidateBucketIds)
    }

    private static var createdAtGen: Gen<Date> {
        Gen.fromElements(of: createdAtPool)
    }

    /// A single item with a placeholder id (reassigned uniquely per list), a
    /// pooled bucket id, a pooled `createdAt` (to force ties), and a random
    /// status. Other fields are fixed since the ordering rule ignores them.
    private static var itemGen: Gen<ActionItem> {
        Gen.zip(bucketIdGen, createdAtGen, statusGen).map { bucketId, createdAt, status in
            makeItem(id: "tmp", bucketId: bucketId, createdAt: createdAt, status: status)
        }
    }

    /// A bounded-length list of items with distinct ids so the `id` tie-breaker
    /// is a total order over the list.
    private static var itemListGen: Gen<[ActionItem]> {
        Gen<Int>.fromElements(in: 0...12).flatMap { count in
            Gen.sequence(Array(repeating: itemGen, count: count))
        }.map(assignUniqueIds)
    }

    /// The known buckets, in a fixed order. Group ordering is out of scope for
    /// Property 9 (that is Property 8); we only assert intra-group ordering.
    private static var bucketsGen: Gen<[Bucket]> {
        Gen.pure(knownBucketIds.map { makeBucket(id: $0) })
    }

    // MARK: - Helpers

    private static func makeItem(
        id: String,
        bucketId: String,
        createdAt: Date,
        status: ActionStatus
    ) -> ActionItem {
        ActionItem(
            id: id,
            accountId: accountId,
            bucketId: bucketId,
            title: "Item \(id)",
            contentType: .text,
            timeframe: .today,
            status: status,
            createdAt: createdAt,
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false)
        )
    }

    private static func makeBucket(id: String) -> Bucket {
        Bucket(
            id: id,
            accountId: accountId,
            name: id,
            notStartedColor: "#100000",
            inProgressColor: "#001000",
            completedColor: "#000010",
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false)
        )
    }

    /// A random permutation of `xs`, driven entirely by SwiftCheck so shrinking
    /// stays deterministic: each element is paired with a random sort key and the
    /// list is reordered by that key (offset breaks key ties, so it is total).
    private static func permutationGen<A>(of xs: [A]) -> Gen<[A]> {
        Gen.sequence(Array(repeating: Gen<Int>.fromElements(in: 0...10_000), count: xs.count))
            .map { keys in
                zip(xs, keys).enumerated()
                    .sorted { lhs, rhs in
                        lhs.element.1 != rhs.element.1
                            ? lhs.element.1 < rhs.element.1
                            : lhs.offset < rhs.offset
                    }
                    .map { $0.element.0 }
            }
    }

    /// Re-keys items with unique, stable, lexicographically meaningful ids so the
    /// `id` tie-breaker is total and the test never collides ids by accident.
    /// Zero-padded so string ordering is well-defined across the list.
    private static func assignUniqueIds(_ items: [ActionItem]) -> [ActionItem] {
        items.enumerated().map { index, item in
            var copy = item
            copy.id = String(format: "item-%03d", index)
            return copy
        }
    }

    /// True iff `items` are in non-decreasing board order: each consecutive pair
    /// `(a, b)` satisfies `a.createdAt < b.createdAt`, or — on a `createdAt`
    /// tie — `a.id < b.id`. Written directly from the Property 9 statement, not
    /// derived from the implementation.
    private static func isInBoardOrder(_ items: [ActionItem]) -> Bool {
        zip(items, items.dropFirst()).allSatisfy { a, b in
            (a.createdAt < b.createdAt) || (a.createdAt == b.createdAt && a.id < b.id)
        }
    }

    // MARK: - Property 9: every group is ordered by (createdAt, id) (Req 8.1)

    /// For arbitrary items (with frequent `createdAt` ties on differing ids) and
    /// the known buckets, every group emitted by `buildBoard` has its items in
    /// ascending `createdAt`, then ascending `id`, order — including the
    /// synthetic placeholder group for unknown bucket ids.
    func testEachGroupIsOrderedByCreatedAtThenId() {
        property("each board group ordered by (createdAt, id) (Property 9, Req 8.1)",
                 arguments: Self.checkArgs)
            <- forAll(Gen.zip(Self.itemListGen, Self.bucketsGen)) { (items: [ActionItem], buckets: [Bucket]) in
                let board = Domain.buildBoard(items: items, buckets: buckets)
                return board.groups.allSatisfy { group in
                    Self.isInBoardOrder(group.items.map(\.item))
                }
            }
    }

    // MARK: - Property 9: order is independent of input order (Req 8.1)

    /// Board ordering is total and deterministic: shuffling the input items does
    /// not change any group's resulting item order. We compare the per-group
    /// sequences of item ids between the original and a shuffled input.
    func testOrderIsIndependentOfInputOrder() {
        let scenarioGen = Self.itemListGen.flatMap { items in
            // A permutation of the same items, so any input-order dependence in
            // the aggregation would surface as a differing per-group id order.
            Self.permutationGen(of: items).map { (items, $0) }
        }

        property("board order independent of input order (Property 9, Req 8.1)",
                 arguments: Self.checkArgs)
            <- forAll(scenarioGen) { (original: [ActionItem], shuffled: [ActionItem]) in
                let buckets = Self.knownBucketIds.map(Self.makeBucket(id:))
                let boardA = Domain.buildBoard(items: original, buckets: buckets)
                let boardB = Domain.buildBoard(items: shuffled, buckets: buckets)

                // Compare group-by-bucket-id item orderings (group order itself
                // can differ for unknown buckets by first-seen rule, so key by
                // bucket id rather than position).
                let orderA = Self.itemIdsByBucket(boardA)
                let orderB = Self.itemIdsByBucket(boardB)
                return orderA == orderB
            }
    }

    /// Maps each group's bucket id to its ordered list of item ids.
    private static func itemIdsByBucket(_ board: BoardState) -> [String: [String]] {
        var result: [String: [String]] = [:]
        for group in board.groups {
            result[group.bucket.id] = group.items.map { $0.item.id }
        }
        return result
    }
}
