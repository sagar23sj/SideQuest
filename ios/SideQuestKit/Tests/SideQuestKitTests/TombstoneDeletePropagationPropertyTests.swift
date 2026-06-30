import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

// Feature: ios-client, Property 9: Deletes propagate via tombstones across a
// sync round trip.
//
/// Property-based test for **Property 9 — Deletes propagate via tombstones
/// across a sync round trip** (task 16.4).
///
/// **Validates: Requirements 6.3**
///
/// > *For any* entity deleted on one device, after a push/pull round trip the
/// > entity is represented as a tombstone such that a second device signed in to
/// > the same account no longer surfaces the record among its non-deleted
/// > records.
///
/// Requirement 6.3: *"WHEN an entity is deleted THE Sync_Service SHALL propagate
/// the deletion to other devices via a tombstone marker so the record is removed
/// everywhere."*
///
/// ## Strategy
///
/// The real ``SyncService`` is exercised end-to-end across **two devices** that
/// sync against one shared account, using only the in-memory seams so the
/// tombstone-propagation logic runs on any host (no Apple toolchain, no GRDB):
///
/// - a **fake account backend** (``RoundTripServer``) holds the account's
///   server-authoritative `ActionItem`s. It is reached through a stub
///   `HTTPTransport` (``RoundTripTransport``) that decodes a real `/sync/push`
///   body and applies the pushed changes, and serves a real `/sync/pull`
///   response carrying every record — **including tombstones** — so the wire
///   round trip (and the fact that `SyncMeta.deleted` survives serialization
///   while the client-only `dirty` flag does not) is genuinely exercised.
/// - two **fake `SyncStore`s** (``InMemorySyncStore``): *device A*, where the
///   delete happens, and *device B*, the second device that must stop surfacing
///   the deleted record after it pulls.
///
/// Both devices and the server start already in sync on the same set of live
/// records (modelling devices that synced previously). For each trial SwiftCheck
/// generates a non-empty set of records and marks a non-empty subset of them
/// deleted on device A (tombstone: `sync.deleted == true`, `dirty == true`, with
/// a strictly newer `updatedAt` so the delete wins last-writer-wins on the
/// second device). The trial then runs the round trip — `deviceA.push()` then
/// `deviceB.pull()` — and asserts:
///
/// - **deletes propagate** — every id deleted on A no longer appears among
///   device B's live (non-deleted) records;
/// - **as a tombstone** — each such id is present on device B as a tombstone
///   (`sync.deleted == true`), i.e. the record was reconciled, not merely
///   absent;
/// - **nothing else is lost** — every record *not* deleted still surfaces on
///   device B, so device B's live id-set equals exactly the surviving set.
///
/// ## Generator notes (constraining to the valid input space)
///
/// - At least one record is always marked deleted, so the push carries a real
///   tombstone and the round trip exercises propagation (a delete-free trial
///   would assert nothing about Property 9).
/// - A tombstone's `updatedAt` is strictly later than the live record both
///   devices already hold, so the deterministic last-writer-wins merge
///   (`Domain.resolveActionItem`, task 4.17) always applies the delete on the
///   second device — the delete is a strictly later write than the create.
final class TombstoneDeletePropagationPropertyTests: XCTestCase {

