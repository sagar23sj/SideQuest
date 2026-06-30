import Foundation

/// A user's authenticated identity
/// (contract: `backend/api/openapi.yaml` → `Account`).
///
/// `createdAt` is a contract `date-time`, represented as a `Date` and encoded
/// RFC 3339 (see `SideQuestCoding`). The contract also defines an optional
/// `orgId`; it is omitted here per the design's Data Models section, and the
/// decoder ignores it (and any other unknown keys) when present in a response.
public struct Account: Codable, Identifiable, Sendable {

    public var id: String
    public var email: String
    public var displayName: String
    public var createdAt: Date

    public init(id: String, email: String, displayName: String, createdAt: Date) {
        self.id = id
        self.email = email
        self.displayName = displayName
        self.createdAt = createdAt
    }
}
