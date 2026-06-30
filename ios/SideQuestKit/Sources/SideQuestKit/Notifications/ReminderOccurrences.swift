import Foundation

// MARK: - Task_Reminder occurrence day-set (pure, portable) — Req 7.6, 7.7, 7.9
//
// Pure, I/O-free derivation of the set of calendar days on which a
// Task_Reminder fires. It is deliberately separate from the iOS-only
// `UNUserNotificationCenter` scheduling layer (`SystemNotificationService`) so
// it compiles and can be unit/property tested on any host — the platform
// notification center cannot be exercised on the build host. The iOS service
// turns each derived day into a `UNCalendarNotificationTrigger` by feeding it to
// `NotificationScheduling.components(at:onDayOf:calendar:)`.
//
// This realizes the day-set half of task 13.4 and is the function the day-set
// property test (13.5, Property 10) targets:
//
//   * One-shot reminder  → the single next firing day on or before the
//     until-date (Req 7.6).
//   * Daily-recurring    → every day from the first firing day up to and
//     including the until-date (Req 7.7).
//   * No day after the until-date, and no day at all once the Action_Item is
//     completed (Req 7.9; completion cancellation is Req 7.8).
//
// "Next firing day" accounts for the reminder's time of day: if today's
// reminder time has already passed relative to `now`, the first firing day is
// tomorrow, because a notification cannot fire earlier today. Days are returned
// as **start-of-day instants** in the injected calendar's time zone so they
// pair cleanly with the local-wall-clock `DateComponents` derivation (Req 7.10).

/// Pure derivation of the calendar days on which a ``TaskReminder`` fires
/// (Req 7.6, 7.7, 7.9). Every function is total, deterministic, and performs no
/// I/O — the `now` instant and `Calendar` (carrying the time zone) are injected.
public enum ReminderOccurrences {

    /// The set of days a ``TaskReminder`` fires, as start-of-day instants in the
    /// injected calendar's time zone, in ascending order (Property 10).
    ///
    /// Rules:
    /// - If `isCompleted` is `true`, the result is empty: a completed item has
    ///   no pending reminders (Req 7.9; completion-time cancellation is Req 7.8).
    /// - Otherwise the **first firing day** is today if the reminder's time of
    ///   day is still in the future relative to `now`, else tomorrow (a
    ///   notification cannot fire at a time that has already passed today).
    /// - If the first firing day is after the until-date's local day, the result
    ///   is empty — there is no remaining day on or before the until-date
    ///   (Req 7.9).
    /// - For a **one-shot** reminder (`recurringDaily == false`) the result is
    ///   exactly that first firing day (Req 7.6).
    /// - For a **daily-recurring** reminder the result is every day from the
    ///   first firing day up to and including the until-date's local day
    ///   (Req 7.7), and never any day after it (Req 7.9).
    ///
    /// Pure and total: it never mutates its inputs and never throws.
    ///
    /// - Parameters:
    ///   - reminder: The reminder whose firing days to derive.
    ///   - isCompleted: Whether the owning Action_Item is completed. When `true`
    ///     the result is empty (Req 7.9).
    ///   - now: The current instant, used to decide the first firing day.
    ///     Injected (default `Date()`).
    ///   - calendar: The calendar (with time zone) used to resolve local days.
    ///     Injected (default `Calendar.current`).
    /// - Returns: Ascending start-of-day instants for each firing day; empty if
    ///   none remain or the item is completed.
    public static func occurrenceDays(
        for reminder: TaskReminder,
        isCompleted: Bool,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> [Date] {
        guard !isCompleted else { return [] }

        let firstDay = firstFiringDay(timeOfDay: reminder.timeOfDay, now: now, calendar: calendar)
        let untilDay = calendar.startOfDay(for: reminder.untilDate)

        // Nothing left to fire on or before the until-date (Req 7.9).
        guard firstDay <= untilDay else { return [] }

        guard reminder.recurringDaily else {
            // One-shot: the single next firing day (Req 7.6).
            return [firstDay]
        }

        // Daily-recurring: every day in [firstDay, untilDay] inclusive (Req 7.7).
        var days: [Date] = []
        var day = firstDay
        while day <= untilDay {
            days.append(day)
            guard let next = calendar.date(byAdding: .day, value: 1, to: day) else { break }
            day = next
        }
        return days
    }

    /// A stable per-day occurrence key for `day`, used to build the
    /// notification request identifier via
    /// ``NotificationIdentifier/taskReminder(itemId:occurrence:)``.
    ///
    /// The key is the day's `yyyy-MM-dd` in the injected calendar's time zone,
    /// so each firing day maps to a distinct, human-readable identifier suffix
    /// and re-scheduling the same day replaces (rather than duplicates) its
    /// request.
    public static func occurrenceKey(for day: Date, calendar: Calendar = .current) -> String {
        let components = calendar.dateComponents([.year, .month, .day], from: day)
        let year = components.year ?? 0
        let month = components.month ?? 0
        let dayOfMonth = components.day ?? 0
        return String(format: "%04d-%02d-%02d", year, month, dayOfMonth)
    }

    // MARK: - Helpers

    /// The first day a reminder set for `timeOfDay` can fire relative to `now`:
    /// today's start-of-day if today's reminder instant is still in the future
    /// (`>= now`), otherwise tomorrow's start-of-day. Returned as a start-of-day
    /// instant in `calendar`'s time zone.
    private static func firstFiringDay(
        timeOfDay: TimeOfDay,
        now: Date,
        calendar: Calendar
    ) -> Date {
        let today = calendar.startOfDay(for: now)

        // The reminder's wall-clock instant on today's local day.
        var components = calendar.dateComponents([.year, .month, .day], from: now)
        components.hour = timeOfDay.hour
        components.minute = timeOfDay.minute
        components.second = 0
        let reminderToday = calendar.date(from: components) ?? today

        if reminderToday >= now {
            return today
        }

        // Today's time already passed → first firing day is tomorrow.
        return calendar.date(byAdding: .day, value: 1, to: today) ?? today
    }
}
