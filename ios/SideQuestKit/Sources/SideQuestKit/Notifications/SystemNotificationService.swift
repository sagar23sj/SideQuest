#if canImport(UserNotifications)
import Foundation
import UserNotifications

// MARK: - UNUserNotificationCenter-backed NotificationService (Req 7, 11)
//
// This file is iOS-only: `UserNotifications` does not exist on the build host,
// so the whole file is compiled only `#if canImport(UserNotifications)`. The
// portable protocol, value types, and pure scheduling logic (which the property
// tests target) live in `NotificationService.swift` / `NotificationScheduling.swift`
// and compile everywhere.

/// Mapping from the platform authorization status to the portable
/// ``NotificationAuthStatus``.
extension NotificationAuthStatus {
    init(_ status: UNAuthorizationStatus) {
        switch status {
        case .notDetermined: self = .notDetermined
        case .denied: self = .denied
        case .authorized: self = .authorized
        case .provisional: self = .provisional
        case .ephemeral: self = .ephemeral
        @unknown default: self = .denied
        }
    }
}

/// The slice of `UNUserNotificationCenter` the service depends on, expressed as
/// a protocol so ``SystemNotificationService`` can be unit-tested with an
/// in-memory double instead of the real, device-bound notification center.
public protocol NotificationCenterAdapting {

    /// The current authorization status (read from the system settings).
    func authorizationStatus() async -> NotificationAuthStatus

    /// Presents the system prompt (only when status is not yet determined) and
    /// returns the resulting status.
    func requestAuthorization(options: UNAuthorizationOptions) async -> NotificationAuthStatus

    /// Adds (schedules) a notification request.
    func add(_ request: UNNotificationRequest) async

    /// Identifiers of all currently pending requests.
    func pendingRequestIdentifiers() async -> [String]

    /// Removes the pending requests with the given identifiers.
    func removePendingRequests(withIdentifiers identifiers: [String])
}

/// Production ``NotificationCenterAdapting`` over the real
/// `UNUserNotificationCenter`.
public struct SystemUserNotificationCenter: NotificationCenterAdapting {

    private let center: UNUserNotificationCenter

    public init(center: UNUserNotificationCenter = .current()) {
        self.center = center
    }

    public func authorizationStatus() async -> NotificationAuthStatus {
        let settings = await center.notificationSettings()
        return NotificationAuthStatus(settings.authorizationStatus)
    }

    public func requestAuthorization(options: UNAuthorizationOptions) async -> NotificationAuthStatus {
        // `requestAuthorization` only presents the system prompt while the status
        // is not yet determined; afterwards it resolves to the existing status
        // without re-prompting. Re-read settings for the precise resulting state.
        _ = try? await center.requestAuthorization(options: options)
        return await authorizationStatus()
    }

    public func add(_ request: UNNotificationRequest) async {
        // A failed add must not crash the app; reminders fail soft.
        try? await center.add(request)
    }

    public func pendingRequestIdentifiers() async -> [String] {
        await center.pendingNotificationRequests().map(\.identifier)
    }

    public func removePendingRequests(withIdentifiers identifiers: [String]) {
        guard !identifiers.isEmpty else { return }
        center.removePendingNotificationRequests(withIdentifiers: identifiers)
    }
}

/// `UNUserNotificationCenter`-backed ``NotificationService``.
///
/// Implements the task-13.1 core: at-most-once permission request (Req 7.1,
/// 11.1, 11.5), the local-wall-clock scheduling primitive built on
/// `UNCalendarNotificationTrigger` (Req 7.10, 7.11), per-item reminder
/// cancellation (Req 7.8), and the global daily notification (Req 7.15). The
/// occurrence day-set (task 13.4) and evening-nudge selection (task 13.8) are
/// realized by the pure ``ReminderOccurrences`` and ``EveningNudgeSelection``
/// helpers this service composes; the LLM-generated text (task 13.10) is layered
/// on top later.
public final class SystemNotificationService: NotificationService {

    private let center: NotificationCenterAdapting
    private let permissionStore: PermissionRequestStore
    private let calendar: Calendar

    public init(
        center: NotificationCenterAdapting = SystemUserNotificationCenter(),
        permissionStore: PermissionRequestStore = UserDefaultsPermissionRequestStore(),
        calendar: Calendar = .current
    ) {
        self.center = center
        self.permissionStore = permissionStore
        self.calendar = calendar
    }

    // MARK: Permission (Req 7.1, 11.1, 11.5)

    public func requestAuthorizationIfNeeded() async -> NotificationAuthStatus {
        let current = await center.authorizationStatus()

        // Prompt only when iOS has not yet determined a status AND we have not
        // already asked. Either condition being false means the prompt has had
        // its single chance, so we return the current status without prompting
        // again (Req 11.1, 11.5).
        guard current == .notDetermined, !permissionStore.hasRequestedAuthorization() else {
            return current
        }

        permissionStore.markAuthorizationRequested()
        return await center.requestAuthorization(options: [.alert, .sound, .badge])
    }

    // MARK: Task reminders