    /// Property 9 / Req 6.3: a delete on one device propagates via a tombstone
    /// so a second device stops surfacing the record after a sync round trip.
    func testDeletesPropagateViaTombstonesAcrossRoundTrip() {
        property(
            "Property 9: deletes propagate via tombstones across a sync round trip",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(scenarioGen) { scenario in
            return self.tombstonePropagationHolds(scenario)
        }
    }

    // MARK: - Property under test

    private func tombstonePropagationHolds(_ scenario: Scenario) -> Bool {
        let deletedIds = Set(scenario.deletedIds)
        let survivingIds = Set(scenario.liveRecords.map { $0.id }).subtracting(deletedIds)

        // Server and both devices start in sync on the same live record set.
        let server = RoundTripServer(items: scenario.liveRecords)
        let deviceAStore = InMemorySyncStore(items: scenario.deviceAItems)
        let deviceBStore = InMemorySyncStore(items: scenario.liveRecords)

        let deviceA = makeService(store: deviceAStore, server: server)
        let deviceB = makeService(store: deviceBStore, server: server)

        let ok = Self.runBlocking { () async -> Bool in
            do {
                // Device A pushes its dirty changes (the tombstoned deletes) to
                // the shared account; device B then pulls and merges them.
                _ = try await deviceA.push()
                _ = try await deviceB.pull()
                return true
            } catch {
                return false
            }
        }
        guard ok else { return false }

        let deviceBItems = deviceBStore.snapshot()
        let deviceBLiveIds = Set(deviceBItems.values.filter { !$0.sync.deleted }.map { $0.id })

        // 1. Deletes propagate: no deleted id surfaces among device B's live
        //    records.
        guard deviceBLiveIds.isDisjoint(with: deletedIds) else { return false }

        // 2. As a tombstone: each deleted id is present on device B and marked
        //    deleted (reconciled, not just missing).
        for id in deletedIds {
            guard let item = deviceBItems[id], item.sync.deleted else { return false }
        }

        // 3. Nothing else is lost: device B's live id-set is exactly the
        //    surviving (non-deleted) set.
        return deviceBLiveIds == survivingIds
    }

    // MARK: - Helpers

    /// Builds a ``SyncService`` over the in-memory store and the shared
    /// round-trip server. `maxRetries: 0` keeps each pass to a single network
    /// call; delays are zeroed so the property runs fast.
    private func makeService(store: SyncStore, server: RoundTripServer) -> SyncService {
        let transport = RoundTripTransport(server: server)
        let backend = BackendClient(transport: transport, maxRetries: 0, retryDelay: 0)
        return SyncService(
            backend: backend,
            store: store,
            authorizer: StubAuthorizer(token: "valid-token"),
            maxAttempts: 1,
            retryDelay: 0
        )
    }

    // MARK: - Async bridge

    /// Runs an `async` operation to completion from a synchronous SwiftCheck
    /// property body and returns its result (same pattern as the other async
    /// property tests in this suite).
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

/// One randomly generated round-trip configuration.
private struct Scenario {
    /// The records both the server and both devices start in sync on (all live).
    var liveRecords: [ActionItem]
    /// Device A's local copy: identical to `liveRecords` except the deleted ones
    /// are tombstoned (deleted + dirty + newer `updatedAt`).
    var deviceAItems: [ActionItem]
    /// The ids deleted on device A (a non-empty subset of `liveRecords`).
    var deletedIds: [String]
}

// MARK: - Fakes

/// Supplies a fixed, always-valid bearer token so the auth seam never fails the
/// trial — the round trip under test concerns the data, not auth.
private struct StubAuthorizer: SyncAuthorizer {
    let token: String
    func authorizationToken() async throws -> String { token }
}

/// The shared, server-authoritative record set for one account. Device A pushes
/// changes into it and device B pulls them back out, so it is the channel a
/// tombstone travels through between the two devices.
private final class RoundTripServer: @unchecked Sendable {

    private let lock = NSLock()
    private var items: [String: ActionItem]
    private var token: Int64 = 1

    init(items: [ActionItem]) {
        // Stored as the server holds them: server-authoritative, never dirty.
        self.items = Dictionary(uniqueKeysWithValues: items.map { item in
            var stored = item
            stored.sync.dirty = false
            return (item.id, stored)
        })
    }

    /// Applies a pushed change set (creates, edits, and tombstones alike),
    /// resolving against any existing record with last-writer-wins, and returns
    /// the count applied plus the advanced token.
    func applyPush(_ changes: [ActionItem]) -> (applied: Int, token: Int64) {
        lock.lock(); defer { lock.unlock() }
        for change in changes {
            if let existing = items[change.id] {
                items[change.id] = Domain.resolveActionItem(existing, change).winner
            } else {
                items[change.id] = change
            }
        }
        token += 1
        return (changes.count, token)
    }

    /// Every record the account holds, including tombstones, so deletes reach a
    /// pulling device (Req 6.3).
    func allChanges() -> (changes: [ActionItem], token: Int64) {
        lock.lock(); defer { lock.unlock() }
        let changes = items.values.sorted { $0.id < $1.id }
        return (changes, token)
    }
}

/// A stub `HTTPTransport` that turns `/sync/push` and `/sync/pull` requests into
/// operations on the shared ``RoundTripServer``, exercising real contract
/// serialization in both directions.
///
/// Decoding the push body proves that `SyncMeta.deleted` (the tombstone marker)
/// survives the wire while the client-only `dirty` flag does not — exactly the
/// contract behaviour Property 9 relies on.
private final class RoundTripTransport: HTTPTransport, @unchecked Sendable {

    private let server: RoundTripServer
    private let encoder = SideQuestCoding.makeEncoder()
    private let decoder = SideQuestCoding.makeDecoder()

    init(server: RoundTripServer) {
        self.server = server
    }

    func perform(_ request: HTTPRequest) async throws -> HTTPResponse {
        switch request.path {
        case BackendEndpoints.syncPush:
            let body = request.body ?? Data()
            let pushRequest = try decoder.decode(SyncPushRequest.self, from: body)
            let result = server.applyPush(pushRequest.changes)
            let response = SyncPushResponse(applied: result.applied, newSyncToken: result.token)
            return HTTPResponse(statusCode: 200, body: try encoder.encode(response))

        case BackendEndpoints.syncPull:
            let all = server.allChanges()
            let response = SyncPullResponse(changes: all.changes, newSyncToken: all.token)
            return HTTPResponse(statusCode: 200, body: try encoder.encode(response))

        default:
            return HTTPResponse(statusCode: 404, body: Data())
        }
    }
}

/// In-memory ``SyncStore`` fake. Holds a device's local `ActionItem`s and
/// applies the same dirty/tombstone semantics the real `ActionItemRepository`
/// does, so the property can observe which records a device surfaces as live.
private final class InMemorySyncStore: SyncStore {

    private var items: [String: ActionItem]

    init(items: [ActionItem]) {
        self.items = Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
    }

    func snapshot() -> [String: ActionItem] { items }

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

// MARK: - Generators (constrain to the relevant input space)

private let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)

private let titleGen: Gen<String> = Gen<Int>.choose((1, 12)).flatMap { size in
    Gen<Character>
        .fromElements(of: Array("abcdefghijklmnopqrstuvwxyz ABCDEF0123456789"))
        .proliferate(withSize: size)
        .map { String($0) }
}

/// Builds a scenario: a non-empty live record set shared by the server and both
/// devices, with a non-empty subset tombstoned on device A.
private let scenarioGen: Gen<Scenario> = Gen.compose { c in
    let count = c.generate(using: Gen<Int>.choose((1, 6)))
    // Instant the records were created (and both devices last synced).
    let createdAt = Date(timeIntervalSince1970: 1_700_000_000)

    let liveRecords: [ActionItem] = (0..<count).map { index in
        ActionItem(
            id: "tomb-item-\(index)",
            accountId: "acct-1",
            bucketId: "bucket-1",
            title: c.generate(using: titleGen),
            contentType: .text,
            timeframe: .today,
            status: c.generate(using: statusGen),
            createdAt: createdAt,
            sync: SyncMeta(updatedAt: createdAt, version: 0, deleted: false, dirty: false)
        )
    }

    // Mark a non-empty subset deleted; force index 0 deleted if the random mask
    // selected none, so every trial carries at least one tombstone.
    var deleteFlags = (0..<count).map { _ in c.generate(using: Gen<Bool>.fromElements(of: [true, false])) }
    if !deleteFlags.contains(true) { deleteFlags[0] = true }

    // The delete is a strictly later write than the create, so its tombstone
    // wins last-writer-wins on the pulling device.
    let deletedAt = createdAt.addingTimeInterval(3600)

    var deletedIds: [String] = []
    let deviceAItems: [ActionItem] = liveRecords.enumerated().map { (index, item) in
        guard deleteFlags[index] else { return item }
        deletedIds.append(item.id)
        var tombstone = item
        tombstone.sync = SyncMeta(updatedAt: deletedAt, version: 1, deleted: true, dirty: true)
        return tombstone
    }

    return Scenario(
        liveRecords: liveRecords,
        deviceAItems: deviceAItems,
        deletedIds: deletedIds
    )
}
