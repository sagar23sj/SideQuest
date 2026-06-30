import XCTest
import Foundation
@testable import SideQuestKit

/// Unit tests for the confirm-capture write to the shared store (task 8.3).
///
/// **Covers: Requirements 4.5, 4.6, 4.10**
///
/// - On confirm, an `ActionItem` with status `.notStarted` is created and
///   written to the (shared) store, preserving the chosen bucket and timeframe
///   (Req 4.5, 4.10).
/// - On a store-write failure, no partial item is created and the user's
///   selections are retained in the result for a retry (Req 4.6).
///
/// The store is a real `SideQuestDatabase` over a unique temp file (the kit's
/// `DatabasePool` has no in-memory mode). A store-write failure is induced
/// naturally by confirming against a bucket id that does not exist: the
/// `actionItem.bucketId` foreign key (enforced by GRDB) rejects the insert, so
/// the transaction rolls back — exactly the failure shape Req 4.6 protects.
final class CaptureConfirmationTests: XCTestCase {

    private var dbPath: String!
    private var database: SideQuestDatabase!

    override func setUpWithError() throws {
        dbPath = NSTemporaryDirectory()
            + "SideQuestCaptureConfirm-\(UUID().uuidString).sqlite"
        database = try SideQuestDatabase(path: dbPath)
    }

    override func tearDownWithError() throws {
        database = nil
        for suffix in ["", "-wal", "-shm"] {
            try? FileManager.default.removeItem(atPath: dbPath + suffix)
        }
        dbPath = nil
    }

    // MARK: - Helpers

    private func seedBucket(id: String = "bucket-1", accountId: String = "acct-1") throws {
        let bucket = Bucket(
            id: id,
            accountId: accountId,
            name: "Reading",
            notStartedColor: "#FF0000",
            inProgressColor: "#00FF00",
            completedColor: "#0000FF",
            sync: .created(now: Date(timeIntervalSince1970: 1_700_000_000))
        )
        try database.saveBucket(bucket)
    }

    private func makeConfirmer(now: Date = Date(timeIntervalSince1970: 1_700_000_500)) -> CaptureConfirmer {
        let repository = ActionItemRepository(database: database, now: { now })
        return CaptureConfirmer(repository: repository, now: { now })
    }

    // MARK: - Success (Req 4.5, 4.10)

    func testConfirmCreatesNotStartedItemPreservingSelectionAndPersistsIt() throws {
        try seedBucket()

        let confirmer = makeConfirmer()
        let draft = CaptureDraft(
            contentType: .text,
            title: "Read this article",
            sourceContent: "Read this article"
        )
        let selection = CategorizationSelection(bucketId: "bucket-1", timeframe: .withinAWeek)

        let result = confirmer.confirm(
            draft: draft,
            selection: selection,
            accountId: "acct-1",
            preview: nil
        )

        guard case .saved(let item) = result else {
            return XCTFail("Expected .saved, got \(result)")
        }

        // Req 4.5 — not started, preserves bucket + timeframe.
        XCTAssertEqual(item.status, .notStarted)
        XCTAssertEqual(item.bucketId, "bucket-1")
        XCTAssertEqual(item.timeframe, .withinAWeek)
        XCTAssertEqual(item.accountId, "acct-1")
        XCTAssertEqual(item.title, "Read this article")
        XCTAssertTrue(item.sync.dirty, "A new capture must be pending sync")

        // Req 4.10 — the item is written to the store (readable back).
        let persisted = try database.fetchAllActionItems()
        XCTAssertEqual(persisted.count, 1)
        XCTAssertEqual(persisted.first, item)
    }

    func testConfirmLinkDraftPersistsUnresolvedFallbackPreview() throws {
        try seedBucket()

        let confirmer = makeConfirmer()
        let url = URL(string: "https://example.com/post")!
        let draft = CaptureDraft(
            contentType: .link,
            title: url.absoluteString,
            sourceContent: url.absoluteString,
            linkURL: url
        )
        let selection = CategorizationSelection(bucketId: "bucket-1", timeframe: .today)

        let result = confirmer.confirm(
            draft: draft,
            selection: selection,
            accountId: "acct-1",
            preview: nil
        )

        guard case .saved(let item) = result else {
            return XCTFail("Expected .saved, got \(result)")
        }
        // Req 4.9 — capture completes with an unresolved preview carrying the raw URL.
        XCTAssertEqual(item.preview?.resolved, false)
        XCTAssertEqual(item.preview?.rawUrl, url.absoluteString)
    }

    // MARK: - Failure (Req 4.6)

    func testStoreWriteFailureCreatesNoPartialItemAndRetainsSelection() throws {
        // No bucket seeded → the foreign key on bucketId rejects the insert.
        let confirmer = makeConfirmer()
        let draft = CaptureDraft(
            contentType: .text,
            title: "Orphan item",
            sourceContent: "Orphan item"
        )
        let selection = CategorizationSelection(bucketId: "missing-bucket", timeframe: .today)

        let result = confirmer.confirm(
            draft: draft,
            selection: selection,
            accountId: "acct-1",
            preview: nil
        )

        guard case .failed(let message, let retained) = result else {
            return XCTFail("Expected .failed, got \(result)")
        }

        // Req 4.6 — a non-empty user-facing error is surfaced.
        XCTAssertFalse(message.isEmpty)
        // Req 4.6 — the user's selections are retained unchanged for a retry.
        XCTAssertEqual(retained, selection)
        // Req 4.6 — no partial item was written (transaction rolled back).
        XCTAssertEqual(try database.fetchAllActionItems().count, 0)
    }

    // MARK: - Incomplete selection (defensive; Req 4.3)

    func testIncompleteSelectionWritesNothingAndRetainsSelection() throws {
        try seedBucket()

        let confirmer = makeConfirmer()
        let draft = CaptureDraft(contentType: .text, title: "Untitled")
        // Timeframe missing → not confirmable.
        let selection = CategorizationSelection(bucketId: "bucket-1", timeframe: nil)

        let result = confirmer.confirm(
            draft: draft,
            selection: selection,
            accountId: "acct-1",
            preview: nil
        )

        guard case .incompleteSelection(let retained) = result else {
            return XCTFail("Expected .incompleteSelection, got \(result)")
        }
        XCTAssertEqual(retained, selection)
        XCTAssertEqual(try database.fetchAllActionItems().count, 0)
    }
}
