import XCTest
@testable import SideQuestKit

/// Example-based unit tests for the `Generated_Models` Codable behaviour
/// (task 2.2). Every model is round-tripped through the contract coders
/// (`SideQuestCoding.makeEncoder()` / `.makeDecoder()`) and asserted equal, so
/// the on-the-wire JSON matches the contract shared with the Android client and
/// the Go backend (Req 2.2, 3.3).
///
/// Harness note: these use **XCTest** to stay consistent with the existing
/// `IdentifierGeneratorTests`, which is the only other test in this target.
///
/// Date note: the contract encodes date-time fields as RFC 3339 *without*
/// fractional seconds (see `SideQuestCoding`), so fixtures use whole-second
/// instants to guarantee an exact round trip. `Timeframe.specificDate` is a
/// `yyyy-MM-dd` calendar date encoded in UTC, so fixtures build that date with
/// the same `CalendarDate.formatter` the model uses.
final class ModelCodableTests: XCTestCase {

    // MARK: - Coders / helpers

    private let encoder = SideQuestCoding.makeEncoder()
    private let decoder = SideQuestCoding.makeDecoder()

    /// Whole-second instant fixture (no fractional seconds to lose on the wire).
    private let fixedInstant = Date(timeIntervalSince1970: 1_700_000_000)

    /// A `specific_date` payload built with the model's own UTC calendar-date
    /// formatter so the decoded value compares equal after a round trip.
    private func calendarDate(_ text: String) -> Date {
        guard let date = CalendarDate.formatter.date(from: text) else {
            fatalError("Invalid test calendar date: \(text)")
        }
        return date
    }

    /// Encode `value`, decode it back, and return the decoded value.
    private func roundTrip<T: Codable>(_ value: T) throws -> T {
        let data = try encoder.encode(value)
        return try decoder.decode(T.self, from: data)
    }

    /// Encode `value` and return its JSON as a `[String: Any]` object so tests
    /// can assert the on-the-wire shape without depending on key ordering.
    private func jsonObject<T: Codable>(_ value: T) throws -> [String: Any] {
        let data = try encoder.encode(value)
        let object = try JSONSerialization.jsonObject(with: data)
        return try XCTUnwrap(object as? [String: Any])
    }

    private func sampleSync() -> SyncMeta {
        SyncMeta(updatedAt: fixedInstant, version: 7, deleted: false)
    }

    // MARK: - SyncMeta

    func testSyncMetaRoundTrip() throws {
        let value = SyncMeta(updatedAt: fixedInstant, version: 42, deleted: true)
        XCTAssertEqual(try roundTrip(value), value)
    }

    /// The client-only `dirty` flag must never reach the wire (it is omitted
    /// from `CodingKeys`) and defaults to `false` when decoded.
    func testSyncMetaDirtyFlagIsNotSerialized() throws {
        let dirty = SyncMeta(updatedAt: fixedInstant, version: 1, deleted: false, dirty: true)

        let object = try jsonObject(dirty)
        XCTAssertNil(object["dirty"], "`dirty` is client-only and must not be encoded")
        XCTAssertEqual(Set(object.keys), ["updatedAt", "version", "deleted"])

        let decoded = try roundTrip(dirty)
        XCTAssertFalse(decoded.dirty, "`dirty` should default to false after decoding")
        // The contract fields still round-trip (asserted explicitly because the
        // synthesized `==` also compares `dirty`, which intentionally differs).
        XCTAssertEqual(decoded.updatedAt, dirty.updatedAt)
        XCTAssertEqual(decoded.version, dirty.version)
        XCTAssertEqual(decoded.deleted, dirty.deleted)
    }

    /// `updatedAt` is written as an RFC 3339 date-time string.
    func testSyncMetaUpdatedAtIsRFC3339String() throws {
        let object = try jsonObject(sampleSync())
        let updatedAt = try XCTUnwrap(object["updatedAt"] as? String)
        XCTAssertEqual(updatedAt, "2023-11-14T22:13:20Z")
    }

    // MARK: - Enums

    func testActionStatusRoundTripAllCases() throws {
        for status in ActionStatus.allCases {
            XCTAssertEqual(try roundTrip(status), status)
        }
    }

