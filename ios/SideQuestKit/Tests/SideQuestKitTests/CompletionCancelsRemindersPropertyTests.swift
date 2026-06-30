import XCTest
import Foundation
import Dispatch
import SwiftCheck
@testable import SideQuestKit

// This property targets `SystemNotificationService.scheduleTaskReminder(for:reminder:)`
// and `SystemNotificationService.cancelReminders(for:)`, both of which sit on the
// iOS-only `UserNotifications` framework (the `NotificationCenterAdapting` seam
// schedules `UNNotificationRequest`s and the service builds
// `UNCalendarNotificationTrigger`s). Mirroring `SystemNotificationService.swift`
// and `PermissionPromptPropertyTests.swift`, the whole file is compiled only
// where that framework exists, so it runs on Apple platforms and is omitted on
// hosts (Windows/Linux) that lack it.
#if canImport(UserNotifications)
import UserNotifications

/// Property-based test for **Property 11 — "Completing an item cancels all of
/// its pending reminders"** (iOS design.md "iOS-specific properties"; task 13.6).
///
/// **Validates: Requirements 7.8**
///
/// Req 7.8:
/// > WHEN an Action_Item is marked completed, THE App SHALL cancel all pending
/// > scheduled reminders associated with that Action_Item.
///
/// Property 11 statement (design.md):
/// > *For any* Action_Item with any number of scheduled reminders, marking it
/// > completed results in zero pending scheduled reminder requests associated
/// > with that Action_Item.
///
/// ## Subject under test
///
/// The reminder-cancellation behaviour of ``SystemNotificationService``, reached
/// two ways the app actually completes an item:
///   * `scheduleTaskReminder(for:reminder:)` called with a **completed** item —
///     the production path when a reminder is (re)applied to an item that is
///     already done; it must leave zero pending requests for that item (Req 7.9
///     guard, which cancels first); and
///   * `cancelReminders(for:)` — the direct cancellation the completion flow
///     invokes (Req 7.8).
///
/// The "pending scheduled reminder requests" are the requests held by the system
/// notification center. We substitute an in-memory ``NotificationCenterAdapting``
/// double that records every added request identifier and supports the
/// `pendingRequestIdentifiers()` / `removePendingRequests(withIdentifiers:)`
/// pair the service relies on — exactly the seam the platform
/// `UNUserNotificationCenter` cannot exercise on the build host.
///
/// ## Property
///
/// For any board of distinct items — each seeded by really scheduling a reminder
/// through the service so its pending requests exist — plus the singleton global
/// daily notification, completing **one** target item (via either completion
/// path) drives the target's pending reminder requests to **zero** while every
/// *other* item's reminder requests and the unrelated singleton request remain
/// byte-for-byte untouched.
///
/// ## Async bridge
///
/// The service methods are `async`; SwiftCheck property bodies are synchronous,
/// so each call is run to completion through the same `runBlocking` helper used
/// by the other async property tests in this suite.
///
/// Each property runs ≥100 iterations (the design mandates a minimum of 100; we
/// configure 200 for extra coverage).
final class CompletionCancelsRemindersPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    // MARK: - Property 11: completing an item cancels exactly its own reminders

    /// Seed several distinct items (each with ≥1 real scheduled reminder) plus
    /// the unrelated global-daily notification, then complete one target item
    /// through one of the two production completion paths. The target ends with
    /// zero pending reminder requests; all other items' requests and the
    /// singleton are unchanged (Property 11, Req 7.8).
    func testCompletingItemCancelsOnlyItsOwnReminders() {
        property("completing an item cancels all and only its own pending reminders (Property 11, Req 7.8)",
                 arguments: Self.checkArgs)
            <- forAllNoShrink(scenarioGen) { scenario in
                let center = InMemoryNotificationCenter()
                let calendar = Self.calendar
                let service = SystemNotificationService(center: center, calendar: calendar)

                // A far-enough until-date so the pure day-set always yields at
                // least one firing day (Req 7.6/7.7), guaranteeing each item is
                // seeded with a non-empty set of pending reminder requests.
                let now = Date()
                let untilDate = calendar.date(byAdding: .day, value: scenario.untilDayOffset, to: now) ?? now

                // Build distinct items. The trailing-dot namespace in
                // `NotificationIdentifier.taskReminderPrefix` means no item id is
                // a reminder-prefix of another, so per-item families never alias.
                let otherIds = (0..<scenario.otherCount).map { "other-item-\($0)" }
                let targetId = "target-item"
                let allIds = otherIds + [targetId]

                // Seed every item by really scheduling a reminder through the
                // service (status not-completed so the day-set is populated).
                for id in allIds {
                    let item = Self.makeItem(id: id, status: .notStarted)
                    let reminder = TaskReminder(
                        actionItemId: id,
                        timeOfDay: scenario.timeOfDay,
                        untilDate: untilDate,
                        recurringDaily: scenario.recurringDaily
                    )
                    Self.runBlocking { await service.scheduleTaskReminder(for: item, reminder: reminder) }
                }

                // Seed an unrelated singleton notification that must survive.
                Self.runBlocking { await service.scheduleGlobalDaily(at: scenario.timeOfDay) }

                // Snapshot the pre-completion reminder families for every item.
                let pendingBefore = Self.runBlocking { await center.pendingRequestIdentifiers() }
                let targetBefore = Self.reminderIdentifiers(in: pendingBefore, forItem: targetId)
                let othersBefore = otherIds.reduce(into: [String: Set<String>]()) { acc, id in
                    acc[id] = Self.reminderIdentifiers(in: pendingBefore, forItem: id)
                }
                let globalDailyPresentBefore = pendingBefore.contains(NotificationIdentifier.globalDaily)

                // Complete the target through one of the two production paths.
                switch scenario.completionPath {
                case .scheduleWithCompletedItem:
                    // The app re-applies the reminder to an item that is now
                    // completed; the method must cancel and then schedule none.
                    let completed = Self.makeItem(id: targetId, status: .completed)
                    let reminder = TaskReminder(
                        actionItemId: targetId,
                        timeOfDay: scenario.timeOfDay,
                        untilDate: untilDate,
                        recurringDaily: scenario.recurringDaily
                    )
                    Self.runBlocking { await service.scheduleTaskReminder(for: completed, reminder: reminder) }
                case .cancelReminders:
                    Self.runBlocking { await service.cancelReminders(for: targetId) }
                }

                let pendingAfter = Self.runBlocking { await center.pendingRequestIdentifiers() }
                let targetAfter = Self.reminderIdentifiers(in: pendingAfter, forItem: targetId)
                let globalDailyPresentAfter = pendingAfter.contains(NotificationIdentifier.globalDaily)

                // The scenario must be non-vacuous: the target really had
                // reminders to cancel, and the singleton was actually scheduled.
                let targetWasSeeded = !targetBefore.isEmpty
                let singletonWasSeeded = globalDailyPresentBefore

                // Core guarantee: zero pending reminder requests for the target.
                let targetCleared = targetAfter.isEmpty

                // Isolation: every other item's reminder family is unchanged…
                let othersUntouched = otherIds.allSatisfy { id in
                    Self.reminderIdentifiers(in: pendingAfter, forItem: id) == (othersBefore[id] ?? [])
                }
                // …and the unrelated singleton survives.
                let singletonUntouched = globalDailyPresentAfter == globalDailyPresentBefore

                return (targetWasSeeded <?> "target had no reminders to cancel (vacuous scenario)")
                    ^&&^ (singletonWasSeeded <?> "global-daily singleton was not seeded (vacuous scenario)")
                    ^&&^ (targetCleared
                            <?> "target retained \(targetAfter.count) reminder(s) after completion via \(scenario.completionPath)")
                    ^&&^ (othersUntouched <?> "another item's reminders changed after completing the target")
                    ^&&^ (singletonUntouched <?> "the unrelated global-daily notification was disturbed")
            }
    }

    // MARK: - Helpers

    /// A Gregorian/POSIX calendar pinned to UTC, matching how the scheduling
    /// layer resolves local calendar days deterministically.
    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return calendar
    }()

    /// Builds a minimal valid ``ActionItem`` with the given id and status.
    private static func makeItem(id: String, status: ActionStatus) -> ActionItem {
        ActionItem(
            id: id,
            accountId: "account",
            bucketId: "bucket",
            title: "Title for \(id)",
            contentType: .text,
            timeframe: .today,
            status: status,
            createdAt: Date(timeIntervalSince1970: 1_700_000_000),
            sync: SyncMeta(updatedAt: Date(timeIntervalSince1970: 1_700_000_000), version: 1, deleted: false)
        )
    }

    /// The set of pending identifiers belonging to `itemId`'s reminder family.
    private static func reminderIdentifiers(in identifiers: [String], forItem itemId: String) -> Set<String> {
        Set(identifiers.filter { NotificationIdentifier.isTaskReminder($0, forItem: itemId) })
    }

    /// Runs an `async` operation to completion from a synchronous SwiftCheck
    /// property body and returns its result.
    @discardableResult
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

