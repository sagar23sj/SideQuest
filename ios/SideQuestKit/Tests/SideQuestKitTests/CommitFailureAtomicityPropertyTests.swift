import XCTest
import Foundation
import SwiftCheck
import GRDB
@testable import SideQuestKit

/// Property-based test for **Property 3 — Capture and commit failures never
/// leave partial state and retain user input** (task 6.3).
///
/// **Validates: Requirements 4.6, 5.8**
///
/// > For any capture confirmation or local-store mutation that fails to commit,
/// > the persisted store equals its prior state (no partial entity is written)
/// > and the user's input/selections are retained.
///
/// ## Strategy
///
/// For each case SwiftCheck generates a random **prior state** (a set of
/// buckets and the items that live in them) and a **failing operation** to run
/// against it. The prior state is seeded through the real repositories
/// (`BucketRepository` / `ActionItemRepository`) over a real on-disk
/// `SideQuestDatabase`, so the snapshot we compare against is exactly what the
/// store durably holds.
///
/// Every generated operation is constructed to *genuinely* fail its commit,
/// using the schema's own integrity constraints rather than a mock:
///
/// - **Unknown-bucket capture/create/update** — the `actionItem.bucketId`
///   foreign key (`onDelete: .cascade`) is enforced by SQLite, so writing an
///   item that references a bucket id absent from the store violates the
///   constraint and the write throws.
/// - **Duplicate-id bucket create** — the `bucket.id` primary key is unique, so
///   inserting a second bucket with an existing id throws.
///
/// A failing write runs inside a GRDB transaction that is **rolled back** on
/// throw, and the repository re-throws the failure as
/// ``RepositoryError/notSaved(underlying:)``. The property then asserts three
/// things hold for every case:
///
/// 1. the call threw ``RepositoryError/notSaved(underlying:)`` (the "not saved"
///    indication — Req 4.6, 5.8),
/// 2. re-reading the store yields a state **field-by-field equal** to the
///    pre-operation snapshot (no partial entity was written — Req 4.6, 5.8), and
/// 3. the user's input value — the `CategorizationSelection` for a capture, or
///    the entity passed to the repository for a store mutation — is unchanged
///    and still usable for a retry (input/selections retained — Req 4.6, 5.8).
///
/// ## Generator notes (constraining to the valid input space)
///
/// - Prior **buckets** get freshly minted lowercased UUIDs at apply time, so
///   their ids never collide with the sentinel "unknown" bucket id used to
///   force foreign-key failures.
/// - Prior **items** are only generated against buckets that exist (their
///   generated index is reduced modulo the live bucket count, and item
///   generation is skipped entirely when no bucket exists), so seeding the
///   prior state always succeeds and leaves a well-defined snapshot.
/// - Operations that need existing state (duplicate-id create needs a bucket;
///   unknown-bucket *update* needs an item) fall back to the always-failing
///   unknown-bucket *create* when that prerequisite is absent, so every
///   generated case exercises a real commit failure.
final class CommitFailureAtomicityPropertyTests: XCTestCase {

    /// A fixed clock so seeded sync timestamps are deterministic; the property
    /// is about state equality before/after a failed write, not about time.
    private let clock: RepositoryClock = {
        { Date(timeIntervalSince1970: 1_700_000_000) }
    }()

