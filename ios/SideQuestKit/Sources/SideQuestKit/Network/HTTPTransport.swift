import Foundation

/// HTTP verbs used by the SideQuest backend contract (`backend/api/openapi.yaml`).
public enum HTTPMethod: String, Equatable, Sendable {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case delete = "DELETE"
}

/// A transport-agnostic description of an outbound request.
///
/// `HTTPRequest`/`HTTPResponse` are deliberately plain value types that do not
/// reference `URLSession`, so the request-building, error-classification, and
/// retry logic in `BackendClient` is portable and unit-testable on any platform
/// (including the Windows/Linux dev host) by injecting a stub `HTTPTransport`.
/// The only `URLSession`-bound code lives in `URLSessionHTTPTransport`, which is
/// Apple-target networking.
///
/// `path` is resolved against the transport's base URL (e.g. `"/auth/login"`).
public struct HTTPRequest: Equatable, Sendable {

    public var method: HTTPMethod
    public var path: String
    public var queryItems: [URLQueryItem]
    public var headers: [String: String]
    public var body: Data?

    /// Per-request timeout. The contract-level rule (Req 2.6) treats a request
    /// that does not complete within 30 seconds as a transient failure, so the
    /// default is `BackendClient.requestTimeout` (30 s).
    public var timeout: TimeInterval

    public init(
        method: HTTPMethod,
        path: String,
        queryItems: [URLQueryItem] = [],
        headers: [String: String] = [:],
        body: Data? = nil,
        timeout: TimeInterval = BackendClient.requestTimeout
    ) {
        self.method = method
        self.path = path
        self.queryItems = queryItems
        self.headers = headers
        self.body = body
        self.timeout = timeout
    }
}

/// A transport-agnostic description of a received response.
public struct HTTPResponse: Equatable, Sendable {
    public var statusCode: Int
    public var headers: [String: String]
    public var body: Data

    public init(statusCode: Int, headers: [String: String] = [:], body: Data = Data()) {
        self.statusCode = statusCode
        self.headers = headers
        self.body = body
    }
}

/// Transport-level failures, classified into the categories the contract's
/// retry rule cares about (Req 2.6): a timeout (request did not complete within
/// the 30 s budget) and connectivity failures are both treated as transient and
/// retried; any other transport failure is also surfaced as transient because
/// the request never produced a contract-defined response.
public enum HTTPTransportError: Error, Equatable {

    /// The request did not complete within its timeout budget (Req 2.6).
    case timedOut

    /// The device could not reach the backend (offline / connection lost).
    case offline

    /// Any other transport-level failure (e.g. non-HTTP response).
    case transportFailure(message: String)
}

/// The seam between `BackendClient` and the network.
///
/// `BackendClient` depends only on this protocol, never on `URLSession`
/// directly, so:
///   * the production path uses `URLSessionHTTPTransport` (Apple-target), and
///   * tests / non-Apple hosts inject a stub that returns canned
///     `HTTPResponse`s or throws `HTTPTransportError`s.
///
/// `AuthService` (task 15.1) and `SyncService` (task 16) share this same
/// transport via `BackendClient`, so the HTTP layer is implemented exactly once.
public protocol HTTPTransport {
    /// Perform a request, returning the raw response, or throwing an
    /// `HTTPTransportError` when the request never produced an HTTP response.
    func perform(_ request: HTTPRequest) async throws -> HTTPResponse
}
