import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

// Feature: ios-client, Property 20: Token refresh and refresh failure preserve
// the local store.
//
/// Property-based test for **Property 20 — Token refresh and refresh failure
/// preserve the local store** (task 15.2).
///
/// **Validates: Requirements 10.5, 10.7**
///
/// > *For any* access-token refresh, whether it succeeds or fails, the local
/// > store is unchanged by the refresh; on failure the user is routed to
/// > re-authentication with the local store preserved.
///
/// - Requirement 10.5: *"WHEN an access token expires, THE App SHALL refresh the
///   token using the iOS Keychain-stored credentials without modifying or
///   deleting any data in the Local_Store."*
/// - Requirement 10.7: *"IF token refresh fails, THEN THE App SHALL route the
///   User to re-authentication while preserving the Local_Store unchanged."*
///
/// ## Strategy
///
/// The real ``AuthService`` is driven through its **silent-refresh-on-expiry**
/// path (``AuthService/accessToken()``, Req 10.5) over its two injected seams,
/// alongside a **real, seeded local store** that the service must never touch:
///
/// - A real ``SideQuestDatabase`` opened over a unique temp file is seeded with
///   generated `Bucket`s and `ActionItem`s (the `Local_Store` whose
///   preservation is asserted). The store is read back *before* the refresh to
///   capture a `before` snapshot and *after* the refresh to capture an `after`
///   snapshot; both are read from the database the same way, so the comparison
///   is a true byte-for-byte persisted-state equality independent of any
///   in-memory representation.
/// - An ``InMemoryTokenStore`` (the Keychain stand-in — `TokenStore` seam) is
///   seeded with a token pair whose **access token is already expired**, so
///   `accessToken()` is forced down the refresh path.
/// - A fake ``AuthTransport`` (``RefreshOnlyTransport``) serves `/auth/refresh`
///   with one of three outcomes:
///   - **success** — returns a fresh `TokenPair`; `accessToken()` returns the
///     fresh access token and the session stays signed in.
///   - **reauth** — fails with a contract `http` error (the refresh token is
///     rejected/expired); ``AuthService`` clears the tokens and throws
///     ``AuthError/sessionExpired`` so the caller routes to re-authentication
///     (Req 10.7), and the session becomes signed-out.
///   - **transient** — fails with a transport-level error; ``AuthService``
///     keeps the tokens and throws ``AuthError/network(message:)`` so the caller
///     can retry, and the session stays signed in.
///
/// In **every** outcome the property asserts the seeded local store is
/// byte-for-byte identical before and after the refresh — the refresh, the
/// refresh failure, and the re-auth routing all leave the `Local_Store`
/// untouched. By construction ``AuthService`` holds no reference to the store;
/// this test exercises that invariant against a real store across all refresh
/// outcomes.
///
/// ## Generator notes (constraining to the valid input space)
///
/// - `ActionItem`s only reference buckets that exist (the schema enforces a
///   `bucketId` → `bucket.id` foreign key), so items are generated against the
///   generated bucket set and none are emitted when no bucket exists.
/// - The stored token's access expiry is in the past relative to the injected
///   fixed `now`, guaranteeing the refresh path is always taken; the refresh
///   token expiry is in the future so only the configured transport outcome
///   decides success vs failure.
/// - Dates are generated on whole-second / UTC-midnight boundaries (matching the
///   persistence round-trip test) so seeded values round-trip cleanly through
///   the store; equality is in any case compared read-to-read.
final class TokenRefreshLocalStorePreservationPropertyTests: XCTestCase {