    /// On-the-wire raw values are the contract's snake_case strings.
    func testActionStatusWireValues() throws {
        XCTAssertEqual(try encoder.encode(ActionStatus.notStarted).utf8String, "\"not_started\"")
        XCTAssertEqual(try encoder.encode(ActionStatus.inProgress).utf8String, "\"in_progress\"")
        XCTAssertEqual(try encoder.encode(ActionStatus.completed).utf8String, "\"completed\"")
    }

    func testContentTypeRoundTripAllCases() throws {
        for contentType in ContentType.allCases {
            XCTAssertEqual(try roundTrip(contentType), contentType)
        }
    }

    func testContentTypeWireValues() throws {
        XCTAssertEqual(try encoder.encode(ContentType.link).utf8String, "\"link\"")
        XCTAssertEqual(try encoder.encode(ContentType.text).utf8String, "\"text\"")
        XCTAssertEqual(try encoder.encode(ContentType.image).utf8String, "\"image\"")
        XCTAssertEqual(try encoder.encode(ContentType.videoRef).utf8String, "\"video_ref\"")
    }

    // MARK: - Timeframe (all variants + wire shape)

    func testTimeframeRoundTripAllVariants() throws {
        let variants: [Timeframe] = [
            .today,
            .withinADay,
            .withinAWeek,
            .specificDate(calendarDate("2025-06-14"))
        ]
        for variant in variants {
            XCTAssertEqual(try roundTrip(variant), variant)
        }
    }

    /// Non-date variants encode as a bare discriminator object.
    func testTimeframeDiscriminatorOnlyShape() throws {
        let cases: [(Timeframe, String)] = [
            (.today, "today"),
            (.withinADay, "within_a_day"),
            (.withinAWeek, "within_a_week")
        ]
        for (timeframe, kind) in cases {
            let object = try jsonObject(timeframe)
            XCTAssertEqual(object["kind"] as? String, kind)
            XCTAssertNil(object["date"], "Non-date variants must not carry a date payload")
            XCTAssertEqual(Set(object.keys), ["kind"])
        }
    }

    /// `specificDate` encodes as discriminator + `yyyy-MM-dd` date payload.
    func testTimeframeSpecificDateShape() throws {
        let object = try jsonObject(Timeframe.specificDate(calendarDate("2025-06-14")))
        XCTAssertEqual(object["kind"] as? String, "specific_date")
        XCTAssertEqual(object["date"] as? String, "2025-06-14")
        XCTAssertEqual(Set(object.keys), ["kind", "date"])
    }

    // MARK: - LinkPreview

    func testLinkPreviewResolvedRoundTrip() throws {
        let value = LinkPreview(
            title: "Example",
            thumbnailUrl: "https://example.com/thumb.png",
            sourceName: "example.com",
            rawUrl: "https://example.com/article",
            resolved: true
        )
        XCTAssertEqual(try roundTrip(value), value)
    }

    /// Unresolved preview (optional fields nil) round-trips intact.
    func testLinkPreviewUnresolvedRoundTrip() throws {
        let value = LinkPreview(rawUrl: "https://example.com/raw", resolved: false)
        XCTAssertEqual(try roundTrip(value), value)
    }

    // MARK: - TimeOfDay / TaskReminder

    func testTimeOfDayRoundTrip() throws {
        let value = TimeOfDay(hour: 21, minute: 30)
        XCTAssertEqual(try roundTrip(value), value)
    }

    func testTaskReminderRoundTrip() throws {
        let value = TaskReminder(
            actionItemId: "11111111-1111-1111-1111-111111111111",
            timeOfDay: TimeOfDay(hour: 8, minute: 5),
            untilDate: fixedInstant,
            recurringDaily: true
        )
        XCTAssertEqual(try roundTrip(value), value)
    }

    // MARK: - SubAction / ActionPlan

    func testSubActionRoundTrip() throws {
        let value = SubAction(id: "sa-1", text: "Draft outline", order: 0, completed: false)
        XCTAssertEqual(try roundTrip(value), value)
    }

    func testActionPlanRoundTrip() throws {
        let value = ActionPlan(
            id: "ap-1",
            actionItemId: "ai-1",
            subActions: [
                SubAction(id: "sa-1", text: "Step one", order: 0, completed: true),
                SubAction(id: "sa-2", text: "Step two", order: 1, completed: false)
            ],
            sync: sampleSync()
        )
        XCTAssertEqual(try roundTrip(value), value)
    }

