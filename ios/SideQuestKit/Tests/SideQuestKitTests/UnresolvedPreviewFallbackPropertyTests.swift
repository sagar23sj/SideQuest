import XCTest
import Foundation
import Dispatch
import SwiftCheck
@testable import SideQuestKit

#if canImport(LinkPresentation)
import LinkPresentation
#endif

/// Property-based test for **Reused Property 4 — Unresolved preview falls back
/// to the raw link without blocking capture** (task 9.2).
///
/// **Validates: Requirements 4.9**
///
/// > For any URL, when link preview metadata is not retrieved within the
/// > timeout or the retrieval fails, the `PreviewService` yields
/// > `PreviewResult.fallback(rawUrl:)` whose `rawUrl` equals the original URL,
/// > the fallback maps to an *unresolved* `LinkPreview` carrying that raw URL,
/// > and capture is never blocked waiting for the preview.
///
/// ## What "on timeout/failure" means here
///
/// Two production paths produce the fallback, and both are exercised:
///
/// - **Failure path (portable).** `FallbackPreviewService` is the production
///   `PreviewService` used wherever `LinkPresentation` is unavailable and as the
///   deterministic stand-in for the unresolved outcome. It models a fetch that
///   never resolves, so it always returns `.fallback(rawUrl:)`. This property
///   runs on every platform.
/// - **Timeout path (Apple platforms).** The real
///   `LinkPresentationPreviewService` is driven with `timeout: 0`, which forces
///   its internal timeout to win the race against the metadata fetch for every
///   URL, returning `.fallback(rawUrl:)` promptly. This exercises the actual
///   timeout machinery in `LinkPresentationPreviewService.swift`.
///
/// ## "Without blocking capture"
///
/// The fetch lives off the capture critical path: each call is bounded and
/// returns within the timeout window rather than waiting on the network. The
/// property asserts each call returns within a generous wall-clock bound, so a
/// non-resolving or timing-out fetch can never stall capture. The companion
/// unit test `CaptureConfirmationTests.testConfirmLinkDraftPersistsUnresolvedFallbackPreview`
/// shows confirm completing with exactly this unresolved fallback preview.
final class UnresolvedPreviewFallbackPropertyTests: XCTestCase {

    // MARK: - Property: failure path (portable, runs everywhere)

    /// Property 4 / Req 4.9 — a non-resolving fetch always falls back to the raw
    /// link, maps to an unresolved preview, and returns without blocking.
    func testUnresolvedFetchFallsBackToRawLinkAndDoesNotBlock() {
        let service = FallbackPreviewService()

        property(
            "an unresolved fetch yields .fallback(rawUrl:) == original url, maps to an unresolved preview, and returns promptly",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(Gen.zip(urlGen, timeoutGen)) { url, timeout in
            let start = Date()
            let result = Self.runBlocking { await service.fetchPreview(url, timeout: timeout) }
            let elapsed = Date().timeIntervalSince(start)

            return (Self.fallbackContractHolds(result, for: url)
                        <?> "result is .fallback(rawUrl:) equal to the original URL")
                ^&&^
                ((elapsed < 5.0)
                        <?> "fetch returned without blocking capture (bounded wall-clock time)")
        }
    }

    // MARK: - Property: timeout path (real service on Apple platforms)

    #if canImport(LinkPresentation)
    /// Property 4 / Req 4.9 — the real `LinkPresentationPreviewService` with a
    /// zero timeout forces the timeout branch to win for every URL, falling back
    /// to the raw link promptly so capture is not blocked.
    func testLinkPresentationServiceTimesOutToRawLinkForAnyUrl() {
        let service = LinkPresentationPreviewService()

        property(
            "a timed-out LinkPresentation fetch yields .fallback(rawUrl:) == original url and returns promptly",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 100)
        ) <- forAllNoShrink(urlGen) { url in
            let start = Date()
            // timeout: 0 makes the timeout child win the race immediately, so the
            // result is deterministically a fallback regardless of reachability.
            let result = Self.runBlocking { await service.fetchPreview(url, timeout: 0) }
            let elapsed = Date().timeIntervalSince(start)

            return (Self.fallbackContractHolds(result, for: url)
                        <?> "real service falls back to the raw URL on timeout")
                ^&&^
                ((elapsed < 5.0)
                        <?> "real service returns without blocking capture")
        }
    }
    #endif