    /// Property 20 / Req 10.5, 10.7: refresh and refresh failure leave the local
    /// store byte-for-byte unchanged; failure routes to re-authentication.
    func testTokenRefreshAndFailurePreserveLocalStore() {
        property(
            "Property 20: token refresh / refresh failure preserve the local store and route re-auth on failure",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(scenarioGen) { scenario in
            return self.localStorePreservedHolds(scenario)
        }
    }

    // MARK: - Property under test

    private func localStorePreservedHolds(_ scenario: Scenario) -> Bool {
        let path = NSTemporaryDirectory()
            + "SideQuestTokenRefresh-\(UUID().uuidString).sqlite"

        // SQLite in WAL mode keeps sidecar files; remove all three on the way out.
        defer {
            for suffix in ["", "-wal", "-shm"] {
                try? FileManager.default.removeItem(atPath: path + suffix)
            }
        }

        do {
            // Seed the real local store with the generated buckets/items.
            let database = try SideQuestDatabase(path: path)
            for bucket in scenario.buckets { try database.saveBucket(bucket) }
            for item in scenario.items { try database.saveActionItem(item) }

            // `before` snapshot — read straight from the store.
            let beforeBuckets = Self.bucketsById(try database.fetchAllBuckets())
            let beforeItems = Self.itemsById(try database.fetchAllActionItems())

            // Auth setup: a fixed clock, an expired stored access token (forces
            // refresh), and the transport outcome under test. The service shares
            // no handle to `database` — preservation is by construction here.
            let now = Date(timeIntervalSince1970: 1_700_000_000)
            let storedTokens = TokenPair(
                accessToken: "stale-access-token",
                refreshToken: "stored-refresh-token",
                accessExpiresAt: now.addingTimeInterval(-3_600), // already expired
                refreshExpiresAt: now.addingTimeInterval(100_000)
            )
            let tokenStore = InMemoryTokenStore(initial: storedTokens)
            let transport = RefreshOnlyTransport(mode: scenario.transportMode(now: now))
            let auth = AuthService(
                transport: transport,
                tokenStore: tokenStore,
                expiryLeeway: 30,
                now: { now }
            )

            // Exercise the silent-refresh-on-expiry path (Req 10.5).
            let outcome = Self.runBlocking { () async -> RefreshOutcome in
                do {
                    let token = try await auth.accessToken()
                    let signedIn = await auth.isSignedIn
                    return .returned(token: token, signedIn: signedIn)
                } catch let error as AuthError {
                    let signedIn = await auth.isSignedIn
                    return .threw(error: error, signedIn: signedIn)
                } catch {
                    return .unexpected
                }
            }

            // The refresh must have been attempted exactly once.
            guard transport.refreshCallCount == 1 else { return false }

            // Verify the auth behaviour for the outcome — including re-auth
            // routing on failure (Req 10.7).
            switch scenario.outcome {
            case .success:
                guard case let .returned(token, signedIn) = outcome else { return false }
                // The fresh access token is handed back and the session persists.
                guard token == scenario.freshAccessToken, signedIn else { return false }

            case .reauth:
                guard case let .threw(error, signedIn) = outcome else { return false }
                // Routed to re-authentication: session invalidated (tokens cleared).
                guard error == .sessionExpired, !signedIn else { return false }

            case .transient:
                guard case let .threw(error, signedIn) = outcome else { return false }
                // Transient failure keeps the session for a later retry.
                guard case .network = error, signedIn else { return false }
            }

            // `after` snapshot — the local store must be byte-for-byte unchanged
            // in every outcome (Req 10.5, 10.7).
            let afterBuckets = Self.bucketsById(try database.fetchAllBuckets())
            let afterItems = Self.itemsById(try database.fetchAllActionItems())

            return afterBuckets == beforeBuckets && afterItems == beforeItems
        } catch {
            XCTFail("Token-refresh local-store preservation threw: \(error)")
            return false
        }
    }

    // MARK: - Helpers

    private static func bucketsById(_ buckets: [Bucket]) -> [String: Bucket] {
        Dictionary(uniqueKeysWithValues: buckets.map { ($0.id, $0) })
    }

    private static func itemsById(_ items: [ActionItem]) -> [String: ActionItem] {
        Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
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

// MARK: - Outcome of the exercised refresh

/// What the `accessToken()` call produced in a trial.
private enum RefreshOutcome {
    /// A token was returned (refresh succeeded). `signedIn` is the session state.
    case returned(token: String, signedIn: Bool)
    /// An ``AuthError`` was thrown. `signedIn` is the session state afterwards.
    case threw(error: AuthError, signedIn: Bool)
    /// An unexpected non-``AuthError`` was thrown (a test failure).
    case unexpected
}

// MARK: - Scenario

/// The refresh outcome under test in a trial.
private enum Outcome: CaseIterable {
    /// `/auth/refresh` succeeds and a fresh token pair is issued.
    case success
    /// `/auth/refresh` rejects the refresh token (contract error) → re-auth.
    case reauth
    /// `/auth/refresh` fails at the transport level → retryable network error.
    case transient
}

/// One randomly generated configuration: the seeded local store, the refresh
/// outcome, and the fresh access token used on the success path.
private struct Scenario {
    var buckets: [Bucket]
    var items: [ActionItem]
    var outcome: Outcome
    /// The access token the transport issues on the success path; asserted to be
    /// the value `accessToken()` returns.
    var freshAccessToken: String

    /// The transport mode for this scenario, built against the property's fixed
    /// clock so the freshly-issued access token is unexpired.
    func transportMode(now: Date) -> RefreshOnlyTransport.Mode {
        switch outcome {
        case .success:
            return .succeed(
                TokenPair(
                    accessToken: freshAccessToken,
                    refreshToken: "fresh-refresh-token",
                    accessExpiresAt: now.addingTimeInterval(3_600),
                    refreshExpiresAt: now.addingTimeInterval(200_000)
                )
            )
        case .reauth:
            return .rejectRefresh
        case .transient:
            return .transientFailure
        }
    }
}

// MARK: - Fakes

/// A fake ``AuthTransport`` that only serves `/auth/refresh`. The registration
/// and sign-in calls are unused by this test and assert if hit. Refresh call
/// count is recorded so the property can confirm the refresh path was exercised.
private final class RefreshOnlyTransport: AuthTransport, @unchecked Sendable {

    enum Mode {
        /// Return a fresh token pair (refresh success).
        case succeed(TokenPair)
        /// Reject the refresh token with a contract `http` error → session expires.
        case rejectRefresh
        /// Fail at the transport level → retryable network error.
        case transientFailure
    }

    private let mode: Mode
    private let lock = NSLock()
    private var refreshCalls = 0

    var refreshCallCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return refreshCalls
    }

    init(mode: Mode) {
        self.mode = mode
    }

    func createAccount(_ request: CreateAccountRequest) async throws -> AuthResult {
        XCTFail("createAccount should not be called by the refresh property")
        throw AuthTransportError.transport(message: "unused")
    }

    func login(_ request: LoginRequest) async throws -> AuthResult {
        XCTFail("login should not be called by the refresh property")
        throw AuthTransportError.transport(message: "unused")
    }

    func refresh(_ request: RefreshRequest) async throws -> TokenPair {
        lock.lock()
        refreshCalls += 1
        lock.unlock()

        switch mode {
        case .succeed(let pair):
            return pair
        case .rejectRefresh:
            // Contract error during refresh → refresh token no longer accepted.
            throw AuthTransportError.http(status: 401, message: "refresh token expired")
        case .transientFailure:
            // No usable contract response → treated as transient by AuthService.
            throw AuthTransportError.transport(message: "offline")
        }
    }
}

// MARK: - Generators (constrain to the relevant input space)

private let accountIdGen = Gen<String>.fromElements(of: ["acct-1", "acct-2", "acct-3"])

private let colorGen = Gen<String>.fromElements(of: [
    "#FF0000", "#00FF00", "#0000FF", "#123456", "#ABCDEF", "#000000", "#FFFFFF"
])

private let nameChars: [Character] =
    Array("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-éü✓")

/// Non-empty short strings (1...16 chars).
private let textGen: Gen<String> = Gen<Int>.choose((1, 16)).flatMap { size in
    Gen<Character>.fromElements(of: nameChars).proliferate(withSize: size).map { String($0) }
}

private func optionalGen<T>(_ gen: Gen<T>) -> Gen<T?> {
    Gen.one(of: [Gen.pure(T?.none), gen.map { Optional($0) }])
}

private let contentTypeGen = Gen<ContentType>.fromElements(of: ContentType.allCases)
private let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)

/// Whole-second instants (GRDB stores `Date` at millisecond precision, so whole
/// seconds round-trip exactly).
private let secondsDateGen: Gen<Date> = Gen<Int>.choose((1_600_000_000, 1_800_000_000))
    .map { Date(timeIntervalSince1970: TimeInterval($0)) }

/// UTC-midnight dates for the `Timeframe.specificDate` calendar-date payload.
private let calendarDateGen: Gen<Date> = Gen<Int>.choose((16_000, 20_000))
    .map { Date(timeIntervalSince1970: TimeInterval($0 * 86_400)) }

private let timeframeGen: Gen<Timeframe> = Gen.one(of: [
    Gen.pure(Timeframe.today),
    Gen.pure(Timeframe.withinADay),
    Gen.pure(Timeframe.withinAWeek),
    calendarDateGen.map { Timeframe.specificDate($0) }
])

private let syncMetaGen: Gen<SyncMeta> = Gen.compose { c in
    SyncMeta(
        updatedAt: c.generate(using: secondsDateGen),
        version: c.generate(using: Gen<Int>.choose((0, 1_000_000)).map(Int64.init)),
        deleted: c.generate(),
        dirty: c.generate()
    )
}

private let linkPreviewGen: Gen<LinkPreview> = Gen.compose { c in
    LinkPreview(
        title: c.generate(using: optionalGen(textGen)),
        thumbnailUrl: c.generate(using: optionalGen(textGen)),
        sourceName: c.generate(using: optionalGen(textGen)),
        rawUrl: c.generate(using: textGen),
        resolved: c.generate()
    )
}

private let tokenStringGen: Gen<String> = Gen<Int>.choose((8, 24)).flatMap { size in
    Gen<Character>
        .fromElements(of: Array("abcdefghijklmnopqrstuvwxyzABCDEF0123456789.-_"))
        .proliferate(withSize: size)
        .map { String($0) }
}

private let scenarioGen: Gen<Scenario> = Gen.compose { c in
    // Generate a bucket set (possibly empty — a store with no rows is still a
    // valid "local store" to preserve).
    let bucketCount = c.generate(using: Gen<Int>.choose((0, 4)))
    let buckets: [Bucket] = (0..<bucketCount).map { index in
        Bucket(
            id: "bucket-\(index)",
            accountId: c.generate(using: accountIdGen),
            name: c.generate(using: textGen),
            notStartedColor: c.generate(using: colorGen),
            inProgressColor: c.generate(using: colorGen),
            completedColor: c.generate(using: colorGen),
            sync: c.generate(using: syncMetaGen)
        )
    }

    // Items reference an existing bucket (foreign key); none when no bucket.
    let itemCount = buckets.isEmpty ? 0 : c.generate(using: Gen<Int>.choose((0, 8)))
    let items: [ActionItem] = (0..<itemCount).map { index in
        let bucket = buckets[c.generate(using: Gen<Int>.choose((0, buckets.count - 1)))]
        return ActionItem(
            id: "item-\(index)",
            accountId: bucket.accountId,
            bucketId: bucket.id,
            title: c.generate(using: textGen),
            description: c.generate(using: optionalGen(textGen)),
            contentType: c.generate(using: contentTypeGen),
            sourceContent: c.generate(using: optionalGen(textGen)),
            preview: c.generate(using: optionalGen(linkPreviewGen)),
            timeframe: c.generate(using: timeframeGen),
            status: c.generate(using: statusGen),
            createdAt: c.generate(using: secondsDateGen),
            sync: c.generate(using: syncMetaGen)
        )
    }

    let outcome = c.generate(using: Gen<Outcome>.fromElements(of: Outcome.allCases))
    let freshAccessToken = c.generate(using: tokenStringGen)

    return Scenario(
        buckets: buckets,
        items: items,
        outcome: outcome,
        freshAccessToken: freshAccessToken
    )
}
