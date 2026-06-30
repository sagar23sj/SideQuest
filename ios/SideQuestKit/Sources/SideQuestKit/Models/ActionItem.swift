import Foundation

/// A tracked, actionable task captured from shared content or manual entry
/// (contract: `backend/api/openapi.yaml` → `ActionItem`).
///
/// `id` is a client-generated UUID so items can be created offline without a
/// server round-trip (Req 5.7). `preview` is present for `ContentType.link`
/// items. `createdAt` orders items within a bucket on the board (Req 8.1) and
/// is a contract `date-time`, represented as a `Date` and encoded RFC 3339 (see
/// `SideQuestCoding`).
///
/// The on-the-wire field names are the contract's camelCase keys
/// (`accountId`, `bucketId`, `contentType`, `sourceContent`, `createdAt`), which
/// Swift's synthesized `Codable` produces directly, so no `CodingKeys` override
/// is required. Optional fields (`description`, `sourceContent`, `preview`) are
/// `nullable` in the contract.
public struct ActionItem: Codable, Identifiable, Equatable {

    public var id: String
    public var accountId: String
    public var bucketId: String
    public var title: String

    /// Optional, may be LLM-generated.
    public var description: String?

    public var contentType: ContentType

    /// Raw shared text / link / media reference.
    public var sourceContent: String?

    /// Present for link items; carries the resolved-or-raw preview (Req 4.9).
    public var preview: LinkPreview?

    public var timeframe: Timeframe
    public var status: ActionStatus

    /// Creation instant; orders items within a bucket (Req 8.1).
    public var createdAt: Date

    public var sync: SyncMeta

    public init(
        id: String,
        accountId: String,
        bucketId: String,
        title: String,
        description: String? = nil,
        contentType: ContentType,
        sourceContent: String? = nil,
        preview: LinkPreview? = nil,
        timeframe: Timeframe,
        status: ActionStatus,
        createdAt: Date,
        sync: SyncMeta
    ) {
        self.id = id
        self.accountId = accountId
        self.bucketId = bucketId
        self.title = title
        self.description = description
        self.contentType = contentType
        self.sourceContent = sourceContent
        self.preview = preview
        self.timeframe = timeframe
        self.status = status
        self.createdAt = createdAt
        self.sync = sync
    }
}
