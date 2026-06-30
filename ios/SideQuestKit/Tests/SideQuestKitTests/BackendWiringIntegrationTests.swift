import XCTest
import Foundation
@testable import SideQuestKit

// MARK: - Backend wiring integration tests (task 18.4)
//
// These tests exercise the real service objects wired together over a single
// in-memory mock backend, rather than testing one unit in isolation:
//
//   * Auth: `AuthService` → `BackendAuthTransport` → `BackendClient` →
//     `HTTPTransport` (the mock), with tokens persisted in a `TokenStore`
//     (the in-memory stand-in for the on-device Keychain — Req 10.4). Covers
//     `POST /accounts`, `POST /auth/login`, and silent `POST /auth/refresh`
//     (Req 2.4, 10.1, 10.4).
//   * Sync: two `SyncService`s (device A and device B) over the same mock
//     account, each authorized by its own real `AuthService`, so a
//     `/sync/push` on device A followed by a `/sync/pull` on device B makes the
//     data available on the "second device" (Req 6.1, 6.7, 10.3).
//   * Notifications: the scheduling layer produces a calendar trigger anchored
//     within 60 s of the intended local wall-clock time, and pending requests
//     survive a simulated reboot + reschedule-on-launch (Req 7.6, 7.10, 7.11).
//
// The mock backend is reached through the same `BackendClient`/`HTTPTransport`
// seam production uses, so real contract JSON (the `Generated_Models`) is
// serialized on the wire in both directions — these are wiring tests, not
// mocks of the services under test.
//
// **Validates: Requirements 2.4, 6.1, 6.7, 7.6, 7.11, 10.1, 10.3, 10.4**

final class BackendWiringIntegrationTests: XCTestCase {

    // MARK: - Auth: /accounts, /auth/login, /auth/refresh + token storage

    /// Registering through `POST /accounts` returns the account and stores the
    /// issued token pair in the token store (the Keychain seam — Req 10.4); a
    /// subsequent `accessToken()` returns the stored, still-valid token without
    /// a network refresh (Req 10.1).
    func testRegisterStoresTokensAndReturnsAccount() async throws {
        let mock = MockBackend(accessTokenLifetime: 3600)
        let tokenStore = InMemoryTokenStore()                 // stands in for KeychainTokenStore (Req 10.4)
        let auth = makeAuthService(mock: mock, tokenStore: tokenStore)

        let account = try await auth.register(
            CreateAccountRequest(email: "ada@example.com", password: "correct horse", displayName: "Ada")
        )

        XCTAssertEqual(account.email, "ada@example.com")

        // Token pair was persisted to the (Keychain) store on sign-up (Req 10.4).
        let stored = try tokenStore.loadTokens()
        XCTAssertNotNil(stored)
        let isSignedIn = await auth.isSignedIn
        XCTAssertTrue(isSignedIn)

        // A valid stored access token is returned directly — no refresh call.
        let token = try await auth.accessToken()
        XCTAssertEqual(token, stored?.accessToken)
        XCTAssertEqual(mock.refreshCallCount, 0)
    }

    /// `POST /auth/login` with valid credentials returns the account and stores
    /// fresh tokens; invalid credentials surface `AuthError.invalidCredentials`
    /// and are never auto-retried (Req 10.1, 10.8).
    func testLoginSucceedsWithValidCredentialsAndRejectsInvalid() async throws {
        let mock = MockBackend(accessTokenLifetime: 3600)
        _ = mock.preregister(email: "grace@example.com", password: "hopper", displayName: "Grace")

        let tokenStore = InMemoryTokenStore()
        let auth = makeAuthService(mock: mock, tokenStore: tokenStore)

        let account = try await auth.signIn(LoginRequest(email: "grace@example.com", password: "hopper"))
        XCTAssertEqual(account.email, "grace@example.com")
        XCTAssertNotNil(try tokenStore.loadTokens())

        // Wrong password → credentials-not-accepted, no token stored over a
        // fresh store, no auto-retry.
        let freshStore = InMemoryTokenStore()
        let auth2 = makeAuthService(mock: mock, tokenStore: freshStore)
        do {
            _ = try await auth2.signIn(LoginRequest(email: "grace@example.com", password: "wrong"))
            XCTFail("Expected invalid credentials to throw")
        } catch let error as AuthError {
            guard case .invalidCredentials = error else {
                return XCTFail("Expected .invalidCredentials, got \(error)")
            }
        }
        XCTAssertNil(try freshStore.loadTokens())
    }

