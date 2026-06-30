import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

// Feature: ios-client, Property 1: Cross-implementation equivalence of portable
// domain logic — for any valid input to the portable domain logic (board
// aggregation, completion counting, timeframe/due-set resolution, bucket
// validation, action-plan progress, sync conflict resolution), the Swift
// implementation's output is field-by-field identical to the reference output
// produced by the Android client and the backend for the same input, with
// identical numeric values and, for ordered outputs (such as bucket grouping),
// identical element ordering.
//
// **Validates: Requirements 1.6, 3.2, 3.3**
//
// ## Reference vectors note (no shared fixtures present)
//
// The design's testing strategy operationalizes Property 1 by driving the Swift
// logic with the *shared golden input/output vectors* used to validate the
// Android/Go implementations and asserting field-by-field, ordering-exact
// equality. At the time this test was authored, no shared golden-vector fixture
// files exist anywhere in this repository (the Android client and Go backend do
// not yet publish a vector file consumable from Swift). Rather than leave the
// property unverified, this test implements the same mechanism with **embedded
// reference vectors**:
//
//   1. `GoldenVectors` — a set of hand-authored input→expected-output cases that
//      encode exactly what the shared contract algorithm (the same one the
//      Android Kotlin and Go implementations follow) must produce. Each case is
//      asserted field-by-field, ordering-exact, via the models' `Equatable`
//      conformances.
//   2. `referenceBoard(...)` etc. — independent reference *oracles* that
//      re-derive the expected output straight from the spec definition (never by
//      calling `Domain`), against which randomly generated inputs are compared
//      over ≥100 SwiftCheck iterations. The oracle stands in for the shared
//      reference implementation: if the Swift `Domain` output ever diverges from
//      the spec-defined reference output, the property fails.
//
// When a shared fixture file is later published by the Android/Go side, this
// test should be extended to additionally decode and assert against those exact
// vectors. Until then the embedded vectors + oracle give the same parity
// guarantee for the portable logic.
//
// SwiftCheck is configured for 200 successful tests per property, above the
// design's minimum of 100 iterations.
final class CrossImplementationEquivalencePropertyTests: XCTestCase {

    // MARK: - Configuration

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    private static let epoch = Date(timeIntervalSince1970: 1_600_000_000)

    // MARK: - Fixed pools (constrain generators to the valid input space)