    /// Property 3 / Req 4.6, 5.8: a failed commit is atomic and retains input.
    func testCommitFailureLeavesNoPartialStateAndRetainsInput() {
        property(
            "a failed capture/mutation leaves the store unchanged and retains the user's input",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(scenarioGen) { scenario in
            return self.atomicityHolds(scenario)
        }
    }

    // MARK: - Property under test

    private func atomicityHolds(_ scenario: Scenario) -> Bool {
        let path = NSTemporaryDirectory()
            + "SideQuestCommitFailure-\(UUID().uuidString).sqlite"

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

            // Phase 1 — seed a well-defined prior state through the repositories.
            var bucketIds: [String] = []
            for seed in scenario.buckets {
                let id = UUID().uuidString.lowercased()
                _ = try bucketRepo.create(seed.makeBucket(id: id))
                bucketIds.append(id)
            }

            var itemIds: [String] = []
            for seed in scenario.items {
                guard !bucketIds.isEmpty else { continue }
                let bucketId = bucketIds[seed.bucketIndex % bucketIds.count]
                let id = UUID().uuidString.lowercased()
                _ = try itemRepo.create(seed.makeItem(id: id, bucketId: bucketId))
                itemIds.append(id)
            }

            // Snapshot the durable prior state (the "should be unchanged" oracle).
            let before = try snapshot(database)

            // A bucket id guaranteed absent from the store, to force a foreign
            // key violation. Prior buckets use plain UUIDs, so this never collides.
            let unknownBucketId = "unknown-bucket-" + UUID().uuidString.lowercased()

            // Phase 2 — run the operation that must fail to commit.
            let outcome = runFailingOperation(
                scenario.failingOp,
                unknownBucketId: unknownBucketId,
                bucketIds: bucketIds,
                itemIds: itemIds,
                bucketRepo: bucketRepo,
                itemRepo: itemRepo
            )

            // Phase 3 — re-read and assert the three guarantees.
            let after = try snapshot(database)
            let storeUnchanged = (before.buckets == after.buckets)
                && (before.items == after.items)

            return outcome.threwNotSaved && storeUnchanged && outcome.inputRetained
        } catch {
            XCTFail("Commit-failure atomicity scenario threw unexpectedly: \(error)")
            return false
        }
    }

    /// Runs one failing operation and reports whether it (a) threw the expected
    /// "not saved" error and (b) left the caller's input value untouched.
    private func runFailingOperation(
        _ op: FailingOp,
        unknownBucketId: String,
        bucketIds: [String],
        itemIds: [String],
        bucketRepo: BucketRepository,
        itemRepo: ActionItemRepository
    ) -> (threwNotSaved: Bool, inputRetained: Bool) {
        switch op {
        case .captureConfirmUnknownBucket(let draftSeed, let timeframe):
            // The user's input is the categorization selection (bucket + timeframe).
            let selection = CategorizationSelection(
                bucketId: unknownBucketId,
                timeframe: timeframe
            )
            let selectionBefore = selection
            guard let confirmed = selection.confirmed else {
                return (false, false)   // generator guarantees this is non-nil
            }
            let draft = draftSeed.makeDraft()
            let item = draft.makeActionItem(
                id: itemRepo.newIdentifier(),
                accountId: "acct-1",
                bucketId: confirmed.bucketId,
                timeframe: confirmed.timeframe,
                now: clock()
            )
            let threw = expectNotSaved { _ = try itemRepo.create(item) }
            // Selection is unchanged and still confirmable -> retained for retry.
            let retained = (selection == selectionBefore) && (selection.confirmed != nil)
            return (threw, retained)

        case .createItemUnknownBucket(let seed):
            let item = seed.makeItem(id: UUID().uuidString.lowercased(), bucketId: unknownBucketId)
            let inputBefore = item
            let threw = expectNotSaved { _ = try itemRepo.create(item) }
            return (threw, item == inputBefore)

        case .createBucketDuplicateId(let seed):
            guard let existingId = bucketIds.first else {
                // No bucket to collide with -> fall back to an always-failing op.
                let item = seed.fallbackItem(bucketId: unknownBucketId)
                let inputBefore = item
                let threw = expectNotSaved { _ = try itemRepo.create(item) }
                return (threw, item == inputBefore)
            }
            let bucket = seed.makeBucket(id: existingId)   // duplicate primary key
            let inputBefore = bucket
            let threw = expectNotSaved { _ = try bucketRepo.create(bucket) }
            return (threw, bucket == inputBefore)

        case .updateItemUnknownBucket(let index, let seed):
            guard !itemIds.isEmpty else {
                // No existing item to update -> fall back to an always-failing op.
                let item = seed.makeItem(id: UUID().uuidString.lowercased(), bucketId: unknownBucketId)
                let inputBefore = item
                let threw = expectNotSaved { _ = try itemRepo.create(item) }
                return (threw, item == inputBefore)
            }
            let id = itemIds[index % itemIds.count]
            // Re-point an existing item at a nonexistent bucket -> FK violation.
            let item = seed.makeItem(id: id, bucketId: unknownBucketId)
            let inputBefore = item
            let threw = expectNotSaved { _ = try itemRepo.update(item) }
            return (threw, item == inputBefore)
        }
    }

