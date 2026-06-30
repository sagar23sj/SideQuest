import Foundation

// MARK: - SyncService (task 16.1)
//
// Implements the client side of the shared sync protocol against the contract
// (`backend/api/openapi.yaml` → `/sync/push`, `/sync/pull`):
//
//   POST /sync/push  { changes, lastSyncToken } -> { applied, newSyncToken }
//   GET  /sync/pull?since={token}               -> { changes, newSyncToken }
//
// The repository writes locally first and marks every mutation `dirty`
// (Req 5.6); this service later pushes those dirty `ActionItem`s and pulls
// remote changes, reconciling them into the local store. It is built from three
// injected seams so the merge / idempotency / bounded-retry logic is fully
// unit-testable on any host (the GRDB-backed `SyncStore` is the only Apple-only
// piece):
//
//   * `BackendClient` — the REST/JSON transport (per-request transient retries,
//     contract error mapping; task 15.3).
//   * `SyncStore`     — the local-store seam (`ActionItemRepository` on device).
//   * `SyncAuthorizer`— supplies the bearer token; the server derives the
//     account from that token, so pushed/pulled data is associated with the
//     current account without ever trusting an account id in the body
//     (Req 10.2).
//
// Behavioural guarantees implemented here:
//   * Idempotent pushes keyed on the client-generated `id`: the dirty set is
//     captured once and the same payload is re-sent on every retry, so the
//     server (which dedupes by account + id) never creates duplicates (Req 6.8).
//   * Deletes propagate as tombstones (`sync.deleted == true`) — they are just
//     dirty changes in the push payload and applied like any other pulled
//     change (Req 6.3).
//   * Conflicts resolve with the portable last-writer-wins logic from task 4.17
//     (`Domain.resolveActionItem`), keyed on `updatedAt` and tie-broken by id
//     (Req 6.2).
//   * `dirty` is cleared only on a push acknowledgment, and only when the
//     persisted version still matches what was pushed (Req 5.6).
//   * On push/pull failure the changes are retained and the call is retried up
//     to `maxAttempts`; authentication failures are never auto-retried
//     (Req 2.7). On total failure the local store is preserved, because the
//     store is mutated only after a successful network call (Req 6.9).
//
// `fullPullForFirstSignIn()` (all-or-nothing first pull) lives below; the
// `BGTaskScheduler` / foreground / connectivity triggers (task 16.6) wrap this
// service in the Apple-only `SyncCoordinator`.

// MARK: - SyncAuthorizer (bearer-token seam)

/// Supplies a currently-valid bearer token for the sync calls. The backend
/// reads the account from this token, so it is how synced data is associated
/// with the signed-in account (Req 10.2). Implemented by `AuthService`
/// (see the conformance below); fakes implement it in tests.
public protocol SyncAuthorizer: Sendable {

    /// A valid access token for authenticating `/sync/*` requests, refreshing
    /// silently first if needed (Req 10.5). Throws when the user is not
    /// authenticated or the session cannot be refreshed (Req 10.7).
    func authorizationToken() async throws -> String
}

extension AuthService: SyncAuthorizer {

    /// Bridges `AuthService.accessToken()` (silent refresh on expiry, Req 10.5)
    /// to the `SyncAuthorizer` seam.
    public func authorizationToken() async throws -> String {
        try await accessToken()
    }
}

// MARK: - SyncError

/// A failure surfaced by ``SyncService`` after its own handling/retry policy.
public enum SyncError: Error, Equatable {

    /// No usable session is available (no token / not signed in). The caller
    /// routes to authentication; the local store is untouched.
    case notAuthenticated

    /// The backend rejected the request as an authentication failure (401).
    /// Never auto-retried (Req 2.7); the caller re-authenticates.
    case authenticationFailed(BackendError)

    /// A push or pull failed on every attempt up to `attempts` (Req 6.9). The
    /// local store is preserved. `lastErrorDescription` captures the final
    /// underlying failure for diagnostics.
    case retriesExhausted(attempts: Int, lastErrorDescription: String)
}

// MARK: - SyncService

