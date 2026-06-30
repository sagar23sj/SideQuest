import Foundation

// MARK: - ProxyLLMService — backend LLM Proxy implementation (Req 7.16, 7.17)
//
// Calls `POST /llm/notification-text` on the backend through the shared
// `BackendClient`, so the HTTP transport, JSON coding, and bearer auth are
// reused rather than reimplemented. The call is raced against a hard 5-second
// timeout (Req 7.17): whichever finishes first wins, and if the timeout wins the
// in-flight request is cancelled and the result is `.timedOut`. Any thrown
// error (including the `BackendClient`'s mapped contract errors and the
// provider-unavailable 503) maps to `.unavailable`. The provider keys stay
// server-side; the client only sends the secret-free `ActionItemSummary` list.

/// The default `LLMService`, backed by the backend LLM Proxy via `BackendClient`.
///
/// `notificationText(for:)` never throws: it returns ``LlmResult/ok(_:)`` on a
/// 2xx response, ``LlmResult/timedOut`` when the 5-second budget elapses first,
/// and ``LlmResult/unavailable`` for every other failure, so callers fail soft
/// to default text through ``LLMService/resolvedNotificationText(for:default:)``.
public final class ProxyLLMService: LLMService {

    /// Request body for `POST /llm/notification-text` (contract:
    /// `backend/api/openapi.yaml`). Mirrors the backend
    /// `notificationTextRequest` and the Android `NotificationTextRequest`.
    struct NotificationTextRequest: Encodable {
        let items: [ActionItemSummary]
    }

    /// Response body for `POST /llm/notification-text`: a single `text` field.
    struct NotificationTextResponse: Decodable {
        let text: String
    }

    private let client: BackendClient
    private let timeout: TimeInterval
    private let authTokenProvider: @Sendable () async -> String?

    /// - Parameters:
    ///   - client: The shared REST/JSON client. The proxy call relies on the
    ///     client's transport and coding but its fail-soft timeout is enforced
    ///     here (Req 7.17) independently of the client's own 30 s request budget.
    ///   - timeout: Hard fail-soft timeout (default ``LlmNotificationText/timeout``,
    ///     5 s).
    ///   - authTokenProvider: Supplies the current bearer token for the request,
    ///     or `nil` when unauthenticated. Async so it can read the Keychain via
    ///     `AuthService` without blocking. Defaults to no token.
    public init(
        client: BackendClient,
        timeout: TimeInterval = LlmNotificationText.timeout,
        authTokenProvider: @escaping @Sendable () async -> String? = { nil }
    ) {
        self.client = client
        self.timeout = timeout
        self.authTokenProvider = authTokenProvider
    }

    /// Requests notification text from the proxy, bounded by the 5-second
    /// fail-soft timeout (Req 7.16, 7.17).
    public func notificationText(for items: [ActionItemSummary]) async -> LlmResult {
        await withTaskGroup(of: LlmResult.self) { group in
            group.addTask { await self.requestText(for: items) }
            group.addTask {
                let nanos = UInt64((self.timeout > 0 ? self.timeout : 0) * 1_000_000_000)
                try? await Task.sleep(nanoseconds: nanos)
                return .timedOut
            }

            let first = await group.next() ?? .timedOut
            group.cancelAll()
            return first
        }
    }

    // MARK: - Proxy call

    /// Performs the single proxy request and maps the outcome. Any error — a
    /// mapped contract error, the 503 provider-unavailable response, a transport
    /// failure, or task cancellation from the racing timeout — becomes
    /// ``LlmResult/unavailable`` so delivery falls back to default text.
    private func requestText(for items: [ActionItemSummary]) async -> LlmResult {
        let token = await authTokenProvider()
        do {
            let response: NotificationTextResponse = try await client.post(
                BackendEndpoints.llmNotificationText,
                body: NotificationTextRequest(items: items),
                authToken: token
            )
            return .ok(response.text)
        } catch {
            return .unavailable
        }
    }
}
