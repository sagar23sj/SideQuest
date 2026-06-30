import Foundation

/// A minimal, secret-free view of an Action_Item sent to the backend LLM Proxy
/// to generate notification text (contract: `backend/api/openapi.yaml` →
/// `ActionItemSummary`; Req 7.16).
///
/// It deliberately carries only the fields the proxy needs to phrase a reminder
/// — the item's `title`, its `bucketName`, and a human `dueLabel` derived from
/// the timeframe — and excludes account, identifier, and any other sensitive
/// data, mirroring the Android client's `ActionItemSummary` and the backend
/// `provider.ActionItemSummary` so the three share one wire schema.
///
/// The on-the-wire field names are the contract's camelCase keys (`title`,
/// `bucketName`, `dueLabel`), which Swift's synthesized `Codable` produces
/// directly, so no `CodingKeys` override is required. All three fields are
/// `required` in the contract.
public struct ActionItemSummary: Codable, Equatable, Sendable {

    /// The item's title.
    public var title: String

    /// The display name of the bucket the item belongs to.
    public var bucketName: String

    /// A human-readable due label derived from the item's timeframe
    /// (for example "today", "within a day", or a formatted date).
    public var dueLabel: String

    public init(title: String, bucketName: String, dueLabel: String) {
        self.title = title
        self.bucketName = bucketName
        self.dueLabel = dueLabel
    }
}