// MARK: - Test double

/// In-memory ``NotificationCenterAdapting`` double.
///
/// Faithfully models the pending-request bookkeeping the service depends on:
/// `add` records (or replaces, by identifier — matching `UNUserNotificationCenter`)
/// a request, `pendingRequestIdentifiers` reports the live set, and
/// `removePendingRequests` deletes by identifier. The permission methods are
/// unused on this path and return a settled status.
private final class InMemoryNotificationCenter: NotificationCenterAdapting {

    /// Live pending requests keyed by identifier (last write wins, like the
    /// real center, which replaces a request added with an existing id).
    private var pending: [String: UNNotificationRequest] = [:]

    func authorizationStatus() async -> NotificationAuthStatus { .authorized }

    func requestAuthorization(options: UNAuthorizationOptions) async -> NotificationAuthStatus { .authorized }

    func add(_ request: UNNotificationRequest) async {
        pending[request.identifier] = request
    }

    func pendingRequestIdentifiers() async -> [String] {
        Array(pending.keys)
    }

    func removePendingRequests(withIdentifiers identifiers: [String]) {
        for identifier in identifiers {
            pending.removeValue(forKey: identifier)
        }
    }
}

// MARK: - Scenario + generators

/// Which production completion path drives the cancellation.
private enum CompletionPath: CustomStringConvertible {
    /// `scheduleTaskReminder(for:reminder:)` with an item whose status is
    /// `.completed` (re-applying a reminder to a finished item).
    case scheduleWithCompletedItem
    /// `cancelReminders(for:)` invoked directly by the completion flow.
    case cancelReminders

