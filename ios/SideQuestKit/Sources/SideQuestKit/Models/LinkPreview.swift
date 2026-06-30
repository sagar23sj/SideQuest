import Foundation

/// Open Graph / link metadata fetched for a shared link
/// (contract: `backend/api/openapi.yaml` → `LinkPreview`).
///
/// When `resolved` is `false` the enrichment failed or timed out and the UI
/// falls back to displaying `rawUrl` (Req 4.9). `title`, `thumbnailUrl`, and
/// `sourceName` are optional and absent until a preview resolves.
public struct LinkPreview: Codable, Equatable {

    public var title: String?
    public var thumbnailUrl: String?
    public var sourceName: String?

    /// The original shared URL; always present so capture can complete and the
    /// UI can fall back to the raw link.
    public var rawUrl: String

    /// `false` => display `rawUrl` instead of the title/thumbnail.
    public var resolved: Bool

    public init(
        title: String? = nil,
        thumbnailUrl: String? = nil,
        sourceName: String? = nil,
        rawUrl: String,
        resolved: Bool
    ) {
        self.title = title
        self.thumbnailUrl = thumbnailUrl
        self.sourceName = sourceName
        self.rawUrl = rawUrl
        self.resolved = resolved
    }
}