    /// Runs `body`, returning `true` iff it threw ``RepositoryError/notSaved``.
    /// A success or any other error is a property violation.
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

    /// Reads the full durable store into id-keyed dictionaries for order-stable
    /// field-by-field comparison.
    private func snapshot(
        _ database: SideQuestDatabase
    ) throws -> (buckets: [String: Bucket], items: [String: ActionItem]) {
        let buckets = try database.fetchAllBuckets()
        let items = try database.fetchAllActionItems()
        return (
            Dictionary(uniqueKeysWithValues: buckets.map { ($0.id, $0) }),
            Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
        )
    }
}

// MARK: - Scenario model

/// A generated prior state plus the failing operation to run against it.
private struct Scenario {
    var buckets: [BucketSeed]
    var items: [ItemSeed]
    var failingOp: FailingOp
}

/// The kinds of commit failures exercised. Each is built to genuinely violate a
/// schema constraint so the write rolls back.
private enum FailingOp {
    /// Confirm a capture into a bucket id that does not exist (Req 4.6).
    case captureConfirmUnknownBucket(DraftSeed, Timeframe)
    /// Create an item referencing a bucket id that does not exist (Req 5.8).
    case createItemUnknownBucket(ItemSeed)
    /// Create a bucket whose id duplicates an existing one (Req 5.8).
    case createBucketDuplicateId(BucketSeed)
    /// Update an existing item to reference a bucket id that does not exist (Req 5.8).
    case updateItemUnknownBucket(Int, ItemSeed)
}

// MARK: - Seeds

private struct BucketSeed {
    var accountId: String
    var name: String
    var notStartedColor: String
    var inProgressColor: String
    var completedColor: String

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
}

private struct ItemSeed {
    var bucketIndex: Int
    var accountId: String
    var title: String
    var contentType: ContentType
    var timeframe: Timeframe
    var status: ActionStatus
    var createdAt: Date

    func makeItem(id: String, bucketId: String) -> ActionItem {
        ActionItem(
            id: id,
            accountId: accountId,
            bucketId: bucketId,
            title: title,
            description: nil,
            contentType: contentType,
            sourceContent: nil,
            preview: nil,
            timeframe: timeframe,
            status: status,
            createdAt: createdAt,
            sync: SyncMeta(updatedAt: createdAt, version: 1, deleted: false, dirty: true)
        )
    }
}

extension BucketSeed {
    /// A throwaway item used only when a bucket-based op has no bucket to act on
    /// and must fall back to an always-failing unknown-bucket create.
    func fallbackItem(bucketId: String) -> ActionItem {
        ActionItem(
            id: UUID().uuidString.lowercased(),
            accountId: accountId,
            bucketId: bucketId,
            title: name,
            description: nil,
            contentType: .text,
            sourceContent: nil,
            preview: nil,
            timeframe: .today,
            status: .notStarted,
            createdAt: Date(timeIntervalSince1970: 1_700_000_000),
            sync: SyncMeta(updatedAt: Date(timeIntervalSince1970: 0), version: 1, deleted: false, dirty: true)
        )
    }
}

private struct DraftSeed {
    var title: String
    var contentType: ContentType

