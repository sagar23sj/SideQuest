import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

// Feature: ios-client, Property 6: Synchronization pushes are idempotent by
// client identifier.
//
/// Property-based test for **Property 6 — Synchronization pushes are idempotent
/// by client identifier** (task 16.2).
///
/// **Validates: Requirements 6.8**
///
/// > *For any* push payload submitted one or more times (including retries), the
/// > resulting record set contains exactly one record per client-generated
/// > identifier, so retried pushes never create duplicates.
///
/// Requirement 6.8: the `Sync_Service` makes pushes idempotent keyed on the
/// client-generated id, so the server (which dedupes by account + id) never
/// creates duplicates when the same payload is re-sent.
///
/// ## Strategy
///
/// The real ``SyncService.push()`` is driven over two in-memory seams so the
/// idempotency logic runs on any host (no Apple toolchain, no GRDB):
///
/// - a **fake `BackendClient`** built from a stub `HTTPTransport`
///   (``RecordingDedupeTransport``) that behaves like the server's id-keyed
///   merge: it decodes each pushed `SyncPushRequest` and upserts every change
///   into a dictionary keyed on the client-generated `id`, so re-delivering a
///   change with the same id overwrites rather than duplicates. To model the
///   *adversarial* idempotency case — the server applied a change but the
///   acknowledgment was lost in transit — the transport **records the changes
///   first and then throws a transient failure** for a configurable number of
///   early calls; the client retries with the identical payload, the transport
///   records again, and the dedupe map collapses the duplicate. The map's size
///   is therefore the number of distinct client ids regardless of how many
///   times the payload was delivered.
/// - a **fake `SyncStore`** (``InMemorySyncStore``) holding the locally-dirty
///   `ActionItem`s; `markAllDirty()` lets the trial re-submit the *same* dirty
///   set across several explicit push passes (a higher-level resubmission of the
///   same ids), complementing the client-level retries.
///
/// For each trial SwiftCheck generates: a non-empty set of dirty items with
/// unique client ids, a number of transport failures before success (0...4,
/// exercising client-level retries that re-send the identical body), and a
/// number of explicit push passes (1...4, re-submitting the same dirty set).
/// The trial runs the chosen number of passes and asserts:
///
/// - **exactly one record per id** — the server-side dedupe map contains exactly
///   the set of distinct client ids, with no id appearing twice;
/// - **duplicates were actually delivered** — when retries or extra passes
///   occurred, the transport received the payload more times than there are
///   distinct ids, proving the dedupe (not an absence of duplicates) is what
///   keeps the record set clean;
/// - **content is the pushed content** — each stored record equals the item that
///   was pushed for that id (re-sends carry the same payload, so the merged
///   value is stable).
final class IdempotentPushPropertyTests: XCTestCase {

