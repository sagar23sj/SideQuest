import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

// Feature: ios-client, Property 7: First-sign-in pull is atomic.
//
/// Property-based test for **Property 7 — First-sign-in pull is atomic**
/// (task 16.7).
///
/// **Validates: Requirements 6.10**
///
/// > *For any* first-sign-in pull that fails partway through, the local store
/// > contains none of the records from that pull (all-or-nothing import), and the
/// > unsynchronized state is preserved for a retry on the next pass.
///
/// Requirement 6.10: *"WHEN a first sign-in full pull fails partway, THE
/// Sync_Service SHALL import nothing, SHALL show a message, and SHALL retry the
/// full pull on the next pass."*
///
/// ## Strategy
///
/// The real ``SyncService/fullPullForFirstSignIn()`` is driven over the two
/// in-memory seams so the all-or-nothing logic runs on any host (no Apple
/// toolchain, no GRDB):
///
/// - a **fake `BackendClient`** built from a stub `HTTPTransport`
///   (``FirstPullTransport``) that either serves a real `/sync/pull` response
///   carrying the account's full record set (with an advanced `newSyncToken`),
///   or fails the pull with a transient transport error so the bounded-retry
///   budget is exhausted *before* any import happens.
/// - a **fake `SyncStore`** (``InMemorySyncStore``) whose
///   ``SyncStore/importAllAtomically(_:)`` models the GRDB single-transaction
///   contract: it builds the import into a scratch copy and commits it only
///   after **every** write succeeds, so a configured mid-import failure throws
///   and discards the scratch, leaving the persisted items byte-for-byte
///   unchanged (Property 7 / Req 6.7, 6.10).
///
/// For each trial SwiftCheck generates a prior local-store state (empty — a true
/// first sign-in — or a disjoint non-empty set, to prove preservation), a
/// non-empty pulled record set, an advanced server token, and one of three
/// outcomes:
///
/// - **`.success`** — the pull returns and the atomic import commits: every
///   pulled record is present and server-authoritative (not dirty), every prior
///   record still surfaces, and the sync cursor advances to the server token so
///   the next pass becomes a normal incremental pull.
/// - **`.importFailsPartway`** — the pull returns but the atomic import throws at
///   a generated index: the store equals its prior state exactly (nothing
///   imported), and the cursor is **not** advanced (stays `nil`) so the next
///   pass retries the full pull (Req 6.10).
/// - **`.pullFails`** — the pull itself fails on every attempt: the import is
///   never reached, the store equals its prior state, and the cursor is not
///   advanced.
///
/// ## Generator notes (constraining to the valid input space)
///
/// - The pulled set is always non-empty so there is a real record that an
///   all-or-nothing import could leave behind on failure (an empty pull imports
///   nothing trivially and would assert nothing about Property 7).
/// - For `.importFailsPartway` the fail index is drawn from `0..<pulled.count`,
///   so the failure genuinely lands *partway through* a non-trivial import.
/// - Prior and pulled ids use disjoint prefixes so the "prior state preserved"
///   comparison is unambiguous and a successful import is purely additive.
/// - The server token is strictly positive and distinct from the `nil` starting
///   cursor, so advancing the cursor on success is observable.
final class AtomicFirstSignInPullPropertyTests: XCTestCase {

