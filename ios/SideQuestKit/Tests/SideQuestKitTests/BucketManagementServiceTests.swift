import XCTest
import Foundation
import GRDB
@testable import SideQuestKit

/// Unit tests for bucket CRUD and the delete-decision flow (task 7.1).
///
/// **Validates: Requirements 9.1, 9.4, 9.5**
///
/// These tests exercise `BucketManagementService` against a real on-disk
/// `SideQuestDatabase` (GRDB does not support in-memory `DatabasePool`s), so the
/// create/rename validation (reusing task 4.1's `Domain.validateBucketName`) and
/// the two-step delete decision/apply flow are checked end to end through the
/// real repositories.
///
/// Coverage:
/// - **Create/rename (Req 9.1, 9.2, 9.3)**: trimmed-name persistence, duplicate
///   rejection, length rejection, rename keeping own name / changing only case,
///   and rename of an unknown bucket.
/// - **Delete decision (Req 9.4, 9.5)**: `beginDelete` classifies an empty
///   bucket as a direct delete, a non-empty bucket with another bucket present
///   as reassign-or-delete, and a non-empty last bucket as confirm-delete-items.
/// - **Delete apply (Req 9.4, 9.5)**: empty deletes directly; reassign moves
///   contained items to the target and tombstones the bucket; delete-items
///   tombstones the contained items and the bucket; a non-empty delete with no
///   confirmed strategy is rejected; an invalid reassign target is rejected.
final class BucketManagementServiceTests: XCTestCase {

    // MARK: - Test harness

    /// A fixed clock so seeded sync timestamps are deterministic.
    private let clock: RepositoryClock = {
        { Date(timeIntervalSince1970: 1_700_000_000) }
    }()

    private var dbPath: String!
    private var database: SideQuestDatabase!
    private var bucketRepo: BucketRepository!
    private var itemRepo: ActionItemRepository!
    private var service: BucketManagementService!

    override func setUpWithError() throws {
        try super.setUpWithError()
        dbPath = NSTemporaryDirectory()
            + "SideQuestBucketMgmt-\(UUID().uuidString).sqlite"
        database = try SideQuestDatabase(path: dbPath)
        bucketRepo = BucketRepository(database: database, now: clock)
        itemRepo = ActionItemRepository(database: database, now: clock)
        service = BucketManagementService(buckets: bucketRepo, items: itemRepo)
    }

    override func tearDownWithError() throws {
        database = nil
        bucketRepo = nil
        itemRepo = nil
        service = nil
        // SQLite in WAL mode keeps sidecar files; remove all three.
        if let dbPath {
            for suffix in ["", "-wal", "-shm"] {
                try? FileManager.default.removeItem(atPath: dbPath + suffix)
            }
        }
        dbPath = nil
        try super.tearDownWithError()
    }

    // MARK: - Fixtures

    private static let accountId = "acct-1"

    private func makeBucketCandidate(
        id: String,
        name: String,
        accountId: String = BucketManagementServiceTests.accountId
    ) -> Bucket {
        Bucket(
            id: id,
            accountId: accountId,
            name: name,
            notStartedColor: "#FF0000",
            inProgressColor: "#00FF00",
            completedColor: "#0000FF",
            sync: SyncMeta(updatedAt: Date(timeIntervalSince1970: 0), version: 1, deleted: false, dirty: true)
        )
    }

    /// Creates a bucket directly through the repository and returns its id.
    @discardableResult
    private func seedBucket(
        name: String,
        accountId: String = BucketManagementServiceTests.accountId
    ) throws -> String {
        let id = UUID().uuidString.lowercased()
        _ = try bucketRepo.create(makeBucketCandidate(id: id, name: name, accountId: accountId))
        return id
    }

    /// Creates an action item in `bucketId` and returns its id.
    @discardableResult
    private func seedItem(
        in bucketId: String,
        accountId: String = BucketManagementServiceTests.accountId,
        title: String = "task"
    ) throws -> String {
        let id = UUID().uuidString.lowercased()
        let item = ActionItem(
            id: id,
            accountId: accountId,
            bucketId: bucketId,
            title: title,
            description: nil,
            contentType: .text,
            sourceContent: title,
            preview: nil,
            timeframe: .today,
            status: .notStarted,
            createdAt: Date(timeIntervalSince1970: 1_700_000_000),
            sync: SyncMeta(updatedAt: Date(timeIntervalSince1970: 0), version: 1, deleted: false, dirty: true)
        )
        _ = try itemRepo.create(item)
        return id
    }

    // MARK: - Create (Req 9.1, 9.2, 9.3)

    func testCreateBucketPersistsTrimmedName() throws {
        let candidate = makeBucketCandidate(id: UUID().uuidString.lowercased(), name: "  Travel  ")
        let result = try service.createBucket(candidate)

        guard case .created(let stored) = result else {
            return XCTFail("expected .created, got \(result)")
        }
        XCTAssertEqual(stored.name, "Travel", "name is persisted trimmed (Req 9.3)")
        XCTAssertTrue(stored.sync.dirty, "a new bucket is pending sync (Req 5.6)")

        let live = try bucketRepo.fetchAll()
        XCTAssertEqual(live.map(\.name), ["Travel"])
    }

