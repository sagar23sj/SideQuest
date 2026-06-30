import Foundation

// MARK: - Content classification (task 8.1, Req 4.2, 4.4)
//
// Pure, host-testable classification of shared attachments into a
// `SharedContentType`. Lives in `SideQuestKit` (not the iOS-only extension) so
// it can be unit/property tested without UIKit or `NSItemProvider`.
//
// Classification is driven entirely by the attachments' Uniform Type Identifier
// (UTI) strings. We cannot query the system UTI conformance graph here (that
// requires `UniformTypeIdentifiers`, an Apple-only framework), so we encode the
// relevant roots of Apple's well-known UTI hierarchy plus conservative
// substring heuristics. This keeps the classifier portable while matching the
// declared `NSExtensionActivationRule` kinds (web URL, text, image, movie —
// Req 4.1).

/// Classifies shared attachments into a ``SharedContentType`` (Req 4.2).
///
/// Pure and total: it never performs I/O and returns a value for every input,
/// including an empty attachment list (which classifies as
/// ``SharedContentType/unsupported``).
public enum ContentClassifier {

    /// Classifies a list of attachments, returning the single
    /// ``SharedContentType`` for the shared item.
    ///
    /// A shared item may carry several attachments (and each attachment several
    /// type identifiers), so classification picks the **highest-priority**
    /// supported kind present, in this fixed order:
    ///
    /// 1. ``SharedContentType/link`` — a shared web link is the most specific
    ///    intent and is often *also* offered as plain text by the source app, so
    ///    a URL wins over text.
    /// 2. ``SharedContentType/image``
    /// 3. ``SharedContentType/videoRef``
    /// 4. ``SharedContentType/text``
    ///
    /// If no attachment matches any supported kind the result is
    /// ``SharedContentType/unsupported`` (Req 4.4). The order is deterministic
    /// so the same shared item always classifies the same way across runs and
    /// platforms.
    public static func classify(_ attachments: [SharedAttachment]) -> SharedContentType {
        let identifiers = attachments.flatMap(\.typeIdentifiers)
        guard !identifiers.isEmpty else { return .unsupported }

        if identifiers.contains(where: isLink) { return .link }
        if identifiers.contains(where: isImage) { return .image }
        if identifiers.contains(where: isVideo) { return .videoRef }
        if identifiers.contains(where: isText) { return .text }
        return .unsupported
    }

    /// Convenience overload classifying a whole ``SharedItem``.
    public static func classify(_ item: SharedItem) -> SharedContentType {
        classify(item.attachments)
    }

    // MARK: - Per-kind UTI matching

    /// Web/file URL UTIs (`public.url`, `public.file-url`). Matches the
    /// extension's `NSExtensionActivationSupportsWebURLWithMaxCount` rule.
    static func isLink(_ identifier: String) -> Bool {
        let id = normalize(identifier)
        return id == "public.url"
            || id == "public.file-url"
            || id.hasSuffix(".url")
    }

    /// Image UTIs. Most concrete image types (`public.jpeg`, `public.png`,
    /// `public.heic`, `com.compuserve.gif`, …) conform to `public.image`; we
    /// match the root plus the common concrete identifiers and an `image`
    /// substring fallback for vendor types.
    static func isImage(_ identifier: String) -> Bool {
        let id = normalize(identifier)
        if Self.imageIdentifiers.contains(id) { return true }
        return id.contains("image")
    }

    /// Movie/video UTIs (`public.movie`, `public.video`, `public.mpeg-4`,
    /// `com.apple.quicktime-movie`, …). Matches the extension's
    /// `NSExtensionActivationSupportsMovieWithMaxCount` rule.
    static func isVideo(_ identifier: String) -> Bool {
        let id = normalize(identifier)
        if Self.videoIdentifiers.contains(id) { return true }
        return id.contains("movie")
            || id.contains("video")
            || id.contains("mpeg-4")
            || id.contains("mpeg4")
    }

    /// Text UTIs (`public.text`, `public.plain-text`, `public.utf8-plain-text`,
    /// `public.rtf`, …). Note links and text can overlap on the source side;
    /// link wins by priority in ``classify(_:)-9w0``.
    static func isText(_ identifier: String) -> Bool {
        let id = normalize(identifier)
        if Self.textIdentifiers.contains(id) { return true }
        return id.contains("text")
    }

    // MARK: - Known concrete identifiers

    private static let imageIdentifiers: Set<String> = [
        "public.image",
        "public.jpeg",
        "public.png",
        "public.tiff",
        "public.heic",
        "public.heif",
        "com.compuserve.gif",
        "com.microsoft.bmp",
        "public.webp",
    ]

    private static let videoIdentifiers: Set<String> = [
        "public.movie",
        "public.video",
        "public.mpeg-4",
        "public.mpeg",
        "com.apple.quicktime-movie",
        "public.avi",
    ]

    private static let textIdentifiers: Set<String> = [
        "public.text",
        "public.plain-text",
        "public.utf8-plain-text",
        "public.utf16-plain-text",
        "public.rtf",
    ]

    /// Lowercases and trims a UTI so comparison is case- and whitespace-
    /// insensitive (UTIs are conventionally lowercased, but be defensive).
    private static func normalize(_ identifier: String) -> String {
        identifier.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }
}
