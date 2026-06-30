import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

// Feature: ios-client, Reused Property 29: Data created while signed in is
// associated with the current account, and Reused Property 30: Sync makes data
// available across devices (round trip).
//
/// Property-based tests for **Reused Property 29 — Data created while signed in
/// is associated with the current account** and **Reused Property 30 — Sync
/// makes data available across devices (round trip)** (task 16.5).
///
/// **Validates: Requirements 10.2, 10.3**
///
/// > **Property 29** — *For any* Action_Item created while signed in as account
/// > A, the entity's `accountId` equals A.
/// >
/// > **Property 30** — *For any* set of local records on one device, after
/// > pushing to the backend and pulling on a second device signed in to the
/// > same account, the second device's non-deleted records equal the first
/// > device's records.
///
/// Requirement 10.2: *"WHILE a User is signed in, THE App SHALL associate the
/// User's Action_Items, Buckets, and Action_Plans with the User's Account."*
/// Requirement 10.3: *"WHEN a User signs in on a new device and network
/// connectivity is available, THE App SHALL make the User's Action_Items,
/// Buckets, and Action_Plans available on that device ..."*
///
/// ## Strategy
///
/// Both properties reuse the in-memory `SyncService` round-trip harness used by
/// `TombstoneDeletePropagationPropertyTests` and
/// `BoundedRetryStatePreservationPropertyTests`, so the logic runs on any host
/// (no Apple toolchain, no GRDB):
///
/// - a **fake account backend** (``RoundTripServer``) holding one account's
///   server-authoritative `ActionItem`s, reached through a stub `HTTPTransport`
///   (``RoundTripTransport``) that decodes a real `/sync/push` body and serves a
///   real `/sync/pull` response, so the contract serialization (including the
///   `accountId` field) is genuinely exercised on the wire;
/// - two **fake `SyncStore`s** (``InMemorySyncStore``): *device A*, where the
///   data is created, and *device B*, the freshly-signed-in second device that
///   must surface the same records after it pulls.
///
/// ### Property 29 — account association on creation
///
/// The portable capture builder ``CaptureDraft/makeActionItem(id:accountId:bucketId:timeframe:now:preview:)``
/// is the path that stamps the signed-in account onto a newly captured item
/// (task 8.3 hands its result to the repository). For each trial SwiftCheck
/// generates a signed-in account id and a batch of distinct drafts, builds an
/// item for each while "signed in" as that account, and asserts every created
/// item's `accountId` equals the signed-in account — and nothing else (a
/// different account id never leaks in).
///
/// ### Property 30 — cross-device round trip
///
/// Device A starts with a generated set of locally-dirty records (all belonging
/// to account A, a non-empty live subset plus an optional tombstoned subset);
/// device B starts **empty** (a new device). The trial runs `deviceA.push()`
/// then `deviceB.pull()` and asserts device B's **non-deleted** records equal
/// device A's non-deleted records **field-by-field** (modulo the client-only
/// `dirty` flag, which is not part of the synced payload), and that no
/// tombstoned record surfaces as live on device B.
///
/// ## Generator notes (constraining to the valid input space)
///
/// - Every generated record / draft is stamped with the **same** signed-in
///   account id, so the association assertion is about that account and the
///   round trip is within one account (Req 10.2, 10.3).
/// - Records are seeded `dirty == true` so device A's push carries a real
///   payload; at least one record is live (non-deleted) so the round trip
///   asserts a non-empty availability set.
/// - Field values (status, timeframe, title, createdAt, version) vary across
///   records so the field-by-field equality check is meaningful rather than
///   trivially comparing identical rows.
final class AccountAssociationAcrossDevicesPropertyTests: XCTestCase {

    // MARK: - Property 29