    /// When the stored access token has expired, `accessToken()` silently
    /// refreshes through `POST /auth/refresh`, persists the new pair to the
    /// token store, and returns the refreshed token — the local store is never
    /// touched (Req 10.5, validated end-to-end here for the wiring; Req 2.4).
    func testExpiredAccessTokenTriggersSilentRefresh() async throws {
        let clock = TestClock(start: Date(timeIntervalSince1970: 1_700_000_000))
        // 60 s access lifetime so we can step the clock past expiry.
        let mock = MockBackend(accessTokenLifetime: 60, now: { clock.now })
        let tokenStore = InMemoryTokenStore()
        let auth = AuthService(
            transport: BackendAuthTransport(client: BackendClient(transport: MockBackendTransport(backend: mock), maxRetries: 0, retryDelay: 0)),
            tokenStore: tokenStore,
            expiryLeeway: 30,
            now: { clock.now }
        )

        _ = try await auth.register(
            CreateAccountRequest(email: "linus@example.com", password: "kernel", displayName: "Linus")
        )
        let firstToken = try tokenStore.loadTokens()?.accessToken

        // Advance past access-token expiry (60 s lifetime + 30 s leeway).
        clock.advance(by: 120)

        let refreshed = try await auth.accessToken()

        XCTAssertEqual(mock.refreshCallCount, 1, "Exactly one silent refresh should have occurred")
        XCTAssertNotEqual(refreshed, firstToken, "A new access token should be issued on refresh")
        XCTAssertEqual(try tokenStore.loadTokens()?.accessToken, refreshed, "The refreshed pair must be persisted to the (Keychain) store")
    }

    // MARK: - Sync: /sync/push + /sync/pull round trip across two devices

    /// Device A creates items locally (dirty), pushes them through `/sync/push`,
    /// and device B — a second device signed in to the same account — sees the
    /// same records after a `/sync/pull` (Req 6.1, 6.7, 10.3). The account is
    /// derived from each device's bearer token (Req 10.2), so no account id is
    /// trusted from the body.
    func testSyncPushPullRoundTripMakesDataAvailableOnSecondDevice() async throws {
        let mock = MockBackend(accessTokenLifetime: 3600)
        let transport = MockBackendTransport(backend: mock)

        // Device A: register (creates the account) and sign in.
        let authA = makeAuthService(transport: transport)
        let account = try await authA.register(
            CreateAccountRequest(email: "team@example.com", password: "shared-secret", displayName: "Team")
        )

        // Device B: a freshly signed-in second device on the SAME account.
        let authB = makeAuthService(transport: transport)
        _ = try await authB.signIn(LoginRequest(email: "team@example.com", password: "shared-secret"))

        // Device A's local store holds dirty items associated with the account.
        let createdBase = Date(timeIntervalSince1970: 1_700_000_000)
        let localItems = (0..<3).map { index in
            ActionItem(
                id: "item-\(index)",
                accountId: account.id,
                bucketId: "bucket-1",
                title: "Task \(index)",
                contentType: .text,
                timeframe: .today,
                status: .notStarted,
                createdAt: createdBase.addingTimeInterval(TimeInterval(index) * 60),
                sync: SyncMeta(updatedAt: createdBase, version: 1, deleted: false, dirty: true)
            )
        }
        let deviceAStore = InMemorySyncStore(items: localItems)
        let deviceBStore = InMemorySyncStore(items: [])

        let deviceA = makeSyncService(transport: transport, store: deviceAStore, authorizer: authA)
        let deviceB = makeSyncService(transport: transport, store: deviceBStore, authorizer: authB)

        let pushOutcome = try await deviceA.push()
        XCTAssertEqual(pushOutcome.pushedCount, 3)

        let pullOutcome = try await deviceB.pull()
        XCTAssertEqual(pullOutcome.mergedCount, 3, "Device B should merge all three records")

        // Device B's non-deleted records equal device A's, field-by-field
        // (ignoring the client-only `dirty` flag, which is not synced).
        let expected = liveNormalized(localItems)
        let actual = liveNormalized(Array(deviceBStore.snapshot().values))
        XCTAssertEqual(actual, expected)

        // Device A's pushed items are no longer dirty (push acknowledged).
        XCTAssertTrue(try deviceAStore.pendingPushItems().isEmpty)
    }

