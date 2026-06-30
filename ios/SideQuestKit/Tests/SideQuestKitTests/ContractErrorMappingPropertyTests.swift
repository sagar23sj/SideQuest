import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Property 21 — "Contract-defined errors map to
/// category-specific messages preserving input"** (iOS design "iOS-specific
/// properties"; task 15.4).
///
/// **Validates: Requirements 2.5**
///
/// Req 2.5:
/// > IF the Backend returns a structured error response defined by the
/// > API_Contract, THEN THE App SHALL map the error to a User-facing message
/// > specific to that error category and SHALL preserve any unsaved User input.
///
/// Property 21 statement (as it applies to `BackendErrorMapper`): for any
/// contract-defined structured error response — whose HTTP status is one of the
/// documented set {400, 401, 409, 413, 503} — the mapper classifies it to the
/// status's category, surfaces a **non-empty, category-specific** user-facing
/// message, and produces a `BackendError` that never mutates caller state, so
/// `preservesUnsavedInput == true`. In addition:
///   * **401 always classifies as authentication** and is **never retriable**,
///     regardless of the response body (Req 2.7 reinforces 2.5 here); and
///   * an **error status carrying no structured body** is *not* a
///     contract-defined error, so it classifies as `.retriableTransient`
///     (the boundary of the Req 2.5 mapping; Req 2.6).
///
/// Subject under test:
///   * `BackendErrorMapper.classify(statusCode:body:decoder:)`
///   * `BackendErrorMapper.category(forStatus:)`
///   * `BackendErrorMapper.userFacingMessage(for:)`
///   * `BackendError.preservesUnsavedInput` / `.isAuthenticationFailure`
///
/// These tests perform no I/O (the mapper is pure), so they run on any host —
/// though, like the rest of the SwiftCheck suite, they must be executed on
/// macOS via `swift test`. Each property runs ≥100 iterations (the design
/// mandates a minimum of 100; we configure 200 for extra coverage).
final class ContractErrorMappingPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    // MARK: - Contract-documented statuses → expected category (Req 2.5)

    /// The documented `Error` statuses and the category each must map to. This
    /// is an independent oracle written directly from the contract/Req 2.5 — it
    /// is intentionally a separate table from the implementation's `switch`.
    private static let documentedStatusToCategory: [(status: Int, category: BackendErrorCategory)] = [
        (400, .validation),
        (401, .authentication),
        (409, .conflict),
        (413, .payloadTooLarge),
        (503, .serviceUnavailable)
    ]

    /// All distinct user-facing messages across every category, used to assert
    /// each produced message is *category-specific* (i.e. unique to its
    /// category and shared by no other).
    private static let allCategories: [BackendErrorCategory] = [
        .validation, .authentication, .conflict, .payloadTooLarge,
        .serviceUnavailable, .unexpected, .transient, .decoding
    ]

    // MARK: - Generators

    private static let encoder = SideQuestCoding.makeEncoder()

    /// Characters used to build arbitrary server messages.
    private static let messageChars: [Character] =
        Array("abcdefghijklmnopqrstuvwxyz é0123456789.,!-")

    /// An arbitrary, possibly-empty (0...40 char) server message (the contract
    /// keeps this intentionally generic; the client never shows it). Exercises a
    /// spread of characters and lengths so the mapping never depends on body text.
    private static let serverMessageGen: Gen<String> = Gen<Int>.choose((0, 40)).flatMap { size in
        Gen<Character>.fromElements(of: messageChars)
            .proliferate(withSize: size)
            .map { String($0) }
    }

    /// A documented status paired with its expected category.
    private static let documentedCaseGen: Gen<(status: Int, category: BackendErrorCategory)> =
        Gen.fromElements(of: documentedStatusToCategory)

    /// Encodes a contract `Error` body, e.g. `{"error":{"status":N,"message":"…"}}`.
    private static func structuredBody(status: Int, message: String) -> Data {
        // The mapper keys the category on the HTTP status, not on this echoed
        // status field; we set it to the HTTP status as the backend does.
        (try? encoder.encode(ContractError(status: status, message: message))) ?? Data()
    }

    /// Bodies that are NOT a contract-defined structured `Error` (so the mapper
    /// must treat the response as an undefined error → transient). Covers an
    /// empty body, plain text, a bare JSON value, a JSON array, and JSON objects
    /// missing the required `error`/`status`/`message` shape.
    private static let nonStructuredBodyGen: Gen<Data> = Gen<String>
        .fromElements(of: [
            "",
            "not json at all",
            "<html><body>502 Bad Gateway</body></html>",
            "null",
            "123",
            "[]",
            "[{\"error\":{\"status\":400,\"message\":\"x\"}}]",
            "{}",
            "{\"message\":\"missing error wrapper\"}",
            "{\"error\":\"a string, not an object\"}",
            "{\"error\":{\"message\":\"missing status\"}}",
            "{\"error\":{\"status\":\"not-an-int\",\"message\":\"bad type\"}}"
        ])
        .map { Data($0.utf8) }

    /// Non-2xx statuses **other than 401** (401 is special-cased to auth even
    /// without a structured body). Includes documented non-auth statuses and a
    /// spread of undocumented error statuses.
    private static let nonAuthErrorStatusGen: Gen<Int> = Gen<Int>
        .fromElements(of: [400, 409, 413, 503, 404, 418, 422, 500, 502, 504])

    /// Arbitrary bodies (structured, non-structured, or empty) for the 401 case,
    /// to prove 401 is auth-classified regardless of body.
    private static let anyBodyFor401Gen: Gen<Data> = Gen<Data>.one(of: [
        serverMessageGen.map { structuredBody(status: 401, message: $0) },
        nonStructuredBodyGen
    ])

    // MARK: - Helpers

    /// The message is *category-specific*: equal to its own category's message
    /// and equal to no other category's message.
    private static func isCategorySpecific(
        message: String,
        category: BackendErrorCategory
    ) -> Bool {
        guard message == BackendErrorMapper.userFacingMessage(for: category) else { return false }
        return allCategories
            .filter { $0 != category }
            .allSatisfy { BackendErrorMapper.userFacingMessage(for: $0) != message }
    }

    // MARK: - Property 21: documented structured error → its category + message

    /// For any documented status with a structured body, the mapper returns
    /// `.failure` carrying the status's category, a non-empty category-specific
    /// message, the originating status code, and `preservesUnsavedInput == true`.
    func testDocumentedStructuredErrorMapsToCategorySpecificMessage() {
        property("documented structured error → category-specific message, input preserved (Property 21, Req 2.5)",
                 arguments: Self.checkArgs)
            <- forAll(Self.documentedCaseGen, Self.serverMessageGen) { (testCase, serverMessage: String) in
                let body = Self.structuredBody(status: testCase.status, message: serverMessage)

                guard case let .failure(error) = BackendErrorMapper.classify(
                    statusCode: testCase.status,
                    body: body
                ) else {
                    return false <?> "status \(testCase.status) did not classify as .failure"
                }

                let categoryMatches = error.category == testCase.category
                let messageIsCategorySpecific = Self.isCategorySpecific(
                    message: error.userFacingMessage,
                    category: testCase.category
                )
                let messageNonEmpty = !error.userFacingMessage.isEmpty
                let statusPreserved = error.statusCode == testCase.status
                let inputPreserved = error.preservesUnsavedInput == true

                return (categoryMatches <?> "category \(error.category) != \(testCase.category)")
                    ^&&^ (messageIsCategorySpecific <?> "message not specific to \(testCase.category)")
                    ^&&^ (messageNonEmpty <?> "empty message")
                    ^&&^ (statusPreserved <?> "statusCode \(String(describing: error.statusCode)) != \(testCase.status)")
                    ^&&^ (inputPreserved <?> "preservesUnsavedInput was false")
            }
    }

    // MARK: - Property 21: 401 → authentication, never retriable, any body

    /// 401 always classifies as `.authentication` and is never retriable,
    /// regardless of whether the body is a structured error, non-structured, or
    /// empty (Req 2.5 mapping reinforced by Req 2.7).
    func testUnauthorizedAlwaysAuthenticationAndNotRetriable() {
        property("401 → authentication, not retriable, any body (Property 21, Req 2.5/2.7)",
                 arguments: Self.checkArgs)
            <- forAll(Self.anyBodyFor401Gen) { (body: Data) in
                let classification = BackendErrorMapper.classify(statusCode: 401, body: body)

                guard case let .failure(error) = classification else {
                    return false <?> "401 did not classify as .failure"
                }

                let isAuth = error.category == .authentication
                let notRetriable = classification != .retriableTransient
                let flaggedAuth = error.isAuthenticationFailure == true
                let statusPreserved = error.statusCode == 401
                let messageSpecific = Self.isCategorySpecific(
                    message: error.userFacingMessage,
                    category: .authentication
                )
                let inputPreserved = error.preservesUnsavedInput == true

                return (isAuth <?> "category \(error.category) != authentication")
                    ^&&^ (notRetriable <?> "401 was retriable")
                    ^&&^ (flaggedAuth <?> "isAuthenticationFailure was false")
                    ^&&^ (statusPreserved <?> "statusCode != 401")
                    ^&&^ (messageSpecific <?> "auth message not category-specific")
                    ^&&^ (inputPreserved <?> "preservesUnsavedInput was false")
            }
    }

    // MARK: - Property 21: error status without a structured body → transient

    /// A non-2xx, non-401 error status whose body is NOT a contract-defined
    /// structured `Error` is not a contract-defined error, so it classifies as
    /// `.retriableTransient` (Req 2.6 boundary of the Req 2.5 mapping).
    func testErrorStatusWithoutStructuredBodyIsRetriableTransient() {
        property("error status without structured body → retriableTransient (Property 21, Req 2.5/2.6)",
                 arguments: Self.checkArgs)
            <- forAll(Self.nonAuthErrorStatusGen, Self.nonStructuredBodyGen) { (status: Int, body: Data) in
                let classification = BackendErrorMapper.classify(statusCode: status, body: body)
                return (classification == .retriableTransient)
                    <?> "status \(status) with non-structured body classified as \(classification)"
            }
    }
}
