import Foundation

#if canImport(LinkPresentation)
import LinkPresentation

// MARK: - LinkPresentation-backed PreviewService (Req 4.8, 4.9)
//
// Resolves Open Graph / link metadata using `LPMetadataProvider`. This file is
// compiled only where `LinkPresentation` exists (iOS/iPadOS/macOS); the
// platform-independent ``PreviewService`` protocol and ``PreviewResult`` live in
// `PreviewService.swift` so the shared module builds on every platform.
//
// Two behaviours matter for the requirements:
//   * Off the critical path (Req 4.8): the method is `async` and the caller may
//     confirm a capture before it returns. It performs no store I/O.
//   * Hard timeout + fail-soft (Req 4.9): the result is guaranteed within
//     `timeout` seconds. On timeout or any error the result is
//     ``PreviewResult/fallback(rawUrl:)`` so capture stores the raw link.

/// A ``PreviewService`` backed by `LPMetadataProvider`.
///
/// Each call uses a fresh `LPMetadataProvider` because the provider is
/// single-use (a provider may fetch metadata only once). The fetch is raced
/// against an independent timeout so the call always returns within `timeout`
/// seconds even if the system fetch stalls; when the timeout wins, the
/// in-flight provider is cancelled (Req 4.9).
public final class LinkPresentationPreviewService: PreviewService {

    public init() {}

    /// Fetches preview metadata for `url`, falling back to the raw link if it
    /// does not resolve within `timeout` seconds (Req 4.9).
    public func fetchPreview(
        _ url: URL,
        timeout: TimeInterval = PreviewDefaults.timeout
    ) async -> PreviewResult {
        let rawUrl = url.absoluteString

        // Race the metadata fetch against a timeout. Whichever finishes first
        // wins; the remaining child task is cancelled. This guarantees the call
        // returns within `timeout` regardless of how the system fetch behaves.
        return await withTaskGroup(of: PreviewResult.self) { group in
            group.addTask {
                await Self.fetchMetadata(for: url, timeout: timeout)
            }
            group.addTask {
                let nanos = UInt64((timeout > 0 ? timeout : 0) * 1_000_000_000)
                try? await Task.sleep(nanoseconds: nanos)
                return .fallback(rawUrl: rawUrl)
            }

            let first = await group.next() ?? .fallback(rawUrl: rawUrl)
            group.cancelAll()
            return first
        }
    }

    // MARK: - Metadata fetch

    /// Runs a single `LPMetadataProvider` fetch and maps the result. Resolves to
    /// ``PreviewResult/fallback(rawUrl:)`` on any error, including cancellation
    /// (which fires when the racing timeout wins).
    private static func fetchMetadata(
        for url: URL,
        timeout: TimeInterval
    ) async -> PreviewResult {
        let rawUrl = url.absoluteString
        let provider = LPMetadataProvider()
        // Belt-and-suspenders: also bound the provider's own fetch so its
        // completion handler fires (and the continuation resumes) even if the
        // surrounding task is never cancelled.
        if timeout > 0 {
            provider.timeout = timeout
        }

        return await withTaskCancellationHandler {
            await withCheckedContinuation { (continuation: CheckedContinuation<PreviewResult, Never>) in
                provider.startFetchingMetadata(for: url) { metadata, error in
                    // The completion handler is invoked exactly once by
                    // LinkPresentation, so the continuation is resumed once.
                    guard let metadata, error == nil else {
                        continuation.resume(returning: .fallback(rawUrl: rawUrl))
                        return
                    }
                    continuation.resume(returning: map(metadata, rawUrl: rawUrl))
                }
            }
        } onCancel: {
            // Timeout won the race: stop the in-flight fetch. The provider then
            // invokes its completion handler with a cancellation error, which
            // resumes the continuation above with a fallback.
            provider.cancel()
        }
    }

    /// Maps `LPLinkMetadata` to a ``PreviewResult``.
    ///
    /// A resolved preview requires a usable title, so metadata without a
    /// non-empty title degrades to ``PreviewResult/fallback(rawUrl:)`` and the
    /// UI shows the raw link. `sourceName` is derived from the resolved URL's
    /// host (the link's site), defaulting to the raw URL when no host is
    /// available. `thumbnailUrl` is `nil`: `LPLinkMetadata` exposes its image as
    /// an `NSItemProvider` (image data), not a remote URL, so there is no URL
    /// string to persist here.
    private static func map(_ metadata: LPLinkMetadata, rawUrl: String) -> PreviewResult {
        let title = metadata.title?.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let title, !title.isEmpty else {
            return .fallback(rawUrl: rawUrl)
        }

        let host = (metadata.url ?? metadata.originalURL)?.host
        let sourceName = (host?.isEmpty == false) ? host! : rawUrl

        return .success(title: title, thumbnailUrl: nil, sourceName: sourceName)
    }
}

#endif
