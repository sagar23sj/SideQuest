import Foundation

// MARK: - Auth networking boundary
//
// ``AuthService`` (token lifecycle, silent refresh, error mapping) is defined
// against this protocol rather than against `URLSession`/`BackendClient`
// directly, so the two compose cleanly even though the `BackendClient` (task
// 15.3) is built in parallel. `BackendClient` will conform to ``AuthTransport``
// by issuing the three contract calls; tests drive ``AuthService`` with a fake
// transport.
//
// The transport's job is only to perform the HTTP call and surface the outcome
// as either a decoded model or an ``AuthTransportError`` that classifies the
// failure (HTTP status + optional contract message, or a transport-level
// problem). ``AuthService`` turns those into user-facing ``AuthError`` values.

/// Performs the three contract auth calls. Conformers (the `BackendClient`)
/// communicate exclusively over REST/JSON per the contract (Req 2.1).
public protocol AuthTransport: Sendable {

    /// `POST /accounts` — create an account and sign in. Returns the new account
    /// plus its initial token pair (contract `201` → `AuthResult`).
    func createAccount(_ request: CreateAccountRequest) async throws -> AuthResult

    /// `POST /auth/login` — verify a credential pair and issue tokens
    /// (contract `200` → `AuthResult`).
    func login(_ request: LoginRequest) async throws -> AuthResult

    /// `POST /auth/refresh` — exchange a refresh token for a fresh pair
    /// (contract `200` → `TokenPair`). Backs silent refresh (Req 10.5).
    func refresh(_ request: RefreshRequest) async throws -> TokenPair
}

/// Classifies an auth call failure so ``AuthService`` can map it to a specific
/// user-facing message while preserving the user's input.
///
/// Conformers throw this from ``AuthTransport`` methods. The `message`, when
/// present, is the contract `Error.error.message` returned by the backend.
public enum AuthTransportError: Error, Equatable {

    /// A structured HTTP error response defined by the contract (e.g. 400, 401,
    /// 409). Carries the HTTP status and the optional contract message.
    case http(status: Int, message: String?)

    /// A transport-level failure with no usable contract response: no network,
    /// a timeout, a non-JSON body, or a decoding failure. Treated as transient.
    case transport(message: String)
}