    /// Property 29 / Req 10.2: every item created while signed in as account A
    /// carries `accountId == A`.
    func testDataCreatedWhileSignedInIsAssociatedWithCurrentAccount() {
        property(
            "Property 29: data created while signed in is associated with the current account",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(creationScenarioGen) { scenario in
            return self.accountAssociationHolds(scenario)
        }
    }

    // MARK: - Property 30

    /// Property 30 / Req 10.3: a push from device A followed by a pull on a
    /// freshly signed-in device B makes the same records available on B,
    /// field-by-field.
    func testSyncMakesDataAvailableAcrossDevicesRoundTrip() {
        property(
            "Property 30: sync makes data available across devices (round trip)",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(roundTripScenarioGen) { scenario in
            return self.roundTripAvailabilityHolds(scenario)
        }
    }

    // MARK: - Property 29 under test

    private func accountAssociationHolds(_ scenario: CreationScenario) -> Bool {
        let now = Date(timeIntervalSince1970: 1_700_000_000)
        for draft in scenario.drafts {
            let item = draft.draft.makeActionItem(
                id: draft.id,
                accountId: scenario.accountId,
                bucketId: draft.bucketId,
                timeframe: draft.timeframe,
                now: now
            )
            // Created while signed in as account A → associated with A (Req 10.2).
            guard item.accountId == scenario.accountId else { return false }
        }
        return true
    }

    // MARK: - Property 30 under test

    private func roundTripAvailabilityHolds(_ scenario: RoundTripScenario) -> Bool {
        // Device A holds the generated records; device B is a new device (empty)
        // signed in to the same account.
        let server = RoundTripServer(items: [])
        let deviceAStore = InMemorySyncStore(items: scenario.deviceARecords)
        let deviceBStore = InMemorySyncStore(items: [])

        let deviceA = makeService(store: deviceAStore, server: server)
        let deviceB = makeService(store: deviceBStore, server: server)

        let ok = Self.runBlocking { () async -> Bool in
            do {
                _ = try await deviceA.push()
                _ = try await deviceB.pull()
                return true
            } catch {
                return false
            }
        }
        guard ok else { return false }

        let deviceBItems = deviceBStore.snapshot()

        // Expected available set: device A's non-deleted records, normalized to
        // drop the client-only `dirty` flag (which is not synced).
        let expectedLive = Self.liveNormalized(scenario.deviceARecords)
        let actualLive = Self.liveNormalized(Array(deviceBItems.values))

        // 1. Field-by-field availability: device B's live records equal device
        //    A's live records exactly (Req 10.3).
        guard actualLive == expectedLive else { return false }

        // 2. No tombstoned record surfaces as live on device B.
        let deletedIds = Set(scenario.deviceARecords.filter { $0.sync.deleted }.map { $0.id })
        let deviceBLiveIds = Set(actualLive.keys)
        return deviceBLiveIds.isDisjoint(with: deletedIds)
    }

    // MARK: - Helpers

    /// Maps the non-deleted records to a `[id: ActionItem]` with the client-only
    /// `dirty` flag cleared, so two devices are compared on their synced content
    /// rather than on local pending-sync bookkeeping.
    private static func liveNormalized(_ items: [ActionItem]) -> [String: ActionItem] {
        var map: [String: ActionItem] = [:]
        for var item in items where !item.sync.deleted {
            item.sync.dirty = false
            map[item.id] = item
        }
        return map
    }

    /// Builds a ``SyncService`` over the in-memory store and the shared
    /// round-trip server, with retries and delays disabled so the property runs
    /// fast and deterministically.
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

// MARK: - Scenarios

/// One randomly generated creation configuration for Property 29: a signed-in
/// account and a batch of drafts to create under it.
private struct CreationScenario {
    var accountId: String
    var drafts: [DraftSpec]
}

/// A single draft plus the client-generated id, bucket, and timeframe the
/// capture flow would supply when building the item.
private struct DraftSpec {
    var id: String
    var draft: CaptureDraft
    var bucketId: String
    var timeframe: Timeframe
}

/// One randomly generated round-trip configuration for Property 30: device A's
/// records, all for the same signed-in account.
private struct RoundTripScenario {
    var accountId: String
    var deviceARecords: [ActionItem]
}

// MARK: - Fakes (shared shapes mirror the other sync round-trip property tests)

/// Supplies a fixed, always-valid bearer token so the auth seam never fails the
/// trial — these properties concern account association and data availability,
/// not auth.
private struct StubAuthorizer: SyncAuthorizer {
    let token: String
    func authorizationToken() async throws -> String { token }
}

/// The shared, server-authoritative record set for one account. Device A pushes
/// records into it and device B pulls them back out, so it is the channel data
/// travels through between the two devices.
private final class RoundTripServer: @unchecked Sendable {

    private let lock = NSLock()
    private var items: [String: ActionItem]
    private var token: Int64 = 1

    init(items: [ActionItem]) {
        self.items = Dictionary(uniqueKeysWithValues: items.map { item in
            var stored = item
            stored.sync.dirty = false
            return (item.id, stored)
        })
    }

    /// Applies a pushed change set, resolving against any existing record with
    /// last-writer-wins, and returns the count applied plus the advanced token.
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

    /// Every record the account holds, including tombstones, so a pulling device
    /// receives the full account dataset.
    func allChanges() -> (changes: [ActionItem], token: Int64) {
        lock.lock(); defer { lock.unlock() }
        let changes = items.values.sorted { $0.id < $1.id }
        return (changes, token)
    }
}

/// A stub `HTTPTransport` that turns `/sync/push` and `/sync/pull` requests into
/// operations on the shared ``RoundTripServer``, exercising real contract
/// serialization (including the `accountId` field) in both directions.
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
/// does, so the property can observe which records a device surfaces.
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

private let accountIdGen: Gen<String> = Gen<Int>.choose((1, 9_999)).map { "acct-\($0)" }

private let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)

private let contentTypeGen = Gen<ContentType>.fromElements(of: ContentType.allCases)

private let titleGen: Gen<String> = Gen<Int>.choose((1, 12)).flatMap { size in
    Gen<Character>
        .fromElements(of: Array("abcdefghijklmnopqrstuvwxyz ABCDEF0123456789"))
        .proliferate(withSize: size)
        .map { String($0) }
}

/// A small spread of timeframe variants (the `specificDate` payload is a fixed
/// instant so it round-trips deterministically through the wire).
private let timeframeGen: Gen<Timeframe> = Gen<Timeframe>.fromElements(of: [
    .today,
    .withinADay,
    .withinAWeek,
    .specificDate(Date(timeIntervalSince1970: 1_800_000_000))
])

// MARK: Property 29 generators

private let creationScenarioGen: Gen<CreationScenario> = Gen.compose { c in
    let accountId = c.generate(using: accountIdGen)
    let count = c.generate(using: Gen<Int>.choose((1, 6)))
    let drafts: [DraftSpec] = (0..<count).map { index in
        let contentType = c.generate(using: contentTypeGen)
        let title = c.generate(using: titleGen)
        let draft = CaptureDraft(
            contentType: contentType,
            title: title,
            sourceContent: contentType == .text ? title : nil,
            linkURL: contentType == .link ? URL(string: "https://example.com/\(index)") : nil
        )
        return DraftSpec(
            id: "create-item-\(index)",
            draft: draft,
            bucketId: "bucket-\(c.generate(using: Gen<Int>.choose((1, 3))))",
            timeframe: c.generate(using: timeframeGen)
        )
    }
    return CreationScenario(accountId: accountId, drafts: drafts)
}

// MARK: Property 30 generators

private let roundTripScenarioGen: Gen<RoundTripScenario> = Gen.compose { c in
    let accountId = c.generate(using: accountIdGen)
    let count = c.generate(using: Gen<Int>.choose((1, 6)))
    let createdBase = Date(timeIntervalSince1970: 1_700_000_000)

    // Decide a deleted mask, but force at least one live record so the round
    // trip asserts a non-empty availability set.
    var deleteFlags = (0..<count).map { _ in
        c.generate(using: Gen<Bool>.fromElements(of: [true, false]))
    }
    if !deleteFlags.contains(false) { deleteFlags[0] = false }

    let records: [ActionItem] = (0..<count).map { index in
        let deleted = deleteFlags[index]
        let createdAt = createdBase.addingTimeInterval(TimeInterval(index) * 60)
        let version = Int64(c.generate(using: Gen<Int>.choose((0, 5))))
        return ActionItem(
            id: "rt-item-\(index)",
            accountId: accountId,
            bucketId: "bucket-\(c.generate(using: Gen<Int>.choose((1, 3))))",
            title: c.generate(using: titleGen),
            contentType: c.generate(using: contentTypeGen),
            timeframe: c.generate(using: timeframeGen),
            status: c.generate(using: statusGen),
            createdAt: createdAt,
            sync: SyncMeta(
                updatedAt: createdAt.addingTimeInterval(deleted ? 3600 : 0),
                version: version,
                deleted: deleted,
                dirty: true
            )
        )
    }

    return RoundTripScenario(accountId: accountId, deviceARecords: records)
}