    /// Property 7 / Req 6.10: a first-sign-in full pull is all-or-nothing — a
    /// failure imports nothing and does not advance the cursor; success imports
    /// every record and advances the cursor.
    func testFirstSignInPullIsAtomic() {
        property(
            "Property 7: first-sign-in pull is atomic (all-or-nothing, cursor preserved on failure)",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(scenarioGen) { scenario in
            return self.atomicFirstPullHolds(scenario)
        }
    }

    // MARK: - Property under test

    private func atomicFirstPullHolds(_ scenario: Scenario) -> Bool {
        // Pre-pull snapshot: the state that must be preserved exactly when the
        // pull or the import fails (Req 6.10).
        let priorSnapshot = Dictionary(uniqueKeysWithValues: scenario.priorItems.map { ($0.id, $0) })

        let store = InMemorySyncStore(
            items: scenario.priorItems,
            failImportAtIndex: scenario.outcome == .importFailsPartway ? scenario.failIndex : nil
        )
        let transport = FirstPullTransport(
            mode: scenario.outcome == .pullFails
                ? .fail
                : .serve(items: scenario.pulledItems, token: scenario.serverToken)
        )
        // maxRetries: 0 → one SyncService attempt maps to one transport call.
        let backend = BackendClient(transport: transport, maxRetries: 0, retryDelay: 0)
        // First sign-in starts with no cursor (nil); a successful full pull is
        // the transition into normal incremental pulls.
        let service = SyncService(
            backend: backend,
            store: store,
            authorizer: StubAuthorizer(token: "valid-token"),
            maxAttempts: 2,
            retryDelay: 0,
            initialSyncToken: nil
        )

        let result = Self.runBlocking { () async -> (threw: Bool, cursor: Int64?) in
            var threw = false
            do {
                try await service.fullPullForFirstSignIn()
            } catch {
                threw = true
            }
            let cursor = await service.syncToken
            return (threw, cursor)
        }

        let finalItems = store.snapshot()

        switch scenario.outcome {
        case .success:
            // The pull and the atomic import both succeeded.
            guard !result.threw else { return false }
            // Cursor advanced to the server token → next pass is incremental.
            guard result.cursor == scenario.serverToken else { return false }
            // Every pulled record imported as server-authoritative (not dirty).
            for pulled in scenario.pulledItems {
                guard let stored = finalItems[pulled.id], !stored.sync.dirty else { return false }
            }
            // Every prior record still surfaces (import is purely additive here).
            for prior in scenario.priorItems {
                guard finalItems[prior.id] == prior else { return false }
            }
            // Exactly the prior + pulled ids are present, nothing invented.
            let expectedIds = Set(scenario.priorItems.map { $0.id })
                .union(scenario.pulledItems.map { $0.id })
            return Set(finalItems.keys) == expectedIds

        case .importFailsPartway:
            // The atomic import threw partway: nothing committed.
            guard result.threw else { return false }
            // All-or-nothing: store equals its prior state exactly.
            guard finalItems == priorSnapshot else { return false }
            // Cursor NOT advanced (still nil) → the next pass retries the full
            // pull (Req 6.10).
            return result.cursor == nil

        case .pullFails:
            // The pull failed on every attempt; the import was never reached.
            guard result.threw else { return false }
            guard store.importCallCount == 0 else { return false }
            // Local store preserved and cursor not advanced.
            return finalItems == priorSnapshot && result.cursor == nil
        }
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

/// The outcome under test in a trial.
private enum Outcome: CaseIterable {
    /// Pull succeeds and the atomic import commits.
    case success
    /// Pull succeeds but the atomic import throws partway through.
    case importFailsPartway
    /// The pull itself fails on every attempt, before any import.
    case pullFails
}

/// One randomly generated first-sign-in configuration.
private struct Scenario {
    /// The store's state before the pull (empty for a true first sign-in, or a
    /// disjoint non-empty set to prove preservation).
    var priorItems: [ActionItem]
    /// The account's full record set the server returns from `/sync/pull`.
    var pulledItems: [ActionItem]
    /// The outcome to drive.
    var outcome: Outcome
    /// The index into `pulledItems` at which the atomic import fails (only used
    /// when `outcome == .importFailsPartway`).
    var failIndex: Int
    /// The advanced cursor the server returns; strictly positive so advancing
    /// from the `nil` start is observable.
    var serverToken: Int64
}

// MARK: - Fakes

/// Supplies a fixed, always-valid bearer token so the auth seam never fails the
/// trial — the atomicity under test concerns the pull and the import, not auth.
private struct StubAuthorizer: SyncAuthorizer {
    let token: String
    func authorizationToken() async throws -> String { token }
}

/// A failure raised by the fake store to model a mid-import write error.
private enum ImportError: Error, Equatable {
    case failedAtIndex(Int)
}

/// In-memory ``SyncStore`` fake whose ``importAllAtomically(_:)`` faithfully
/// models the real GRDB single-transaction contract: it stages every write into
/// a scratch copy and commits it only after the whole set succeeds, so a
/// configured mid-import failure throws and leaves the persisted items exactly
/// as they were (all-or-nothing). The non-sync seam methods mirror the other
/// in-memory stores in this suite.
private final class InMemorySyncStore: SyncStore {

    private var items: [String: ActionItem]
    private let failImportAtIndex: Int?
    private(set) var importCallCount = 0

    init(items: [ActionItem], failImportAtIndex: Int? = nil) {
        self.items = Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
        self.failImportAtIndex = failImportAtIndex
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

    /// All-or-nothing import: stage into a scratch copy and commit only after
    /// every write succeeds; a configured failure index throws and discards the
    /// scratch, leaving `items` untouched (models the GRDB transaction rollback).
    func importAllAtomically(_ newItems: [ActionItem]) throws {
        importCallCount += 1
        var scratch = items
        for (index, item) in newItems.enumerated() {
            if let failAt = failImportAtIndex, index == failAt {
                throw ImportError.failedAtIndex(index)
            }
            var stored = item
            stored.sync.dirty = false
            scratch[item.id] = stored
        }
        items = scratch
    }
}

/// A stub `HTTPTransport` that serves the first-sign-in `/sync/pull` — either a
/// full record set with an advanced token, or a transient failure on every call
/// so the bounded-retry budget is exhausted before any import.
private final class FirstPullTransport: HTTPTransport, @unchecked Sendable {

    enum Mode {
        case serve(items: [ActionItem], token: Int64)
        case fail
    }

    private let mode: Mode
    private let encoder = SideQuestCoding.makeEncoder()
    private let lock = NSLock()
    private(set) var callCount = 0

    init(mode: Mode) {
        self.mode = mode
    }

    func perform(_ request: HTTPRequest) async throws -> HTTPResponse {
        lock.lock()
        callCount += 1
        lock.unlock()

        guard request.path == BackendEndpoints.syncPull else {
            return HTTPResponse(statusCode: 404, body: Data())
        }

        switch mode {
        case .fail:
            // Retryable transport failure (never produced a contract response).
            throw HTTPTransportError.offline

        case let .serve(items, token):
            let response = SyncPullResponse(changes: items, newSyncToken: token)
            return HTTPResponse(statusCode: 200, body: try encoder.encode(response))
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

/// Builds `count` `ActionItem`s with unique ids under `prefix`. Pulled records
/// are server-authoritative (not dirty); prior records are likewise clean so the
/// preservation comparison is exact.
private func itemsGen(prefix: String, count: Int) -> Gen<[ActionItem]> {
    Gen.compose { c in
        let createdAt = Date(timeIntervalSince1970: 1_700_000_000)
        return (0..<count).map { index in
            ActionItem(
                id: "\(prefix)-item-\(index)",
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
    }
}

private let scenarioGen: Gen<Scenario> = Gen.compose { c in
    // Prior store state: empty (true first sign-in) or a disjoint non-empty set.
    let priorCount = c.generate(using: Gen<Int>.choose((0, 4)))
    let priorItems = c.generate(using: itemsGen(prefix: "prior", count: priorCount))

    // The pulled set is always non-empty so an all-or-nothing import has a real
    // record it could leave behind on failure.
    let pulledCount = c.generate(using: Gen<Int>.choose((1, 6)))
    let pulledItems = c.generate(using: itemsGen(prefix: "pulled", count: pulledCount))

    let outcome = c.generate(using: Gen<Outcome>.fromElements(of: Outcome.allCases))
    // A valid mid-import failure index into the (non-empty) pulled set.
    let failIndex = c.generate(using: Gen<Int>.choose((0, pulledCount - 1)))
    let serverToken = Int64(c.generate(using: Gen<Int>.choose((1, 1_000_000))))

    return Scenario(
        priorItems: priorItems,
        pulledItems: pulledItems,
        outcome: outcome,
        failIndex: failIndex,
        serverToken: serverToken
    )
}
