import Foundation

/// The category a backend failure falls into, derived from the contract's
/// structured `Error` (keyed on HTTP status) or from a transport-level outcome.
///
/// Each category drives a distinct, user-facing message (Req 2.5) and a distinct
/// handling policy:
///   * `authentication` (401) — show an auth-failure message and do **not**
///     auto-retry (Req 2.7); callers route to re-authentication.
///   * `validation` (400), `conflict` (409), `payloadTooLarge` (413),
///     `serviceUnavailable` (503), `unexpected` (structured error with an
///     undocumented status) — contract-defined structured errors that are
///     surfaced immediately (no auto-retry) with their category message, while
///     the caller preserves the user's unsaved input (Req 2.5).
///   * `transient` — an error response that is *not* a contract-defined
///     structured error, or a request that did not complete within 30 s; these
///     are retried up to 3 times and only surfaced after retries are exhausted
///     (Req 2.6).
///   * `decoding` — a 2xx response whose body did not match the expected
///     `Generated_Models` shape (a client/contract mismatch); surfaced without
///     retry.
public enum BackendErrorCategory: String, Equatable, Sendable {
    case validation
    case authentication
    case conflict
    case payloadTooLarge
    case serviceUnavailable
    case unexpected
    case transient
    case decoding
}

/// A fully-mapped, user-presentable backend failure.
///
/// `BackendClient` only ever throws this type (never a raw `URLError` or
/// `HTTPTransportError`), so every call site has a category, a ready-to-show
/// message, and the policy flags it needs — without inspecting transport
/// internals.
///
/// The client is read-only with respect to caller state: producing a
/// `BackendError` never mutates anything the caller owns, so a caller can always
/// retain the user's unsaved input after a failure (`preservesUnsavedInput`,
/// Req 2.5).
public struct BackendError: Error, Equatable, Sendable {

    /// The mapped category (see `BackendErrorCategory`).
    public let category: BackendErrorCategory

    /// A category-specific message safe to display to the user (Req 2.5).
    public let userFacingMessage: String

    /// The HTTP status that produced the error, when one was received. `nil`
    /// for transport-level transient failures (timeout/offline).
    public let statusCode: Int?

    /// The raw, generic message from the contract's `Error` payload, retained
    /// for logging/diagnostics. Never shown to the user.
    public let serverMessage: String?

    public init(
        category: BackendErrorCategory,
        userFacingMessage: String,
        statusCode: Int? = nil,
        serverMessage: String? = nil
    ) {
        self.category = category
        self.userFacingMessage = userFacingMessage
        self.statusCode = statusCode
        self.serverMessage = serverMessage
    }

    /// `true` only for `authentication`. Callers use this to skip auto-retry and
    /// route to re-authentication (Req 2.7).
    public var isAuthenticationFailure: Bool {
        category == .authentication
    }

    /// Always `true`: because the client never mutates caller-owned state while
    /// producing an error, the caller can preserve the user's unsaved input
    /// regardless of category (Req 2.5).
    public var preservesUnsavedInput: Bool {
        true
    }
}
