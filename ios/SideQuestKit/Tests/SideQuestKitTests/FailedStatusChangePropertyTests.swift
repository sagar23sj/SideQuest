import XCTest
import Foundation
import SwiftCheck
import GRDB
@testable import SideQuestKit

/// Property-based test for **Property 16 — A failed status change preserves the
/// prior status and indicator** (task 11.2).
///
/// **Validates: Requirements 8.4**
///
/// > For any Action_Item whose status change fails to persist, the item's
/// > stored status and its displayed color indicator remain equal to their
/// > values before the attempted change.
///
/// ## Strategy
///
/// For each case SwiftCheck generates a bucket (with three **distinct**
/// per-status colors so the indicator genuinely differs between statuses), an
/// item seeded into that bucket with a **prior** status, and a **new** status
/// distinct from the prior one that the user attempts to switch to. The prior
/// state is written through the real repositories (`BucketRepository` /
/// `ActionItemRepository`) over a real on-disk `SideQuestDatabase`, so the
/// "before" snapshot is exactly what the store durably holds.
///
/// The status change is then made to **genuinely fail its commit** — not via a
/// mock, but using the schema's own integrity constraints: the update carries
/// the new status *and* re-points the item at a bucket id that does not exist in
/// the store. The `actionItem.bucketId` foreign key is enforced by SQLite, so
/// the write violates the constraint, the GRDB transaction rolls back, and the
/// repository re-throws ``RepositoryError/notSaved(underlying:)`` (the "did not
/// save" indication — Req 8.4). The status change therefore never reaches the
/// store.
///
/// The property then asserts, for every case:
///
/// 1. the update threw ``RepositoryError/notSaved`` (Req 8.4 — the status update
///    did not save and an error indication is surfaced),
/// 2. re-reading the item yields a stored **status equal to the prior status**
///    (and a `bucketId` still equal to the real bucket — nothing changed), and
/// 3. the **board color indicator** derived from the persisted item is unchanged
///    — both the pure resolver ``Domain/statusColor(for:in:)`` and the
///    aggregated ``BoardItem/statusColor`` from ``Domain/buildBoard(items:buckets:)``
///    still equal the prior indicator, and (because the bucket's colors are
///    distinct) differ from the color the *attempted* new status would have
///    shown — proving the indicator did not move.
///
/// ## Generator notes (constraining to the valid input space)
///
/// - **Bucket colors** are generated as three distinct strings (three
///   consecutive entries of a fixed pool, modulo its size), so the would-be new
///   indicator color always differs from the prior one and the "unchanged"
///   assertion is meaningful rather than vacuous.
/// - **The new status differs from the prior status**, so every case exercises a
///   real attempted transition (not a no-op).
/// - The bucket id used to force the failure is a fresh sentinel guaranteed
///   absent from the store, so the foreign-key violation is deterministic.
///
/// SwiftCheck is configured for **200 successful tests** per property, above the
/// design's minimum of 100 iterations.
final class FailedStatusChangePropertyTests: XCTestCase {

    /// A fixed clock so seeded sync timestamps are deterministic; the property
    /// is about status/indicator equality before/after a failed write, not time.
    private let clock: RepositoryClock = {
        { Date(timeIntervalSince1970: 1_700_000_000) }
    }()

