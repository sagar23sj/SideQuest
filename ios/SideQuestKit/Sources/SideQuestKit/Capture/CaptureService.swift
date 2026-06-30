import Foundation

// MARK: - Capture service (task 8.1, Req 4.2, 4.3, 4.4; structures 4.5/4.6 for 8.3)
//
// Portable capture orchestration that lives in `SideQuestKit` so it is
// host-testable. It covers the parts of the design's `CaptureService` that have
// no I/O:
//   * `classify` — delegated to `ContentClassifier` (Req 4.2, 4.4).
//   * `beginCapture` — lower a supported `SharedItem` into a `CaptureDraft`
//     describing what the categorization sheet edits (Req 4.2).
//
// The confirm-capture WRITE to the shared App Group store is the separate
// task 8.3. To structure that path without performing the write here,
// `CaptureDraft` exposes a *pure* `makeActionItem(...)` builder that produces an
// unsaved `ActionItem` (status `.notStarted`, preserving the chosen bucket and
// timeframe — Req 4.5). Task 8.3 calls this builder and hands the result to
// `ActionItemRepository.create(_:)`.

/// A categorization draft: the supported content lowered into the fields the
/// categorization sheet works with before the user picks a Bucket and Timeframe.
///
/// `linkURL` is populated for ``ContentType/link`` items so the sheet can start
/// the asynchronous `LinkPresentation` fetch off the capture critical path
/// (Req 4.8, implemented in task 9 / wired in 8.3). The draft itself carries no
/// store dependency.
public struct CaptureDraft: Equatable {

    /// The classified, **supported** content type (never the unsupported case).
    public var contentType: ContentType

    /// A human-readable title seeded from the shared payload (the link's
    /// absolute string, the shared text, or a generic label for media). The
    /// user may edit this in the sheet.
    public var title: String

    /// The raw shared payload to persist on the item (`sourceContent`): the link
    /// string or the shared text. `nil` for media referenced by type only.
    public var sourceContent: String?

    /// The shared link, present only for ``ContentType/link`` items. Drives the
    /// asynchronous preview fetch (Req 4.8).
    public var linkURL: URL?

    public init(
        contentType: ContentType,
        title: String,
        sourceContent: String? = nil,
        linkURL: URL? = nil
    ) {
        self.contentType = contentType
        self.title = title
        self.sourceContent = sourceContent
        self.linkURL = linkURL
    }
}

extension CaptureDraft {

    /// Builds the `ActionItem` to persist when the user confirms the capture,
    /// **without** writing it to the store (Req 4.5). The actual store write is
    /// task 8.3, which calls this and then `ActionItemRepository.create(_:)`.
    ///
    /// The new item starts with ``ActionStatus/notStarted`` and carries the
    /// confirmed `bucketId` and `timeframe` unchanged, so a confirmed capture
    /// preserves the user's selections (Req 4.5). For a link draft an unresolved
    /// ``LinkPreview`` (carrying the raw URL) is attached so the UI can show the
    /// raw link immediately and update reactively once the preview resolves
    /// (Req 4.9) — unless a resolved `preview` is supplied.
    ///
    /// - Parameters:
    ///   - id: Client-generated identifier (Req 5.7); typically
    ///     `ActionItemRepository.newIdentifier()`.
    ///   - accountId: The signed-in account the item belongs to.
    ///   - bucketId: The confirmed bucket (exactly one — Req 4.3).
    ///   - timeframe: The confirmed timeframe (exactly one — Req 4.3).
    ///   - now: Creation instant; injected for deterministic tests.
    ///   - preview: A resolved preview when one is already available; when `nil`
    ///     a link draft gets an unresolved fallback preview and non-link drafts
    ///     get no preview.
    /// - Returns: An unsaved `ActionItem` ready to hand to the repository. The
    ///   `sync` metadata is a placeholder; the repository stamps the
    ///   authoritative `dirty` metadata on create.
    public func makeActionItem(
        id: String,
        accountId: String,
        bucketId: String,
        timeframe: Timeframe,
        now: Date,
        preview: LinkPreview? = nil
    ) -> ActionItem {
        let resolvedPreview: LinkPreview?
        if let preview {
            resolvedPreview = preview
        } else if contentType == .link, let linkURL {
            resolvedPreview = LinkPreview(rawUrl: linkURL.absoluteString, resolved: false)
        } else {
            resolvedPreview = nil
        }

        return ActionItem(
            id: id,
            accountId: accountId,
            bucketId: bucketId,
            title: title,
            contentType: contentType,
            sourceContent: sourceContent,
            preview: resolvedPreview,
            timeframe: timeframe,
            status: .notStarted,
            createdAt: now,
            sync: SyncMeta(updatedAt: now, version: 1, deleted: false, dirty: true)
        )
    }
}

/// Orchestrates the portable parts of the Share Extension capture flow
/// (design: "CaptureService"). The store write on confirm is task 8.3.
public protocol CaptureService {

    /// Classifies the shared attachments (Req 4.2, 4.4).
    func classify(_ attachments: [SharedAttachment]) -> SharedContentType

    /// Lowers a shared item into a ``CaptureDraft`` for the categorization sheet,
    /// or `nil` when the item is ``SharedContentType/unsupported`` (Req 4.4) and
    /// must instead be discarded with a "not supported" message.
    func beginCapture(_ item: SharedItem) -> CaptureDraft?
}

/// Default ``CaptureService`` used by the Share Extension. Pure: classification
/// and draft construction perform no I/O, so the extension's view controller
/// stays a thin shell over this logic.
public struct DefaultCaptureService: CaptureService {

    public init() {}

    public func classify(_ attachments: [SharedAttachment]) -> SharedContentType {
        ContentClassifier.classify(attachments)
    }

    public func beginCapture(_ item: SharedItem) -> CaptureDraft? {
        let classification = ContentClassifier.classify(item)
        guard let contentType = classification.contentType else {
            return nil   // unsupported (Req 4.4)
        }

        switch contentType {
        case .link:
            let url = item.attachments.compactMap(\.url).first
            let raw = url?.absoluteString
            return CaptureDraft(
                contentType: .link,
                title: raw ?? "Shared link",
                sourceContent: raw,
                linkURL: url
            )

        case .text:
            let text = item.attachments.compactMap(\.text).first
            return CaptureDraft(
                contentType: .text,
                title: Self.title(fromText: text),
                sourceContent: text
            )

        case .image:
            return CaptureDraft(contentType: .image, title: "Shared image")

        case .videoRef:
            return CaptureDraft(contentType: .videoRef, title: "Shared video")
        }
    }

    /// Seeds a concise title from shared text: first non-empty line, trimmed and
    /// capped so the sheet shows something sensible (the user can edit it).
    private static func title(fromText text: String?) -> String {
        guard let text else { return "Shared text" }
        let firstLine = text
            .split(whereSeparator: \.isNewline)
            .first
            .map(String.init)?
            .trimmingCharacters(in: .whitespaces) ?? ""
        if firstLine.isEmpty { return "Shared text" }
        return String(firstLine.prefix(80))
    }
}