    func testCreateBucketRejectsDuplicateNameCaseInsensitively() throws {
        _ = try service.createBucket(makeBucketCandidate(id: UUID().uuidString.lowercased(), name: "Travel"))

        let result = try service.createBucket(
            makeBucketCandidate(id: UUID().uuidString.lowercased(), name: "  travel ")
        )
        XCTAssertEqual(result, .duplicateName, "normalized duplicate is rejected (Req 9.2)")

        let live = try bucketRepo.fetchAll()
        XCTAssertEqual(live.count, 1, "existing buckets are left unchanged (Req 9.2)")
    }

    func testCreateBucketRejectsInvalidLength() throws {
        let tooLong = String(repeating: "a", count: 51)
        let result = try service.createBucket(
            makeBucketCandidate(id: UUID().uuidString.lowercased(), name: tooLong)
        )
        XCTAssertEqual(result, .invalidLength, "over-50 name is rejected (Req 9.3)")
        XCTAssertTrue(try bucketRepo.fetchAll().isEmpty)
    }

    func testCreateBucketRejectsWhitespaceOnlyName() throws {
        let result = try service.createBucket(
            makeBucketCandidate(id: UUID().uuidString.lowercased(), name: "   ")
        )
        XCTAssertEqual(result, .invalidLength, "whitespace-only name is rejected (Req 9.3)")
        XCTAssertTrue(try bucketRepo.fetchAll().isEmpty)
    }

    func testDuplicateNameAllowedAcrossDifferentAccounts() throws {
        _ = try service.createBucket(
            makeBucketCandidate(id: UUID().uuidString.lowercased(), name: "Travel", accountId: "acct-1")
        )
        let result = try service.createBucket(
            makeBucketCandidate(id: UUID().uuidString.lowercased(), name: "Travel", accountId: "acct-2")
        )
        guard case .created = result else {
            return XCTFail("uniqueness is per-account (Req 9.2); expected .created, got \(result)")
        }
    }

    // MARK: - Rename (Req 9.1, 9.2, 9.3)

    func testRenameBucketSucceeds() throws {
        let id = try seedBucket(name: "Travel")
        let result = try service.renameBucket(id: id, to: "  Trips  ")

        guard case .renamed(let stored) = result else {
            return XCTFail("expected .renamed, got \(result)")
        }
        XCTAssertEqual(stored.name, "Trips")
        XCTAssertEqual(try bucketRepo.fetchAll().first(where: { $0.id == id })?.name, "Trips")
    }

    func testRenameBucketKeepingOwnNameIsAllowed() throws {
        let id = try seedBucket(name: "Travel")
        // Changing only the casing must not be a self-collision (Req 9.2).
        let result = try service.renameBucket(id: id, to: "TRAVEL")
        guard case .renamed(let stored) = result else {
            return XCTFail("expected .renamed, got \(result)")
        }
        XCTAssertEqual(stored.name, "TRAVEL")
    }

    func testRenameBucketRejectsDuplicateOfAnotherBucket() throws {
        _ = try seedBucket(name: "Travel")
        let id = try seedBucket(name: "Work")

        let result = try service.renameBucket(id: id, to: "travel")
        XCTAssertEqual(result, .duplicateName, "rename to another bucket's name is rejected (Req 9.2)")
        XCTAssertEqual(try bucketRepo.fetchAll().first(where: { $0.id == id })?.name, "Work")
    }

    func testRenameBucketRejectsInvalidLength() throws {
        let id = try seedBucket(name: "Travel")
        let result = try service.renameBucket(id: id, to: "")
        XCTAssertEqual(result, .invalidLength, "empty rename is rejected (Req 9.3)")
        XCTAssertEqual(try bucketRepo.fetchAll().first(where: { $0.id == id })?.name, "Travel")
    }

    func testRenameUnknownBucketReportsNotFound() throws {
        let result = try service.renameBucket(id: "no-such-bucket", to: "Whatever")
        XCTAssertEqual(result, .bucketNotFound)
    }

    // MARK: - Delete decision (Req 9.4, 9.5)

    func testBeginDeleteUnknownBucketReturnsNil() throws {
        XCTAssertNil(try service.beginDelete(id: "no-such-bucket"))
    }

    func testBeginDeleteEmptyBucketIsDirect() throws {
        let id = try seedBucket(name: "Travel")
        XCTAssertEqual(try service.beginDelete(id: id), .deleteDirectly)
    }

    func testBeginDeleteNonEmptyWithAnotherBucketRequiresReassignOrDelete() throws {
        let travel = try seedBucket(name: "Travel")
        let work = try seedBucket(name: "Work")
        _ = try seedItem(in: travel)
        _ = try seedItem(in: travel)

        let decision = try service.beginDelete(id: travel)
        guard case .requiresReassignOrDelete(let itemCount, let candidates) = decision else {
            return XCTFail("expected .requiresReassignOrDelete, got \(String(describing: decision))")
        }
        XCTAssertEqual(itemCount, 2)
        XCTAssertEqual(candidates.map(\.id), [work], "the other bucket is offered as a reassign target (Req 9.4)")
    }

