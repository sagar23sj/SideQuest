import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

// Feature: ios-client, Property 8: Sync failures retain changes within a bounded
// retry count and preserve state.
//
/// Property-based test for **Property 8 — Sync failures retain changes within a
/// bounded retry count and preserve state** (task 16.3).
///
/// **Validates: Requirements 2.6, 6.9**
///
/// > *For any* sequence of push/pull failures, the number of retry attempts for a
/// > change does not exceed the configured maximum, the unsynchronized changes
/// > are retained across attempts, and on total failure the local store equals
/// > its pre-sync state.
///
/// Requirement 6.9: *"IF a synchronization push or pull fails, THEN THE
/// Sync_Service SHALL retain the unsynchronized changes, SHALL retry up to a
/// configured maximum number of attempts, and SHALL preserve the Local_Store
/// state on total failure."* Requirement 2.7 (exercised here as the
/// auth-failure case) adds that an authentication failure is **never**
/// auto-retried.
///
/// ## Strategy
///
/// The real ``SyncService`` is driven over two in-memory seams so the
/// bounded-retry / state-preservation logic runs on any host (no Apple
/// toolchain, no GRDB):
///
/// - a **fake `BackendClient`** built from a stub `HTTPTransport`
///   (``FailingTransport``) that fails a *configurable* number of times before
///   succeeding, either with a transient transport error (retryable, Req 2.6)
///   or with a `401` authentication response (never retried, Req 2.7). The
///   client is created with `maxRetries: 0`, so each `SyncService` attempt maps
///   to **exactly one** `transport.perform` call and the transport's call count
///   *is* the number of attempts the service made.
/// - a **fake `SyncStore`** (``InMemorySyncStore``) holding the local
///   `ActionItem`s; on a failed pass the service never reaches it, so its
///   contents are the pre-sync state to compare against.
///
/// For each trial SwiftCheck generates: a configured `maxAttempts` (1...5), a
/// number of failures before the transport would succeed (0...8), the failure
/// mode (transient vs. authentication), the operation under test (push vs.
/// pull), and a non-empty set of locally-dirty items. The trial runs the chosen
/// operation once and asserts the three facets of the property together:
///
/// - **bounded attempts** — the service makes at most `maxAttempts` attempts;
///   an authentication failure makes exactly one (no auto-retry, Req 2.7);
/// - **changes retained / state preserved on total failure** — when every
///   attempt fails, the local store is byte-for-byte its pre-sync state and the
///   dirty changes are still pending;
/// - **bounded on eventual success** — when a retry eventually succeeds, the
///   attempt count is `failuresBeforeSuccess + 1` and never exceeds
///   `maxAttempts`.
///
/// ## Generator notes (constraining to the valid input space)
///
/// - At least one dirty item is always seeded so a `push` performs a real
///   network call (an empty pending set is a no-op that makes no request and so
///   could not exercise retry).
/// - `failuresBeforeSuccess` deliberately spans both sides of the `maxAttempts`
///   boundary so trials cover total failure (`>= maxAttempts`) and eventual
///   success (`< maxAttempts`).
/// - A `pull` success returns an empty change set, so a successful pull leaves
///   the store unchanged too; the push/pull success branches are distinguished
///   in the expectation below.
final class BoundedRetryStatePreservationPropertyTests: XCTestCase {

