import Foundation

/// How a received response (or transport failure) should be handled.
public enum ResponseClassification: Equatable, Sendable {

    /// A 2xx response; the caller should decode the body.
    case success

    /// A contract-defined failure that must be surfaced immediately, with no
    /// auto-retry (covers structured 4xx/5xx errors and 401 auth failures).
    case failure(BackendError)

    /// A failure that is *not* a contract-defined structured error, or a
    /// transport timeout/offline condition. Eligible for retry (Req 2.6).
    case retriableTransient
}

/// Pure, side-effect-free mapping from raw HTTP/transport outcomes to
/// `BackendError`s and retry decisions.
///
/// This type is the testable heart of the contract error-mapping requirement
/// (Req 2.5, 2.6, 2.7) and is validated by the Property 21 property test
/// (task 15.4). It performs no I/O, holds no state, and never touches
/// `URLSession`, so it runs anywhere.
///
/// ### Classification rules
/// 1. **2xx** → `.success`.
/// 2. **401** → `.failure(authentication)`, *always*, regardless of body, and
///    never retried (Req 2.7).
/// 3. **Other non-2xx with a structured `Error` body** → `.failure(category)`
///    where the category is keyed on the status (Req 2.5); not retried.
/// 4. **Other non-2xx whose body is NOT a structured `Error`** → undefined error
///    response → `.retriableTransient` (Req 2.6).
/// 5. **Transport timeout / offline / failure** → `.retriableTransient`
///    (Req 2.6).
public enum BackendErrorMapper {

    // MARK: - Status → category

    /// Maps a contract-documented HTTP status to its error category. Returns
    /// `nil` for statuses the contract does not document for the `Error` shape;
    /// a structured error carrying such a status is mapped to `.unexpected`.
    public static func category(forStatus status: Int) -> BackendErrorCategory? {
        switch status {
        case 400: return .validation
        case 401: return .authentication
        case 409: return .conflict
        case 413: return .payloadTooLarge
        case 503: return .serviceUnavailable
        default: return nil
        }
    }

    // MARK: - User-facing copy

    /// The category-specific, user-facing message (Req 2.5). The client shows
    /// these rather than the contract's intentionally-generic server text.
    public static func userFacingMessage(for category: BackendErrorCategory) -> String {
        switch category {
        case .validation:
            return "Some of the details you entered weren't accepted. Please review your input and try again."
        case .authentication:
            return "We couldn't verify your account. Please sign in again."
        case .conflict:
            return "That change conflicts with information already saved. Please review and try again."
        case .payloadTooLarge:
            return "That content is too large to upload. Please try something smaller."
        case .serviceUnavailable:
            return "That feature is temporarily unavailable. Please try again in a moment."
        case .unexpected:
            return "Something went wrong on our end. Please try again."
        case .transient:
            return "We're having trouble reaching SideQuest. Please check your connection and try again."
        case .decoding:
            return "We received an unexpected response. Please update SideQuest and try again."
        }
    }

    // MARK: - Builders

    /// Builds a `BackendError` for a mapped category, attaching the status and
    /// raw server message for diagnostics.
    public static func makeError(
        category: BackendErrorCategory,
        statusCode: Int?,
        serverMessage: String? = nil
    ) -> BackendError {
        BackendError(
            category: category,
            userFacingMessage: userFacingMessage(for: category),
            statusCode: statusCode,
            serverMessage: serverMessage
        )
    }

    /// The error surfaced once transient retries are exhausted (Req 2.6).
    public static func exhaustedTransientError(statusCode: Int?) -> BackendError {
        makeError(category: .transient, statusCode: statusCode)
    }

    /// The error surfaced when a 2xx body cannot be decoded into the expected
    /// `Generated_Models` type.
    public static func decodingError(statusCode: Int?) -> BackendError {
        makeError(category: .decoding, statusCode: statusCode)
    }

    // MARK: - Classification

    /// Classifies a received HTTP response per the rules above.
    ///
    /// - Parameters:
    ///   - statusCode: the HTTP status of the response.
    ///   - body: the raw response body (used to detect the structured `Error`).
    ///   - decoder: decoder used to probe for the contract `Error` shape;
    ///     defaults to the shared contract decoder.
    public static func classify(
        statusCode: Int,
        body: Data,
        decoder: JSONDecoder = SideQuestCoding.makeDecoder()
    ) -> ResponseClassification {
        if (200..<300).contains(statusCode) {
            return .success
        }

        // Req 2.7: authentication failures always surface as such and are never
        // auto-retried, even if the body is missing or malformed.
        if statusCode == 401 {
            let serverMessage = (try? decoder.decode(ContractError.self, from: body))?.error.message
            return .failure(makeError(category: .authentication, statusCode: 401, serverMessage: serverMessage))
        }

        // Req 2.5: a contract-defined structured error maps to its category.
        if let contract = try? decoder.decode(ContractError.self, from: body) {
            let category = category(forStatus: statusCode) ?? .unexpected
            return .failure(makeError(category: category, statusCode: statusCode, serverMessage: contract.error.message))
        }

        // Req 2.6: an error response that is NOT a contract-defined structured
        // error is treated as transient and retried.
        return .retriableTransient
    }

    /// Classifies a transport-level failure. Timeouts (>30 s, Req 2.6),
    /// offline conditions, and other transport failures are all transient.
    public static func classify(transportError: HTTPTransportError) -> ResponseClassification {
        switch transportError {
        case .timedOut, .offline, .transportFailure:
            return .retriableTransient
        }
    }
}
