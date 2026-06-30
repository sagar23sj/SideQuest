import Foundation

// MARK: - Timeframe validation (Req 9.6, 9.7)
//
// Pure, portable timeframe validation. Mirrors the Android client's
// `com.sidequest.domain.capture.TimeframeValidator` /
// `TimeframeValidationResult` so the iOS Swift implementation produces
// field-by-field equivalent results (Req 3.2, 3.3) and satisfies the reused
// sibling Property 7 ("specific-date timeframe accepts today-or-later, rejects
// past").
//
// Scope of this task (4.4): the relative timeframes — `today`, `withinADay`,
// `withinAWeek` — are always valid; a `specificDate` is valid only when its
// calendar day is today or later in the device's local time zone (Req 9.6,
// 9.7). This is pure domain logic with no I/O: the "current date" and the
// `Calendar` (which carries the time zone) are injected rather than read from a
// clock inside the comparison, so the function stays total, deterministic, and
// testable.

/// Outcome of validating a ``Timeframe`` against the current date.
///
/// The relative timeframes (today / within a day / within a week) are always
/// valid, while a ``Timeframe/specificDate(_:)`` is only valid when its calendar
/// day is the current date or later (Req 9.7). This carries the corrective
/// ``invalid(reason:)`` message so the UI (task 12) can surface it when a past
/// date is rejected. Mirrors the Android `TimeframeValidationResult`.
public enum TimeframeValidationResult: Equatable {

    /// The timeframe is acceptable and may be assigned to an `ActionItem`.
    case valid

    /// The timeframe is rejected. `reason` explains why and is suitable for
    /// display to the user (for example, requesting a current or future date).
    case invalid(reason: String)
}

extension Domain {

    /// Message shown when a ``Timeframe/specificDate(_:)`` in the past is
    /// rejected (Req 9.7). Kept identical in intent to the Android client's
    /// `PAST_SPECIFIC_DATE_MESSAGE` so both clients prompt the user the same way.
    public static let pastSpecificDateMessage =
        "Please choose the current date or a future date."

    /// Validates a ``Timeframe`` against the current date (Req 9.6, 9.7).
    ///
    /// Rules:
    /// - ``Timeframe/today``, ``Timeframe/withinADay``, and
    ///   ``Timeframe/withinAWeek`` are always ``TimeframeValidationResult/valid``
    ///   (Req 9.6).
    /// - ``Timeframe/specificDate(_:)`` is ``TimeframeValidationResult/valid`` if
    ///   and only if its **calendar day** is `now`'s calendar day or later in
    ///   the device's local time zone; an earlier calendar day is rejected as
    ///   ``TimeframeValidationResult/invalid(reason:)`` (Req 9.7, sibling
    ///   Property 7).
    ///
    /// The comparison is by **calendar day**, not by instant: a `specificDate`
    /// that falls on today's local calendar day is accepted regardless of the
    /// time of day carried by `now`, so a same-day selection is never rejected
    /// because the clock has advanced past midnight. The chosen date is read in
    /// UTC because the `specificDate` payload is a calendar-date-only value
    /// encoded at UTC midnight (see `CalendarDate`); `now` is read in the
    /// injected `calendar`'s time zone to obtain the local calendar day. This
    /// matches the Android client's pure `LocalDate`-vs-`LocalDate` comparison.
    ///
    /// - Parameters:
    ///   - timeframe: The timeframe to validate.
    ///   - now: The current instant. Injected (default `Date()`) so the function
    ///     is deterministic and testable without reading a clock.
    ///   - calendar: The calendar — including its time zone — used to resolve
    ///     `now`'s local calendar day. Injected (default `Calendar.current`).
    /// - Returns: ``TimeframeValidationResult/valid`` or
    ///   ``TimeframeValidationResult/invalid(reason:)``.
    ///
    /// This function is pure and total: it never mutates its inputs and never
    /// throws for any input.
    public static func validateTimeframe(
        _ timeframe: Timeframe,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> TimeframeValidationResult {
        switch timeframe {
        case .today, .withinADay, .withinAWeek:
            return .valid

        case .specificDate(let date):
            // The chosen date is a calendar-date-only value encoded at UTC
            // midnight, so recover its day in UTC. `now` is an instant, so read
            // its day in the injected (local) calendar's time zone.
            let chosenDay = dayComponents(of: date, in: utcCalendar)
            let todayDay = dayComponents(of: now, in: calendar)

            return chosenDay < todayDay
                ? .invalid(reason: pastSpecificDateMessage)
                : .valid
        }
    }

    // MARK: - Helpers

    /// The (year, month, day) of `date` in `calendar`, as a tuple that orders
    /// chronologically under `<`. Reducing to year/month/day discards the
    /// time-of-day so the comparison is purely by calendar day.
    private static func dayComponents(
        of date: Date,
        in calendar: Calendar
    ) -> (Int, Int, Int) {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        return (components.year ?? 0, components.month ?? 0, components.day ?? 0)
    }

    /// A Gregorian/UTC/POSIX calendar matching `CalendarDate.formatter`, used to
    /// recover the calendar day of a `specificDate` payload exactly as it was
    /// encoded — independent of the device's locale or time zone.
    private static var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? TimeZone(secondsFromGMT: 0)!
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return calendar
    }
}