    /// A delete made on device A propagates as a tombstone through a push/pull
    /// round trip, so device B no longer surfaces the record among its live
    /// records (Req 6.3) — a representative cross-device sync case.
    func testTombstonePropagatesAcrossRoundTrip() async throws {
        let mock = MockBackend(accessTokenLifetime: 3600)
        let transport = MockBackendTransport(backend: mock)

        let authA = makeAuthService(transport: transport)
        let account = try await authA.register(
            CreateAccountRequest(email: "del@example.com", password: "pw", displayName: "Del")
        )
        let authB = makeAuthService(transport: transport)
        _ = try await authB.signIn(LoginRequest(email: "del@example.com", password: "pw"))

        let base = Date(timeIntervalSince1970: 1_700_000_000)
        let live = ActionItem(
            id: "keep", accountId: account.id, bucketId: "b", title: "Keep",
            contentType: .text, timeframe: .today, status: .notStarted,
            createdAt: base, sync: SyncMeta(updatedAt: base, version: 1, deleted: false, dirty: true)
        )
        let tombstone = ActionItem(
            id: "gone", accountId: account.id, bucketId: "b", title: "Gone",
            contentType: .text, timeframe: .today, status: .notStarted,
            createdAt: base, sync: SyncMeta(updatedAt: base.addingTimeInterval(60), version: 2, deleted: true, dirty: true)
        )

        let deviceAStore = InMemorySyncStore(items: [live, tombstone])
        let deviceBStore = InMemorySyncStore(items: [])
        let deviceA = makeSyncService(transport: transport, store: deviceAStore, authorizer: authA)
        let deviceB = makeSyncService(transport: transport, store: deviceBStore, authorizer: authB)

        _ = try await deviceA.push()
        _ = try await deviceB.pull()

        let liveIds = Set(deviceBStore.snapshot().values.filter { !$0.sync.deleted }.map(\.id))
        XCTAssertEqual(liveIds, ["keep"], "The tombstoned record must not surface as live on device B")
    }

    // MARK: - Notifications: local-wall-clock anchoring + reboot survival

    /// A scheduled task reminder is anchored to a local wall-clock instant
    /// within 60 s of the intended time (Req 7.6, 7.10): the pure scheduling
    /// derivation resolves to the configured hour/minute on the firing day in
    /// the device's local time zone.
    func testReminderIsAnchoredWithin60sOfLocalTime() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!

        let now = calendar.date(from: DateComponents(year: 2025, month: 6, day: 14, hour: 8))!
        let untilDate = calendar.date(byAdding: .day, value: 3, to: now)!
        let reminder = TaskReminder(
            actionItemId: "item-1",
            timeOfDay: TimeOfDay(hour: 9, minute: 30),
            untilDate: untilDate,
            recurringDaily: false
        )

        let days = ReminderOccurrences.occurrenceDays(
            for: reminder, isCompleted: false, now: now, calendar: calendar
        )
        XCTAssertEqual(days.count, 1, "A one-shot reminder has a single firing day")

        let components = NotificationScheduling.components(
            at: reminder.timeOfDay, onDayOf: days[0], calendar: calendar
        )
        // The anchored instant the system would resolve the trigger to.
        let anchored = calendar.date(from: components)!