    // MARK: - Shared oracle

    /// Asserts the full fallback contract (Req 4.9) for `result` against `url`:
    ///
    /// 1. `result` is `.fallback(rawUrl:)` whose `rawUrl` equals the original
    ///    URL's `absoluteString`.
    /// 2. Mapping the result to the persisted `LinkPreview` produces an
    ///    *unresolved* preview (`resolved == false`) carrying that same raw URL
    ///    and no resolved fields, so the UI displays the raw link.
    private static func fallbackContractHolds(_ result: PreviewResult, for url: URL) -> Bool {
        let raw = url.absoluteString

        guard case let .fallback(rawUrl) = result, rawUrl == raw else {
            return false
        }

        let preview = result.linkPreview(forRawUrl: raw)
        return preview.resolved == false
            && preview.rawUrl == raw
            && preview.title == nil
            && preview.thumbnailUrl == nil
            && preview.sourceName == nil
    }

    // MARK: - Async bridge

    /// Runs an `async` operation to completion from a synchronous SwiftCheck
    /// property body and returns its result.
    private static func runBlocking<T>(_ operation: @escaping () async -> T) -> T {
        let semaphore = DispatchSemaphore(value: 0)
        let box = ResultBox<T>()
        Task {
            box.value = await operation()
            semaphore.signal()
        }
        semaphore.wait()
        return box.value!
    }

    private final class ResultBox<T> {
        var value: T?
    }
}

// MARK: - Generators (constrain to the valid URL input space)

/// Path/query token characters that are safe inside URL components.
private let urlTokenChars: [Character] =
    Array("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_~")

private let urlTokenGen: Gen<String> = Gen<Int>.choose((1, 8)).flatMap { size in
    Gen<Character>.fromElements(of: urlTokenChars).proliferate(withSize: size).map { String($0) }
}

private let schemeGen = Gen<String>.fromElements(of: ["https", "http", "ftp", "app"])

private let hostGen = Gen<String>.fromElements(of: [
    "example.com", "sub.example.org", "news.site.io", "a.co",
    "localhost", "192.168.0.1", "very-long-host-name.example.museum"
])

/// Structurally composed URLs (the bulk of the input space): random scheme,
/// host, path depth, and an optional query and fragment, all built through
/// `URLComponents` so the resulting `URL` is always valid.
private let composedUrlGen: Gen<URL> = Gen.compose { c in
    var components = URLComponents()
    components.scheme = c.generate(using: schemeGen)
    components.host = c.generate(using: hostGen)

    let depth = c.generate(using: Gen<Int>.choose((0, 4)))
    if depth > 0 {
        let segments = c.generate(using: urlTokenGen.proliferate(withSize: depth))
        components.path = "/" + segments.joined(separator: "/")
    }

    if c.generate(using: Gen<Bool>.fromElements(of: [true, false])) {
        components.queryItems = [
            URLQueryItem(name: c.generate(using: urlTokenGen),
                         value: c.generate(using: urlTokenGen))
        ]
    }

    if c.generate(using: Gen<Bool>.fromElements(of: [true, false])) {
        components.fragment = c.generate(using: urlTokenGen)
    }

    // Components are always valid here, so `url` is non-nil.
    return components.url!
}

/// A pool of fixed edge-case URLs (ports, opaque schemes, no path, encoded
/// characters) to widen coverage beyond the composed generator.
private let edgeCaseUrlGen: Gen<URL> = Gen<String>.fromElements(of: [
    "https://example.com",
    "https://example.com:8443/path?x=1#frag",
    "http://localhost:3000/",
    "ftp://files.example.org/pub/file.txt",
    "mailto:someone@example.com",
    "app://open/item/42",
    "https://example.com/path%20with%20encoded",
    "https://例え.テスト/path"
].map { URL(string: $0)! })

/// The full URL generator: mostly composed URLs, with edge cases mixed in.
private let urlGen: Gen<URL> = Gen.one(of: [
    composedUrlGen,
    composedUrlGen,
    composedUrlGen,
    edgeCaseUrlGen
])

/// Timeout values spanning zero and small positive windows. For the failure
/// path the timeout is irrelevant to the outcome; varying it confirms the
/// fallback holds regardless of the configured window.
private let timeoutGen: Gen<TimeInterval> = Gen<TimeInterval>.fromElements(of: [
    0, 0.001, 0.05, 1, 5
])
