import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Reused Property 32 — Conflict resolution is
/// deterministic last-writer-wins** (iOS design "Reused properties" table;
/// sibling `action-tracker-app` Property 32).
///
/// **Validates: Requirements 6.2**
///
/// Property 32 statement (as it applies to the iOS Swift domain logic): when a
/// sync round trip surfaces two concurrent versions of the same record, the
/// version with the greater `SyncMeta.updatedAt` is kept (last-writer-wins),
/// and WHERE two conflicting records share the same update time the tie is
/// broken deterministically by record identifier (Req 6.2). Resolution is:
///
///   1. **Deterministic** — the same pair always resolves to the same winner.
///   2. **Order-independent (commutative)** — `resolve(a, b)` and `resolve(b, a)`
///      produce the same winner/loser values.
///   3. **Last-writer-wins** — the winner is the version with the maximum
///      `updatedAt`; ties fall through a fixed total order:
///      `updatedAt` → `version` → `id` (Req 6.2) → `canonicalForm`.
///   4. **Total / partitioning** — the winner and loser are exactly the two
///      inputs; nothing is fabricated or dropped.
///
/// Subject under test (`Sources/SideQuestKit/Domain/ConflictResolution.swift`):
///   * ``Domain/resolveConflict(_:_:)`` — the generic resolver.
///   * ``Domain/resolveActionItem(_:_:)`` / ``Domain/resolveBucket(_:_:)`` —
///     named conveniences that must equal the generic resolver.
///
/// The generators draw `updatedAt`, `version`, `id`, and content fields from
/// small pools so ties on each key occur frequently, exercising every level of
/// the tie-break order. A dedicated generator produces *concurrent versions of
/// the same record* (shared `id`) for the realistic LWW case, and another
/// produces same-instant/same-version pairs with **distinct ids** to exercise
/// the Req 6.2 identifier tie-break directly. Each property runs ≥100 iterations
/// (the design mandates a minimum of 100; we configure 200 for extra coverage).
final class ConflictResolutionPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    // MARK: - Fixed pools (small, so ties on each resolution key occur often)

    private static let accountId = "acct-conflict"
    private static let bucketId = "bucket-conflict"
    private static let epoch = Date(timeIntervalSince1970: 0)

    /// A handful of record ids so general pairs frequently collide (forcing the
    /// later tie-breakers) and frequently differ (exercising the id tie-break).
    private static let idPool = ["rec-1", "rec-2", "rec-3"]

    /// A small pool of update instants so `updatedAt` ties — the trigger for the
    /// Req 6.2 identifier tie-break — occur frequently.
    private static let updatedAtPool: [Date] = (0..<4).map { offset in
        Date(timeIntervalSince1970: TimeInterval(offset * 3_600))
    }

    /// A small version pool so `version` ties occur frequently.
    private static let versionPool: [Int64] = [0, 1, 2, 3]

    /// Distinct titles so two otherwise-tied versions differ in content,
    /// exercising the final `canonicalForm` tie-breaker.
    private static let titlePool = ["Alpha", "Beta", "Gamma"]

    // MARK: - Generators

    private static var statusGen: Gen<ActionStatus> {
        Gen.fromElements(of: ActionStatus.allCases)
    }

    private static var versionGen: Gen<Int64> {
        Gen.fromElements(of: versionPool)
    }

    private static var titleGen: Gen<String> {
        Gen.fromElements(of: titlePool)
    }

    /// One resolution "facet": the fields that can differ between two concurrent
    /// versions of the same record — `updatedAt`, `version`, `status`, `title`
    /// (content), and the tombstone flag. Built from nested 2-/3-argument zips
    /// (the arities the existing suite relies on) and flattened to a 5-tuple.
    private static var facetGen: Gen<(Date, Int64, ActionStatus, String, Bool)> {
        Gen.zip(
            Gen.zip(Gen.fromElements(of: updatedAtPool), versionGen, statusGen),
            Gen.zip(titleGen, Gen<Bool>.fromElements(of: [true, false]))
        ).map { keys, content in
            (keys.0, keys.1, keys.2, content.0, content.1)
        }
    }

    /// An arbitrary item drawn from the pools (id may differ between two such
    /// items, so this drives the general-pair / id-tie-break paths).
    private static var itemGen: Gen<ActionItem> {
        Gen.zip(Gen.fromElements(of: idPool), facetGen).map { id, facet in
            makeItem(id: id, facet: facet)
        }
    }

    /// A general pair of two independently generated items.
    private static var pairGen: Gen<(ActionItem, ActionItem)> {
        Gen.zip(itemGen, itemGen)
    }

    /// Two concurrent versions of the **same** record: they share an `id`
    /// (the realistic conflict scenario) but vary on the resolution facets.
    private static var concurrentVersionsGen: Gen<(ActionItem, ActionItem)> {
        Gen.zip(Gen.fromElements(of: idPool), facetGen, facetGen).map { sharedId, fa, fb in
            (makeItem(id: sharedId, facet: fa), makeItem(id: sharedId, facet: fb))
        }
    }

    /// Ordered, distinct id pairs used to exercise the Req 6.2 identifier
    /// tie-break (the two versions share `updatedAt` and `version` but differ
    /// only in `id`).
    private static let distinctIdPairs: [(String, String)] = [
        ("rec-1", "rec-2"), ("rec-2", "rec-1"),
        ("rec-1", "rec-3"), ("rec-3", "rec-1"),
        ("rec-2", "rec-3"), ("rec-3", "rec-2")
    ]

    /// A pair with identical `updatedAt` and `version` but distinct ids, so the
    /// record-identifier tie-break (Req 6.2) is the sole decider. Titles are held
    /// equal so `canonicalForm` cannot pre-empt the id comparison.
    private static var idTieBreakGen: Gen<(ActionItem, ActionItem)> {
        Gen.zip(
            Gen.fromElements(of: distinctIdPairs),
            Gen.zip(Gen.fromElements(of: updatedAtPool), versionGen, statusGen)
        ).map { idPair, keys in
            let facet = (keys.0, keys.1, keys.2, "Shared", false)
            return (makeItem(id: idPair.0, facet: facet), makeItem(id: idPair.1, facet: facet))
        }
    }

    // MARK: - Helpers

    private static func makeItem(
        id: String,
        facet: (Date, Int64, ActionStatus, String, Bool)
    ) -> ActionItem {
        ActionItem(
            id: id,
            accountId: accountId,
            bucketId: bucketId,
            title: facet.3,
            contentType: .text,
            timeframe: .today,
            status: facet.2,
            createdAt: epoch,
            sync: SyncMeta(updatedAt: facet.0, version: facet.1, deleted: facet.4)
        )
    }

    /// Tri-state oracle for which input the documented total order selects,
    /// expressed directly from the Property 32 / Req 6.2 statement (NOT from the
    /// implementation under test). Returns:
    ///   * `.a`   — `a` strictly wins on (`updatedAt`, then `version`, then `id`)
    ///   * `.b`   — `b` strictly wins on the same keys
    ///   * `.tie` — all three keys are equal; the final `canonicalForm`
    ///     tie-breaker (an implementation detail) decides, so identity is not
    ///     asserted in this case.
    private enum ExpectedWinner { case a, b, tie }

    private static func expectedWinner<T: SyncResolvable>(_ a: T, _ b: T) -> ExpectedWinner {
        if a.sync.updatedAt != b.sync.updatedAt {
            return a.sync.updatedAt > b.sync.updatedAt ? .a : .b
        }
        if a.sync.version != b.sync.version {
            return a.sync.version > b.sync.version ? .a : .b
        }
        if a.id != b.id {
            return a.id > b.id ? .a : .b
        }
        return .tie
    }

    /// True iff `result` partitions exactly the two inputs (nothing fabricated
    /// or dropped): winner/loser are `a` and `b` in some order.
    private static func partitionsInputs(
        _ result: Domain.Conflict<ActionItem>,
        _ a: ActionItem,
        _ b: ActionItem
    ) -> Bool {
        (result.winner == a && result.loser == b)
            || (result.winner == b && result.loser == a)
    }

    // MARK: - Property 32: winner follows the documented total order (Req 6.2)

    /// For arbitrary pairs (frequent ties on each key), `resolveConflict`
    /// partitions the inputs and selects the winner dictated by the
    /// `updatedAt` → `version` → `id` order. When all three keys tie, only the
    /// partition invariant is asserted (the `canonicalForm` decider is an
    /// implementation detail).
    func testWinnerFollowsDocumentedOrder() {
        property("resolveConflict winner follows (updatedAt, version, id) (Property 32, Req 6.2)",
                 arguments: Self.checkArgs)
            <- forAll(Self.pairGen) { (a: ActionItem, b: ActionItem) in
                let result = Domain.resolveConflict(a, b)
                guard Self.partitionsInputs(result, a, b) else {
                    return false <?> "winner/loser do not partition the inputs"
                }
                switch Self.expectedWinner(a, b) {
                case .a:
                    return (result.winner == a)
                        <?> "expected a to win; updatedAt/version/id favored a"
                case .b:
                    return (result.winner == b)
                        <?> "expected b to win; updatedAt/version/id favored b"
                case .tie:
                    // canonicalForm decides; partition already verified above.
                    return true <?> "full tie on (updatedAt, version, id)"
                }
            }
    }

    // MARK: - Property 32: last-writer-wins by updatedAt (Req 6.2)

    /// For two concurrent versions of the SAME record, the winner is the one
    /// with the maximum `updatedAt` — the core last-writer-wins guarantee. When
    /// the update instants differ, the strictly-later version wins; when equal,
    /// both share that instant so the winner still carries it.
    func testLastWriterWinsByUpdatedAt() {
        property("winner has the maximum updatedAt (Property 32, Req 6.2)",
                 arguments: Self.checkArgs)
            <- forAll(Self.concurrentVersionsGen) { (a: ActionItem, b: ActionItem) in
                let result = Domain.resolveActionItem(a, b)
                let maxUpdatedAt = max(a.sync.updatedAt, b.sync.updatedAt)
                let winnerIsLatest = result.winner.sync.updatedAt == maxUpdatedAt
                let partitions = Self.partitionsInputs(result, a, b)
                return (winnerIsLatest ^&&^ partitions)
                    <?> "winner updatedAt=\(result.winner.sync.updatedAt) max=\(maxUpdatedAt)"
            }
    }

    // MARK: - Property 32: identifier tie-break on equal update time (Req 6.2)

    /// WHERE two conflicting records share the same `updatedAt` (and `version`),
    /// the tie is broken deterministically by record identifier: the greater
    /// `id` wins. This is the explicit Req 6.2 tie-break clause.
    func testIdentifierBreaksUpdateTimeTie() {
        property("greater id wins on an updatedAt/version tie (Property 32, Req 6.2)",
                 arguments: Self.checkArgs)
            <- forAll(Self.idTieBreakGen) { (a: ActionItem, b: ActionItem) in
                let result = Domain.resolveConflict(a, b)
                let expected = a.id > b.id ? a : b
                return (result.winner == expected)
                    <?> "ids a=\(a.id) b=\(b.id) winner=\(result.winner.id)"
            }
    }

    // MARK: - Property 32: resolution is order-independent / commutative

    /// `resolve(a, b)` and `resolve(b, a)` yield the same winner and loser
    /// values — resolution does not depend on argument order.
    func testResolutionIsCommutative() {
        property("resolveConflict is commutative (Property 32)",
                 arguments: Self.checkArgs)
            <- forAll(Self.pairGen) { (a: ActionItem, b: ActionItem) in
                let forward = Domain.resolveConflict(a, b)
                let reverse = Domain.resolveConflict(b, a)
                return (forward.winner == reverse.winner) ^&&^ (forward.loser == reverse.loser)
            }
    }

    // MARK: - Property 32: resolution is deterministic / repeatable

    /// Resolving the same pair repeatedly always produces an identical result —
    /// the "deterministic" half of the property.
    func testResolutionIsDeterministic() {
        property("resolveConflict is deterministic across repeated calls (Property 32)",
                 arguments: Self.checkArgs)
            <- forAll(Self.pairGen) { (a: ActionItem, b: ActionItem) in
                Domain.resolveConflict(a, b) == Domain.resolveConflict(a, b)
            }
    }

    // MARK: - Property 32: named conveniences equal the generic resolver

    /// `resolveActionItem` is exactly `resolveConflict` specialized to
    /// `ActionItem`, so the named convenience never diverges from the shared
    /// rule.
    func testActionItemConvenienceMatchesGeneric() {
        property("resolveActionItem == resolveConflict (Property 32)",
                 arguments: Self.checkArgs)
            <- forAll(Self.pairGen) { (a: ActionItem, b: ActionItem) in
                Domain.resolveActionItem(a, b) == Domain.resolveConflict(a, b)
            }
    }

    // MARK: - Property 32: the rule is generic across syncable entities (Bucket)

    /// The same last-writer-wins rule applies to `Bucket`: `resolveBucket`
    /// equals the generic resolver and selects the maximum-`updatedAt` version.
    func testBucketConvenienceMatchesGenericAndLatestWins() {
        let bucketPairGen: Gen<(Bucket, Bucket)> = Gen.zip(
            Gen.fromElements(of: Self.idPool),
            Gen.zip(Gen.fromElements(of: Self.updatedAtPool), Self.versionGen),
            Gen.zip(Gen.fromElements(of: Self.updatedAtPool), Self.versionGen)
        ).map { sharedId, fa, fb in
            (Self.makeBucket(id: sharedId, updatedAt: fa.0, version: fa.1),
             Self.makeBucket(id: sharedId, updatedAt: fb.0, version: fb.1))
        }

        property("resolveBucket == resolveConflict and latest updatedAt wins (Property 32, Req 6.2)",
                 arguments: Self.checkArgs)
            <- forAll(bucketPairGen) { (a: Bucket, b: Bucket) in
                let viaConvenience = Domain.resolveBucket(a, b)
                let viaGeneric = Domain.resolveConflict(a, b)
                let maxUpdatedAt = max(a.sync.updatedAt, b.sync.updatedAt)
                return (viaConvenience == viaGeneric)
                    ^&&^ (viaConvenience.winner.sync.updatedAt == maxUpdatedAt)
            }
    }

    private static func makeBucket(id: String, updatedAt: Date, version: Int64) -> Bucket {
        Bucket(
            id: id,
            accountId: accountId,
            name: "bucket-\(id)",
            notStartedColor: "#100000",
            inProgressColor: "#001000",
            completedColor: "#000010",
            sync: SyncMeta(updatedAt: updatedAt, version: version, deleted: false)
        )
    }
}