        // Intended local wall-clock instant: 09:30 local on the firing day.
        let intended = calendar.date(
            bySettingHour: 9, minute: 30, second: 0, of: days[0]
        )!

        XCTAssertLessThanOrEqual(
            abs(anchored.timeIntervalSince(intended)), 60,
            "The scheduled trigger must fire within 60 s of the intended local time"
        )
        // No fixed time zone is baked into the components — that is what lets it
        // track local wall-clock time across a time-zone change (Req 7.10).
        XCTAssertNil(components.timeZone)
    }

    /// Pending reminder requests survive a simulated reboot and a
    /// reschedule-on-launch pass: the system-persisted requests remain, and
    /// re-deriving the day-set on launch is idempotent — no request is lost or
    /// duplicated (Req 7.11).
    func testPendingRequestsSurviveRebootAndRescheduleOnLaunch() throws {
        let calendar = Calendar(identifier: .gregorian)
        let now = Date(timeIntervalSince1970: 1_700_000_000)
        let untilDate = calendar.date(byAdding: .day, value: 4, to: now)!
        let reminder = TaskReminder(
            actionItemId: "item-7",
            timeOfDay: TimeOfDay(hour: 18, minute: 0),
            untilDate: untilDate,
            recurringDaily: true
        )

        // The OS-persisted pending-request store (calendar triggers persist
        // across reboot at the system level — Req 7.11).
        let system = PersistentPendingStore()

        func scheduledIdentifiers() -> [String] {
            ReminderOccurrences
                .occurrenceDays(for: reminder, isCompleted: false, now: now, calendar: calendar)
                .map { day in
                    NotificationIdentifier.taskReminder(
                        itemId: reminder.actionItemId,
                        occurrence: ReminderOccurrences.occurrenceKey(for: day, calendar: calendar)
                    )
                }
        }

        // First launch: schedule the recurring day-set.
        let initial = scheduledIdentifiers()
        XCTAssertFalse(initial.isEmpty)
        system.replaceRequests(forItem: reminder.actionItemId, identifiers: initial)
        let afterFirstSchedule = system.identifiers()

        // --- Simulated reboot --- the `system` store keeps its requests; only
        // in-process state is discarded. Reschedule-on-launch recomputes the
        // same idempotent day-set.
        let onLaunch = scheduledIdentifiers()
        system.replaceRequests(forItem: reminder.actionItemId, identifiers: onLaunch)

        XCTAssertEqual(
            Set(system.identifiers()), Set(afterFirstSchedule),
            "Pending requests must survive the reboot/reschedule unchanged"
        )
        XCTAssertEqual(
            system.identifiers().count, Set(system.identifiers()).count,
            "Reschedule-on-launch must not duplicate requests"
        )
    }

    // MARK: - Helpers

    private func makeAuthService(mock: MockBackend, tokenStore: TokenStore) -> AuthService {
        let transport = MockBackendTransport(backend: mock)
        return makeAuthService(transport: transport, tokenStore: tokenStore)
    }

    private func makeAuthService(transport: MockBackendTransport, tokenStore: TokenStore = InMemoryTokenStore()) -> AuthService {
        let backend = BackendClient(transport: transport, maxRetries: 0, retryDelay: 0)
        return AuthService(transport: BackendAuthTransport(client: backend), tokenStore: tokenStore)
    }

    private func makeSyncService(transport: MockBackendTransport, store: SyncStore, authorizer: SyncAuthorizer) -> SyncService {
        let backend = BackendClient(transport: transport, maxRetries: 0, retryDelay: 0)
        return SyncService(backend: backend, store: store, authorizer: authorizer, maxAttempts: 1, retryDelay: 0)
    }

    /// Non-deleted records keyed by id, with the client-only `dirty` flag
    /// cleared so two devices are compared on synced content alone.
    private func liveNormalized(_ items: [ActionItem]) -> [String: ActionItem] {
        var map: [String: ActionItem] = [:]
        for var item in items where !item.sync.deleted {
            item.sync.dirty = false
            map[item.id] = item
        }
        return map
    }
}