    /// Property 16 / Req 8.4: a failed status change leaves the persisted status
    /// and its derived board indicator unchanged.
    func testFailedStatusChangePreservesStatusAndIndicator() {
        property(
            "Property 16: a failed status change preserves the prior status and indicator",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(scenarioGen) { scenario in
            return self.failedStatusChangeIsInert(scenario)
        }
    }

    // MARK: - Property under test

    private func failedStatusChangeIsInert(_ scenario: Scenario) -> Bool {
        let path = NSTemporaryDirectory()
            + "SideQuestFailedStatusChange-\(UUID().uuidString).sqlite"

        // SQLite in WAL mode keeps sidecar files; remove all three afterwards.
        defer {
            for suffix in ["", "-wal", "-shm"] {
                try? FileManager.default.removeItem(atPath: path + suffix)
            }
        }

        do {
            let database = try SideQuestDatabase(path: path)
            let bucketRepo = BucketRepository(database: database, now: clock)
            let itemRepo = ActionItemRepository(database: database, now: clock)

            // Phase 1 — seed the prior state: one bucket, one item in it.
            let bucketId = UUID().uuidString.lowercased()
            let bucket = try bucketRepo.create(scenario.makeBucket(id: bucketId))

            let itemId = itemRepo.newIdentifier()
            let storedBefore = try itemRepo.create(
                scenario.makeItem(id: itemId, bucketId: bucketId, status: scenario.priorStatus)
            )

            // The prior indicator: how the board renders the item right now.
            let priorStatus = storedBefore.status
            let priorIndicator = Domain.statusColor(for: priorStatus, in: bucket)
            let priorBoardColor = boardColor(forItemId: itemId, items: [storedBefore], buckets: [bucket])

            // Sanity: the *attempted* new status would have shown a different
            // color (bucket colors are distinct), so "unchanged" is meaningful.
            let wouldBeNewIndicator = Domain.statusColor(for: scenario.newStatus, in: bucket)

            // Phase 2 — attempt the status change with a write that must fail.
            // Carry the new status AND re-point at a nonexistent bucket id so the
            // foreign key rejects the commit (the change never persists).
            let unknownBucketId = "unknown-bucket-" + UUID().uuidString.lowercased()
            var attempted = storedBefore
            attempted.status = scenario.newStatus
            attempted.bucketId = unknownBucketId

            let threwNotSaved = expectNotSaved { _ = try itemRepo.update(attempted) }

            // Phase 3 — re-read and assert nothing moved.
            guard let persisted = try itemRepo.localItem(id: itemId) else { return false }
            let afterIndicator = Domain.statusColor(for: persisted.status, in: bucket)
            let afterBoardColor = boardColor(forItemId: itemId, items: [persisted], buckets: [bucket])

            let statusUnchanged = persisted.status == priorStatus
            let stillInRealBucket = persisted.bucketId == bucketId
            let resolverUnchanged = afterIndicator == priorIndicator
            let boardUnchanged = afterBoardColor == priorBoardColor
            // The transition was real (distinct colors), so the indicator that
            // would have been shown differs — confirming the indicator stayed put.
            let indicatorActuallyHeld = priorIndicator != wouldBeNewIndicator

            return threwNotSaved
                && statusUnchanged
                && stillInRealBucket
                && resolverUnchanged
                && boardUnchanged
                && indicatorActuallyHeld
        } catch {
            XCTFail("Failed-status-change scenario threw unexpectedly: \(error)")
            return false
        }
    }

    // MARK: - Helpers

    /// The aggregated board color for the item with `id`, as the UI would show
    /// it (``Domain/buildBoard(items:buckets:)`` → ``BoardItem/statusColor``).
    private func boardColor(forItemId id: String, items: [ActionItem], buckets: [Bucket]) -> String? {
        let board = Domain.buildBoard(items: items, buckets: buckets)
        for group in board.groups {
            for boardItem in group.items where boardItem.item.id == id {
                return boardItem.statusColor
            }
        }
        return nil
    }

    /// Runs `body`, returning `true` iff it threw ``RepositoryError/notSaved``.
    /// A success (the change persisted) or any other error is a violation.
    private func expectNotSaved(_ body: () throws -> Void) -> Bool {
        do {
            try body()
            return false   // committed when it should have failed
        } catch RepositoryError.notSaved {
            return true
        } catch {
            return false   // wrong error type
        }
    }
}

// MARK: - Scenario model

/// A generated bucket (with distinct per-status colors), an item's prior status,
/// and the new status the user attempts to switch to (distinct from prior).
private struct Scenario {
    var accountId: String
    var name: String
    var notStartedColor: String
    var inProgressColor: String
    var completedColor: String
    var priorStatus: ActionStatus
    var newStatus: ActionStatus

    func makeBucket(id: String) -> Bucket {
        Bucket(
            id: id,
            accountId: accountId,
            name: name,
            notStartedColor: notStartedColor,
            inProgressColor: inProgressColor,
            completedColor: completedColor,
            sync: SyncMeta(updatedAt: Date(timeIntervalSince1970: 0), version: 1, deleted: false, dirty: true)
        )
    }

    func makeItem(id: String, bucketId: String, status: ActionStatus) -> ActionItem {
        ActionItem(
            id: id,
            accountId: accountId,
            bucketId: bucketId,
            title: name,
            contentType: .text,
            timeframe: .today,
            status: status,
            createdAt: Date(timeIntervalSince1970: 1_700_000_000),
            sync: SyncMeta(updatedAt: Date(timeIntervalSince1970: 0), version: 1, deleted: false, dirty: true)
        )
    }
}

// MARK: - Generators

private let accountIdGen = Gen<String>.fromElements(of: ["acct-1", "acct-2", "acct-3"])

private let nameChars: [Character] =
    Array("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-éü✓")

private let nameGen: Gen<String> = Gen<Int>.choose((1, 16)).flatMap { size in
    Gen<Character>.fromElements(of: nameChars).proliferate(withSize: size).map { String($0) }
}

/// A fixed pool of colors; three consecutive entries (mod the pool size) are
/// always distinct, which is how a bucket's distinct color triple is built.
private let colorPool: [String] = [
    "#FF0000", "#00FF00", "#0000FF", "#FFAA00",
    "#123456", "#ABCDEF", "#000000", "#FFFFFF", "red", "green"
]

/// Three distinct colors drawn from `colorPool` at a random offset.
private let distinctColorTripleGen: Gen<(String, String, String)> =
    Gen<Int>.choose((0, colorPool.count - 1)).map { start in
        let n = colorPool.count
        return (colorPool[start], colorPool[(start + 1) % n], colorPool[(start + 2) % n])
    }

/// A prior status paired with a *different* new status, so each case exercises
/// a genuine attempted transition.
private let statusPairGen: Gen<(ActionStatus, ActionStatus)> =
    Gen<ActionStatus>.fromElements(of: ActionStatus.allCases).flatMap { prior in
        let others = ActionStatus.allCases.filter { $0 != prior }
        return Gen<ActionStatus>.fromElements(of: others).map { (prior, $0) }
    }

private let scenarioGen: Gen<Scenario> = Gen.compose { c in
    let colors = c.generate(using: distinctColorTripleGen)
    let statuses = c.generate(using: statusPairGen)
    return Scenario(
        accountId: c.generate(using: accountIdGen),
        name: c.generate(using: nameGen),
        notStartedColor: colors.0,
        inProgressColor: colors.1,
        completedColor: colors.2,
        priorStatus: statuses.0,
        newStatus: statuses.1
    )
}
