import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Reused Property 10 — Status indicator color
/// always matches current status** (iOS design "Reused properties" table;
/// sibling `action-tracker-app` Property 10).
///
/// **Validates: Requirements 8.2, 8.3**
///
/// Property 10 statement (as it applies to the iOS Swift domain logic): for any
/// `Action_Item` in any `Bucket`, the resolved status indicator color equals
/// the bucket's configured color field for the item's **current**
/// `Action_Status` —
///
/// - ``ActionStatus/notStarted`` → ``Bucket/notStartedColor``
/// - ``ActionStatus/inProgress`` → ``Bucket/inProgressColor``
/// - ``ActionStatus/completed`` → ``Bucket/completedColor``
///
/// and, after an item's status changes and the board is rebuilt, the resolved
/// ``BoardItem/statusColor`` matches the **new** status's color (Req 8.3 — the
/// indicator tracks the current status).
///
/// Subjects under test:
///   * ``Domain/statusColor(for:in:)`` — the pure status→color resolver.
///   * ``Domain/buildBoard(items:buckets:)`` — wires the resolved color into
///     each ``BoardItem`` at aggregation time.
///
/// ## Generator notes (constraining to the valid input space)
///
/// Buckets are generated with **independently random** per-status color strings
/// drawn from a small pool, so the three fields are frequently distinct and
/// sometimes collide — Property 10 must hold either way, since it concerns the
/// color *matching the current status*, not the injectivity of the map (that is
/// Property 17 / task 4.11). Items reference a generated bucket's id (so the
/// board groups them under a known bucket) and carry a random status, so every
/// status branch of the resolver is exercised across many inputs.
///
/// An **independent oracle** (`expectedColor(for:in:)`) restates the mapping
/// directly from the property statement rather than calling the implementation,
/// so the tests do not merely mirror the code under test.
///
/// SwiftCheck is configured for **200 successful tests** per property, above the
/// design's minimum of 100 iterations.
final class StatusColorMatchingPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    // MARK: - Fixed pools

    private static let accountIds = ["acct-A", "acct-B", "acct-C"]

    /// A small pool of color strings. Small enough that independently chosen
    /// per-status fields frequently coincide (non-injective buckets) and
    /// frequently differ (injective buckets), exercising both shapes.
    private static let colors = [
        "#FF0000", "#00FF00", "#0000FF", "#FFAA00",
        "#123456", "#ABCDEF", "#000000", "#FFFFFF", "red", "green"
    ]

    private static let epoch = Date(timeIntervalSince1970: 0)

    // MARK: - Independent oracle

    /// Restates the status→color mapping directly from the Property 10
    /// statement, independently of ``Domain/statusColor(for:in:)``.
    private static func expectedColor(for status: ActionStatus, in bucket: Bucket) -> String {
        switch status {
        case .notStarted: return bucket.notStartedColor
        case .inProgress: return bucket.inProgressColor
        case .completed: return bucket.completedColor
        }
    }

    // MARK: - Generators

    private static let colorGen = Gen<String>.fromElements(of: colors)
    private static let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)
    private static let accountGen = Gen<String>.fromElements(of: accountIds)

    /// A bucket with independently random per-status colors and a placeholder
    /// id (callers assign unique ids when building lists).
    private static var bucketGen: Gen<Bucket> {
        Gen.compose { c in
            makeBucket(
                id: "tmp",
                account: c.generate(using: accountGen),
                notStarted: c.generate(using: colorGen),
                inProgress: c.generate(using: colorGen),
                completed: c.generate(using: colorGen)
            )
        }
    }

    /// Whole-second instants so ordering is well-defined; the exact value is
    /// irrelevant to color resolution.
    private static let createdAtGen: Gen<Date> = Gen<Int>
        .choose((1_600_000_000, 1_800_000_000))
        .map { Date(timeIntervalSince1970: TimeInterval($0)) }

    // MARK: - Helpers

    private static func makeBucket(
        id: String,
        account: String,
        notStarted: String,
        inProgress: String,
        completed: String
    ) -> Bucket {
        Bucket(
            id: id,
            accountId: account,
            name: "bucket-\(id)",
            notStartedColor: notStarted,
            inProgressColor: inProgress,
            completedColor: completed,
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false)
        )
    }

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
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false)
        )
    }

    /// A scenario: a non-empty list of buckets with unique ids, and a list of
    /// items each assigned to one of those buckets, with random statuses. Item
    /// `id`s are unique so a rebuild can address them deterministically.
    private static var scenarioGen: Gen<(buckets: [Bucket], items: [ActionItem])> {
        Gen<Int>.choose((1, 5)).flatMap { bucketCount in
            Gen.sequence(Array(repeating: bucketGen, count: bucketCount))
                .map(assignUniqueBucketIds)
                .flatMap { buckets in
                    let bucketIds = buckets.map(\.id)
                    let itemGen: Gen<ActionItem> = Gen.compose { c in
                        makeItem(
                            id: "tmp",
                            account: c.generate(using: accountGen),
                            bucketId: c.generate(using: Gen.fromElements(of: bucketIds)),
                            status: c.generate(using: statusGen),
                            createdAt: c.generate(using: createdAtGen)
                        )
                    }
                    return Gen<Int>.choose((0, 12)).flatMap { itemCount in
                        Gen.sequence(Array(repeating: itemGen, count: itemCount))
                    }.map { (buckets: buckets, items: assignUniqueItemIds($0)) }
                }
        }
    }

    private static func assignUniqueBucketIds(_ buckets: [Bucket]) -> [Bucket] {
        buckets.enumerated().map { index, bucket in
            var copy = bucket
            copy.id = "b-\(index)"
            return copy
        }
    }

    private static func assignUniqueItemIds(_ items: [ActionItem]) -> [ActionItem] {
        items.enumerated().map { index, item in
            var copy = item
            copy.id = "i-\(index)"
            return copy
        }
    }

    // MARK: - Property 10: resolver matches the current status's field (Req 8.2, 8.3)

    /// For any bucket and any status, ``Domain/statusColor(for:in:)`` returns
    /// exactly the bucket's configured color field for that status.
    func testStatusColorMatchesBucketFieldForCurrentStatus() {
        property("statusColor(for:in:) == bucket field for the status (Property 10, Req 8.2/8.3)",
                 arguments: Self.checkArgs)
            <- forAll(Self.bucketGen, Self.statusGen) { (bucket: Bucket, status: ActionStatus) in
                let resolved = Domain.statusColor(for: status, in: bucket)
                let expected = Self.expectedColor(for: status, in: bucket)
                return (resolved == expected)
                    <?> "status=\(status) bucket=\(bucket.id) resolved=\(resolved) expected=\(expected)"
            }
    }

    // MARK: - Property 10: every BoardItem color matches its item's status (Req 8.2, 8.3)

    /// After ``Domain/buildBoard(items:buckets:)``, every ``BoardItem`` carries
    /// the color resolved for its item's current status within the item's group
    /// bucket — equal both to ``Domain/statusColor(for:in:)`` for that bucket
    /// and to the independent field oracle.
    func testBoardItemColorsMatchCurrentStatus() {
        property("buildBoard color == current status's bucket field (Property 10, Req 8.2/8.3)",
                 arguments: Self.checkArgs)
            <- forAll(Self.scenarioGen) { scenario in
                let board = Domain.buildBoard(items: scenario.items, buckets: scenario.buckets)
                return board.groups.allSatisfy { group in
                    group.items.allSatisfy { boardItem in
                        let expected = Self.expectedColor(for: boardItem.item.status, in: group.bucket)
                        let viaResolver = Domain.statusColor(for: boardItem.item.status, in: group.bucket)
                        return boardItem.statusColor == expected && boardItem.statusColor == viaResolver
                    }
                }
            }
    }

    // MARK: - Property 10: color tracks the NEW status after a change + rebuild (Req 8.3)

    /// Changing items' statuses and rebuilding the board yields ``BoardItem``
    /// colors that match each item's **new** status — the indicator always
    /// follows the current status, never a stale one.
    func testRebuildAfterStatusChangeUpdatesColor() {
        // Pair each scenario with a fresh random status per item to mutate to.
        let mutationGen: Gen<(buckets: [Bucket], items: [ActionItem], newStatuses: [ActionStatus])> =
            Self.scenarioGen.flatMap { scenario in
                Gen.sequence(Array(repeating: Self.statusGen, count: scenario.items.count))
                    .map { (buckets: scenario.buckets, items: scenario.items, newStatuses: $0) }
            }

        property("color tracks new status after change + rebuild (Property 10, Req 8.3)",
                 arguments: Self.checkArgs)
            <- forAll(mutationGen) { scenario in
                // Apply the new status to each item, preserving identity/bucket.
                let mutated = zip(scenario.items, scenario.newStatuses).map { item, newStatus -> ActionItem in
                    var copy = item
                    copy.status = newStatus
                    return copy
                }
                let board = Domain.buildBoard(items: mutated, buckets: scenario.buckets)
                return board.groups.allSatisfy { group in
                    group.items.allSatisfy { boardItem in
                        let expected = Self.expectedColor(for: boardItem.item.status, in: group.bucket)
                        return boardItem.statusColor == expected
                    }
                }
            }
    }
}