    func testBeginDeleteNonEmptyLastBucketRequiresConfirmDeleteItems() throws {
        let id = try seedBucket(name: "Travel")
        _ = try seedItem(in: id)

        let decision = try service.beginDelete(id: id)
        XCTAssertEqual(decision, .requiresConfirmDeleteItems(itemCount: 1), "last bucket → confirm-delete-items (Req 9.5)")
    }

    // MARK: - Delete apply (Req 9.4, 9.5)

    func testConfirmDeleteEmptyBucketDeletesDirectly() throws {
        let id = try seedBucket(name: "Travel")
        _ = try service.confirmDelete(id: id, strategy: nil)

        XCTAssertNil(try bucketRepo.fetchAll().first(where: { $0.id == id }), "bucket is removed from live state")
        XCTAssertTrue(
            try database.fetchAllBuckets().first(where: { $0.id == id })?.sync.deleted ?? false,
            "bucket is tombstoned for sync (Req 6.3)"
        )
    }

    func testConfirmDeleteReassignMovesItemsToTarget() throws {
        let travel = try seedBucket(name: "Travel")
        let work = try seedBucket(name: "Work")
        let itemA = try seedItem(in: travel)
        let itemB = try seedItem(in: travel)

        let outcome = try service.confirmDelete(id: travel, strategy: .reassign(targetBucketId: work))

        XCTAssertEqual(Set(outcome.reassignedItemIds), [itemA, itemB])
        XCTAssertTrue(outcome.deletedItemIds.isEmpty)

        let liveItems = try itemRepo.fetchAll()
        XCTAssertEqual(liveItems.count, 2, "no item is lost on reassign (Req 9.4)")
        XCTAssertTrue(liveItems.allSatisfy { $0.bucketId == work }, "items moved to the target bucket (Req 9.4)")
        XCTAssertNil(try bucketRepo.fetchAll().first(where: { $0.id == travel }), "the bucket is deleted")
    }

    func testConfirmDeleteDeleteItemsTombstonesContents() throws {
        let travel = try seedBucket(name: "Travel")
        _ = try seedBucket(name: "Work")
        let itemA = try seedItem(in: travel)
        let itemB = try seedItem(in: travel)

        let outcome = try service.confirmDelete(id: travel, strategy: .deleteItems)

        XCTAssertEqual(Set(outcome.deletedItemIds), [itemA, itemB])
        XCTAssertTrue(outcome.reassignedItemIds.isEmpty)

        XCTAssertTrue(try itemRepo.fetchAll().isEmpty, "contained items are removed from live state (Req 9.4)")
        let stored = try database.fetchAllActionItems()
        XCTAssertTrue(
            stored.filter { $0.id == itemA || $0.id == itemB }.allSatisfy { $0.sync.deleted },
            "deleted items are tombstoned for sync (Req 6.3)"
        )
        XCTAssertNil(try bucketRepo.fetchAll().first(where: { $0.id == travel }))
    }

    func testConfirmDeleteLastBucketDeletesContainedItems() throws {
        let id = try seedBucket(name: "Travel")
        let item = try seedItem(in: id)

        let outcome = try service.confirmDelete(id: id, strategy: .deleteItems)

        XCTAssertEqual(outcome.deletedItemIds, [item])
        XCTAssertTrue(try itemRepo.fetchAll().isEmpty, "last-bucket delete removes its items (Req 9.5)")
        XCTAssertTrue(try bucketRepo.fetchAll().isEmpty)
    }

    func testConfirmDeleteNonEmptyWithoutStrategyThrowsConfirmationRequired() throws {
        let id = try seedBucket(name: "Travel")
        _ = try seedItem(in: id)

        XCTAssertThrowsError(try service.confirmDelete(id: id, strategy: nil)) { error in
            XCTAssertEqual(error as? BucketManagementError, .confirmationRequired)
        }
        // Nothing changed: bucket and item are still live.
        XCTAssertEqual(try bucketRepo.fetchAll().count, 1)
        XCTAssertEqual(try itemRepo.fetchAll().count, 1)
    }

    func testConfirmDeleteRejectsInvalidReassignTarget() throws {
        let travel = try seedBucket(name: "Travel")
        _ = try seedItem(in: travel)

        // Target that is not a live bucket.
        XCTAssertThrowsError(
            try service.confirmDelete(id: travel, strategy: .reassign(targetBucketId: "no-such-bucket"))
        ) { error in
            XCTAssertEqual(error as? BucketManagementError, .invalidReassignTarget)
        }
        // Target equal to the bucket being deleted is also invalid.
        XCTAssertThrowsError(
            try service.confirmDelete(id: travel, strategy: .reassign(targetBucketId: travel))
        ) { error in
            XCTAssertEqual(error as? BucketManagementError, .invalidReassignTarget)
        }

        XCTAssertEqual(try bucketRepo.fetchAll().count, 1, "the bucket is untouched on an invalid request")
        XCTAssertEqual(try itemRepo.fetchAll().count, 1)
    }
}
