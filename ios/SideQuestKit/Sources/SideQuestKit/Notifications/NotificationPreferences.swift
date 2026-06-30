import Foundation

// MARK: - Evening-nudge / global-daily preferences (portable) — Req 7.12, 7.15
//
// The user-facing toggles for the two opt-in notifications: the
// Collective_Evening_Nudge (Req 7.12) and the Global_Daily_Notification
// (Req 7.15). Each is an enable/disable flag plus a local-wall-clock time of day
// (hour + minute, Req 7.10). The value type and store are kept free of any
// `UserNotifications`/UIKit import so they compile and can be unit tested on any
// host; the SwiftUI settings screen (task 18.1) binds to the store and the
// ``NotificationPreferenceApplier`` turns a changed preference into a scheduled
// or cancelled request.

/// An opt-in notification toggle: whether it is enabled and the local time of
/// day at which it fires (Req 7.12, 7.15).
public struct NotificationToggle: Equatable, Codable, Sendable {

    /// Whether the user has enabled this notification.
    public var isEnabled: Bool

    /// The local wall-clock time at which it fires (Req 7.10).
    public var time: TimeOfDay

    public init(isEnabled: Bool, time: TimeOfDay) {
        self.isEnabled = isEnabled
        self.time = time
    }
}

/// Default toggle values used before the user has chosen anything: both
/// notifications are **disabled** (opt-in), with sensible default times — the
/// evening nudge at 19:00 and the global daily at 09:00, both local time.
public extension NotificationToggle {

    /// Default evening-nudge toggle: disabled, 19:00 local (Req 7.12).
    static let eveningNudgeDefault = NotificationToggle(
        isEnabled: false,
        time: TimeOfDay(hour: 19, minute: 0)
    )

    /// Default global-daily toggle: disabled, 09:00 local (Req 7.15).
    static let globalDailyDefault = NotificationToggle(
        isEnabled: false,
        time: TimeOfDay(hour: 9, minute: 0)
    )
}

/// Persists the user's enable/disable + time-of-day choices for the
/// evening nudge and the global daily notification (Req 7.12, 7.15).
public protocol NotificationPreferencesStore {

    /// The current evening-nudge toggle (Req 7.12).
    func eveningNudge() -> NotificationToggle

    /// Records the evening-nudge toggle (Req 7.12).
    func setEveningNudge(_ toggle: NotificationToggle)

    /// The current global-daily toggle (Req 7.15).
    func globalDaily() -> NotificationToggle

    /// Records the global-daily toggle (Req 7.15).
    func setGlobalDaily(_ toggle: NotificationToggle)
}

/// `UserDefaults`-backed ``NotificationPreferencesStore``.
///
/// Defaults to the **App Group** suite so the choices are shared between the
/// main app and the Share Extension (Req 13.2), falling back to `.standard` if
/// the App Group suite is unavailable — mirroring
/// ``UserDefaultsPermissionRequestStore``.
public struct UserDefaultsNotificationPreferencesStore: NotificationPreferencesStore {

    private static let eveningNudgeKey = "sidequest.notifications.eveningNudge"
    private static let globalDailyKey = "sidequest.notifications.globalDaily"

    private let defaults: UserDefaults

    /// Injects a specific `UserDefaults` (used by tests).
    public init(defaults: UserDefaults) {
        self.defaults = defaults
    }

    /// Uses the shared App Group suite, falling back to `.standard`.
    public init() {
        self.defaults = UserDefaults(suiteName: AppGroup.identifier) ?? .standard
    }

    public func eveningNudge() -> NotificationToggle {
        toggle(forKey: Self.eveningNudgeKey, default: .eveningNudgeDefault)
    }

    public func setEveningNudge(_ toggle: NotificationToggle) {
        store(toggle, forKey: Self.eveningNudgeKey)
    }

    public func globalDaily() -> NotificationToggle {
        toggle(forKey: Self.globalDailyKey, default: .globalDailyDefault)
    }

    public func setGlobalDaily(_ toggle: NotificationToggle) {
        store(toggle, forKey: Self.globalDailyKey)
    }

    // MARK: - Coding helpers

    private func toggle(forKey key: String, default fallback: NotificationToggle) -> NotificationToggle {
        guard
            let data = defaults.data(forKey: key),
            let decoded = try? JSONDecoder().decode(NotificationToggle.self, from: data)
        else {
            return fallback
        }
        return decoded
    }

    private func store(_ toggle: NotificationToggle, forKey key: String) {
        guard let data = try? JSONEncoder().encode(toggle) else { return }
        defaults.set(data, forKey: key)
    }
}

// MARK: - Applying preferences to the notification service

/// Turns a stored preference into the right scheduling action: schedule when the
/// toggle is enabled, cancel when it is disabled (Req 7.12, 7.15).
///
/// This is the portable seam the settings UI (task 18.1) and the launch/reschedule
/// hook use, so the enable/disable + time-selection behaviour can be unit tested
/// against a test-double ``NotificationService`` without the iOS-only
/// notification center.
public struct NotificationPreferenceApplier {

    private let service: NotificationService

    public init(service: NotificationService) {
        self.service = service
    }

    /// Applies the evening-nudge toggle: when enabled, schedules the nudge for
    /// the given items at the chosen time (the service still omits it when no
    /// item is eligible — Req 7.14); when disabled, cancels any pending nudge
    /// (Req 7.12).
    public func applyEveningNudge(_ toggle: NotificationToggle, items: [ActionItem]) async {
        if toggle.isEnabled {
            await service.scheduleEveningNudge(at: toggle.time, items: items)
        } else {
            await service.cancelEveningNudge()
        }
    }

    /// Applies the global-daily toggle: when enabled, schedules the daily
    /// self-reminder at the chosen time; when disabled, cancels it (Req 7.15).
    public func applyGlobalDaily(_ toggle: NotificationToggle) async {
        if toggle.isEnabled {
            await service.scheduleGlobalDaily(at: toggle.time)
        } else {
            await service.cancelGlobalDaily()
        }
    }
}
