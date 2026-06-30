import Foundation

// MARK: - Token storage abstraction
//
// Auth tokens must live in the iOS Keychain (Req 10.4), but the Keychain is an
// Apple-only API (`Security`). To keep the shared `SideQuestKit` module buildable
// and testable on non-Apple hosts, token storage is abstracted behind this
// protocol: the real Keychain-backed implementation
// (``KeychainTokenStore``) is compiled only `#if canImport(Security)`, while an
// in-memory implementation (``InMemoryTokenStore``) is always available for
// tests and non-Apple builds.
//
// ``AuthService`` depends on this protocol, never on the Keychain directly, so
// the token lifecycle (save on sign-in, load + silent refresh, clear on
// re-auth) is identical regardless of the backing store.

/// Persists the auth ``TokenPair`` securely across launches and (on device)
/// across the main-app / Share-Extension process boundary.
///
/// Implementations are synchronous: the Keychain APIs are synchronous, and the
/// stored payload is a single small JSON blob. Conformers must be safe to call
/// from any thread (``AuthService`` is an actor and may invoke these from its
/// executor).
public protocol TokenStore: Sendable {

    /// Returns the stored token pair, or `nil` when no tokens are stored
    /// (the user is not signed in).
    func loadTokens() throws -> TokenPair?

    /// Stores (replacing any existing) the token pair durably.
    func saveTokens(_ tokens: TokenPair) throws

    /// Removes any stored tokens. Idempotent: clearing when nothing is stored
    /// is not an error.
    func clearTokens() throws
}

/// A thread-safe, process-local in-memory ``TokenStore``.
///
/// Used by tests and on non-Apple hosts where the Keychain is unavailable. It
/// is **not** persistent and is **not** shared across processes, so it is not
/// suitable for production on device — production uses ``KeychainTokenStore``.
public final class InMemoryTokenStore: TokenStore, @unchecked Sendable {

    private let lock = NSLock()
    private var stored: TokenPair?

    public init(initial: TokenPair? = nil) {
        stored = initial
    }

    public func loadTokens() throws -> TokenPair? {
        lock.lock()
        defer { lock.unlock() }
        return stored
    }

    public func saveTokens(_ tokens: TokenPair) throws {
        lock.lock()
        defer { lock.unlock() }
        stored = tokens
    }

    public func clearTokens() throws {
        lock.lock()
        defer { lock.unlock() }
        stored = nil
    }
}
