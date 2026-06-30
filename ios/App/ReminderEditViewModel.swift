import Foundation
import SideQuestKit

/// Drives the reminder editor (Req 7.2–7.5, 11.1).
///
/// Holds the in-progress reminder selection (time of day, until-date, optional
/// daily recurrence) for an `ActionItem`, validates it through the portable
/// `Domain.validateTaskReminder` (task 13.4) so a missing time or an
/// out-of-window until-date is rejected with a corrective message while the
/// other values are retained (Req 7.4, 7.5), and — on save — requests
/// notification permission the first time a notifying feature is used (Req 11.1)
/// before scheduling the reminder occurrences through the `NotificationService`
/// (Req 7.6, 7.7).
@MainActor
final class ReminderEditViewModel: ObservableObject {

    /// The reminder time of day. Backed by a `Date` for the SwiftUI time picker;
    /// resolved to a `TimeOfDay` on validation.
    @Published var time: Date

    /// The last calendar day (inclusive) the reminder may fire (Req 7.4).
    @Published var untilDate: Date

    /// Whether the reminder repeats daily until the until-date (Req 7.3).
    @Published var recurringDaily: Bool = false

    /// A field-level validation message (missing time / out-of-window date),
    /// or `nil` when the current selection is valid (Req 7.4, 7.5).
    @Published private(set) var validationMessage: String?

    /// Set when notification permission is denied, prompting the UI to show an
    /// explanation and a deep link to iOS settings (Req 7.18, 11.4).
    @Published var permissionDenied = false

    /// Flips to `true` once the reminder has been scheduled, so the host can
    /// dismiss.
    @Published private(set) var didSchedule = false

    /// The deep link to the app's iOS notification settings (Req 11.4).
    let settingsURL: URL? = NotificationSettingsLink.settingsURL

    let item: ActionItem
    private let notificationService: NotificationService
    private let now: () -> Date
    private let calendar: Calendar

    init(
        item: ActionItem,
        notificationService: NotificationService,
        now: @escaping () -> Date = Date.init,
        calendar: Calendar = .current
    ) {
        self.item = item
        self.notificationService = notificationService
        self.now = now
        self.calendar = calendar

        // Default selection: 9:00 AM today, non-recurring.
        let start = now()
        self.untilDate = start
        self.time = calendar.date(
            bySettingHour: 9, minute: 0, second: 0, of: start
        ) ?? start
    }

    /// Resolves the picked `time` into a `TimeOfDay` in the local calendar.
    private var resolvedTimeOfDay: TimeOfDay {
        let components = calendar.dateComponents([.hour, .minute], from: time)
        return TimeOfDay(hour: components.hour ?? 0, minute: components.minute ?? 0)
    }

    /// Validates the current selection with the portable rule, returning the
    /// constructed reminder when valid and updating ``validationMessage``.
    @discardableResult
    private func validatedReminder() -> TaskReminder? {
        switch Domain.validateTaskReminder(
            actionItemId: item.id,
            timeOfDay: resolvedTimeOfDay,
            untilDate: untilDate,
            recurringDaily: recurringDaily,
            now: now(),
            calendar: calendar
        ) {
        case .valid(let reminder):
            validationMessage = nil
            return reminder
        case .missingTime(let reason), .untilDateOutOfWindow(let reason):
            validationMessage = reason
            return nil
        }
    }

    /// Saves the reminder (Req 7.2): requests notification permission on first
    /// use (Req 11.1), and — when authorized and the selection is valid —
    /// schedules the occurrences (Req 7.6, 7.7). A denied permission surfaces the
    /// settings affordance (Req 7.18, 11.4); an invalid selection surfaces the
    /// corrective message and retains the other values (Req 7.4, 7.5).
    func save() async {
        guard let reminder = validatedReminder() else { return }

        let status = await notificationService.requestAuthorizationIfNeeded()
        guard status.isAuthorized else {
            permissionDenied = true
            return
        }

        await notificationService.scheduleTaskReminder(for: item, reminder: reminder)
        didSchedule = true
    }
}
