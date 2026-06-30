import Foundation

/// A thin REST/JSON client over the shared backend contract
/// (`backend/api/openapi.yaml`), operating exclusively on the `Generated_Models`
/// (Req 2.1).
///
/// `BackendClient` is the single HTTP entry point reused by `AuthService`
/// (task 15.1), `SyncService` (task 16), and the LLM proxy calls (task 13.10),
/// so the HTTP transport, JSON coding, retry policy, and contract error mapping
/// live in exactly one place rather than being duplicated per service.
///
/// Responsibilities:
///   * Encode request bodies and decode responses with the contract coders
///     (`SideQuestCoding`), communicating only over REST/JSON (Req 2.1).
///   * Attach `Accept`/`Content-Type: application/json` and an optional bearer
///     token (the contract's `bearerAuth` scheme, Req 2.4).
///   * Map contract-defined structured errors to category-specific, user-facing
///     `BackendError`s while never mutating caller state, so unsaved input is
///     preserved (Req 2.5).
///   * Retry transient failures — undocumented error responses and requests
///     exceeding the 30 s budget — up to `maxRetries` times (Req 2.6).
///   * Surface authentication failures immediately, without auto-retry (Req 2.7).
///
/// The network seam is the injected `HTTPTransport`; the production transport is
/// `URLSessionHTTPTransport` (Apple-target), and tests inject a stub.
public final class BackendClient {

    /// The 30 s per-request budget. A request that does not complete within this
    /// window is treated as a transient failure and retried (Req 2.6).
    public static let requestTimeout: TimeInterval = 30

    /// The maximum number of *retries* for a transient failure (Req 2.6); the
    /// initial attempt is not counted, so a request is performed at most
    /// `maxRetries + 1` times.
    public let maxRetries: Int

    private let transport: HTTPTransport
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder
    private let retryDelay: TimeInterval

    /// - Parameters:
    ///   - transport: the network seam (`URLSessionHTTPTransport` in production).
    ///   - maxRetries: transient-failure retries (default 3, Req 2.6).
    ///   - retryDelay: delay between retries; injected as 0 in tests.
    public init(
        transport: HTTPTransport,
        maxRetries: Int = 3,
        retryDelay: TimeInterval = 0.5,
        encoder: JSONEncoder = SideQuestCoding.makeEncoder(),
        decoder: JSONDecoder = SideQuestCoding.makeDecoder()
    ) {
        self.transport = transport
        self.maxRetries = maxRetries
        self.retryDelay = retryDelay
        self.encoder = encoder
        self.decoder = decoder
    }

    // MARK: - Typed convenience API

    /// Performs a GET and decodes the JSON response into `Response`.
    public func get<Response: Decodable>(
        _ path: String,
        query: [URLQueryItem] = [],
        authToken: String? = nil,
        as type: Response.Type = Response.self
    ) async throws -> Response {
        let request = makeRequest(method: .get, path: path, query: query, body: nil, authToken: authToken)
        return try await sendDecoding(request)
    }

    /// Encodes `body`, performs a POST, and decodes the JSON response into
    /// `Response`.
    public func post<RequestBody: Encodable, Response: Decodable>(
        _ path: String,
        body: RequestBody,
        authToken: String? = nil,
        as type: Response.Type = Response.self
    ) async throws -> Response {
        let encoded = try encodeBody(body)
        let request = makeRequest(method: .post, path: path, body: encoded, authToken: authToken)
        return try await sendDecoding(request)
    }

    /// Performs a request and returns the raw 2xx body, applying the full retry
    /// and error-mapping policy. Lower-level entry point for callers that need
    /// custom decoding.
    public func sendRaw(_ request: HTTPRequest) async throws -> Data {
        try await performWithRetry(request)
    }

    // MARK: - Request building

    private func makeRequest(
        method: HTTPMethod,
        path: String,
        query: [URLQueryItem] = [],
        body: Data?,
        authToken: String?
    ) -> HTTPRequest {
        var headers = [
            "Accept": "application/json"
        ]
        if body != nil {
            headers["Content-Type"] = "application/json"
        }
        if let authToken, !authToken.isEmpty {
            headers["Authorization"] = "Bearer \(authToken)"
        }
        return HTTPRequest(
            method: method,
            path: path,
            queryItems: query,
            headers: headers,
            body: body,
            timeout: Self.requestTimeout
        )
    }

    private func encodeBody<Body: Encodable>(_ body: Body) throws -> Data {
        do {
            return try encoder.encode(body)
        } catch {
            // Encoding our own request model should never fail; surface it as a
            // decoding/contract-mismatch error rather than a transient one so it
            // is not silently retried.
            throw BackendErrorMapper.decodingError(statusCode: nil)
        }
    }

    // MARK: - Send + decode

    private func sendDecoding<Response: Decodable>(_ request: HTTPRequest) async throws -> Response {
        let data = try await performWithRetry(request)
        do {
            return try decoder.decode(Response.self, from: data)
        } catch {
            throw BackendErrorMapper.decodingError(statusCode: nil)
        }
    }

    // MARK: - Retry loop

    /// Drives the request through the transport, applying the classification and
    /// bounded-retry policy. Returns the raw 2xx body, or throws a fully-mapped
    /// `BackendError`.
    private func performWithRetry(_ request: HTTPRequest) async throws -> Data {
        var attempt = 0
        while true {
            let classification: ResponseClassification
            var lastStatus: Int?

            do {
                let response = try await transport.perform(request)
                lastStatus = response.statusCode
                classification = BackendErrorMapper.classify(
                    statusCode: response.statusCode,
                    body: response.body,
                    decoder: decoder
                )
                if case .success = classification {
                    return response.body
                }
            } catch let transportError as HTTPTransportError {
                classification = BackendErrorMapper.classify(transportError: transportError)
            }

            switch classification {
            case .success:
                // Unreachable: handled inside the `do` block above.
                return Data()

            case .failure(let backendError):
                // Contract-defined structured error or auth failure: surface
                // immediately, no retry (Req 2.5, 2.7).
                throw backendError

            case .retriableTransient:
                // Req 2.6: retry up to `maxRetries`, then surface as transient.
                if attempt >= maxRetries {
                    throw BackendErrorMapper.exhaustedTransientError(statusCode: lastStatus)
                }
                attempt += 1
                if retryDelay > 0 {
                    try? await Task.sleep(nanoseconds: UInt64(retryDelay * 1_000_000_000))
                }
                continue
            }
        }
    }
}
