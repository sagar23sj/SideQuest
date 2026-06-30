import XCTest
@testable import SideQuestKit

/// Example/edge-case unit tests for the portable evening-nudge selection and the
/// notification-preferences store + applier (task 13.8). These exercise the pure
/// eligible-item selection (Req 7.13, 7.14), the enable/disable + time-selection
/// persistence (Req 7.12, 7.15), and the schedule-vs-cancel applier — none of
/// which depend on the iOS-only `UserNotifications` framework, so they run on any
/// host. The exhaustive selection property test is task 13.9 (Property 14).
final class EveningNudgeTests: XCTestCase {

    // MARK: - Fixtures

    private func makeItem(
        id: String,
        status: ActionStatus = .notStarted,
        title: String = "Item"
    ) -> ActionItem {
        ActionItem(
            id: id,
            accountId: "acct",
            bucketId: "bucket",
            title: title,
            contentType: .text,
            timeframe: .today,
            status: status,
            createdAt: Date(timeIntervalSince1970: 0),
            sync: SyncMeta(updatedAt: Date(timeIntervalSince1970: 0), version: 1, deleted: false)
        )
    }

    // MARK: - Eligible-item selection (Req 7.13, 7.14)

    func testSelectionExcludesCompletedItems() {
        let items = [
            makeItem(id: "a", status: .notStarted),
            makeItem(id: "b", status: .completed),
            makeItem(id: "c", status: .inProgress),
        ]
        let selected = EveningNudgeSelection.eligibleItems(from: items, itemIdsWithReminder: [])
        XCTAssertEqual(selected.map(\.id), ["a", "c"])
    }

    func testSelectionExcludesItemsWithReminder() {
        let items = [makeItem(id: "a"), makeItem(id: "b"), makeItem(id: "c")]
        let selected = EveningNudgeSelection.eligibleItems(
            from: items,
            itemIdsWithReminder: ["b"]
        )
        XCTAssertEqual(selected.map(\.id), ["a", "c"])
    }

    func testSelectionPreservesInputOrder() {
        let items = [makeItem(id: "z"), makeItem(id: "a"), makeItem(id: "m")]
        let selected = EveningNudgeSelection.eligibleItems(from: items, itemIdsWithReminder: [])
        XCTAssertEqual(selected.map(\.id), ["z", "a", "m"])
    }

    func testSelectionIsCappedAtTwenty() {
        let items = (0..<50).map { makeItem(id: "item-\($0)") }
        let selected = EveningNudgeSelection.eligibleItems(from: items, itemIdsWithReminder: [])
        XCTAssertEqual(selected.count, EveningNudgeSelection.maxItems)
        XCTAssertEqual(selected.map(\.id), (0..<20).map { "item-\($0)" })
    }

    func testSelectionIsEmptyWhenNoEligibleItems() {
        let items = [
            makeItem(id: "a", status: .completed),
            makeItem(id: "b"),
        ]
        let selected = EveningNudgeSelection.eligibleItems(
            from: items,
            itemIdsWithReminder: ["b"]
        )
        XCTAssertTrue(selected.isEmpty)
    }

    func testSelectionWithNonPositiveLimitIsEmpty() {
        let items = [makeItem(id: "a")]
        XCTAssertTrue(EveningNudgeSelection.eligibleItems(from: items, itemIdsWithReminder: [], limit: 0).isEmpty)
    }

    // MARK: - Default body (Req 7.16, 7.17)

    func testDefaultBodyIsNonEmptyAndBounded() {
        let items = (0..<20).map { makeItem(id: "i\($0)", title: String(repeating: "x", count: 40)) }
        let body = EveningNudgeSelection.defaultBody(for: items)
        XCTAssertFalse(body.isEmpty)
        XCTAssertLessThanOrEqual(body.count, EveningNudgeSelection.maxBodyLength)
    }

    func testDefaultBodyFallsBackForEmptySelection() {
        XCTAssertEqual(EveningNudgeSelection.defaultBody(for: []), NotificationDefaults.eveningNudgeBody)
    }

    func testDefaultBodyUsesSingularForOneItem() {
        let body = EveningNudgeSelection.defaultBody(for: [makeItem(id: "a", title: "Call mom")])
        XCTAssertTrue(body.contains("1 open item:"))
        XCTAssertTrue(body.contains("Call mom"))
    }

    // MARK: - Preferences store round-trip (Req 7.12, 7.15)