    var description: String {
        switch self {
        case .scheduleWithCompletedItem: return "scheduleTaskReminder(completed item)"
        case .cancelReminders: return "cancelReminders(for:)"
        }
    }
}

/// One generated scenario: how many *other* items share the center, the reminder
/// shape, how far ahead the until-date sits, and which completion path is used.
private struct CompletionScenario {
    var otherCount: Int
    var timeOfDay: TimeOfDay
    var recurringDaily: Bool
    var untilDayOffset: Int
    var completionPath: CompletionPath
}

/// 0...4 unrelated items also holding reminders, so isolation is exercised with
/// and without neighbours.
private let otherCountGen = Gen<Int>.choose((0, 4))

/// A `TimeOfDay` spanning the full valid hour/minute range.
private let timeOfDayGen: Gen<TimeOfDay> = Gen
    .zip(Gen<Int>.fromElements(in: 0...23), Gen<Int>.fromElements(in: 0...59))
    .map { TimeOfDay(hour: $0, minute: $1) }

/// 2...7 days ahead: always ≥1 firing day (so seeding is non-empty, even across
/// a midnight rollover between this test's clock read and the service's internal
/// `Date()`), while keeping the recurring day-set — and thus the request count —
/// small.
private let untilDayOffsetGen = Gen<Int>.choose((2, 7))

/// Either branch of a binary choice.
private let boolGen = Gen<Bool>.fromElements(of: [true, false])

private let completionPathGen = Gen<CompletionPath>.fromElements(of: [
    .scheduleWithCompletedItem, .cancelReminders
])

private let scenarioGen: Gen<CompletionScenario> = Gen.compose { c in
    CompletionScenario(
        otherCount: c.generate(using: otherCountGen),
        timeOfDay: c.generate(using: timeOfDayGen),
        recurringDaily: c.generate(using: boolGen),
        untilDayOffset: c.generate(using: untilDayOffsetGen),
        completionPath: c.generate(using: completionPathGen)
    )
}
#endif