// MARK: - Mock backend (one in-memory account-aware server)

/// A single in-memory backend that answers the contract calls the integration
/// tests exercise: `/accounts`, `/auth/login`, `/auth/refresh`, `/sync/push`,
/// and `/sync/pull`. It holds account credentials and per-account
/// server-authoritative `ActionItem`s, derives the account from the bearer
/// token for sync calls (Req 10.2), and issues opaque tokens whose expiry is
/// driven by an injectable clock so the refresh path is testable.
private final class MockBackend: @unchecked Sendable {

    private struct AccountRecord {
        var account: Account
        var password: String
    }

    private let lock = NSLock()
    private let accessTokenLifetime: TimeInterval
    private let now: () -> Date

    private var accountsByEmail: [String: AccountRecord] = [:]
    private var accountIdByAccessToken: [String: String] = [:]
    private var accountIdByRefreshToken: [String: String] = [:]
    private var itemsByAccount: [String: [String: ActionItem]] = [:]
    private var tokenCounter = 0
    private var accountCounter = 0
    private var syncToken: Int64 = 1

    /// Number of `/auth/refresh` calls served (asserted by the refresh test).
    private(set) var refreshCallCount = 0

    init(accessTokenLifetime: TimeInterval = 3600, now: @escaping () -> Date = { Date() }) {
        self.accessTokenLifetime = accessTokenLifetime
        self.now = now
    }

    /// Seeds an account without going through the network, for the login tests.
    @discardableResult
    func preregister(email: String, password: String, displayName: String) -> Account {
        lock.lock(); defer { lock.unlock() }
        return makeAccountLocked(email: email, password: password, displayName: displayName)
    }

    // MARK: Contract handlers

    func createAccount(_ request: CreateAccountRequest) -> (Int, AuthResult?, ContractError?) {
        lock.lock(); defer { lock.unlock() }
        guard accountsByEmail[request.email] == nil else {
            return (409, nil, ContractError(status: 409, message: "email already in use"))
        }
        let account = makeAccountLocked(
            email: request.email, password: request.password, displayName: request.displayName
        )
        return (201, AuthResult(account: account, tokens: issueTokensLocked(for: account.id)), nil)
    }

    func login(_ request: LoginRequest) -> (Int, AuthResult?, ContractError?) {
        lock.lock(); defer { lock.unlock() }
        guard let record = accountsByEmail[request.email], record.password == request.password else {
            return (401, nil, ContractError(status: 401, message: "invalid credentials"))
        }
        return (200, AuthResult(account: record.account, tokens: issueTokensLocked(for: record.account.id)), nil)
    }

    func refresh(_ request: RefreshRequest) -> (Int, TokenPair?, ContractError?) {
        lock.lock(); defer { lock.unlock() }
        refreshCallCount += 1
        guard let accountId = accountIdByRefreshToken[request.refreshToken] else {
            return (401, nil, ContractError(status: 401, message: "invalid refresh token"))
        }
        return (200, issueTokensLocked(for: accountId), nil)
    }

    func push(authToken: String?, _ request: SyncPushRequest) -> (Int, SyncPushResponse?, ContractError?) {
        lock.lock(); defer { lock.unlock() }
        guard let accountId = accountId(forToken: authToken) else {
            return (401, nil, ContractError(status: 401, message: "unauthorized"))
        }
        var store = itemsByAccount[accountId] ?? [:]
        for change in request.changes {
            if let existing = store[change.id] {
                store[change.id] = Domain.resolveActionItem(existing, change).winner
            } else {
                store[change.id] = change
            }
        }
        itemsByAccount[accountId] = store
        syncToken += 1
        return (200, SyncPushResponse(applied: request.changes.count, newSyncToken: syncToken), nil)
    }

