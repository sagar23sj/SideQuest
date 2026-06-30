import Foundation

/// The single structured error shape defined by the contract
/// (`backend/api/openapi.yaml` → `Error`):
///
/// ```json
/// { "error": { "status": 409, "message": "email already in use" } }
/// ```
///
/// Every contract endpoint that can fail returns this shape (400, 401, 409,
/// 413, 503). The backend keeps `message` intentionally generic (no field-level
/// detail, no account enumeration), so the client maps the *category* — keyed on
/// the HTTP status — to its own user-facing copy rather than surfacing the raw
/// server text. The raw `message` is retained on `BackendError.serverMessage`
/// for diagnostics/logging only.
public struct ContractError: Codable, Equatable, Sendable {

    public struct Payload: Codable, Equatable, Sendable {
        public let status: Int
        public let message: String

        public init(status: Int, message: String) {
            self.status = status
            self.message = message
        }
    }

    public let error: Payload

    public init(error: Payload) {
        self.error = error
    }

    public init(status: Int, message: String) {
        self.error = Payload(status: status, message: message)
    }
}
