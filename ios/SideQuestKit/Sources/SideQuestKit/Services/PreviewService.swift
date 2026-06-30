import Foundation

// MARK: - Link preview service (Req 4.8, 4.9)
//
// Fetches link metadata for a shared URL *off the capture critical path*: the
// Share Extension may present and confirm a capture before this returns (Req
// 4.8), and a timeout or failure never blocks capture — it yields a fallback
// that displays the raw link (Req 4.9). The concrete, platform-backed
// implementation lives in `LinkPresentationPreviewService` (guarded by
// `#if canImport(LinkPresentation)`); this file declares the platform-independent
// abstraction so the shared module stays buildable everywhere and so capture
// code and tests can depend on the protocol rather than LinkPresentation.

/// Outcome of fetching link preview metadata for a shared URL.
///
/// - ``success(title:thumbnailUrl:sourceName:)`` — metadata resolved; the
///   capture stores the resolved preview fields.
/// - ``fallback(rawUrl:)`` — the fetch failed or timed out (Req 4.9); the
///   capture stores an *unresolved* preview whose `rawUrl` equals the original
///   URL and the UI displays the raw link in place of a preview.
///
/// Mapping to the persisted ``LinkPreview`` model is provided by
/// ``linkPreview(forRawUrl:)`` so callers store a consistent value regardless of
/// which case occurred.
public enum PreviewResult: Equatable {

    /// Metadata resolved. `title` and `sourceName` are guaranteed non-empty;
    /// `thumbnailUrl` is optional because not every link exposes one.
    case success(title: String, thumbnailUrl: String?, sourceName: String)

    /// The fetch failed or exceeded its timeout (Req 4.9). `rawUrl` is the
    /// original shared URL to display in place of a preview.
    case fallback(rawUrl: String)
}

extension PreviewResult {

    /// Maps this result to the persisted ``LinkPreview`` model (Req 4.9).
    ///
    /// On ``success(title:thumbnailUrl:sourceName:)`` the returned preview is
    /// `resolved == true` and carries the resolved fields. On
    /// ``fallback(rawUrl:)`` it is `resolved == false` with only `rawUrl`
    /// populated, so the UI falls back to the raw link. In both cases `rawUrl`
    /// is set to the supplied original URL so the stored preview always carries
    /// the link that was shared.
    ///
    /// - Parameter rawUrl: The original shared URL string. Used as the preview's
    ///   `rawUrl` for both cases so a later successful re-fetch can update the
    ///   same item reactively without losing the source link.
    public func linkPreview(forRawUrl rawUrl: String) -> LinkPreview {
        switch self {
        case let .success(title, thumbnailUrl, sourceName):
            return LinkPreview(
                title: title,
                thumbnailUrl: thumbnailUrl,
                sourceName: sourceName,
                rawUrl: rawUrl,
                resolved: true
            )
        case .fallback:
            return LinkPreview(rawUrl: rawUrl, resolved: false)
        }
    }
}

/// Fetches link preview metadata for a shared URL without blocking capture.
///
/// Implementations MUST honor `timeout`: if metadata does not resolve within
/// `timeout` seconds the call completes with ``PreviewResult/fallback(rawUrl:)``
/// rather than waiting longer (Req 4.9). The call is `async` and side-effect
/// free with respect to the local store — the caller decides when and whether
/// to persist the resulting ``LinkPreview``.
public protocol PreviewService {

    /// Fetches preview metadata for `url`, returning within `timeout` seconds.
    ///
    /// - Parameters:
    ///   - url: The shared link to resolve.
    ///   - timeout: Maximum time to wait before falling back to the raw link.
    ///     Defaults to ``PreviewDefaults/timeout`` (5 seconds, Req 4.9).
    /// - Returns: ``PreviewResult/success(title:thumbnailUrl:sourceName:)`` when
    ///   metadata resolves in time, otherwise ``PreviewResult/fallback(rawUrl:)``.
    func fetchPreview(_ url: URL, timeout: TimeInterval) async -> PreviewResult
}

/// Shared defaults for link preview fetching.
public enum PreviewDefaults {

    /// Default fetch timeout (Req 4.9): if metadata is not retrieved within 5
    /// seconds the capture falls back to the raw link.
    public static let timeout: TimeInterval = 5
}

extension PreviewService {

    /// Convenience overload using the default ``PreviewDefaults/timeout`` (5 s).
    public func fetchPreview(_ url: URL) async -> PreviewResult {
        await fetchPreview(url, timeout: PreviewDefaults.timeout)
    }
}

/// A ``PreviewService`` that never resolves metadata and always returns
/// ``PreviewResult/fallback(rawUrl:)``.
///
/// Used as the default on platforms where `LinkPresentation` is unavailable so
/// the shared module stays buildable, and as a deterministic stand-in for tests
/// and previews. Capture still completes; the UI shows the raw link (Req 4.9).
public struct FallbackPreviewService: PreviewService {

    public init() {}

    public func fetchPreview(_ url: URL, timeout: TimeInterval) async -> PreviewResult {
        .fallback(rawUrl: url.absoluteString)
    }
}

/// The concrete ``PreviewService`` to use by default on the current platform:
/// ``LinkPresentationPreviewService`` where `LinkPresentation` is available
/// (iOS), otherwise ``FallbackPreviewService``.
#if canImport(LinkPresentation)
public typealias DefaultPreviewService = LinkPresentationPreviewService
#else
public typealias DefaultPreviewService = FallbackPreviewService
#endif