    func pull(authToken: String?) -> (Int, SyncPullResponse?, ContractError?) {
        lock.lock(); defer { lock.unlock() }
        guard let accountId = accountId(forToken: authToken) else {
            return (401, nil, ContractError(status: 401, message: "unauthorized"))
        }
        let changes = (itemsByAccount[accountId] ?? [:]).values.sorted { $0.id < $1.id }
        return (200, SyncPullResponse(changes: changes, newSyncToken: syncToken), nil)
    }

    // MARK: Locked helpers (must be called with `lock` held)

    private func makeAccountLocked(email: String, password: String, displayName: String) -> Account {
        accountCounter += 1
        let account = Account(
            id: "acct-\(accountCounter)",
            email: email,
            displayName: displayName,
            createdAt: now()
        )
        accountsByEmail[email] = AccountRecord(account: account, password: password)
        itemsByAccount[account.id] = [:]
        return account
    }

    private func issueTokensLocked(for accountId: String) -> TokenPair {
        tokenCounter += 1
        let access = "access-\(accountId)-\(tokenCounter)"
        let refresh = "refresh-\(accountId)-\(tokenCounter)"
        accountIdByAccessToken[access] = accountId
        accountIdByRefreshToken[refresh] = accountId
        let issuedAt = now()
        return TokenPair(
            accessToken: access,
            refreshToken: refresh,
            accessExpiresAt: issuedAt.addingTimeInterval(accessTokenLifetime),
            refreshExpiresAt: issuedAt.addingTimeInterval(30 * 24 * 3600)
        )
    }

    private func accountId(forToken token: String?) -> String? {
        guard let token else { return nil }
        return accountIdByAccessToken[token]
    }
}

/// Routes `HTTPRequest`s onto the in-memory ``MockBackend``, decoding request
/// bodies and encoding responses with the shared contract coders so real
/// contract JSON crosses the seam in both directions.
private final class MockBackendTransport: HTTPTransport, @unchecked Sendable {

    private let backend: MockBackend
    private let encoder = SideQuestCoding.makeEncoder()
    private let decoder = SideQuestCoding.makeDecoder()

    init(backend: MockBackend) {
        self.backend = backend
    }

    func perform(_ request: HTTPRequest) async throws -> HTTPResponse {
        let authToken = bearerToken(request)
        switch (request.method, request.path) {
        case (.post, BackendEndpoints.accounts):
            let body = try decoder.decode(CreateAccountRequest.self, from: request.body ?? Data())
            return try encode(backend.createAccount(body))

        case (.post, BackendEndpoints.login):
            let body = try decoder.decode(LoginRequest.self, from: request.body ?? Data())
            return try encode(backend.login(body))

        case (.post, BackendEndpoints.refresh):
            let body = try decoder.decode(RefreshRequest.self, from: request.body ?? Data())
            return try encode(backend.refresh(body))

        case (.post, BackendEndpoints.syncPush):
            let body = try decoder.decode(SyncPushRequest.self, from: request.body ?? Data())
            return try encode(backend.push(authToken: authToken, body))

        case (.get, BackendEndpoints.syncPull):
            return try encode(backend.pull(authToken: authToken))

        default:
            return HTTPResponse(statusCode: 404, body: Data())
        }
    }

    private func bearerToken(_ request: HTTPRequest) -> String? {
        guard let header = request.headers["Authorization"], header.hasPrefix("Bearer ") else {
            return nil
        }
        return String(header.dropFirst("Bearer ".count))
    }

    /// Encodes a `(status, success?, error?)` triple into an `HTTPResponse`.
    private func encode<Success: Encodable>(
        _ result: (Int, Success?, ContractError?)
    ) throws -> HTTPResponse {
        let (status, success, error) = result
        if let success {
            return HTTPResponse(statusCode: status, body: try encoder.encode(success))
        }
        if let error {
            return HTTPResponse(statusCode: status, body: try encoder.encode(error))
        }
        return HTTPResponse(statusCode: status, body: Data())
    }
}

// MARK: - In-memory test doubles

/// In-memory ``SyncStore`` fake mirroring the dirty/tombstone semantics of the
/// real `ActionItemRepository`, so a device's surfaced records can be observed.
private final class InMemorySyncStore: SyncStore {