    /// Property 6 / Req 6.8: pushing the same payload one or more times,
    /// including retries, yields exactly one record per client-generated id.
    func testPushesAreIdempotentByClientIdentifier() {
        property(
            "Property 6: synchronization pushes are idempotent by client identifier",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(scenarioGen) { scenario in
            return self.idempotentPushHolds(scenario)
        }
    }

    // MARK: - Property under test

    private func idempotentPushHolds(_ scenario: Scenario) -> Bool {
        let store = InMemorySyncStore(items: scenario.items)
        let transport = RecordingDedupeTransport(
            failuresBeforeSuccess: scenario.failuresBeforeSuccess
        )
        // maxRetries covers the transient failures, so each push pass succeeds
        // within the client by re-sending the identical body (Req 6.8).
        let backend = BackendClient(
            transport: transport,
            maxRetries: scenario.failuresBeforeSuccess + 2,
            retryDelay: 0
        )
        let service = SyncService(
            backend: backend,
            store: store,
            authorizer: StubAuthorizer(token: "valid-token"),
            maxAttempts: 3,
            retryDelay: 0
        )

        let distinctIds = Set(scenario.items.map { $0.id })

        let succeeded = Self.runBlocking { () async -> Bool in
            for pass in 0..<scenario.pushPasses {
                // Re-mark the same dirty set so each pass re-submits the same ids
                // (a higher-level resubmission); the first pass is already dirty.
                if pass > 0 { store.markAllDirty() }
                do {
                    _ = try await service.push()
                } catch {
                    return false
                }
            }
            return true
        }

        guard succeeded else { return false }

        let records = transport.recordedById()

        // 1) Exactly one record per client-generated id: the dedupe map's key set
        //    equals the distinct pushed ids, and a dictionary cannot hold two
        //    records for the same id.
        guard Set(records.keys) == distinctIds else { return false }
        guard records.count == distinctIds.count else { return false }

        // 2) Duplicates were actually delivered when retries / extra passes
        //    happened, proving dedupe (not an empty re-send) keeps the set clean.
        let expectedDeliveriesPerPass = scenario.failuresBeforeSuccess + 1
        let expectedTotalDeliveries = expectedDeliveriesPerPass * scenario.pushPasses
        guard transport.deliveredPayloadCount == expectedTotalDeliveries else { return false }
        if expectedTotalDeliveries > 1 {
            // More payload deliveries than distinct records ⇒ duplicates collapsed.
            guard transport.deliveredItemCount > distinctIds.count else { return false }
        }

        // 3) Each stored record is the pushed content for that id (re-sends carry
        //    the same payload, so the merged value is the pushed item).
        for item in scenario.items {
            guard let stored = records[item.id], stored == item else { return false }
        }

        return true
    }

    // MARK: - Async bridge

    /// Runs an `async` operation to completion from a synchronous SwiftCheck
    /// property body (same pattern as the other async property tests in this
    /// suite).
    private static func runBlocking<T>(_ operation: @escaping () async -> T) -> T {
        let semaphore = DispatchSemaphore(value: 0)
        let box = ResultBox<T>()
        Task {
            box.value = await operation()
            semaphore.signal()
        }
        semaphore.wait()
        return box.value!
    }

    private final class ResultBox<T> {
        var value: T?
    }
}

// MARK: - Scenario

/// One randomly generated trial configuration.
private struct Scenario {
    /// The dirty set pushed each pass; ids are unique within the set.
    var items: [ActionItem]
    /// Transport failures before it succeeds, exercising client-level retries
    /// that re-send the identical body (the lost-acknowledgment case).
    var failuresBeforeSuccess: Int
    /// Explicit push passes that re-submit the same dirty set.
    var pushPasses: Int
}

// MARK: - Fakes

/// Supplies a fixed, always-valid bearer token so the auth seam never fails the
/// trial — the behaviour under test is the server-side id dedupe, not auth.
private struct StubAuthorizer: SyncAuthorizer {
    let token: String
    func authorizationToken() async throws -> String { token }
}

/// In-memory ``SyncStore`` fake holding the local `ActionItem`s, applying the
/// same dirty/version semantics the real `ActionItemRepository` does.
/// ``markAllDirty()`` lets a trial re-submit the same dirty set across passes.
private final class InMemorySyncStore: SyncStore {

    private var items: [String: ActionItem]

    init(items: [ActionItem]) {
        self.items = Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
    }

    /// Re-marks every held item dirty so it is pushed again on the next pass.
    func markAllDirty() {
        for (id, var item) in items {
            item.sync.dirty = true
            items[id] = item
        }
    }

    func pendingPushItems() throws -> [ActionItem] {
        items.values.filter { $0.sync.dirty }.sorted { $0.id < $1.id }
    }

    func localItem(id: String) throws -> ActionItem? { items[id] }

    func applyRemoteChange(_ item: ActionItem) throws {
        var stored = item
        stored.sync.dirty = false
        items[item.id] = stored
    }

    func acknowledgePush(id: String, version: Int64) throws {
        guard var item = items[id], item.sync.version == version else { return }
        item.sync.dirty = false
        items[id] = item
    }

    func importAllAtomically(_ newItems: [ActionItem]) throws {
        for item in newItems {
            var stored = item
            stored.sync.dirty = false
            items[item.id] = stored
        }
    }
}

/// A stub `HTTPTransport` that models the server's id-keyed, idempotent merge.
///
/// Every `/sync/push` it receives is decoded and each change is **upserted** into
/// `recordsById` keyed on the client-generated `id`, so re-delivering a change
/// with the same id overwrites rather than duplicates — exactly the dedupe the
/// contract guarantees (Req 6.8). To exercise the adversarial idempotency case
/// (the server applied the change but the acknowledgment was lost), the first
/// `failuresBeforeSuccess` calls **record first and then throw a transient
/// failure**; the owning `BackendClient` retries with the identical payload, the
/// transport records again, and the dedupe map collapses the duplicate.
///
/// `deliveredPayloadCount` counts how many push payloads were received and
/// `deliveredItemCount` how many individual change records were delivered
/// (across all attempts); both exceed the number of distinct ids when retries or
/// extra passes occur, demonstrating that dedupe — not the absence of duplicate
/// deliveries — keeps the record set at one per id.
private final class RecordingDedupeTransport: HTTPTransport, @unchecked Sendable {

