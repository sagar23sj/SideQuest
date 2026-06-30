import Foundation

// MARK: - Production AuthTransport over BackendClient (task 15.1 / 18.1 wiring)
//
// `AuthService` (token lifecycle, silent refresh, error mapping) is defined
// against the portable ``AuthTransport`` seam so it composes without depending
// on `URLSession` directly. This adapter is the production conformer: it issues
// the three contract auth calls through the shared ``BackendClient`` (so the
// HTTP transport, JSON coding, bearer auth, retry, and contract error mapping
// are reused) and translates the client's mapped ``BackendError`` into the
// ``AuthTransportError`` classification `AuthService` understands.
//
// It lives in the package (next to `BackendClient`) rather than in the app
// target so any host that links `SideQuestKit` — the main app and any future
// caller — can construct a real `AuthService` from a `BackendClient`.

/// The production ``AuthTransport``: issues `POST /accounts`, `POST /auth/login`,
/// and `POST /auth/refresh` through ``BackendClient`` (Req 2.4, 10.1, 10.5).
///
/// A `final class` marked `@unchecked Sendable` (mirroring the test transport):
/// it holds only an immutable ``BackendClient`` reference and performs no mutable
/// shared state of its own, so it is safe to hand to the `AuthService` actor.
public final class BackendAuthTransport: AuthTransport, @unchecked Sendable {

    private let client: BackendClient

    /// - Parameter client: the shared REST/JSON client (task 15.3).
    public init(client: BackendClient) {
        self.client = client
    }

    /// `POST /accounts` → `AuthResult` (Req 10.1).
    public func createAccount(_ request: CreateAccountRequest) async throws -> AuthResult {
        try await perform {
            try await client.post(BackendEndpoints.accounts, body: request, as: AuthResult.self)
        }
    }

    /// `POST /auth/login` → `AuthResult` (Req 10.1).
    public func login(_ request: LoginRequest) async throws -> AuthResult {
        try await perform {
            try await client.post(BackendEndpoints.login, body: request, as: AuthResult.self)
        }
    }

    /// `POST /auth/refresh` → `TokenPair`; backs silent refresh (Req 10.5).
    public func refresh(_ request: RefreshRequest) async throws -> TokenPair {
        try await perform {
            try await client.post(BackendEndpoints.refresh, body: request, as: TokenPair.self)
        }
    }

    // MARK: - Error translation

    /// Runs `operation`, translating a thrown ``BackendError`` into the
    /// ``AuthTransportError`` `AuthService` maps to user-facing categories.
    private func perform<T>(_ operation: () async throws -> T) async throws -> T {
        do {
            return try await operation()
        } catch let error as BackendError {
            throw Self.map(error)
        }
    }

    /// Maps a mapped ``BackendError`` to the auth transport classification.
    ///
    /// A structured, contract-defined response (it carries an HTTP status) is a
    /// ``AuthTransportError/http(status:message:)`` so `AuthService` can
    /// distinguish 400/401/409; a transient or decoding failure (no usable
    /// contract response) is a ``AuthTransportError/transport(message:)``, which
    /// `AuthService` treats as a connectivity problem.
    static func map(_ error: BackendError) -> AuthTransportError {
        switch error.category {
        case .transient, .decoding:
            return .transport(message: error.serverMessage ?? error.userFacingMessage)
        case .validation, .authentication, .conflict,
             .payloadTooLarge, .serviceUnavailable, .unexpected:
            return .http(status: error.statusCode ?? 0, message: error.serverMessage)
        }
    }
}
