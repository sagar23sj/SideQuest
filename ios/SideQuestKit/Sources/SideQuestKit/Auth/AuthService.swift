import Foundation

// MARK: - AuthError (user-facing categories)

/// The auth outcomes the UI distinguishes. Each maps to a specific message and
/// behavior required by the contract/spec; ``AuthService`` produces these from
/// the lower-level ``AuthTransportError``.
public enum AuthError: Error, Equatable {

    /// Account creation failed. `reason` is a human-readable explanation (from
    /// the contract message when present). The caller MUST retain the user's
    /// entered registration inputs and show this reason (Req 10.6).
    case accountCreationFailed(reason: String)

    /// Sign-in credentials were not accepted (contract `401`). The caller shows
    /// a credentials-not-accepted message and does not auto-retry (Req 10.8,
    /// 2.7).
    case invalidCredentials(message: String)

    /// The session could not be refreshed (the refresh token is invalid or
    /// expired). The caller routes the user to re-authentication; the local
    /// store is left untouched (Req 10.7).
    case sessionExpired

    /// No tokens are stored — the user is not signed in.
    case notAuthenticated

    /// A transient transport problem (no network, timeout, undefined error).
    /// `message` describes the condition.
    case network(message: String)

    /// The secure token store could not be read or written. `message` describes
    /// the condition.
    case storage(message: String)
}

// MARK: - AuthService

