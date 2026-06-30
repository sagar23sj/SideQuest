import Foundation
import SideQuestKit

/// Drives the notification settings screen (Req 7.12, 7.15, 11.1, 11.4).
///
/// Surfaces the two opt-in notifications — the collective evening nudge
/// (Req 7.12) and the global daily self-reminder (Req 7.15) — as enable/disable
/// toggles with a local time of day. Applying the choices persists them through
/// the `NotificationPreferencesStore`, requests notification permission the
/// first time a notifying feature is enabled (Req 11.1), and turns each
/// preference into a scheduled or cancelled request via the portable
/// `NotificationPreferenceApplier`. The evening nudge is scheduled against the
/// current item set, which the service further filters to eligible items
/// (Req 7.13, 7.14).
@MainActor
final class NotificationSettingsViewModel: ObservableObject {

    @Published var eveningNudgeEnabled: Bool
    @Published var eveningNudgeTime: Date
    @Published var globalDailyEnabled: Bool
    @Published var globalDailyTime: Date

    /// Set when permission is denied, prompting an explanation + settings link
    /// (Req 7.18, 11.4).
    @Published var permissionDenied = false

    let settingsURL: URL? = NotificationSettingsLink.settingsURL

    private let notificationService: NotificationService
    private let preferences: NotificationPreferencesStore
    private let itemRepository: ActionItemRepository
    private let calendar: Calendar

    init(
        notificationService: NotificationService,
        preferences: NotificationPreferencesStore,
        itemRepository: ActionItemRepository,
        calendar: Calendar = .current
    ) {
        self.notificationService = notificationService
        self.preferences = preferences
        self.itemRepository = itemRepository
        self.calendar = calendar

        let nudge = preferences.eveningNudge()
        let daily = preferences.globalDaily()
        self.eveningNudgeEnabled = nudge.isEnabled
        self.eveningNudgeTime = Self.date(from: nudge.time, calendar: calendar)
        self.globalDailyEnabled = daily.isEnabled
        self.globalDailyTime = Self.date(from: daily.time, calendar: calendar)
    }

    /// Persists and applies the current toggles (Req 7.12, 7.15).
    ///
    /// Requests notification permission on first use when either notification is
    /// being enabled (Req 11.1); if permission is denied the toggles are reverted
    /// to disabled and the settings affordance is surfaced (Req 7.18, 11.4).
    func apply() async {
        if eveningNudgeEnabled || globalDailyEnabled {
            let status = await notificationService.requestAuthorizationIfNeeded()
            guard status.isAuthorized else {
                eveningNudgeEnabled = false
                globalDailyEnabled = false
                persist()
                permissionDenied = true
                await applyToService()
                return
            }
        }
        persist()
        await applyToService()
    }

    // MARK: - Helpers

    private func persist() {
        preferences.setEveningNudge(
            NotificationToggle(isEnabled: eveningNudgeEnabled, time: timeOfDay(from: eveningNudgeTime))
        )
        preferences.setGlobalDaily(
            NotificationToggle(isEnabled: globalDailyEnabled, time: timeOfDay(from: globalDailyTime))
        )
    }

    private func applyToService() async {
        let applier = NotificationPreferenceApplier(service: notificationService)
        let items = (try? itemRepository.fetchAll()) ?? []
        await applier.applyEveningNudge(preferences.eveningNudge(), items: items)
        await applier.applyGlobalDaily(preferences.globalDaily())
    }

    private func timeOfDay(from date: Date) -> TimeOfDay {
        let components = calendar.dateComponents([.hour, .minute], from: date)
        return TimeOfDay(hour: components.hour ?? 0, minute: components.minute ?? 0)
    }

    private static func date(from time: TimeOfDay, calendar: Calendar) -> Date {
        calendar.date(
            bySettingHour: time.hour, minute: time.minute, second: 0, of: Date()
        ) ?? Date()
    }
}
