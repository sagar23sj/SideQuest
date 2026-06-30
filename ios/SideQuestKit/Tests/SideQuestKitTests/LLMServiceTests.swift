import XCTest
@testable import SideQuestKit

/// Unit tests for the LLM notification-text service (task 13.10).
///
/// Covers the pure bounding/fail-soft resolution (`LlmNotificationText`,
/// `LLMService.resolvedNotificationText`) and the proxy-backed `ProxyLLMService`
/// success / error / timeout paths.
///
/// Requirements: 7.16 (text ≤ 200 chars from the LLM Proxy), 7.17 (on timeout or
/// error, deliver with default text). The named **Property 15** is exercised by
/// the separate property-test task (13.11); these are example-based checks.
final class LLMServiceTests: XCTestCase {

    // MARK: - Stub transport

    /// Minimal `HTTPTransport` stub: returns a canned response, throws a canned
    /// transport error, or sleeps before responding (to drive the timeout path).
    private struct StubTransport: HTTPTransport {
        enum Behavior {
            case respond(HTTPResponse)
            case fail(HTTPTransportError)
            case delayThenRespond(seconds: TimeInterval, HTTPResponse)
        }

        let behavior: Behavior

        func perform(_ request: HTTPRequest) async throws -> HTTPResponse {
            switch behavior {
            case let .respond(response):
                return response
            case let .fail(error):
                throw error
            case let .delayThenRespond(seconds, response):
                try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                return response
            }
        }
    }

    private func makeClient(_ behavior: StubTransport.Behavior) -> BackendClient {
        // retryDelay 0 so transient retries do not slow the test.
        BackendClient(transport: StubTransport(behavior: behavior), retryDelay: 0)
    }

    private func okResponse(text: String) -> HTTPResponse {
        let body = try! JSONSerialization.data(withJSONObject: ["text": text])
        return HTTPResponse(statusCode: 200, body: body)
    }

    private let sampleItems = [
        ActionItemSummary(title: "Book flight", bucketName: "Travel", dueLabel: "today")
    ]

    // MARK: - LlmNotificationText.bounded (Req 7.16)

    func testBoundedTrimsWhitespace() {
        XCTAssertEqual(LlmNotificationText.bounded("  hello  "), "hello")
    }

    func testBoundedKeepsShortTextUnchanged() {
        let text = "Don't forget to book your flight"
        XCTAssertEqual(LlmNotificationText.bounded(text), text)
    }

    func testBoundedTruncatesToMaxLength() {
        let long = String(repeating: "a", count: 250)
        let result = LlmNotificationText.bounded(long)
        XCTAssertEqual(result.count, LlmNotificationText.maxLength)
    }

    func testBoundedAtExactlyMaxLengthIsUnchanged() {
        let exact = String(repeating: "b", count: LlmNotificationText.maxLength)
        XCTAssertEqual(LlmNotificationText.bounded(exact), exact)
    }

    func testBoundedDoesNotSplitGraphemeClusters() {
        // 201 family emoji (each a multi-scalar grapheme); truncation must count
        // characters, never cutting one in half.
        let emoji = String(repeating: "👨‍👩‍👧‍👦", count: 201)
        let result = LlmNotificationText.bounded(emoji)
        XCTAssertEqual(result.count, LlmNotificationText.maxLength)
    }

    // MARK: - resolvedNotificationText fail-soft (Req 7.16, 7.17)

    /// A fake service so the resolution extension can be tested independent of
    /// the network.
    private struct FixedResultService: LLMService {
        let result: LlmResult
        func notificationText(for items: [ActionItemSummary]) async -> LlmResult { result }
    }

    func testResolvedReturnsBoundedProxyTextOnOk() async {
        let service = FixedResultService(result: .ok("  Time to book your flight  "))
        let text = await service.resolvedNotificationText(for: sampleItems, default: "Default")
        XCTAssertEqual(text, "Time to book your flight")
    }

    func testResolvedBoundsLongProxyTextToMaxLength() async {
        let service = FixedResultService(result: .ok(String(repeating: "x", count: 500)))
        let text = await service.resolvedNotificationText(for: sampleItems, default: "Default")
        XCTAssertEqual(text.count, LlmNotificationText.maxLength)
    }

    func testResolvedFallsBackWhenProxyTextBlank() async {
        let service = FixedResultService(result: .ok("    "))
        let text = await service.resolvedNotificationText(for: sampleItems, default: "Default reminder")
        XCTAssertEqual(text, "Default reminder")
    }

    func testResolvedUsesDefaultOnTimedOut() async {
        let service = FixedResultService(result: .timedOut)
        let text = await service.resolvedNotificationText(for: sampleItems, default: "Default reminder")
        XCTAssertEqual(text, "Default reminder")
        XCTAssertFalse(text.isEmpty)
    }

    func testResolvedUsesDefaultOnUnavailable() async {
        let service = FixedResultService(result: .unavailable)
        let text = await service.resolvedNotificationText(for: sampleItems, default: "Default reminder")
        XCTAssertEqual(text, "Default reminder")
        XCTAssertFalse(text.isEmpty)
    }

    func testResolvedBoundsDefaultText() async {
        let service = FixedResultService(result: .unavailable)
        let longDefault = String(repeating: "d", count: 300)
        let text = await service.resolvedNotificationText(for: sampleItems, default: longDefault)
        XCTAssertEqual(text.count, LlmNotificationText.maxLength)
    }

    // MARK: - ProxyLLMService (Req 7.16, 7.17)

    func testProxyReturnsOkOnSuccess() async {
        let service = ProxyLLMService(client: makeClient(.respond(okResponse(text: "Book your flight"))))
        let result = await service.notificationText(for: sampleItems)
        XCTAssertEqual(result, .ok("Book your flight"))
    }

    func testProxyReturnsUnavailableOnTransportFailure() async {
        let service = ProxyLLMService(client: makeClient(.fail(.offline)))
        let result = await service.notificationText(for: sampleItems)
        XCTAssertEqual(result, .unavailable)
    }

    func testProxyReturnsUnavailableOnProviderError() async {
        // 503 ProviderUnavailable from the contract → mapped error → unavailable.
        let service = ProxyLLMService(client: makeClient(.respond(HTTPResponse(statusCode: 503))))
        let result = await service.notificationText(for: sampleItems)
        XCTAssertEqual(result, .unavailable)
    }

    func testProxyTimesOutWhenResponseIsTooSlow() async {
        // Transport sleeps 2 s; the service timeout is 0.2 s, so the race yields
        // .timedOut (Req 7.17).
        let slow = StubTransport.Behavior.delayThenRespond(seconds: 2, okResponse(text: "late"))
        let service = ProxyLLMService(client: makeClient(slow), timeout: 0.2)
        let result = await service.notificationText(for: sampleItems)
        XCTAssertEqual(result, .timedOut)
    }

    func testProxyResolvedDeliversNonEmptyDefaultOnTimeout() async {
        let slow = StubTransport.Behavior.delayThenRespond(seconds: 2, okResponse(text: "late"))
        let service = ProxyLLMService(client: makeClient(slow), timeout: 0.2)
        let text = await service.resolvedNotificationText(
            for: sampleItems,
            default: NotificationDefaults.taskReminderTitle
        )
        XCTAssertEqual(text, NotificationDefaults.taskReminderTitle)
        XCTAssertFalse(text.isEmpty)
    }
}
