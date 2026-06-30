import Foundation

// MARK: - NotificationService abstraction (Req 7, 11.1, 11.4, 11.5)
//
// `NotificationService` is the portable, platform-agnostic seam over the iOS
// user-notification system. The protocol and its supporting value types are
// deliberately free of any `UserNotifications` import so they compile and can be
// substituted (by test doubles or view models) on any host; the concrete
// `UNUserNotificationCenter`-backed implementation lives in
// `SystemNotificationService.swift`, guarded by `#if canImport(UserNotifications)`.
//
// This task (13.1) implements the *core* primitives: the at-most-once permission
// request (Req 7.1, 11.1, 11.5), the local-wall-clock scheduling primitive built
// on `UNCalendarNotificationTrigger` + `DateComponents` (Req 7.10, 7.11),
// reminder cancellation by item (Req 7.8), the global daily notification, the
// reschedule-on-launch hook (Req 7.11), and the denied-permission affordance
// (explanation + deep link to iOS settings — Req 7.18, 11.4). The richer
// scheduling — task-reminder occurrence day-sets (13.4), evening-nudge eligible
// item selection (13.8), and LLM notification text (13.10) — is layered on top
// by later tasks, which is why those methods are present here as fillable stubs.

/// Authorization status for delivering user notifications, mirrored from the
/// platform `UNAuthorizationStatus` but defined here so the protocol stays
/// portable (no `UserNotifications` import). The mapping from the platform enum
/// lives next to the concrete service.
public enum NotificationAuthStatus: Equatable, Sendable {

    /// The user has not yet been asked. The system prompt may still be shown.
    case notDetermined

    /// The user declined. Reminders are unavailable; the app should show an
    /// explanation and a deep link to iOS settings (Req 7.18, 11.4).
    case denied

    /// Full authorization to deliver notifications.
    case authorized

    /// Quiet/provisional authorization (delivered silently to Notification
    /// Center without prompting).
    case provisional

    /// Time-limited authorization (App Clips).
    case ephemeral

    /// Whether notifications may be delivered for this status. Provisional and
    /// ephemeral both allow delivery, so reminders work without a hard prompt.
    public var isAuthorized: Bool {
        switch self {
        case .authorized, .provisional, .ephemeral:
            return true
        case .notDetermined, .denied:
            return false
        }
    }
}

/// Schedules and cancels the app's local notifications through the iOS
/// user-notification system, anchored to local wall-clock time (Req 7).
///
/// All scheduling is expressed through `UNCalendarNotificationTrigger` over the
/// `DateComponents` produced by ``NotificationScheduling`` so fire times track
/// the device's local time zone (Req 7.10) and survive reboots (Req 7.11).
public protocol NotificationService {

    /// Requests notification authorization the first time a notifying feature is
    /// used, triggering the iOS system prompt **at most once** per app install
    /// (Req 7.1, 11.1, 11.5). If authorization has already been determined (or
    /// already requested), it returns the current status without prompting
    /// again.
    ///
    /// - Returns: The resulting ``NotificationAuthStatus``. A ``NotificationAuthStatus/denied``
    ///   result is the cue for the UI to show an explanation plus a deep link to
    ///   iOS settings (Req 7.18, 11.4) via ``NotificationSettingsLink``.
    func requestAuthorizationIfNeeded() async -> NotificationAuthStatus

    /// Schedules the reminder occurrences for an item (Req 7.6, 7.7).
    ///
    /// The occurrence day-set derivation (one-shot vs daily-recurring up to the
    /// until-date) is task 13.4; the local-wall-clock scheduling primitive it
    /// builds on is implemented here.
    func scheduleTaskReminder(for item: ActionItem, reminder: TaskReminder) async

    /// Cancels every pending reminder belonging to the given item (Req 7.8) —
    /// for example when the item is marked completed.
    func cancelReminders(for itemId: String) async

    /// Schedules the single daily collective evening nudge (Req 7.13).
    ///
    /// The eligible items are exactly the not-completed items with no
    /// Task_Reminder set, capped at 20 (selected by ``EveningNudgeSelection``);
    /// when no item is eligible the nudge is omitted for the day (Req 7.14). The
    /// LLM-generated summary text is task 13.10; the daily local-time scheduling
    /// primitive it builds on is implemented here.
    func scheduleEveningNudge(at time: TimeOfDay, items: [ActionItem]) async

