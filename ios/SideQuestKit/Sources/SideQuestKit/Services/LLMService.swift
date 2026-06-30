import Foundation

// MARK: - LLMService (via backend LLM Proxy) — Req 7.16, 7.17
//
// The client never holds LLM provider keys; it asks the backend LLM Proxy
// (`POST /llm/notification-text`) to phrase reminder text from a secret-free
// list of `ActionItemSummary`. The call is bounded by a hard 5-second timeout
// and *fails soft*: on timeout, an error, or an unavailable provider the
// notification is still delivered using non-empty default text (Req 7.17). The
// generated (or default) text delivered to the user is always bounded to at
// most 200 characters (Req 7.16).
//
// This file declares the platform-independent abstraction (`LLMService`,
// `LlmResult`) plus the pure bounding/fail-soft resolution shared by callers and
// tests; the concrete proxy-backed implementation is `ProxyLLMService` below,
// which talks to the backend through the shared `BackendClient` so the HTTP
// transport, JSON coding, and auth header live in one place.

/// Outcome of requesting notification text from the LLM Proxy (Req 7.16, 7.17).
///
/// The cases mirror the Android client's `LlmResult` so the fail-soft behavior
/// stays parallel across platforms:
///   * ``ok(_:)`` — the proxy returned text. The associated string is the raw
///     proxy text *before* bounding; callers bound it to ``LlmNotificationText/maxLength``
///     when delivering.
///   * ``unavailable`` — the proxy returned an error or the provider is
///     unavailable (Req 7.17). Deliver default text.
///   * ``timedOut`` — the request did not complete within the 5-second budget
///     (Req 7.17). Deliver default text.
public enum LlmResult: Equatable, Sendable {

    /// The proxy returned notification text (not yet bounded).
    case ok(String)

    /// The proxy returned an error or the provider is unavailable (Req 7.17).
    case unavailable

    /// The request exceeded the 5-second timeout (Req 7.17).
    case timedOut
}

/// Requests reminder notification text from the backend LLM Proxy, returning an
/// ``LlmResult`` rather than throwing so every caller can *fail soft* (Req
/// 7.16, 7.17).
///
/// Implementations MUST honor the timeout: if the proxy does not respond within
/// ``LlmNotificationText/timeout`` (5 s) the call completes with
/// ``LlmResult/timedOut`` rather than waiting longer. The convenience
/// ``resolvedNotificationText(for:default:)`` collapses any result into the
/// non-empty, length-bounded string actually delivered with the notification.
public protocol LLMService {

    /// Requests notification text summarizing `items` from the LLM Proxy.
    ///
    /// - Returns: ``LlmResult/ok(_:)`` with the proxy text when it responds in
    ///   time; otherwise ``LlmResult/timedOut`` or ``LlmResult/unavailable``.
    func notificationText(for items: [ActionItemSummary]) async -> LlmResult
}

/// Bounds and fail-soft constants for LLM notification text (Req 7.16, 7.17).
public enum LlmNotificationText {

    /// Maximum length of delivered notification text (Req 7.16): the LLM is
    /// asked for at most 200 characters and any text the app delivers — proxy
    /// or default — is bounded to this length.
    public static let maxLength = 200

    /// Hard request timeout (Req 7.17): if the proxy does not return text within
    /// 5 seconds the notification is delivered with default text.
    public static let timeout: TimeInterval = 5

    /// Bounds `text` to at most ``maxLength`` characters without splitting it
    /// across the limit, trimming surrounding whitespace first so a blank or
    /// padding-only string becomes empty (and is therefore replaced by the
    /// default on the delivery path).
    ///
    /// Truncation counts `Character`s (grapheme clusters), so multi-scalar
    /// emoji and combined characters are never cut in half.
    public static func bounded(_ text: String) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count <= maxLength {
            return trimmed
        }
        return String(trimmed.prefix(maxLength))
    }
}

extension LLMService {

    /// Resolves the text to actually deliver with a notification, applying the
    /// fail-soft rule (Req 7.17) and the 200-character bound (Req 7.16).
    ///
    /// The returned string is **guaranteed non-empty** as long as `defaultText`
    /// is non-empty:
    ///   * ``LlmResult/ok(_:)`` → the proxy text bounded to ``LlmNotificationText/maxLength``;
    ///     if the proxy text is blank, the bounded `defaultText` is used instead.
    ///   * ``LlmResult/timedOut`` / ``LlmResult/unavailable`` → the bounded
    ///     `defaultText` (Req 7.17).
    ///
    /// - Parameters:
    ///   - items: The summaries to phrase.
    ///   - defaultText: Non-empty fallback copy (for example
    ///     ``NotificationDefaults``). Bounded to 200 characters before delivery.
    /// - Returns: The non-empty, ≤200-character text to deliver.
    public func resolvedNotificationText(
        for items: [ActionItemSummary],
        default defaultText: String
    ) async -> String {
        let result = await notificationText(for: items)
        let fallback = LlmNotificationText.bounded(defaultText)
        switch result {
        case let .ok(text):
            let bounded = LlmNotificationText.bounded(text)
            return bounded.isEmpty ? fallback : bounded
        case .unavailable, .timedOut:
            return fallback
        }
    }
}
