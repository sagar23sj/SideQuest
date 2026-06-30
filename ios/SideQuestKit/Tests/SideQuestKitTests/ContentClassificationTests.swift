import XCTest
import Foundation
@testable import SideQuestKit

/// Unit tests for the Share Extension's content classification and the
/// categorization sheet's selection gating (task 8.1).
///
/// **Covers: Requirements 4.2, 4.3, 4.4, 4.7**
///
/// These exercise the portable, host-testable pieces of task 8.1 that live in
/// `SideQuestKit`:
///   * ``ContentClassifier`` / ``DefaultCaptureService/classify(_:)`` —
///     classifying shared attachments into links, text, images, video
///     references, or unsupported (Req 4.2, 4.4).
///   * ``DefaultCaptureService/beginCapture(_:)`` — lowering a supported item
///     into a draft, or returning `nil` for unsupported content so the
///     extension shows "not supported", discards, and ends (Req 4.4).
///   * ``CategorizationSelection`` — the Save gate requiring exactly one Bucket
///     and one Timeframe (Req 4.3) and the discard-on-cancel contract (Req 4.7).
final class ContentClassificationTests: XCTestCase {

    private let service = DefaultCaptureService()

    // MARK: - Classification of supported types (Req 4.2)

    func testClassifiesWebURLAsLink() {
        let item = SharedItem(attachments: [
            SharedAttachment(typeIdentifiers: ["public.url"], url: URL(string: "https://example.com"))
        ])
        XCTAssertEqual(service.classify(item.attachments), .link)
    }

    func testClassifiesPlainTextAsText() {
        let item = SharedItem(attachments: [
            SharedAttachment(typeIdentifiers: ["public.plain-text"], text: "hello")
        ])
        XCTAssertEqual(service.classify(item.attachments), .text)
    }

    func testClassifiesImage() {
        let item = SharedItem(attachments: [
            SharedAttachment(typeIdentifiers: ["public.jpeg"])
        ])
        XCTAssertEqual(service.classify(item.attachments), .image)
    }

    func testClassifiesMovieAsVideoRef() {
        let item = SharedItem(attachments: [
            SharedAttachment(typeIdentifiers: ["public.movie"])
        ])
        XCTAssertEqual(service.classify(item.attachments), .videoRef)
    }

    // MARK: - Priority + unsupported (Req 4.2, 4.4)

    func testLinkWinsOverTextWhenBothPresent() {
        // Source apps often offer a URL and its string form together; the link
        // is the more specific intent and must win.
        let item = SharedItem(attachments: [
            SharedAttachment(
                typeIdentifiers: ["public.url", "public.plain-text"],
                url: URL(string: "https://example.com"),
                text: "https://example.com"
            )
        ])
        XCTAssertEqual(service.classify(item.attachments), .link)
    }

    func testUnknownTypeIsUnsupported() {
        let item = SharedItem(attachments: [
            SharedAttachment(typeIdentifiers: ["com.acme.proprietary-blob"])
        ])
        XCTAssertEqual(service.classify(item.attachments), .unsupported)
    }

    func testEmptyAttachmentsAreUnsupported() {
        XCTAssertEqual(service.classify([]), .unsupported)
    }

    // MARK: - beginCapture drafting (Req 4.2, 4.4)

    func testBeginCaptureReturnsNilForUnsupported() {
        let item = SharedItem(attachments: [
            SharedAttachment(typeIdentifiers: ["com.acme.proprietary-blob"])
        ])
        // nil => extension shows "not supported", discards, ends (Req 4.4).
        XCTAssertNil(service.beginCapture(item))
    }

    func testBeginCaptureLinkSeedsURLAndSource() {
        let url = URL(string: "https://example.com/post")!
        let item = SharedItem(attachments: [
            SharedAttachment(typeIdentifiers: ["public.url"], url: url)
        ])
        let draft = service.beginCapture(item)
        XCTAssertEqual(draft?.contentType, .link)
        XCTAssertEqual(draft?.linkURL, url)
        XCTAssertEqual(draft?.sourceContent, url.absoluteString)
    }

    func testBeginCaptureTextSeedsTitleFromFirstLine() {
        let item = SharedItem(attachments: [
            SharedAttachment(
                typeIdentifiers: ["public.plain-text"],
                text: "Buy milk\nand eggs"
            )
        ])
        let draft = service.beginCapture(item)
        XCTAssertEqual(draft?.contentType, .text)
        XCTAssertEqual(draft?.title, "Buy milk")
        XCTAssertEqual(draft?.sourceContent, "Buy milk\nand eggs")
    }

    // MARK: - Selection gating (Req 4.3)

    func testSaveDisabledUntilBothBucketAndTimeframeChosen() {
        // Nothing chosen.
        XCTAssertFalse(CategorizationSelection().canSave)
        // Only a bucket.
        XCTAssertFalse(CategorizationSelection(bucketId: "b1", timeframe: nil).canSave)
        // Only a timeframe.
        XCTAssertFalse(CategorizationSelection(bucketId: nil, timeframe: .today).canSave)
        // Both chosen — Save enabled (Req 4.3).
        XCTAssertTrue(CategorizationSelection(bucketId: "b1", timeframe: .today).canSave)
    }

    func testConfirmedIsNilUntilComplete() {
        XCTAssertNil(CategorizationSelection(bucketId: "b1", timeframe: nil).confirmed)
        let complete = CategorizationSelection(bucketId: "b1", timeframe: .withinAWeek)
        let confirmed = complete.confirmed
        XCTAssertEqual(confirmed?.bucketId, "b1")
        XCTAssertEqual(confirmed?.timeframe, .withinAWeek)
    }
}