    /// Cancels the pending collective evening nudge, if any — used when the user
    /// disables it (Req 7.12).
    func cancelEveningNudge() async

    /// Schedules the optional global daily self-reminder at the chosen local
    /// time of day (Req 7.15).
    func scheduleGlobalDaily(at time: TimeOfDay) async

    /// Cancels the pending global daily self-reminder, if any — used when the
    /// user disables it (Req 7.15).
    func cancelGlobalDaily() async

    /// Re-establishes pending schedules on launch / after a reboot is detected
    /// (Req 7.11). Calendar triggers already persist across reboots at the
    /// system level; this hook lets the app reconcile its schedule with the
    /// local store once reminder persistence exists (tasks 6 / 13.4).
    func rescheduleAllPending() async
}

/// Default, non-empty notification copy used when no richer (LLM-generated) text
/// is available — for instance on the LLM timeout/error fail-soft path (Req
/// 7.17). Defined here (portable) so both the service and later tasks share one
/// source of truth.
public enum NotificationDefaults {

    /// Title for the global daily self-reminder (Req 7.15).
    public static let globalDailyTitle = "SideQuest"

    /// Body prompting the user to open the app (Req 7.15).
    public static let globalDailyBody = "Open SideQuest to keep your momentum going."

    /// Title for a task reminder (Req 7.6).
    public static let taskReminderTitle = "SideQuest reminder"

    /// Title for the collective evening nudge (Req 7.13).
    public static let eveningNudgeTitle = "SideQuest"

    /// Body for the collective evening nudge used on the LLM fail-soft path
    /// before per-day item titles are available (Req 7.17).
    public static let eveningNudgeBody = "You have open items waiting in SideQuest."
}

/// Deep link to the app's entry in the iOS Settings app, surfaced when
/// notification permission is denied (Req 7.18, 11.4).
///
/// The string equals `UIApplication.openSettingsURLString` (`"app-settings:"`).
/// It is defined as a plain literal here — rather than importing UIKit — so the
/// value is available to the portable layer and testable on any host; the UI
/// layer (tasks 13.8 / 18.1) opens it with `openURL`/`UIApplication.open`.
public enum NotificationSettingsLink {

    /// URL string that opens the app's page in iOS Settings.
    public static let settingsURLString = "app-settings:"

    /// The settings deep link as a `URL`, or `nil` if it cannot be formed.
    public static var settingsURL: URL? {
        URL(string: settingsURLString)
    }
}

// MARK: - Permission-requested flag (Req 11.1, 11.5)

/// Persists whether the app has already triggered the notification permission
/// prompt, so it is shown **at most once** per capability (Req 11.1, 11.5) even
/// across launches and across the main app / Share Extension processes.
public protocol PermissionRequestStore {

    /// Whether the notification authorization prompt has already been requested.
    func hasRequestedAuthorization() -> Bool

    /// Records that the notification authorization prompt has been requested.
    func markAuthorizationRequested()
}

/// `UserDefaults`-backed ``PermissionRequestStore``.
///
/// Defaults to the **App Group** suite so the flag is shared between the main
/// app and the Share Extension (Req 13.2), keeping the "at most once" guarantee
/// consistent across both processes; falls back to `.standard` if the App Group
/// suite is unavailable.
public struct UserDefaultsPermissionRequestStore: PermissionRequestStore {

    private static let key = "sidequest.notifications.authorizationRequested"
    private let defaults: UserDefaults

    /// Injects a specific `UserDefaults` (used by tests).
    public init(defaults: UserDefaults) {
        self.defaults = defaults
    }

    /// Uses the shared App Group suite, falling back to `.standard`.
    public init() {
        self.defaults = UserDefaults(suiteName: AppGroup.identifier) ?? .standard
    }

    public func hasRequestedAuthorization() -> Bool {
        defaults.bool(forKey: Self.key)
    }

    public func markAuthorizationRequested() {
        defaults.set(true, forKey: Self.key)
    }
}