    private let lock = NSLock()
    private var items: [String: ActionItem]

    init(items: [ActionItem]) {
        self.items = Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
    }

    func snapshot() -> [String: ActionItem] {
        lock.lock(); defer { lock.unlock() }
        return items
    }

    func pendingPushItems() throws -> [ActionItem] {
        lock.lock(); defer { lock.unlock() }
        return items.values.filter { $0.sync.dirty }.sorted { $0.id < $1.id }
    }

    func localItem(id: String) throws -> ActionItem? {
        lock.lock(); defer { lock.unlock() }
        return items[id]
    }

    func applyRemoteChange(_ item: ActionItem) throws {
        lock.lock(); defer { lock.unlock() }
        var stored = item
        stored.sync.dirty = false
        items[item.id] = stored
    }

    func acknowledgePush(id: String, version: Int64) throws {
        lock.lock(); defer { lock.unlock() }
        guard var item = items[id], item.sync.version == version else { return }
        item.sync.dirty = false
        items[id] = item
    }

    func importAllAtomically(_ newItems: [ActionItem]) throws {
        lock.lock(); defer { lock.unlock() }
        for item in newItems {
            var stored = item
            stored.sync.dirty = false
            items[item.id] = stored
        }
    }
}

/// A test clock whose `now` can be stepped forward, used to drive the auth
/// silent-refresh path deterministically.
private final class TestClock: @unchecked Sendable {

    private let lock = NSLock()
    private var current: Date

    init(start: Date) {
        self.current = start
    }

    var now: Date {
        lock.lock(); defer { lock.unlock() }
        return current
    }

    func advance(by interval: TimeInterval) {
        lock.lock(); defer { lock.unlock() }
        current = current.addingTimeInterval(interval)
    }
}

/// Models the OS-persisted set of pending notification requests, which survives
/// a reboot at the system level (Req 7.11). The integration test keeps one
/// instance across a simulated relaunch to assert the requests endure.
private final class PersistentPendingStore {

    private var requestsByItem: [String: [String]] = [:]

    /// Replaces an item's pending requests with `identifiers` (idempotent
    /// reschedule-on-launch), de-duplicating within the item.
    func replaceRequests(forItem itemId: String, identifiers: [String]) {
        var seen = Set<String>()
        requestsByItem[itemId] = identifiers.filter { seen.insert($0).inserted }
    }

    /// All currently pending request identifiers across every item.
    func identifiers() -> [String] {
        requestsByItem.values.flatMap { $0 }.sorted()
    }
}

// MARK: - Notification service wiring (Apple-only)
//
// `NotificationCenterAdapting` and `SystemNotificationService` live behind
// `#if canImport(UserNotifications)` (the framework does not exist on the
// Linux/Windows build host), so this section — which drives the real service
// through an in-memory notification center — is compiled only where the
// notification system is available. The portable anchoring/reboot assertions
// above run on every host; these add a faithful end-to-end check on Apple
// platforms.

#if canImport(UserNotifications)
import UserNotifications

final class NotificationServiceWiringIntegrationTests: XCTestCase {