    func testPreferencesDefaultToDisabledOptIn() {
        let suite = "test.notifprefs.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }

        let store = UserDefaultsNotificationPreferencesStore(defaults: defaults)
        XCTAssertFalse(store.eveningNudge().isEnabled)
        XCTAssertFalse(store.globalDaily().isEnabled)
        XCTAssertEqual(store.eveningNudge().time, TimeOfDay(hour: 19, minute: 0))
        XCTAssertEqual(store.globalDaily().time, TimeOfDay(hour: 9, minute: 0))
    }

    func testPreferencesPersistEnableAndTime() {
        let suite = "test.notifprefs.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }

        let store = UserDefaultsNotificationPreferencesStore(defaults: defaults)
        store.setEveningNudge(NotificationToggle(isEnabled: true, time: TimeOfDay(hour: 20, minute: 15)))
        store.setGlobalDaily(NotificationToggle(isEnabled: true, time: TimeOfDay(hour: 8, minute: 30)))

        let reopened = UserDefaultsNotificationPreferencesStore(defaults: defaults)
        XCTAssertEqual(reopened.eveningNudge(), NotificationToggle(isEnabled: true, time: TimeOfDay(hour: 20, minute: 15)))
        XCTAssertEqual(reopened.globalDaily(), NotificationToggle(isEnabled: true, time: TimeOfDay(hour: 8, minute: 30)))
    }

    // MARK: - Applier schedules when enabled, cancels when disabled (Req 7.12, 7.15)

    func testApplierSchedulesEveningNudgeWhenEnabled() async {
        let service = RecordingNotificationService()
        let applier = NotificationPreferenceApplier(service: service)
        let items = [makeItem(id: "a")]

        await applier.applyEveningNudge(
            NotificationToggle(isEnabled: true, time: TimeOfDay(hour: 19, minute: 0)),
            items: items
        )
        XCTAssertEqual(service.scheduledEveningNudgeTimes, [TimeOfDay(hour: 19, minute: 0)])
        XCTAssertEqual(service.cancelledEveningNudgeCount, 0)
    }

    func testApplierCancelsEveningNudgeWhenDisabled() async {
        let service = RecordingNotificationService()
        let applier = NotificationPreferenceApplier(service: service)

        await applier.applyEveningNudge(
            NotificationToggle(isEnabled: false, time: TimeOfDay(hour: 19, minute: 0)),
            items: []
        )
        XCTAssertTrue(service.scheduledEveningNudgeTimes.isEmpty)
        XCTAssertEqual(service.cancelledEveningNudgeCount, 1)
    }

    func testApplierSchedulesGlobalDailyWhenEnabled() async {
        let service = RecordingNotificationService()
        let applier = NotificationPreferenceApplier(service: service)

        await applier.applyGlobalDaily(NotificationToggle(isEnabled: true, time: TimeOfDay(hour: 9, minute: 0)))
        XCTAssertEqual(service.scheduledGlobalDailyTimes, [TimeOfDay(hour: 9, minute: 0)])
        XCTAssertEqual(service.cancelledGlobalDailyCount, 0)
    }

    func testApplierCancelsGlobalDailyWhenDisabled() async {
        let service = RecordingNotificationService()
        let applier = NotificationPreferenceApplier(service: service)

        await applier.applyGlobalDaily(NotificationToggle(isEnabled: false, time: TimeOfDay(hour: 9, minute: 0)))
        XCTAssertTrue(service.scheduledGlobalDailyTimes.isEmpty)
        XCTAssertEqual(service.cancelledGlobalDailyCount, 1)
    }
}

/// In-memory ``NotificationService`` test double recording the applier's calls.
/// Lives in the portable test target (no `UserNotifications` import) so it runs
/// on any host.
private final class RecordingNotificationService: NotificationService {

    private(set) var scheduledEveningNudgeTimes: [TimeOfDay] = []
    private(set) var cancelledEveningNudgeCount = 0
    private(set) var scheduledGlobalDailyTimes: [TimeOfDay] = []
    private(set) var cancelledGlobalDailyCount = 0

    func requestAuthorizationIfNeeded() async -> NotificationAuthStatus { .authorized }

    func scheduleTaskReminder(for item: ActionItem, reminder: TaskReminder) async {}

    func cancelReminders(for itemId: String) async {}

    func scheduleEveningNudge(at time: TimeOfDay, items: [ActionItem]) async {
        scheduledEveningNudgeTimes.append(time)
    }

    func cancelEveningNudge() async {
        cancelledEveningNudgeCount += 1
    }

    func scheduleGlobalDaily(at time: TimeOfDay) async {
        scheduledGlobalDailyTimes.append(time)
    }

    func cancelGlobalDaily() async {
        cancelledGlobalDailyCount += 1
    }

    func rescheduleAllPending() async {}
}