    // MARK: - Bucket

    func testBucketRoundTrip() throws {
        let value = Bucket(
            id: "bucket-1",
            accountId: "acct-1",
            name: "Reading",
            notStartedColor: "#FF0000",
            inProgressColor: "#00FF00",
            completedColor: "#0000FF",
            sync: sampleSync()
        )
        XCTAssertEqual(try roundTrip(value), value)
    }

    // MARK: - ActionItem (covers nested Timeframe / ContentType / LinkPreview)

    func testActionItemLinkRoundTrip() throws {
        let value = ActionItem(
            id: "ai-link",
            accountId: "acct-1",
            bucketId: "bucket-1",
            title: "Read this article",
            description: "An LLM-generated summary",
            contentType: .link,
            sourceContent: "https://example.com/article",
            preview: LinkPreview(
                title: "Example",
                thumbnailUrl: "https://example.com/t.png",
                sourceName: "example.com",
                rawUrl: "https://example.com/article",
                resolved: true
            ),
            timeframe: .specificDate(calendarDate("2025-12-01")),
            status: .inProgress,
            createdAt: fixedInstant,
            sync: sampleSync()
        )
        XCTAssertEqual(try roundTrip(value), value)
    }

    /// A text item with all optionals nil and a non-date timeframe.
    func testActionItemTextMinimalRoundTrip() throws {
        let value = ActionItem(
            id: "ai-text",
            accountId: "acct-1",
            bucketId: "bucket-1",
            title: "A quick note",
            contentType: .text,
            timeframe: .today,
            status: .notStarted,
            createdAt: fixedInstant,
            sync: sampleSync()
        )
        XCTAssertEqual(try roundTrip(value), value)
    }

    /// Exercise every ContentType / Timeframe combination through a full item.
    func testActionItemRoundTripAcrossContentAndTimeframe() throws {
        let timeframes: [Timeframe] = [
            .today, .withinADay, .withinAWeek, .specificDate(calendarDate("2025-06-14"))
        ]
        for contentType in ContentType.allCases {
            for timeframe in timeframes {
                let value = ActionItem(
                    id: "ai-\(contentType.rawValue)",
                    accountId: "acct-1",
                    bucketId: "bucket-1",
                    title: "Item",
                    contentType: contentType,
                    sourceContent: "payload",
                    timeframe: timeframe,
                    status: .completed,
                    createdAt: fixedInstant,
                    sync: sampleSync()
                )
                XCTAssertEqual(
                    try roundTrip(value), value,
                    "Round trip failed for \(contentType) / \(timeframe)"
                )
            }
        }
    }

    // MARK: - Account (Codable but not Equatable -> assert field-by-field)

    func testAccountRoundTrip() throws {
        let value = Account(
            id: "acct-1",
            email: "user@example.com",
            displayName: "Sample User",
            createdAt: fixedInstant
        )
        let decoded = try roundTrip(value)
        XCTAssertEqual(decoded.id, value.id)
        XCTAssertEqual(decoded.email, value.email)
        XCTAssertEqual(decoded.displayName, value.displayName)
        XCTAssertEqual(decoded.createdAt, value.createdAt)
    }

    /// The decoder tolerates unknown contract keys (e.g. the optional `orgId`).
    func testAccountDecodingIgnoresUnknownKeys() throws {
        let json = """
        {
          "id": "acct-1",
          "email": "user@example.com",
          "displayName": "Sample User",
          "createdAt": "2023-11-14T22:13:20Z",
          "orgId": "org-9"
        }
        """
        let decoded = try decoder.decode(Account.self, from: Data(json.utf8))
        XCTAssertEqual(decoded.id, "acct-1")
        XCTAssertEqual(decoded.createdAt, fixedInstant)
    }

    // MARK: - Thought

    func testThoughtRoundTrip() throws {
        let value = Thought(id: 3, text: "Small steps still move you forward.")
        XCTAssertEqual(try roundTrip(value), value)
    }
}

// MARK: - Test utilities

private extension Data {
    /// UTF-8 string view of encoded JSON, for compact wire-value assertions.
    var utf8String: String { String(decoding: self, as: UTF8.self) }
}