/// Owns the authentication lifecycle: account registration (`POST /accounts`),
/// sign-in (`POST /auth/login`), and silent token refresh (`POST /auth/refresh`)
/// per the contract's bearer/JWT scheme (Req 2.4, 10.1, 10.8).
///
/// Tokens are persisted via a ``TokenStore`` (the Keychain on device — Req 10.4).
/// When the access token is expired, ``accessToken()`` refreshes silently using
/// the stored refresh token (Req 10.5); when refresh fails it clears the tokens
/// and reports ``AuthError/sessionExpired`` so the caller can route to
/// re-authentication (Req 10.7).
///
/// **Local-store preservation (Req 10.5, 10.7):** this service has no reference
/// to the `Local_Store` and never touches it. Refresh and refresh-failure paths
/// only read/write the token store, so the local store is preserved unchanged by
/// construction (validated by Property 20, task 15.2).
///
/// An `actor` so concurrent callers can't corrupt the token state; refresh is
/// additionally single-flighted so simultaneous expired-token callers trigger
/// exactly one network refresh.
public actor AuthService {

    private let transport: AuthTransport
    private let tokenStore: TokenStore
    private let now: @Sendable () -> Date
    /// Refresh slightly before the real expiry to avoid racing the clock and to
    /// absorb request latency / minor clock skew.
    private let expiryLeeway: TimeInterval

    /// The account from the most recent successful register/sign-in this
    /// session, if any. Tokens (not the account) are what persist across
    /// launches, so this is `nil` after a cold start until the next sign-in.
    private var account: Account?

    /// In-flight refresh, used to single-flight concurrent refresh requests.
    private var refreshInFlight: Task<TokenPair, Error>?

    public init(
        transport: AuthTransport,
        tokenStore: TokenStore,
        expiryLeeway: TimeInterval = 30,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.transport = transport
        self.tokenStore = tokenStore
        self.expiryLeeway = expiryLeeway
        self.now = now
    }

    // MARK: Session state

    /// The account from the most recent successful register/sign-in this
    /// session, or `nil`.
    public var currentAccount: Account? {
        account
    }

    /// Whether a token pair is currently stored (i.e. the user is signed in).
    /// A storage read failure is reported as not signed in.
    public var isSignedIn: Bool {
        if let tokens = try? tokenStore.loadTokens() {
            return tokens != nil
        }
        return false
    }

    // MARK: Registration & sign-in

    /// Registers a new account (`POST /accounts`) and signs in, storing the
    /// issued tokens (Req 10.1). On failure throws ``AuthError/accountCreationFailed(reason:)``;
    /// the caller retains the entered inputs and shows the reason (Req 10.6).
    @discardableResult
    public func register(_ request: CreateAccountRequest) async throws -> Account {
        let result: AuthResult
        do {
            result = try await transport.createAccount(request)
        } catch {
            throw Self.mapRegistrationError(error)
        }
        try persist(result.tokens)
        account = result.account
        return result.account
    }

    /// Signs in with a credential pair (`POST /auth/login`) and stores the
    /// issued tokens. On a rejected credential pair throws
    /// ``AuthError/invalidCredentials(message:)`` (Req 10.8).
    @discardableResult
    public func signIn(_ request: LoginRequest) async throws -> Account {
        let result: AuthResult
        do {
            result = try await transport.login(request)
        } catch {
            throw Self.mapSignInError(error)
        }
        try persist(result.tokens)
        account = result.account
        return result.account
    }

    /// Signs out by clearing the stored tokens and the in-session account. Does
    /// not touch the local store.
    public func signOut() throws {
        try clearTokens()
        account = nil
    }

    // MARK: Access token / silent refresh

    /// Returns a currently-valid access token for authenticating requests,
    /// refreshing silently first if the stored access token has expired
    /// (Req 10.5).
    ///
    /// - Throws: ``AuthError/notAuthenticated`` when no tokens are stored;
    ///   ``AuthError/sessionExpired`` when a refresh is required but fails
    ///   (tokens are cleared so the caller re-authenticates — Req 10.7);
    ///   ``AuthError/network(message:)`` on a transient refresh failure that
    ///   does not invalidate the session.
    public func accessToken() async throws -> String {
        guard let tokens = try loadTokens() else {
            throw AuthError.notAuthenticated
        }
        if !isExpired(tokens.accessExpiresAt) {
            return tokens.accessToken
        }
        let refreshed = try await refresh(using: tokens)
        return refreshed.accessToken
    }

    /// Forces a silent token refresh using the stored refresh token, returning
    /// the new pair. Exposed for callers that proactively refresh; the normal
    /// path is through ``accessToken()``.
    @discardableResult
    public func refreshTokens() async throws -> TokenPair {
        guard let tokens = try loadTokens() else {
            throw AuthError.notAuthenticated
        }
        return try await refresh(using: tokens)
    }

    // MARK: - Private

    /// Performs (or joins an in-flight) silent refresh. On a transport error
    /// that means the refresh token is no longer accepted (HTTP 401, or any
    /// other defined error during refresh), the session is invalidated: tokens
    /// are cleared and ``AuthError/sessionExpired`` is thrown so the caller
    /// routes to re-auth (Req 10.7). A transient transport failure leaves the
    /// tokens in place and throws ``AuthError/network(message:)`` so the caller
    /// can retry later.
    ///
    /// Concurrent expired-token callers share a single network refresh: the
    /// first caller is the originator (it owns persisting the new pair and
    /// clearing the in-flight slot); joiners await the same task and apply the
    /// same error mapping.
    private func refresh(using tokens: TokenPair) async throws -> TokenPair {
        let task: Task<TokenPair, Error>
        let isOriginator: Bool
        if let inFlight = refreshInFlight {
            task = inFlight
            isOriginator = false
        } else {
            let request = RefreshRequest(refreshToken: tokens.refreshToken)
            task = Task { [transport] in try await transport.refresh(request) }
            refreshInFlight = task
            isOriginator = true
        }
        defer { if isOriginator { refreshInFlight = nil } }

        do {
            let pair = try await task.value
            // The originator owns writing the freshly issued pair to the store.
            if isOriginator {
                try persist(pair)
            }
            return pair
        } catch let error as AuthTransportError {
            switch error {
            case .http:
                // Refresh token rejected/expired (or any defined error during
                // refresh) -> invalidate session for re-authentication, without
                // touching the local store. Clearing is idempotent, so it is
                // safe for both the originator and any joiners.
                try? clearTokens()
                throw AuthError.sessionExpired
            case .transport(let message):
                // Transient (no network / timeout): keep tokens, allow retry.
                throw AuthError.network(message: message)
            }
        } catch let error as AuthError {
            throw error
        } catch {
            throw AuthError.network(message: String(describing: error))
        }
    }

    /// Whether `expiry` is at or before "now plus the leeway".
    private func isExpired(_ expiry: Date) -> Bool {
        expiry <= now().addingTimeInterval(expiryLeeway)
    }

    // MARK: Token store wrappers (map storage failures to AuthError)

    private func loadTokens() throws -> TokenPair? {
        do {
            return try tokenStore.loadTokens()
        } catch {
            throw AuthError.storage(message: String(describing: error))
        }
    }

    private func persist(_ tokens: TokenPair) throws {
        do {
            try tokenStore.saveTokens(tokens)
        } catch {
            throw AuthError.storage(message: String(describing: error))
        }
    }

    private func clearTokens() throws {
        do {
            try tokenStore.clearTokens()
        } catch {
            throw AuthError.storage(message: String(describing: error))
        }
    }

    // MARK: Error mapping

    /// Maps a registration transport error to a user-facing
    /// ``AuthError/accountCreationFailed(reason:)`` with a descriptive reason
    /// (Req 10.6). A `409` is the email-in-use case; `400` is a validation
    /// failure; transport failures are connectivity problems.
    private static func mapRegistrationError(_ error: Error) -> AuthError {
        guard let transportError = error as? AuthTransportError else {
            return .accountCreationFailed(reason: String(describing: error))
        }
        switch transportError {
        case .http(let status, let message):
            let reason: String
            switch status {
            case 409:
                reason = message ?? "That email address can’t be used. Try signing in instead."
            case 400:
                reason = message ?? "Some of the details entered aren’t valid. Please review and try again."
            default:
                reason = message ?? "The account couldn’t be created. Please try again."
            }
            return .accountCreationFailed(reason: reason)
        case .transport(let message):
            return .accountCreationFailed(
                reason: "The account couldn’t be created right now. Check your connection and try again. (\(message))"
            )
        }
    }

    /// Maps a sign-in transport error: a `401` is a rejected credential pair
    /// (Req 10.8, no auto-retry); other defined errors and transport failures
    /// are surfaced as transient network problems.
    private static func mapSignInError(_ error: Error) -> AuthError {
        guard let transportError = error as? AuthTransportError else {
            return .network(message: String(describing: error))
        }
        switch transportError {
        case .http(let status, let message) where status == 401:
            return .invalidCredentials(
                message: message ?? "The email or password wasn’t accepted. Please try again."
            )
        case .http(_, let message):
            return .network(message: message ?? "Sign-in couldn’t be completed. Please try again.")
        case .transport(let message):
            return .network(message: message)
        }
    }
}