    public func scheduleTaskReminder(for item: ActionItem, reminder: TaskReminder) async {
        // Replace any previously scheduled reminders for this item so editing a
        // reminder (or re-scheduling on launch) never leaves stale requests and
        // a completed item ends up with none (Req 7.8).
        await cancelReminders(for: item.id)

        // A completed item has no pending reminders (Req 7.9); the empty day-set
        // below already yields this, but short-circuit to make it explicit.
        guard item.status != .completed else { return }

        // Pure day-set: one-shot → the single next firing day on/before the
        // until-date; daily-recurring → every day up to and including the
        // until-date; none after it or once completed (Req 7.6, 7.7, 7.9).
        let days = ReminderOccurrences.occurrenceDays(
            for: reminder,
            isCompleted: item.status == .completed,
            now: Date(),
            calendar: calendar
        )

        for day in days {
            // Local-wall-clock components for this firing day (Req 7.10).
            let components = NotificationScheduling.components(
                at: reminder.timeOfDay,
                onDayOf: day,
                calendar: calendar
            )
            let occurrence = ReminderOccurrences.occurrenceKey(for: day, calendar: calendar)
            await scheduleCalendarNotification(
                identifier: NotificationIdentifier.taskReminder(itemId: item.id, occurrence: occurrence),
                title: NotificationDefaults.taskReminderTitle,
                body: item.title,
                components: components,
                // Each occurrence is a distinct one-shot calendar day, so the
                // day-set fully expresses recurrence and the trigger never
                // repeats beyond the until-date (Req 7.9).
                repeats: false
            )
        }
    }

    public func cancelReminders(for itemId: String) async {
        let pending = await center.pendingRequestIdentifiers()
        let toCancel = pending.filter {
            NotificationIdentifier.isTaskReminder($0, forItem: itemId)
        }
        center.removePendingRequests(withIdentifiers: toCancel)
    }

    // MARK: Evening nudge (Req 7.13, 7.14)

    public func scheduleEveningNudge(at time: TimeOfDay, items: [ActionItem]) async {
        // An item "has a Task_Reminder set" iff it currently owns a pending
        // task-reminder request. Deriving this from the system's pending requests
        // (rather than re-reading the store) keeps the nudge consistent with what
        // is actually scheduled and needs no extra dependency.
        let pending = await center.pendingRequestIdentifiers()
        let idsWithReminder = Set(
            items.map(\.id).filter { itemId in
                pending.contains { NotificationIdentifier.isTaskReminder($0, forItem: itemId) }
            }
        )

        // Pure selection: not-completed items with no reminder, capped at 20
        // (Req 7.13).
        let selected = EveningNudgeSelection.eligibleItems(
            from: items,
            itemIdsWithReminder: idsWithReminder
        )

        // No eligible items → omit the nudge for the day, clearing any nudge that
        // was scheduled previously so a stale summary is never delivered
        // (Req 7.14).
        guard !selected.isEmpty else {
            await cancelEveningNudge()
            return
        }

        // Default summary text on the LLM fail-soft path (Req 7.17); the
        // LLM-generated text is layered in by task 13.10 / 13.11.
        let body = EveningNudgeSelection.defaultBody(for: selected)
        let components = NotificationScheduling.dailyComponents(at: time)
        await scheduleCalendarNotification(
            identifier: NotificationIdentifier.eveningNudge,
            title: NotificationDefaults.eveningNudgeTitle,
            body: body,
            components: components,
            repeats: true
        )
    }

    public func cancelEveningNudge() async {
        center.removePendingRequests(withIdentifiers: [NotificationIdentifier.eveningNudge])
    }

    // MARK: Global daily self-reminder (Req 7.15)

    public func scheduleGlobalDaily(at time: TimeOfDay) async {
        let components = NotificationScheduling.dailyComponents(at: time)
        await scheduleCalendarNotification(
            identifier: NotificationIdentifier.globalDaily,
            title: NotificationDefaults.globalDailyTitle,
            body: NotificationDefaults.globalDailyBody,
            components: components,
            repeats: true
        )
    }

    public func cancelGlobalDaily() async {
        center.removePendingRequests(withIdentifiers: [NotificationIdentifier.globalDaily])
    }

    // MARK: Reschedule on launch (Req 7.11)

    public func rescheduleAllPending() async {
        // `UNCalendarNotificationTrigger` requests persist across reboots at the
        // system level, so no reminder is lost across a restart (Req 7.11). This
        // hook is the place to reconcile the system's pending requests with the
        // local store once reminder persistence exists (tasks 6 / 13.4): re-read
        // active reminders, cancel stale requests, and re-schedule the current
        // day-set. It is intentionally a no-op until that data is available.
    }

    // MARK: - Core scheduling primitive (Req 7.10, 7.11)

    /// Schedules a single notification from already-derived calendar components.
    ///
    /// This is the one place that constructs a `UNCalendarNotificationTrigger`.
    /// Because `components` carry no `timeZone` (see ``NotificationScheduling``),
    /// the system evaluates the trigger in the device's current local time zone,
    /// anchoring the notification to local wall-clock time (Req 7.10) and
    /// persisting it across reboots (Req 7.11). Later scheduling tasks (13.4,
    /// 13.8) reuse this primitive.
    ///
    /// - Parameters:
    ///   - identifier: Stable request id (see ``NotificationIdentifier``); a
    ///     repeat add with the same id replaces the prior request.
    ///   - title: Notification title.
    ///   - body: Notification body (non-empty default text on the fail-soft path).
    ///   - components: Local calendar components from ``NotificationScheduling``.
    ///   - repeats: `true` for a daily-recurring trigger, `false` for one-shot.
    func scheduleCalendarNotification(
        identifier: String,
        title: String,
        body: String,
        components: DateComponents,
        repeats: Bool
    ) async {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default

        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: repeats)
        let request = UNNotificationRequest(
            identifier: identifier,
            content: content,
            trigger: trigger
        )
        await center.add(request)
    }
}
#endif