    /// `SystemNotificationService` schedules a task reminder as a
    /// `UNCalendarNotificationTrigger` whose fire time is anchored to the
    /// configured local wall-clock time (within 60 s — Req 7.6, 7.10), and the
    /// pending request survives a simulated reboot + reschedule-on-launch
    /// (Req 7.11).
    func testScheduleAnchorsLocalTimeAndSurvivesReboot() async throws {
        // One persistent center stands in for the OS-persisted request set,
        // which calendar triggers survive a reboot inside (Req 7.11).
        let center = FakeNotificationCenter(status: .authorized)
        let calendar = Calendar.current

        let service = SystemNotificationService(
            center: center,
            permissionStore: InMemoryPermissionRequestStore(),
            calendar: calendar
        )

        let time = TimeOfDay(hour: 9, minute: 30)
        let item = ActionItem(
            id: "item-1", accountId: "acct-1", bucketId: "b", title: "Ship it",
            contentType: .text, timeframe: .today, status: .notStarted,
            createdAt: Date(), sync: SyncMeta(updatedAt: Date(), version: 1, deleted: false)
        )
        let reminder = TaskReminder(
            actionItemId: item.id,
            timeOfDay: time,
            untilDate: calendar.date(byAdding: .day, value: 3, to: Date())!,
            recurringDaily: false
        )

        await service.scheduleTaskReminder(for: item, reminder: reminder)

        // A pending request was scheduled for the item.
        let pending = await center.pendingRequestIdentifiers()
        let reminderIds = pending.filter { NotificationIdentifier.isTaskReminder($0, forItem: item.id) }
        XCTAssertEqual(reminderIds.count, 1)

        // Its trigger is a calendar trigger anchored to the local wall-clock
        // time: the components carry the configured hour/minute and no fixed
        // time zone, so the fire instant matches 09:30 local within 60 s.
        let request = await center.request(withIdentifier: reminderIds[0])
        let trigger = try XCTUnwrap(request?.trigger as? UNCalendarNotificationTrigger)
        XCTAssertNil(trigger.dateComponents.timeZone)

        let fireDate = try XCTUnwrap(trigger.nextTriggerDate())
        let fireComponents = calendar.dateComponents([.hour, .minute], from: fireDate)
        XCTAssertEqual(fireComponents.hour, time.hour)
        XCTAssertEqual(fireComponents.minute, time.minute)

        // --- Simulated reboot --- the center keeps its (system-persisted)
        // requests; a fresh service reschedules on launch without dropping them.
        let relaunched = SystemNotificationService(
            center: center,
            permissionStore: InMemoryPermissionRequestStore(),
            calendar: calendar
        )
        await relaunched.rescheduleAllPending()

        let afterReboot = await center.pendingRequestIdentifiers()
        XCTAssertTrue(
            afterReboot.contains(reminderIds[0]),
            "The pending reminder must survive the reboot/reschedule-on-launch"
        )
    }
}

/// In-memory ``NotificationCenterAdapting`` double: holds scheduled requests so
/// the service can be exercised without the device-bound `UNUserNotificationCenter`.
private final class FakeNotificationCenter: NotificationCenterAdapting, @unchecked Sendable {

    private let lock = NSLock()
    private var status: NotificationAuthStatus
    private var requests: [String: UNNotificationRequest] = [:]

    init(status: NotificationAuthStatus) {
        self.status = status
    }

    func authorizationStatus() async -> NotificationAuthStatus {
        lock.lock(); defer { lock.unlock() }
        return status
    }

    func requestAuthorization(options: UNAuthorizationOptions) async -> NotificationAuthStatus {
        lock.lock(); defer { lock.unlock() }
        if status == .notDetermined { status = .authorized }
        return status
    }

    func add(_ request: UNNotificationRequest) async {
        lock.lock(); defer { lock.unlock() }
        requests[request.identifier] = request
    }

    func pendingRequestIdentifiers() async -> [String] {
        lock.lock(); defer { lock.unlock() }
        return Array(requests.keys)
    }

    func removePendingRequests(withIdentifiers identifiers: [String]) {
        lock.lock(); defer { lock.unlock() }
        for identifier in identifiers { requests.removeValue(forKey: identifier) }
    }

    /// Test accessor for the stored request.
    func request(withIdentifier identifier: String) async -> UNNotificationRequest? {
        lock.lock(); defer { lock.unlock() }
        return requests[identifier]
    }
}

/// Process-local ``PermissionRequestStore`` so the at-most-once flag does not
/// touch shared `UserDefaults` during the test.
private final class InMemoryPermissionRequestStore: PermissionRequestStore {

    private let lock = NSLock()
    private var requested = false

    func hasRequestedAuthorization() -> Bool {
        lock.lock(); defer { lock.unlock() }
        return requested
    }

    func markAuthorizationRequested() {
        lock.lock(); defer { lock.unlock() }
        requested = true
    }
}
#endif