    /// Property 8 / Req 2.6, 6.9: bounded retries, retained changes, preserved
    /// local store on total failure; auth failures are never retried (Req 2.7).
    func testSyncFailuresAreBoundedAndPreserveState() {
        property(
            "Property 8: sync failures retry within the configured maximum and preserve local state",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(scenarioGen) { scenario in
            return self.boundedRetryStatePreservationHolds(scenario)
        }
    }

    // MARK: - Property under test

    private func boundedRetryStatePreservationHolds(_ scenario: Scenario) -> Bool {
        // Pre-sync snapshot of the local store: this is the state that must be
        // preserved on total failure (Req 6.9) / never touched on auth failure.
        let snapshot = Dictionary(uniqueKeysWithValues: scenario.items.map { ($0.id, $0) })

        let store = InMemorySyncStore(items: scenario.items)
        let transport = FailingTransport(
            failuresBeforeSuccess: scenario.failuresBeforeSuccess,
            mode: scenario.failureMode
        )
        // maxRetries: 0 → the client does not retry, so one SyncService attempt
        // == one transport call and transport.callCount measures attempts.
        let backend = BackendClient(transport: transport, maxRetries: 0, retryDelay: 0)
        let service = SyncService(
            backend: backend,
            store: store,
            authorizer: StubAuthorizer(token: "valid-token"),
            maxAttempts: scenario.maxAttempts,
            retryDelay: 0
        )

        let outcome = Self.runBlocking { () async -> TrialOutcome in
            do {
                switch scenario.operation {
                case .push: _ = try await service.push()
                case .pull: _ = try await service.pull()
                }
                return .success
            } catch let error as SyncError {
                switch error {
                case .authenticationFailed:           return .authFailed
                case .retriesExhausted(let n, _):     return .retriesExhausted(attempts: n)
                case .notAuthenticated:               return .other("notAuthenticated")
                }
            } catch {
                return .other(String(describing: error))
            }
        }

        let attempts = transport.callCount
        let finalItems = store.snapshot()
        let maxAttempts = scenario.maxAttempts

        switch scenario.failureMode {
        case .authentication:
            // Req 2.7: an auth failure surfaces immediately and is NOT retried —
            // exactly one attempt — and the store is never touched.
            guard case .authFailed = outcome else { return false }
            return attempts == 1
                && attempts <= maxAttempts
                && finalItems == snapshot

        case .transient:
            if scenario.failuresBeforeSuccess >= maxAttempts {
                // Total failure: every attempt failed. Attempts are capped at the
                // configured maximum, retries are exhausted, and the local store
                // (including dirty flags) is preserved (Req 6.9).
                guard case .retriesExhausted(let n) = outcome else { return false }
                return n == maxAttempts
                    && attempts == maxAttempts
                    && attempts <= maxAttempts
                    && finalItems == snapshot
                    && self.allItemsDirty(finalItems)
            } else {
                // A retry eventually succeeds: the attempt count is the failures
                // plus the one success, and never exceeds the configured maximum.
                guard case .success = outcome else { return false }
                let attemptsOK = attempts == scenario.failuresBeforeSuccess + 1
                    && attempts <= maxAttempts
                switch scenario.operation {
                case .pull:
                    // A successful pull returns no remote changes, so the store
                    // is unchanged and the dirty items are still pending.
                    return attemptsOK && finalItems == snapshot
                case .push:
                    // A successful push acknowledges every pushed item, clearing
                    // its dirty flag — the only legitimate state change here.
                    return attemptsOK && self.noItemsDirty(finalItems)
                }
            }
        }
    }

    // MARK: - Helpers

    private func allItemsDirty(_ items: [String: ActionItem]) -> Bool {
        !items.isEmpty && items.values.allSatisfy { $0.sync.dirty }
    }

    private func noItemsDirty(_ items: [String: ActionItem]) -> Bool {
        items.values.allSatisfy { !$0.sync.dirty }
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

// MARK: - Trial outcome

/// The observable result of running one push/pull pass.
private enum TrialOutcome {
    case success
    case authFailed
    case retriesExhausted(attempts: Int)
    case other(String)
}

// MARK: - Scenario

/// The operation under test in a trial.
private enum Operation: CaseIterable {
    case push
    case pull
}

/// One randomly generated trial configuration.
private struct Scenario {
    var maxAttempts: Int
    var failuresBeforeSuccess: Int
    var failureMode: FailingTransport.Mode
    var operation: Operation
    var items: [ActionItem]
}

// MARK: - Fakes

/// Supplies a fixed, always-valid bearer token so the auth seam never fails the
/// trial — the failures under test come from the transport, not the authorizer.
private struct StubAuthorizer: SyncAuthorizer {
    let token: String
    func authorizationToken() async throws -> String { token }
}

/// In-memory ``SyncStore`` fake. Holds the local `ActionItem`s and applies the
/// same dirty/version semantics the real `ActionItemRepository` does, so the
/// property can observe whether a pass touched the store.
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

/// A stub `HTTPTransport` that fails a configurable number of times before
/// returning a valid contract response.
///
/// - `.transient` mode throws `HTTPTransportError.offline` for the first
///   `failuresBeforeSuccess` calls — a retryable failure (Req 2.6) — then
///   returns a 200 with the matching push/pull response body.
/// - `.authentication` mode always returns a `401`, which the contract maps to
///   an authentication failure that is never auto-retried (Req 2.7).
///
/// `callCount` records how many times the transport was invoked; with the
/// owning `BackendClient` created at `maxRetries: 0`, that equals the number of
/// attempts `SyncService` made.
private final class FailingTransport: HTTPTransport, @unchecked Sendable {

    enum Mode: CaseIterable {
        case transient
        case authentication
    }

    private let failuresBeforeSuccess: Int
    private let mode: Mode
    private let encoder = SideQuestCoding.makeEncoder()
    private let lock = NSLock()
    private var _callCount = 0

    var callCount: Int {
        lock.lock(); defer { lock.unlock() }
        return _callCount
    }

    init(failuresBeforeSuccess: Int, mode: Mode) {
        self.failuresBeforeSuccess = failuresBeforeSuccess
        self.mode = mode
    }

    func perform(_ request: HTTPRequest) async throws -> HTTPResponse {
        lock.lock()
        _callCount += 1
        let attempt = _callCount
        lock.unlock()

        switch mode {
        case .authentication:
            // Always an auth failure → surfaced immediately, never retried.
            return HTTPResponse(statusCode: 401, body: Data())

        case .transient:
            if attempt <= failuresBeforeSuccess {
                throw HTTPTransportError.offline
            }
            return HTTPResponse(statusCode: 200, body: successBody(for: request))
        }
    }

    /// A valid contract response body for the requested endpoint.
    private func successBody(for request: HTTPRequest) -> Data {
        if request.path == BackendEndpoints.syncPush {
            return (try? encoder.encode(SyncPushResponse(applied: 0, newSyncToken: 1))) ?? Data()
        }
        // /sync/pull: no remote changes, so a successful pull leaves the store
        // unchanged.
        return (try? encoder.encode(SyncPullResponse(changes: [], newSyncToken: 1))) ?? Data()
    }
}

// MARK: - Generators (constrain to the relevant input space)

/// Configured retry budget (Req 6.9). At least 1 (the service clamps to 1 too).
private let maxAttemptsGen = Gen<Int>.choose((1, 5))

/// Failures before the transport would succeed. Spans both sides of the
/// `maxAttempts` boundary so trials cover total failure and eventual success.
private let failuresBeforeSuccessGen = Gen<Int>.choose((0, 8))

private let failureModeGen = Gen<FailingTransport.Mode>.fromElements(of: FailingTransport.Mode.allCases)

private let operationGen = Gen<Operation>.fromElements(of: Operation.allCases)

private let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)

private let titleGen: Gen<String> = Gen<Int>.choose((1, 12)).flatMap { size in
    Gen<Character>
        .fromElements(of: Array("abcdefghijklmnopqrstuvwxyz ABCDEF0123456789"))
        .proliferate(withSize: size)
        .map { String($0) }
}

/// A non-empty set of locally-dirty `ActionItem`s with unique ids, so a push
/// always performs a real network call and the dirty set is observable.
private let dirtyItemsGen: Gen<[ActionItem]> = Gen.compose { c in
    let count = c.generate(using: Gen<Int>.choose((1, 5)))
    let now = Date(timeIntervalSince1970: 1_700_000_000)
    return (0..<count).map { index in
        ActionItem(
            id: "ret-item-\(index)",
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

private let scenarioGen: Gen<Scenario> = Gen.compose { c in
    Scenario(
        maxAttempts: c.generate(using: maxAttemptsGen),
        failuresBeforeSuccess: c.generate(using: failuresBeforeSuccessGen),
        failureMode: c.generate(using: failureModeGen),
        operation: c.generate(using: operationGen),
        items: c.generate(using: dirtyItemsGen)
    )
}
