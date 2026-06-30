import Foundation

// MARK: - Shared content model (task 8.1)
//
// Portable, host-testable value types describing what the iOS share sheet hands
// to the Share Extension. The extension's `ShareViewController` reads the
// `NSExtensionItem`/`NSItemProvider`s and lowers them into these plain values so
// the classification and capture logic can live in `SideQuestKit` (and be unit
// tested on any platform) rather than in the iOS-only extension target.

/// One attachment shared into the app, described by its registered type
/// identifiers plus any eagerly-extracted payload.
///
/// `typeIdentifiers` are the Uniform Type Identifier (UTI) strings the system
/// reports for the attachment (`NSItemProvider.registeredTypeIdentifiers`),
/// e.g. `"public.url"`, `"public.plain-text"`, `"public.jpeg"`,
/// `"public.movie"`. Classification (``ContentClassifier``) is driven by these
/// strings so it stays pure and host-testable — it never touches UIKit or
/// `NSItemProvider`.
///
/// `url` and `text` carry payload the extension was able to load up front (a
/// shared web link, or shared/typed text) so ``CaptureService/beginCapture(_:)``
/// can build a draft without re-reading the providers. Image and video
/// attachments are referenced by type only at this stage; their bytes are
/// loaded later by the capture/preview path, not here.
public struct SharedAttachment: Equatable {

    /// The UTI strings the system reports for this attachment.
    public var typeIdentifiers: [String]

    /// A shared web/file URL, when one was extracted (link attachments).
    public var url: URL?

    /// Shared or typed text, when one was extracted (text attachments).
    public var text: String?

    public init(typeIdentifiers: [String], url: URL? = nil, text: String? = nil) {
        self.typeIdentifiers = typeIdentifiers
        self.url = url
        self.text = text
    }
}

/// A complete item shared into the app: the set of attachments the share sheet
/// delivered in a single share action.
public struct SharedItem: Equatable {

    /// The attachments delivered with this share, in the order the system
    /// provided them.
    public var attachments: [SharedAttachment]

    public init(attachments: [SharedAttachment]) {
        self.attachments = attachments
    }
}

/// The classified kind of a ``SharedItem``.
///
/// The four supported kinds map 1:1 onto the contract's ``ContentType``
/// (`link | text | image | videoRef`) and onto the Share Extension's declared
/// `NSExtensionActivationRule` (web URL, text, image, movie — Req 4.1).
/// ``unsupported`` is the catch-all for anything the app does not capture; the
/// extension shows a "content type not supported" message, discards the item,
/// and ends the request (Req 4.4).
public enum SharedContentType: Equatable {
    case link
    case text
    case image
    case videoRef
    case unsupported

    /// The contract ``ContentType`` for a supported kind, or `nil` for
    /// ``unsupported``. Lets the capture path translate a classification into
    /// the persisted model type without re-deriving it.
    public var contentType: ContentType? {
        switch self {
        case .link: return .link
        case .text: return .text
        case .image: return .image
        case .videoRef: return .videoRef
        case .unsupported: return nil
        }
    }
}
