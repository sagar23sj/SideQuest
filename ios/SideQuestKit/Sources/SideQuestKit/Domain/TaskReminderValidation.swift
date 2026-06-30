import Foundation

// MARK: - Task_Reminder creation & validation (Req 7.2, 7.3, 7.4, 7.5)
//
// Pure, portable validation for attaching a Task_Reminder to an Action_Item.
// Like the other validators in this folder (bucket name, timeframe) it performs
// no I/O: the "current date" and the `Calendar` (which carries the time zone)
// are injected so the function stays total, deterministic, and host-testable —
// important because the surrounding scheduling layer is iOS-only.
//
// Scope of this task (13.4 — validation half):
//   * Reject a Task_Reminder with no reminder time of day, retaining the user's
//     other values, with a corrective message (Req 7.5).
//   * Reject an until-date earlier than the current local date or more than 365
//     days after it, retaining the user's other values, with a corrective
//     message requesting a date inside the window (Req 7.4, Property 13).
//   * On success, return the constructed `TaskReminder` (Req 7.2, 7.3).
//
// "Retaining the user's other values" is naturally satisfied by purity: the
// validator never mutates its inputs and the failure cases echo back nothing,
// so the caller's draft (time/until/recurrence) is left untouched and only the
// offending field needs re-entry. The accept/reject window is exposed as its
// own predicate (`isUntilDateWithinWindow`) so the until-date property test
// (13.7, Property 13) can target it directly.

/// The outcome of validating the inputs for a Task_Reminder (Req 7.4, 7.5).
///
/// The failure cases are distinguished so the UI can surface the matching
/// message against the right field while preserving the user's other entries.
/// ``valid(_:)`` carries the constructed ``TaskReminder`` ready to persist and
/// schedule.
public enum TaskReminderValidation: Equatable {

    /// The inputs are acceptable; the associated value is the reminder to save.
    case valid(TaskReminder)

    /// No reminder time of day was provided (Req 7.5). `reason` is suitable for
    /// display; the user's until-date and recurrence selections are retained.
    case missingTime(reason: String)

    /// The until-date is earlier than today or more than
    /// ``Domain/maxReminderUntilDaysAhead`` days ahead (Req 7.4). `reason` is
    /// suitable for display; the user's time and recurrence selections are
    /// retained.
    case untilDateOutOfWindow(reason: String)
}

extension Domain {

    /// The maximum number of days after the current date that a Task_Reminder
    /// until-date may be set (Req 7.4).
    public static let maxReminderUntilDaysAhead = 365

    /// Message shown when a Task_Reminder is missing its reminder time of day
    /// (Req 7.5).
    public static let missingReminderTimeMessage =
        "Please choose a reminder time."

    /// Message shown when a Task_Reminder until-date falls outside the accepted
    /// `[today, today + 365 days]` window (Req 7.4).
    public static let untilDateOutOfWindowMessage =
        "Please choose a date between today and 365 days from now."

    /// Whether `untilDate`'s local calendar day falls inside the accepted
    /// window — on or after today and no more than
    /// ``maxReminderUntilDaysAhead`` days after today (Req 7.4, Property 13).
    ///
    /// The comparison is by **calendar day** in the injected calendar's time
    /// zone (start-of-day to start-of-day): an until-date on today's local day
    /// is accepted regardless of its time component, and the upper bound is
    /// today's start-of-day advanced by exactly 365 days, so the boundary days
    /// (today and today + 365) are both inside the window. Pure and total.
    ///
    /// - Parameters:
    ///   - untilDate: The candidate until-date (an instant; its local calendar
    ///     day is what matters).
    ///   - now: The current instant. Injected (default `Date()`) so the function
    ///     is deterministic and testable without reading a clock.
    ///   - calendar: The calendar — including its time zone — used to resolve
    ///     local calendar days. Injected (default `Calendar.current`).
    /// - Returns: `true` iff `untilDate` is inside `[today, today + 365 days]`.
    public static func isUntilDateWithinWindow(
        _ untilDate: Date,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> Bool {
        let todayStart = calendar.startOfDay(for: now)
        let untilStart = calendar.startOfDay(for: untilDate)

        guard let maxStart = calendar.date(
            byAdding: .day,
            value: maxReminderUntilDaysAhead,
            to: todayStart
        ) else {
            return false
        }

        return untilStart >= todayStart && untilStart <= maxStart
    }

    /// Validates the inputs for a Task_Reminder and, on success, constructs it
    /// (Req 7.2, 7.3, 7.4, 7.5).
    ///
    /// Checks are applied in order, each retaining the user's other values:
    /// 1. **Reminder time** (Req 7.5): a `timeOfDay` must be supplied; a `nil`
    ///    time yields ``TaskReminderValidation/missingTime(reason:)``.
    /// 2. **Until-date window** (Req 7.4): the until-date must satisfy
    ///    ``isUntilDateWithinWindow(_:now:calendar:)``; otherwise
    ///    ``TaskReminderValidation/untilDateOutOfWindow(reason:)``.
    ///
    /// On success the result is ``TaskReminderValidation/valid(_:)`` carrying a
    /// ``TaskReminder`` built from the inputs. Pure and total: it never mutates
    /// its inputs and never throws for any input.
    ///
    /// - Parameters:
    ///   - actionItemId: The Action_Item the reminder is attached to.
    ///   - timeOfDay: The reminder time of day, or `nil` if the user has not
    ///     chosen one yet (Req 7.5).
    ///   - untilDate: The last calendar day (inclusive) the reminder may fire.
    ///   - recurringDaily: Whether the reminder repeats daily until the
    ///     until-date (Req 7.3).
    ///   - now: The current instant. Injected (default `Date()`).
    ///   - calendar: The calendar (with time zone) used for the window check.
    ///     Injected (default `Calendar.current`).
    /// - Returns: ``TaskReminderValidation/valid(_:)`` or a failure case.
    public static func validateTaskReminder(
        actionItemId: String,
        timeOfDay: TimeOfDay?,
        untilDate: Date,
        recurringDaily: Bool,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> TaskReminderValidation {
        guard let timeOfDay else {
            return .missingTime(reason: missingReminderTimeMessage)
        }

        guard isUntilDateWithinWindow(untilDate, now: now, calendar: calendar) else {
            return .untilDateOutOfWindow(reason: untilDateOutOfWindowMessage)
        }

        return .valid(
            TaskReminder(
                actionItemId: actionItemId,
                timeOfDay: timeOfDay,
                untilDate: untilDate,
                recurringDaily: recurringDaily
            )
        )
    }
}