/// Drives `/sync/push` and `/sync/pull` with idempotent, bounded-retry
/// behaviour (task 16.1).
///
/// An `actor` so the in-memory sync-token cursor and concurrent passes can't
/// race (mirrors `AuthService`).
public actor SyncService {

    /// Default maximum attempts per push/pull pass (the initial try plus
    /// retries), i.e. the "configured maximum" of Req 6.9.
    public static let defaultMaxAttempts = 3

    /// Maximum attempts for a single push or pull pass (Req 6.9). At least 1.
    public let maxAttempts: Int

    private let backend: BackendClient
    private let store: SyncStore
    private let authorizer: SyncAuthorizer
    private let retryDelay: TimeInterval

    /// The last sync-token cursor known to this service, advanced by every
    /// successful push/pull. Persisting it across launches is layered on in
    /// task 16.6; here it lives for the lifetime of the service.
    private var lastSyncToken: Int64?

    /// - Parameters:
    ///   - backend: the REST/JSON client (task 15.3).
    ///   - store: the local-store seam (`ActionItemRepository` on device).
    ///   - authorizer: supplies the bearer token (`AuthService` on device).
    ///   - maxAttempts: per-pass attempt budget (default 3, Req 6.9); clamped
    ///     to at least 1.
    ///   - retryDelay: delay between attempts; inject 0 in tests.
    ///   - initialSyncToken: a previously-persisted cursor to resume from.
    public init(
        backend: BackendClient,
        store: SyncStore,
        authorizer: SyncAuthorizer,
        maxAttempts: Int = SyncService.defaultMaxAttempts,
        retryDelay: TimeInterval = 0.5,
        initialSyncToken: Int64? = nil
    ) {
        self.backend = backend
        self.store = store
        self.authorizer = authorizer
        self.maxAttempts = max(1, maxAttempts)
        self.retryDelay = retryDelay
        self.lastSyncToken = initialSyncToken
    }

    /// The current sync-token cursor, exposed so task 16.6 can persist it.
    public var syncToken: Int64? {
        lastSyncToken
    }

    // MARK: - Push (Req 6.1, 6.8, 6.9)

    /// Pushes the locally-dirty `ActionItem`s — creates, edits, and tombstoned
    /// deletes alike — to `/sync/push`, then clears their `dirty` flag on the
    /// server's acknowledgment (Req 5.6, 6.1, 6.3).
    ///
    /// The dirty set is captured **once** before the retry loop, so a retried
    /// push re-sends the identical payload; combined with the server's dedupe by
    /// account + client `id`, retries never create duplicates (Req 6.8). When
    /// there is nothing dirty, the call is a no-op and makes no network request.
    ///
    /// On failure the changes are retained and the push is retried up to
    /// ``maxAttempts``; the store is only mutated after a successful response,
    /// so a total failure preserves local-store state (Req 6.9). An
    /// authentication failure is never auto-retried (Req 2.7).
    @discardableResult
    public func push() async throws -> SyncOutcome {
        let pending = try store.pendingPushItems()
        guard !pending.isEmpty else {
            return SyncOutcome()
        }

        let token = try await authorizer.authorizationToken()
        let request = SyncPushRequest(changes: pending, lastSyncToken: lastSyncToken)

        let response: SyncPushResponse = try await withBoundedRetry {
            try await self.backend.post(
                BackendEndpoints.syncPush,
                body: request,
                authToken: token,
                as: SyncPushResponse.self
            )
        }

        // Clear dirty for exactly the versions we pushed; an edit made after the
        // push was sent has a newer version and stays dirty (Req 5.6).
        for item in pending {
            try store.acknowledgePush(id: item.id, version: item.sync.version)
        }

        lastSyncToken = response.newSyncToken
        return SyncOutcome(
            pushedCount: pending.count,
            appliedCount: response.applied,
            newSyncToken: response.newSyncToken
        )
    }

    // MARK: - Pull (Req 6.1, 6.2, 6.3, 6.9)

    /// Pulls remote changes since `token` (or the service's stored cursor when
    /// `token` is `nil`) from `/sync/pull` and merges them into the local store
    /// with last-writer-wins (Req 6.1, 6.2). Tombstones in the response are
    /// applied like any other change, so deletes reach this device (Req 6.3).
    ///
    /// Each remote change is reconciled against the local version via
    /// ``Domain/resolveActionItem(_:_:)`` (task 4.17): the remote write is
    /// applied only when it wins, otherwise the local (still-dirty) version is
    /// kept for its own push. Applying a remote change clears its `dirty` flag,
    /// since it is now reconciled with the server.
    ///
    /// On failure the pull is retried up to ``maxAttempts``; the store is
    /// mutated only after a successful response, so a total failure preserves
    /// local-store state (Req 6.9). An authentication failure is never
    /// auto-retried (Req 2.7).
    @discardableResult
    public func pull(since token: Int64? = nil) async throws -> SyncOutcome {
        let authToken = try await authorizer.authorizationToken()
        let since = token ?? lastSyncToken

        var query: [URLQueryItem] = []
        if let since {
            query.append(URLQueryItem(name: "since", value: String(since)))
        }

        let response: SyncPullResponse = try await withBoundedRetry {
            try await self.backend.get(
                BackendEndpoints.syncPull,
                query: query,
                authToken: authToken,
                as: SyncPullResponse.self
            )
        }

        let mergedCount = try merge(remoteChanges: response.changes)
        lastSyncToken = response.newSyncToken
        return SyncOutcome(
            pulledCount: response.changes.count,
            mergedCount: mergedCount,
            newSyncToken: response.newSyncToken
        )
    }

    // MARK: - Sync pass (Req 6.4, 6.6)

    /// Runs one full synchronization pass — pushing locally-dirty changes, then
    /// pulling and merging remote changes — and returns the combined outcome.
    ///
    /// This is the unit of work the triggers in ``SyncCoordinator`` invoke on
    /// connectivity restore, foreground entry, and each `BGTaskScheduler` run
    /// (Req 6.4, 6.5, 6.6). Push is performed first so local work reaches the
    /// server before the pull merges remote state on top of it. Either phase may
    /// throw (auth failure or exhausted retries); the store is preserved in that
    /// case (Req 6.9) and the caller decides whether to surface or swallow the
    /// error (background passes treat transient failures as non-fatal).
    @discardableResult
    public func sync() async throws -> SyncOutcome {
        let pushed = try await push()
        let pulled = try await pull()
        return SyncOutcome(
            pushedCount: pushed.pushedCount,
            appliedCount: pushed.appliedCount,
            pulledCount: pulled.pulledCount,
            mergedCount: pulled.mergedCount,
            newSyncToken: pulled.newSyncToken ?? pushed.newSyncToken
        )
    }

    // MARK: - First sign-in full pull (all-or-nothing, Req 6.7, 6.10)

    /// Performs the first-sign-in full pull on a device: fetches the account's
    /// entire `ActionItem` set from `/sync/pull` (no `since` cursor) and imports
    /// it into the local store **atomically** (Req 6.7).
    ///
    /// All-or-nothing is delivered by handing the whole pulled set to the
    /// store's single ``SyncStore/importAllAtomically(_:)`` transaction rather
    /// than applying changes one at a time. If the pull fails (after the bounded
    /// retry budget) nothing has been imported yet; if the atomic import fails
    /// partway it rolls back. In both cases the local store keeps its prior
    /// state and the sync-token cursor is **not** advanced, so the caller can
    /// surface a "data could not be retrieved" message and the next ordinary
    /// sync pass retries the full pull from scratch (Req 6.10, Property 7). The
    /// error is re-thrown for the caller to present.
    ///
    /// The cursor is advanced to the pull's `newSyncToken` only after the import
    /// has committed in full, so a successful first pull transitions the service
    /// into normal incremental pulls. An authentication failure is never
    /// auto-retried (Req 2.7).
    public func fullPullForFirstSignIn() async throws {
        let authToken = try await authorizer.authorizationToken()

        // Full set: omit `since` so the server returns every record (including
        // tombstones) for the account derived from the token.
        let response: SyncPullResponse = try await withBoundedRetry {
            try await self.backend.get(
                BackendEndpoints.syncPull,
                query: [],
                authToken: authToken,
                as: SyncPullResponse.self
            )
        }

        // One atomic import — a mid-import failure leaves nothing behind and
        // does not advance the cursor, so the next pass retries (Req 6.10).
        try store.importAllAtomically(response.changes)
        lastSyncToken = response.newSyncToken
    }

    // MARK: - Merge (last-writer-wins, Req 6.2)

    /// Reconciles `remoteChanges` into the local store with deterministic
    /// last-writer-wins, returning the number of local records actually changed.
    ///
    /// For each remote change: if there is no local version, the remote is
    /// applied (a new record or an incoming tombstone). Otherwise the two
    /// versions are resolved with ``Domain/resolveActionItem(_:_:)``; the remote
    /// is applied only when it strictly wins, leaving a locally-dirty winner in
    /// place for its own push. The merge is idempotent: re-running it after a
    /// retry re-applies nothing, because the just-applied remote now equals the
    /// local version.
    private func merge(remoteChanges: [ActionItem]) throws -> Int {
        var mergedCount = 0
        for remote in remoteChanges {
            guard let local = try store.localItem(id: remote.id) else {
                try store.applyRemoteChange(remote)
                mergedCount += 1
                continue
            }
            // Equal versions need no write; otherwise apply only when the remote
            // wins last-writer-wins (a tie/equal value resolves to the local
            // first argument, so it is skipped here).
            if remote != local {
                let winner = Domain.resolveActionItem(local, remote).winner
                if winner == remote {
                    try store.applyRemoteChange(remote)
                    mergedCount += 1
                }
            }
        }
        return mergedCount
    }

    // MARK: - Bounded retry (Req 6.9, 2.7)

    /// Runs `operation` (a single network call), retrying transient failures up
    /// to ``maxAttempts`` total attempts with `retryDelay` between them
    /// (Req 6.9). Authentication failures are surfaced immediately without
    /// retry (Req 2.7); exhausting the budget throws
    /// ``SyncError/retriesExhausted(attempts:lastErrorDescription:)``.
    private func withBoundedRetry<T>(_ operation: () async throws -> T) async throws -> T {
        var attempt = 1
        while true {
            do {
                return try await operation()
            } catch let error as BackendError where error.isAuthenticationFailure {
                throw SyncError.authenticationFailed(error)
            } catch {
                if attempt >= maxAttempts {
                    throw SyncError.retriesExhausted(
                        attempts: attempt,
                        lastErrorDescription: String(describing: error)
                    )
                }
                attempt += 1
                if retryDelay > 0 {
                    try? await Task.sleep(nanoseconds: UInt64(retryDelay * 1_000_000_000))
                }
            }
        }
    }
}
