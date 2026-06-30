import Foundation

#if canImport(FoundationNetworking)
// On non-Apple platforms (swift-corelibs-foundation), `URLSession` lives in the
// separate FoundationNetworking module. On Apple targets it is part of
// Foundation. This keeps the file compiling on the Linux/Windows dev host even
// though networking is, in practice, Apple-target code.
import FoundationNetworking
#endif

/// The production `HTTPTransport`: a thin wrapper over `URLSession` (Req 2.1 —
/// REST/JSON over HTTPS).
///
/// This is the only networking-bound type in the layer; all request building,
/// JSON coding, retry, and error mapping live in `BackendClient` /
/// `BackendErrorMapper` as portable, testable code. Keeping `URLSession` isolated
/// here means the rest of the client can be exercised on any host by injecting a
/// stub `HTTPTransport`.
///
/// It translates `URLError`s into the `HTTPTransportError` cases that the
/// contract retry rule understands: `.timedOut` for the 30 s budget overrun
/// (Req 2.6) and `.offline` for connectivity loss.
public final class URLSessionHTTPTransport: HTTPTransport {

    private let baseURL: URL
    private let session: URLSession

    public init(baseURL: URL, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
    }

    public func perform(_ request: HTTPRequest) async throws -> HTTPResponse {
        let urlRequest = try makeURLRequest(request)

        do {
            let (data, response) = try await session.data(for: urlRequest)
            guard let http = response as? HTTPURLResponse else {
                throw HTTPTransportError.transportFailure(message: "Non-HTTP response received.")
            }
            return HTTPResponse(
                statusCode: http.statusCode,
                headers: headerDictionary(from: http),
                body: data
            )
        } catch let urlError as URLError {
            throw Self.mapURLError(urlError)
        } catch let transportError as HTTPTransportError {
            throw transportError
        } catch {
            throw HTTPTransportError.transportFailure(message: error.localizedDescription)
        }
    }

    // MARK: - URLRequest construction

    private func makeURLRequest(_ request: HTTPRequest) throws -> URLRequest {
        guard var components = URLComponents(
            url: baseURL.appendingPathComponent(request.path),
            resolvingAgainstBaseURL: false
        ) else {
            throw HTTPTransportError.transportFailure(message: "Invalid URL for path \(request.path).")
        }

        if !request.queryItems.isEmpty {
            components.queryItems = request.queryItems
        }

        guard let url = components.url else {
            throw HTTPTransportError.transportFailure(message: "Invalid URL components for path \(request.path).")
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = request.method.rawValue
        urlRequest.httpBody = request.body
        urlRequest.timeoutInterval = request.timeout
        for (field, value) in request.headers {
            urlRequest.setValue(value, forHTTPHeaderField: field)
        }
        return urlRequest
    }

    private func headerDictionary(from response: HTTPURLResponse) -> [String: String] {
        var headers: [String: String] = [:]
        for (key, value) in response.allHeaderFields {
            if let key = key as? String, let value = value as? String {
                headers[key] = value
            }
        }
        return headers
    }

    // MARK: - URLError → HTTPTransportError

    static func mapURLError(_ error: URLError) -> HTTPTransportError {
        switch error.code {
        case .timedOut:
            return .timedOut
        case .notConnectedToInternet,
             .networkConnectionLost,
             .cannotConnectToHost,
             .cannotFindHost,
             .dataNotAllowed,
             .internationalRoamingOff:
            return .offline
        default:
            return .transportFailure(message: error.localizedDescription)
        }
    }
}