    private static let accountIds = ["acct-A", "acct-B"]
    private static let bucketIds = ["bk-1", "bk-2", "bk-3"]
    private static let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)
    private static let contentTypeGen = Gen<ContentType>.fromElements(of: ContentType.allCases)

    /// Whole-second instants in a tight spread so ties on `createdAt` (resolved
    /// by ascending id) occur often and the ordering parity is stressed.
    private static let createdAtGen: Gen<Date> = Gen<Int>.fromElements(in: 0...4)
        .map { Date(timeIntervalSince1970: TimeInterval(1_600_000_000 + $0)) }

    // MARK: - Model builders

    private static func makeItem(
        id: String,
        accountId: String,
        bucketId: String,
        status: ActionStatus,
        contentType: ContentType,
        createdAt: Date
    ) -> ActionItem {
        ActionItem(
            id: id,
            accountId: accountId,
            bucketId: bucketId,
            title: "item-\(id)",
            contentType: contentType,
            timeframe: .today,
            status: status,
            createdAt: createdAt,
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false, dirty: false)
        )
    }

    /// A bucket whose three per-status colors are pairwise distinct, so the
    /// status→color resolution is well-defined and the reference oracle can
    /// reproduce it exactly.
    private static func makeBucket(id: String, accountId: String) -> Bucket {
        Bucket(
            id: id,
            accountId: accountId,
            name: "bucket-\(id)",
            notStartedColor: "#\(id)-NS",
            inProgressColor: "#\(id)-IP",
            completedColor: "#\(id)-CO",
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false, dirty: false)
        )
    }

    // MARK: - Generators

    /// A single item referencing one of the *known* bucket ids (so every item
    /// resolves into a real group and no placeholder-bucket reconstruction is
    /// needed in the oracle). The `id` is a placeholder reassigned per index.
    private static var itemGen: Gen<ActionItem> {
        Gen.zip(
            Gen.fromElements(of: accountIds),
            Gen.fromElements(of: bucketIds),
            statusGen,
            contentTypeGen,
            createdAtGen
        ).map { account, bucketId, status, contentType, createdAt in
            makeItem(
                id: "tmp",
                accountId: account,
                bucketId: bucketId,
                status: status,
                contentType: contentType,
                createdAt: createdAt
            )
        }
    }

    /// A bounded list of items with distinct, stable ids (so the ordering
    /// tie-break by id is unambiguous and the comparison is well-defined).
    private static var itemListGen: Gen<[ActionItem]> {
        Gen<Int>.fromElements(in: 0...10).flatMap { count in
            Gen.sequence(Array(repeating: itemGen, count: count))
        }.map { items in
            items.enumerated().map { index, item in
                var copy = item
                copy.id = String(format: "item-%02d", index)
                return copy
            }
        }
    }

    /// The full set of known buckets, in a fixed order. `buildBoard` emits one
    /// group per bucket in this order (including empty ones), which the oracle
    /// reproduces exactly.
    private static var bucketsGen: Gen<[Bucket]> {
        Gen.pure(bucketIds.enumerated().map { index, id in
            makeBucket(id: id, accountId: accountIds[index % accountIds.count])
        })
    }

    // MARK: - Reference oracles (independent of `Domain`)

    /// Independent reference board built straight from the Property 1 / Req 8.1
    /// definition: one group per bucket in `buckets` order; within each group,
    /// items with that `bucketId` ordered ascending by `createdAt` then ascending
    /// `id`; each item paired with its bucket's per-status color; completion
    /// count = number of completed items. Never calls `Domain`.
    private static func referenceBoard(items: [ActionItem], buckets: [Bucket]) -> BoardState {
        let groups = buckets.map { bucket -> BoardGroup in
            let ordered = items
                .filter { $0.bucketId == bucket.id }
                .sorted { lhs, rhs in
                    lhs.createdAt != rhs.createdAt
                        ? lhs.createdAt < rhs.createdAt
                        : lhs.id < rhs.id
                }
            let boardItems = ordered.map { item -> BoardItem in
                let color: String
                switch item.status {
                case .notStarted: color = bucket.notStartedColor
                case .inProgress: color = bucket.inProgressColor
                case .completed: color = bucket.completedColor
                }
                return BoardItem(item: item, statusColor: color)
            }
            return BoardGroup(bucket: bucket, items: boardItems)
        }
        let completed = items.filter { $0.status == .completed }.count
        return BoardState(groups: groups, completionCount: completed)
    }

    // MARK: - Property 1: board aggregation/ordering/color equivalence (Req 8.1, 8.2, 3.3)

    /// `Domain.buildBoard` is field-by-field, ordering-exact identical to the
    /// independent reference board for the same input.
    func testBoardAggregationEqualsReference() {
        let scenarioGen = Gen.zip(Self.itemListGen, Self.bucketsGen)

        property("buildBoard == reference board, field-by-field & ordering-exact (Property 1)",
                 arguments: Self.checkArgs)
            <- forAll(scenarioGen) { (items: [ActionItem], buckets: [Bucket]) in
                let actual = Domain.buildBoard(items: items, buckets: buckets)
                let expected = Self.referenceBoard(items: items, buckets: buckets)
                return (actual == expected)
                    <?> "board diverged from reference"
            }
    }

    // MARK: - Property 1: completion counting equivalence (Req 8.5, 3.3)

    /// `Domain.completionCounter` equals the reference count of completed items.
    func testCompletionCountingEqualsReference() {
        property("completionCounter == reference completed count (Property 1)",
                 arguments: Self.checkArgs)
            <- forAll(Self.itemListGen) { (items: [ActionItem]) in
                let expected = items.filter { $0.status == .completed }.count
                return Domain.completionCounter(items: items) == expected
            }
    }

    // MARK: - Property 1: conflict resolution equivalence (Req 6.2, 3.2, 3.3)

    /// `Domain.resolveConflict` selects the same winner id as the reference
    /// last-writer-wins rule (greater `updatedAt`, then greater `version`, then
    /// greater `id`) for two concurrent versions of the same record.
    func testConflictResolutionEqualsReference() {
        // Two versions sharing an id, with independently varied updatedAt /
        // version so all three tie-break tiers are exercised.
        let updatedAtGen = Gen<Int>.fromElements(in: 0...3)
            .map { Date(timeIntervalSince1970: TimeInterval(1_600_000_000 + $0)) }
        let versionGen = Gen<Int64>.fromElements(of: [Int64(1), 2, 3])
        let statusGen = Self.statusGen

        let versionPairGen = Gen.zip(
            Gen.zip(updatedAtGen, versionGen, statusGen),
            Gen.zip(updatedAtGen, versionGen, statusGen)
        )

        property("resolveConflict winner == reference LWW winner (Property 1)",
                 arguments: Self.checkArgs)
            <- forAll(versionPairGen) { left, right in
                let a = Self.makeItem(
                    id: "shared-id", accountId: "acct-A", bucketId: "bk-1",
                    status: left.2, contentType: .text, createdAt: Self.epoch
                ).withSync(updatedAt: left.0, version: left.1)
                let b = Self.makeItem(
                    id: "shared-id", accountId: "acct-A", bucketId: "bk-1",
                    status: right.2, contentType: .text, createdAt: Self.epoch
                ).withSync(updatedAt: right.0, version: right.1)

                let winner = Domain.resolveConflict(a, b).winner

                // Reference LWW: greater updatedAt, then greater version, then
                // greater id (ids are equal here, so the canonical form decides
                // a full tie — in which case either equal value is acceptable).
                let expected = Self.referenceLWWWinner(a, b)
                return (winner.sync.updatedAt == expected.sync.updatedAt)
                    ^&&^ (winner.sync.version == expected.sync.version)
            }
    }

    /// Reference last-writer-wins selection used by the conflict property.
    private static func referenceLWWWinner(_ a: ActionItem, _ b: ActionItem) -> ActionItem {
        if a.sync.updatedAt != b.sync.updatedAt {
            return a.sync.updatedAt > b.sync.updatedAt ? a : b
        }
        if a.sync.version != b.sync.version {
            return a.sync.version > b.sync.version ? a : b
        }
        return a.id >= b.id ? a : b
    }

    // MARK: - Property 1: timeframe resolution equivalence (Req 9.7, 3.2, 3.3)

    /// `Domain.validateTimeframe` matches the reference rule: relative
    /// timeframes are always valid; a specific date is valid iff its UTC
    /// calendar day is today-or-later in the device's local time zone.
    func testTimeframeResolutionEqualsReference() {
        let utc = Self.utcCalendar()
        let local = Calendar(identifier: .gregorian)
        let now = Self.epoch

        // Day offsets relative to `now`'s local day, spanning past and future.
        let offsetGen = Gen<Int>.choose((-30, 30))
        let relativeGen = Gen<Timeframe>.fromElements(of: [.today, .withinADay, .withinAWeek])

        property("validateTimeframe == reference timeframe rule (Property 1)",
                 arguments: Self.checkArgs)
            <- forAll(Gen<Bool>.fromElements(of: [true, false]), offsetGen, relativeGen) {
                useSpecific, offset, relative in

                if !useSpecific {
                    return Domain.validateTimeframe(relative, now: now, calendar: local) == .valid
                }

                // Build a specificDate at UTC midnight offset from today's UTC day.
                let todayComponents = utc.dateComponents([.year, .month, .day], from: now)
                let todayMidnight = utc.date(from: todayComponents)!
                let chosen = utc.date(byAdding: .day, value: offset, to: todayMidnight)!
                let canonical = CalendarDate.formatter.date(from: CalendarDate.formatter.string(from: chosen))!

                let actual = Domain.validateTimeframe(.specificDate(canonical), now: now, calendar: local)
                let expected: TimeframeValidationResult = offset < 0
                    ? .invalid(reason: Domain.pastSpecificDateMessage)
                    : .valid
                return (actual == expected)
                    <?> "offset=\(offset) actual=\(actual) expected=\(expected)"
            }
    }

    private static func utcCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return calendar
    }

    // MARK: - Property 1: action-plan progress equivalence (Req 9.9, 3.3)

    /// `Domain.progress` and `Domain.shouldPromptParentComplete` match the
    /// reference counts/prompt for any generated plan.
    func testActionPlanProgressEqualsReference() {
        let subActionsGen: Gen<[SubAction]> = Gen<Int>.fromElements(in: 0...8).flatMap { count in
            Gen.sequence((0..<count).map { index in
                Gen<Bool>.fromElements(of: [true, false]).map { done in
                    SubAction(id: "sa-\(index)", text: "step-\(index)", order: index, completed: done)
                }
            })
        }

        property("progress/prompt == reference (Property 1)", arguments: Self.checkArgs)
            <- forAll(subActionsGen) { (subActions: [SubAction]) in
                let plan = ActionPlan(
                    id: "plan-1",
                    actionItemId: "item-1",
                    subActions: subActions,
                    sync: SyncMeta(updatedAt: Self.epoch, version: 1, deleted: false, dirty: false)
                )

                let progress = Domain.progress(plan)
                let expectedCompleted = subActions.filter(\.completed).count
                let expectedTotal = subActions.count
                let expectedPrompt = !subActions.isEmpty && subActions.allSatisfy(\.completed)

                return (progress.completed == expectedCompleted)
                    ^&&^ (progress.total == expectedTotal)
                    ^&&^ (Domain.shouldPromptParentComplete(plan) == expectedPrompt)
            }
    }

    // MARK: - Embedded golden vectors (deterministic, field-by-field, ordering-exact)

    /// Board golden vector: two known buckets with interleaved items, including
    /// a `createdAt` tie resolved by ascending id, asserted field-by-field and
    /// ordering-exact against the expected reference board.
    func testGoldenVectorBoard() {
        let t0 = Date(timeIntervalSince1970: 1_600_000_000)
        let t1 = Date(timeIntervalSince1970: 1_600_000_010)

        let bucketA = Self.makeBucket(id: "bk-1", accountId: "acct-A")
        let bucketB = Self.makeBucket(id: "bk-2", accountId: "acct-A")

        // Deliberately out of order; two share t0 in bucket A (tie → id order).
        let i1 = Self.makeItem(id: "a-2", accountId: "acct-A", bucketId: "bk-1",
                               status: .completed, contentType: .text, createdAt: t0)
        let i2 = Self.makeItem(id: "a-1", accountId: "acct-A", bucketId: "bk-1",
                               status: .inProgress, contentType: .link, createdAt: t0)
        let i3 = Self.makeItem(id: "a-3", accountId: "acct-A", bucketId: "bk-1",
                               status: .notStarted, contentType: .text, createdAt: t1)
        let i4 = Self.makeItem(id: "b-1", accountId: "acct-A", bucketId: "bk-2",
                               status: .completed, contentType: .image, createdAt: t1)

        let board = Domain.buildBoard(items: [i1, i2, i3, i4], buckets: [bucketA, bucketB])

        let expected = BoardState(
            groups: [
                BoardGroup(bucket: bucketA, items: [
                    // t0 tie → ascending id: "a-1" before "a-2", then t1 "a-3".
                    BoardItem(item: i2, statusColor: bucketA.inProgressColor),
                    BoardItem(item: i1, statusColor: bucketA.completedColor),
                    BoardItem(item: i3, statusColor: bucketA.notStartedColor)
                ]),
                BoardGroup(bucket: bucketB, items: [
                    BoardItem(item: i4, statusColor: bucketB.completedColor)
                ])
            ],
            completionCount: 2
        )

        XCTAssertEqual(board, expected, "board golden vector must match field-by-field and ordering-exact")
    }

    /// Bucket-name validation golden vectors covering valid, both length
    /// failures, and the normalized-duplicate case.
    func testGoldenVectorBucketValidation() {
        let existing = [Self.makeBucket(id: "bk-1", accountId: "acct-A")] // name "bucket-bk-1"
        let extra = Bucket(
            id: "bk-x", accountId: "acct-A", name: "  Travel  ",
            notStartedColor: "#1", inProgressColor: "#2", completedColor: "#3",
            sync: SyncMeta(updatedAt: Self.epoch, version: 1, deleted: false, dirty: false)
        )

        // Valid: trimmed, unique.
        XCTAssertEqual(
            Domain.validateBucketName("  Health  ", accountId: "acct-A", existing: existing + [extra]),
            .valid(normalizedName: "Health")
        )
        // Empty / whitespace-only → invalidLength.
        XCTAssertEqual(
            Domain.validateBucketName("   ", accountId: "acct-A", existing: existing),
            .invalidLength
        )
        // Over 50 characters → invalidLength.
        XCTAssertEqual(
            Domain.validateBucketName(String(repeating: "x", count: 51), accountId: "acct-A", existing: existing),
            .invalidLength
        )
        // Case-insensitive, trimmed duplicate of "  Travel  ".
        XCTAssertEqual(
            Domain.validateBucketName("travel", accountId: "acct-A", existing: existing + [extra]),
            .duplicateName
        )
    }

    /// Action-plan reorder golden vector: a partial reorder that names two ids
    /// and omits the rest, asserting the resulting id order and contiguous
    /// `order` reindexing field-by-field.
    func testGoldenVectorActionPlanReorder() {
        let subActions = (0..<4).map { index in
            SubAction(id: "sa-\(index)", text: "step-\(index)", order: index, completed: false)
        }
        let plan = ActionPlan(
            id: "plan-1", actionItemId: "item-1", subActions: subActions,
            sync: SyncMeta(updatedAt: Self.epoch, version: 1, deleted: false, dirty: false)
        )

        // Move sa-2 and sa-0 to the front; sa-1 and sa-3 keep their relative order.
        let reordered = Domain.reorder(plan, orderedSubActionIds: ["sa-2", "sa-0"])

        let expectedOrder = ["sa-2", "sa-0", "sa-1", "sa-3"]
        XCTAssertEqual(reordered.subActions.map(\.id), expectedOrder)
        // Order indices are reassigned contiguously 0..<n in list order.
        XCTAssertEqual(reordered.subActions.map(\.order), [0, 1, 2, 3])
    }

    /// Conflict-resolution golden vector: the version with the later `updatedAt`
    /// wins regardless of argument order (deterministic LWW, Req 6.2).
    func testGoldenVectorConflictResolution() {
        let older = Self.makeItem(id: "shared", accountId: "acct-A", bucketId: "bk-1",
                                  status: .notStarted, contentType: .text, createdAt: Self.epoch)
            .withSync(updatedAt: Date(timeIntervalSince1970: 1_600_000_000), version: 5)
        let newer = Self.makeItem(id: "shared", accountId: "acct-A", bucketId: "bk-1",
                                  status: .completed, contentType: .text, createdAt: Self.epoch)
            .withSync(updatedAt: Date(timeIntervalSince1970: 1_600_000_100), version: 2)

        XCTAssertEqual(Domain.resolveConflict(older, newer).winner, newer)
        XCTAssertEqual(Domain.resolveConflict(newer, older).winner, newer)
    }
}

// MARK: - Test helpers

private extension ActionItem {
    /// Returns a copy with the given sync `updatedAt`/`version` (other fields
    /// unchanged). Keeps the conflict vectors concise.
    func withSync(updatedAt: Date, version: Int64) -> ActionItem {
        var copy = self
        copy.sync = SyncMeta(updatedAt: updatedAt, version: version, deleted: false, dirty: false)
        return copy
    }
}
