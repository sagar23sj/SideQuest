import XCTest
@testable import SideQuestKit

/// Example/edge-case unit tests for the portable notification-scheduling core
/// (task 13.1). These exercise the pure `DateComponents` derivation, identifier
/// scheme, status mapping, settings deep link, and the at-most-once
/// permission-request flag — none of which depend on the iOS-only
/// `UserNotifications` framework, so they run on any host. The exhaustive
/// local-wall-clock-anchoring and at-most-once property tests are tasks 13.2 and
/// 13.3.
final class NotificationSchedulingTests: XCTestCase {

    // MARK: - Local wall-clock anchoring (Req 7.10)

    /// A daily trigger carries only hour/minute — no date and no time zone — so
    /// the system evaluates it in the device's current local time zone.
    func testDailyComponentsCarryOnlyHourAndMinute() {
        let components = NotificationScheduling.dailyComponents(at: TimeOfDay(hour: 19, minute: 30))

        XCTAssertEqual(components.hour, 19)
        XCTAssertEqual(components.minute, 30)
        XCTAssertNil(components.year)
        XCTAssertNil(components.month)
        XCTAssertNil(components.day)
        // No time zone is the property that anchors to local wall-clock time.
        XCTAssertNil(components.timeZone)
    }

    /// A one-shot trigger carries year/month/day + hour/minute and, critically,
    /// no time zone, so it fires once at the intended local wall-clock time.
    func testOnDayComponentsCarryDateAndTimeWithoutTimeZone() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!

        // 2025-06-14 12:00 in New York.
        let date = calendar.date(from: DateComponents(year: 2025, month: 6, day: 14, hour: 12))!
        let components = NotificationScheduling.components(
            at: TimeOfDay(hour: 9, minute: 5),
            onDayOf: date,
            calendar: calendar
        )

        XCTAssertEqual(components.year, 2025)
        XCTAssertEqual(components.month, 6)
        XCTAssertEqual(components.day, 14)
        XCTAssertEqual(components.hour, 9)
        XCTAssertEqual(components.minute, 5)
        XCTAssertNil(components.timeZone)
    }

    /// The calendar day is resolved in the injected calendar's time zone, so the
    /// same instant yields different calendar days under different zones — the
    /// anchoring is by local wall-clock day, not by UTC instant.
    func testOnDayComponentsResolveCalendarDayInInjectedTimeZone() {
        // A single instant whose calendar day differs by time zone:
        // 2025-06-15T01:00:00Z is still the 14th in New York (UTC-4 in June) but
        // already the 15th in Tokyo (UTC+9).
        let instant = Date(timeIntervalSince1970: 1_749_949_200) // 2025-06-15T01:00:00Z

        var ny = Calendar(identifier: .gregorian)
        ny.timeZone = TimeZone(identifier: "America/New_York")!
        var tokyo = Calendar(identifier: .gregorian)
        tokyo.timeZone = TimeZone(identifier: "Asia/Tokyo")!

        let nyComponents = NotificationScheduling.components(
            at: TimeOfDay(hour: 8, minute: 0), onDayOf: instant, calendar: ny
        )
        let tokyoComponents = NotificationScheduling.components(
            at: TimeOfDay(hour: 8, minute: 0), onDayOf: instant, calendar: tokyo
        )

        // New York 2025-06-14T21:00 → day 14; Tokyo 2025-06-15T10:00 → day 15.
        XCTAssertEqual(nyComponents.day, 14)
        XCTAssertEqual(tokyoComponents.day, 15)
    }

    // MARK: - Identifier scheme (Req 7.8)

    func testTaskReminderIdentifierIsNamespacedByItem() {
        let id = NotificationIdentifier.taskReminder(itemId: "item-1", occurrence: "2025-06-14")
        XCTAssertTrue(id.hasPrefix(NotificationIdentifier.taskReminderNamespace))
        XCTAssertTrue(NotificationIdentifier.isTaskReminder(id, forItem: "item-1"))
    }

    func testTaskReminderMatchIsScopedToTheOwningItem() {
        let id = NotificationIdentifier.taskReminder(itemId: "item-1", occurrence: "d1")
        XCTAssertFalse(NotificationIdentifier.isTaskReminder(id, forItem: "item-2"))
        // A different item whose id is a prefix of another must not match.
        let idForItem10 = NotificationIdentifier.taskReminder(itemId: "item-10", occurrence: "d1")
        XCTAssertFalse(NotificationIdentifier.isTaskReminder(idForItem10, forItem: "item-1"))
    }

    func testSingletonIdentifiersAreDistinct() {
        XCTAssertNotEqual(NotificationIdentifier.globalDaily, NotificationIdentifier.eveningNudge)
    }

    // MARK: - Authorization status

    func testAuthStatusIsAuthorizedReflectsDeliverability() {
        XCTAssertTrue(NotificationAuthStatus.authorized.isAuthorized)
        XCTAssertTrue(NotificationAuthStatus.provisional.isAuthorized)
        XCTAssertTrue(NotificationAuthStatus.ephemeral.isAuthorized)
        XCTAssertFalse(NotificationAuthStatus.denied.isAuthorized)
        XCTAssertFalse(NotificationAuthStatus.notDetermined.isAuthorized)
    }

    // MARK: - Settings deep link (Req 7.18, 11.4)

    func testSettingsLinkMatchesOpenSettingsURLString() {
        // Equals UIApplication.openSettingsURLString without importing UIKit.
        XCTAssertEqual(NotificationSettingsLink.settingsURLString, "app-settings:")
        XCTAssertNotNil(NotificationSettingsLink.settingsURL)
    }

    // MARK: - At-most-once permission flag (Req 11.1, 11.5)

    func testPermissionRequestStoreRecordsRequestedFlag() {
        let suite = "test.notifications.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }

        let store = UserDefaultsPermissionRequestStore(defaults: defaults)
        XCTAssertFalse(store.hasRequestedAuthorization())

        store.markAuthorizationRequested()
        XCTAssertTrue(store.hasRequestedAuthorization())

        // Persists for a new store instance over the same defaults.
        let reopened = UserDefaultsPermissionRequestStore(defaults: defaults)
        XCTAssertTrue(reopened.hasRequestedAuthorization())
    }
}
