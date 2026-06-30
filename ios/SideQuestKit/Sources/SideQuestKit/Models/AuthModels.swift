import Foundation

// MARK: - Auth request / response models (Generated_Models)
//
// Swift mirrors of the auth-related schemas in the shared OpenAPI contract
// (`backend/api/openapi.yaml`): the request bodies for `POST /accounts`,
// `POST /auth/login`, and `POST /auth/refresh`, and the `TokenPair` / `AuthResult`
// responses. They are `Codable` and use the shared `SideQuestCoding` coders so
// the date-time fields (`accessExpiresAt`, `refreshExpiresAt`) land on the
// contract's RFC 3339 wire format, identical to the Android client and the Go
// backend (Req 2.2).
//
// The contract intentionally keeps auth errors generic (a duplicate email is a
// 409 with no field detail; a bad login is a 401 with one generic message), so
// these types carry only the fields the contract defines.

/// Request body for `POST /accounts` (contract: `CreateAccountRequest`).
///
/// `joinOrgId` and `newOrgName` are optional and mutually exclusive (the server
/// enforces "at most one"); both are omitted from the JSON when `nil`.
public struct CreateAccountRequest: Codable, Equatable, Sendable {

    public var email: String
    /// Plaintext password over TLS; hashed server-side (argon2id). 8–1024 chars
    /// per the contract — length is validated by the backend, not the client.
    public var password: String
    public var displayName: String
    /// Join an existing organization. Mutually exclusive with `newOrgName`.
    public var joinOrgId: String?
    /// Create and join a new organization. Mutually exclusive with `joinOrgId`.
    public var newOrgName: String?

    public init(
        email: String,
        password: String,
        displayName: String,
        joinOrgId: String? = nil,
        newOrgName: String? = nil
    ) {
        self.email = email
        self.password = password
        self.displayName = displayName
        self.joinOrgId = joinOrgId
        self.newOrgName = newOrgName
    }
}

/// Request body for `POST /auth/login` (contract: `LoginRequest`).
public struct LoginRequest: Codable, Equatable, Sendable {

    public var email: String
    public var password: String

    public init(email: String, password: String) {
        self.email = email
        self.password = password
    }
}

/// Request body for `POST /auth/refresh` (contract: `RefreshRequest`).
public struct RefreshRequest: Codable, Equatable, Sendable {

    public var refreshToken: String

    public init(refreshToken: String) {
        self.refreshToken = refreshToken
    }
}

/// A signed JWT access token (short-lived) plus a refresh token
/// (contract: `TokenPair`). Stored in the iOS Keychain by the ``TokenStore``
/// (Req 10.4); the `accessExpiresAt` instant drives silent refresh (Req 10.5).
public struct TokenPair: Codable, Equatable, Sendable {

    /// Short-lived bearer token sent on authenticated requests.
    public var accessToken: String
    /// Longer-lived token exchanged at `/auth/refresh` for a new pair.
    public var refreshToken: String
    /// When the access token expires; used to decide when to refresh silently.
    public var accessExpiresAt: Date
    /// When the refresh token expires; once past, refresh fails and the user is
    /// routed to re-authentication (Req 10.7).
    public var refreshExpiresAt: Date

    public init(
        accessToken: String,
        refreshToken: String,
        accessExpiresAt: Date,
        refreshExpiresAt: Date
    ) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.accessExpiresAt = accessExpiresAt
        self.refreshExpiresAt = refreshExpiresAt
    }
}

/// The signed-in account plus its initial token pair, returned by
/// `POST /accounts` and `POST /auth/login` (contract: `AuthResult`).
public struct AuthResult: Codable, Sendable {

    public var account: Account
    public var tokens: TokenPair

    public init(account: Account, tokens: TokenPair) {
        self.account = account
        self.tokens = tokens
    }
}