    private let failuresBeforeSuccess: Int
    private let encoder = SideQuestCoding.makeEncoder()
    private let decoder = SideQuestCoding.makeDecoder()
    private let lock = NSLock()

    private var _callCount = 0
    private var _deliveredPayloadCount = 0
    private var _deliveredItemCount = 0
    private var _recordsById: [String: ActionItem] = [:]
    private var _syncToken: Int64 = 0

    init(failuresBeforeSuccess: Int) {
        self.failuresBeforeSuccess = failuresBeforeSuccess
    }

    /// The server-side record set after dedupe (one entry per client id).
    func recordedById() -> [String: ActionItem] {
        lock.lock(); defer { lock.unlock() }
        return _recordsById
    }

    /// Number of push payloads the transport received (across all attempts).
    var deliveredPayloadCount: Int {
        lock.lock(); defer { lock.unlock() }
        return _deliveredPayloadCount
    }

    /// Number of individual change records delivered (across all attempts).
    var deliveredItemCount: Int {
        lock.lock(); defer { lock.unlock() }
        return _deliveredItemCount
    }

    func perform(_ request: HTTPRequest) async throws -> HTTPResponse {
        lock.lock()
        _callCount += 1
        let attempt = _callCount
        let appliedCount: Int

        if request.path == BackendEndpoints.syncPush, let body = request.body {
            // Decode the payload and upsert by client id (server dedupe). This
            // happens even on the calls that go on to "fail", modelling a server
            // that applied the change before the acknowledgment was lost.
            let pushRequest = (try? decoder.decode(SyncPushRequest.self, from: body))
                ?? SyncPushRequest(changes: [])
            _deliveredPayloadCount += 1
            _deliveredItemCount += pushRequest.changes.count
            for change in pushRequest.changes {
                _recordsById[change.id] = change
            }
            appliedCount = pushRequest.changes.count
        } else {
            appliedCount = 0
        }

        _syncToken += 1
        let token = _syncToken
        lock.unlock()

        if attempt <= failuresBeforeSuccess {
            // Acknowledgment lost after the change was applied → client retries
            // the identical payload (Req 6.8); the dedupe map collapses it.
            throw HTTPTransportError.offline
        }

        let response = SyncPushResponse(applied: appliedCount, newSyncToken: token)
        let data = (try? encoder.encode(response)) ?? Data()
        return HTTPResponse(statusCode: 200, body: data)
    }
}

// MARK: - Generators (constrain to the relevant input space)

private let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)

private let titleGen: Gen<String> = Gen<Int>.choose((1, 12)).flatMap { size in
    Gen<Character>
        .fromElements(of: Array("abcdefghijklmnopqrstuvwxyz ABCDEF0123456789"))
        .proliferate(withSize: size)
        .map { String($0) }
}

/// A non-empty set of locally-dirty `ActionItem`s with **unique** client ids, so
/// the dedupe map's expected size is the number of items and the "one record per
/// id" assertion is meaningful.
private let dirtyItemsGen: Gen<[ActionItem]> = Gen.compose { c in
    let count = c.generate(using: Gen<Int>.choose((1, 5)))
    let now = Date(timeIntervalSince1970: 1_700_000_000)
    return (0..<count).map { index in
        ActionItem(
            id: "push-item-\(index)",
            accountId: "acct-1",
            bucketId: "bucket-1",
            title: c.generate(using: titleGen),
            contentType: .text,
            timeframe: .today,
            status: c.generate(using: statusGen),
            createdAt: now,
            sync: SyncMeta(updatedAt: now, version: 0, deleted: false, dirty: true)
        )
    }
}

/// Transport failures before success (0...4): client-level retries re-send the
/// identical body, delivering the same ids multiple times.
private let failuresBeforeSuccessGen = Gen<Int>.choose((0, 4))

/// Explicit push passes (1...4) re-submitting the same dirty set.
private let pushPassesGen = Gen<Int>.choose((1, 4))

private let scenarioGen: Gen<Scenario> = Gen.compose { c in
    Scenario(
        items: c.generate(using: dirtyItemsGen),
        failuresBeforeSuccess: c.generate(using: failuresBeforeSuccessGen),
        pushPasses: c.generate(using: pushPassesGen)
    )
}