    func makeDraft() -> CaptureDraft {
        switch contentType {
        case .link:
            let url = URL(string: "https://example.com/" + UUID().uuidString.lowercased())
            return CaptureDraft(contentType: .link, title: title, sourceContent: title, linkURL: url)
        case .text:
            return CaptureDraft(contentType: .text, title: title, sourceContent: title)
        case .image:
            return CaptureDraft(contentType: .image, title: title)
        case .videoRef:
            return CaptureDraft(contentType: .videoRef, title: title)
        }
    }
}

// MARK: - Generators

private let stringChars: [Character] =
    Array("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-./éü✓")

private let stringGen: Gen<String> = Gen<Int>.choose((1, 16)).flatMap { size in
    Gen<Character>.fromElements(of: stringChars).proliferate(withSize: size).map { String($0) }
}

private let accountIdGen = Gen<String>.fromElements(of: ["acct-1", "acct-2", "acct-3"])

private let colorGen = Gen<String>.fromElements(of: [
    "#FF0000", "#00FF00", "#0000FF", "#123456", "#ABCDEF", "#000000", "#FFFFFF"
])

private let indexGen = Gen<Int>.choose((0, 64))

private let secondsDateGen: Gen<Date> = Gen<Int>.choose((1_600_000_000, 1_800_000_000))
    .map { Date(timeIntervalSince1970: TimeInterval($0)) }

private let calendarDateGen: Gen<Date> = Gen<Int>.choose((16_000, 20_000))
    .map { Date(timeIntervalSince1970: TimeInterval($0 * 86_400)) }

private let timeframeGen: Gen<Timeframe> = Gen.one(of: [
    Gen.pure(Timeframe.today),
    Gen.pure(Timeframe.withinADay),
    Gen.pure(Timeframe.withinAWeek),
    calendarDateGen.map { Timeframe.specificDate($0) }
])

private let contentTypeGen = Gen<ContentType>.fromElements(of: ContentType.allCases)
private let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)

private let bucketSeedGen: Gen<BucketSeed> = Gen.compose { c in
    BucketSeed(
        accountId: c.generate(using: accountIdGen),
        name: c.generate(using: stringGen),
        notStartedColor: c.generate(using: colorGen),
        inProgressColor: c.generate(using: colorGen),
        completedColor: c.generate(using: colorGen)
    )
}

private let itemSeedGen: Gen<ItemSeed> = Gen.compose { c in
    ItemSeed(
        bucketIndex: c.generate(using: indexGen),
        accountId: c.generate(using: accountIdGen),
        title: c.generate(using: stringGen),
        contentType: c.generate(using: contentTypeGen),
        timeframe: c.generate(using: timeframeGen),
        status: c.generate(using: statusGen),
        createdAt: c.generate(using: secondsDateGen)
    )
}

private let draftSeedGen: Gen<DraftSeed> = Gen.compose { c in
    DraftSeed(
        title: c.generate(using: stringGen),
        contentType: c.generate(using: contentTypeGen)
    )
}

private let failingOpGen: Gen<FailingOp> = Gen.one(of: [
    Gen.zip(draftSeedGen, timeframeGen).map { FailingOp.captureConfirmUnknownBucket($0.0, $0.1) },
    itemSeedGen.map { FailingOp.createItemUnknownBucket($0) },
    bucketSeedGen.map { FailingOp.createBucketDuplicateId($0) },
    Gen.zip(indexGen, itemSeedGen).map { FailingOp.updateItemUnknownBucket($0.0, $0.1) }
])

private let scenarioGen: Gen<Scenario> = Gen.compose { c in
    let bucketCount = c.generate(using: Gen<Int>.choose((0, 4)))
    let itemCount = c.generate(using: Gen<Int>.choose((0, 6)))
    return Scenario(
        buckets: c.generate(using: bucketSeedGen.proliferate(withSize: bucketCount)),
        items: c.generate(using: itemSeedGen.proliferate(withSize: itemCount)),
        failingOp: c.generate(using: failingOpGen)
    )
}
