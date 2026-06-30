import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Property 10 — "Reminder occurrences are exactly
/// the scheduled day-set up to the until-date"** (iOS design.md), validating
/// **Requirements 7.7, 7.9**.
///
/// Property 10 statement (design.md):
///
/// > *For any* Task_Reminder, the set of scheduled occurrence dates equals: for
/// > a one-shot reminder, the single next reminder date on or before the
/// > until-date; for a recurring reminder, every day from the start date up to
/// > and including the until-date — and contains no date after the until-date,
/// > and none once the Action_Item is marked completed.
///
/// Subject under test: the pure, I/O-free day-set derivation
/// `ReminderOccurrences.occurrenceDays(for:isCompleted:now:calendar:)`
/// (`Sources/SideQuestKit/Notifications/ReminderOccurrences.swift`). It is
/// deliberately separable from the iOS-only `UNUserNotificationCenter`
/// scheduling layer so it can be exercised on any host (the platform
/// notification center cannot run on the build host). The `now` instant and the
/// `Calendar` (carrying the time zone) are injected, making every run
/// deterministic.
///
/// The statement decomposes into the following independently-checkable facts,
/// one test method each:
///
///  1. **Completed ⇒ empty** — once the Action_Item is marked completed there
///     are no occurrences at all, regardless of recurrence or until-date
///     (Req 7.9; completion-time cancellation itself is Property 11 / Req 7.8).
///  2. **One-shot ⇒ the single next firing day on or before the until-date** —
///     exactly one day when one remains, else empty (Req 7.6/7.9).
///  3. **Recurring ⇒ every day from the first firing day through the until-date
///     inclusive** — a contiguous, ascending, gap-free run of start-of-day
///     instants spanning `[firstFiringDay, untilDay]` (Req 7.7).
///  4. **No day after the until-date** — for *any* reminder, every returned day
///     is on or before the until-date's local day (Req 7.9).
///
/// Rather than copy the production loop wholesale, each test asserts a structural
/// characterization (emptiness condition, single-element identity, contiguity,
/// boundary inclusion/exclusion, day-count) so an off-by-one at the until
/// boundary, a missing "fires tomorrow once today's time has passed" decision,
/// or a stray day beyond the until-date is caught.
///
/// Generators draw `TimeOfDay` across the full `0...23` / `0...59` range, `now`
/// across a century, and an until-date offset spanning negatives (already past
/// → empty) through ~400 days ahead, under time zones with the most extreme UTC
/// offsets so day-boundary arithmetic is exercised where a mismatch is most
/// likely. SwiftCheck is configured for **200 successful tests** per property,
/// above the design minimum of 100.
final class ReminderOccurrenceDaySetPropertyTests: XCTestCase {

    // MARK: - Configuration (design: ≥100 iterations per property)

    private static let checkerArguments = CheckerArguments(
        maxAllowableSuccessfulTests: 200
    )

    // MARK: - Time-zone pool (extremes + fractional offsets + DST)

    private static let timeZoneIdentifiers = [
        "UTC",
        "Pacific/Kiritimati",   // +14:00 — furthest ahead
        "Pacific/Pago_Pago",    // -11:00 — furthest behind
        "Asia/Kolkata",         // +05:30 — half-hour offset
        "Asia/Tokyo",           // +09:00
        "America/New_York",     // -05:00 / -04:00 (DST)
        "Europe/London",        // +00:00 / +01:00 (DST)
        "Australia/Eucla"       // +08:45 — quarter-hour offset
    ]

    private static let timeZoneGen: Gen<TimeZone> = Gen<String>
        .fromElements(of: timeZoneIdentifiers)
        .map { TimeZone(identifier: $0)! }

    // MARK: - Generators

    /// A `TimeOfDay` spanning the full valid hour/minute range.
    private static let timeOfDayGen: Gen<TimeOfDay> = Gen
        .zip(Gen<Int>.fromElements(in: 0...23), Gen<Int>.fromElements(in: 0...59))
        .map { TimeOfDay(hour: $0, minute: $1) }

    /// A random instant spanning 2000-01-01 .. 2100-01-01 (seconds since the
    /// Unix epoch), giving a broad spread of calendar days and clock readings.
    private static let nowGen: Gen<Date> = Gen<Int>
        .choose((946_684_800, 4_102_444_800))
        .map { Date(timeIntervalSince1970: TimeInterval($0)) }

    /// Offset, in whole days, from `now` to the reminder's until-date. The
    /// negative tail drives the "already past → no occurrences" case (Req 7.9),
    /// `0` the "until is today" boundary, and the long positive tail multi-day
    /// recurring runs up to roughly the one-year validation window (Req 7.4).
    private static let untilDayOffsetGen: Gen<Int> = Gen<Int>.fromElements(in: -3...400)

    /// A Gregorian/POSIX calendar pinned to `timeZone`, matching how the
    /// scheduling layer resolves local calendar days.
    private static func calendar(_ timeZone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return calendar
    }

    /// Builds a `TaskReminder` whose until-date is `untilDayOffset` days from
    /// `now` in `calendar`'s zone (same wall-clock time as `now`).
    private static func reminder(
        timeOfDay: TimeOfDay,
        recurringDaily: Bool,
        now: Date,
        untilDayOffset: Int,
        calendar: Calendar
    ) -> TaskReminder {
        let untilDate = calendar.date(byAdding: .day, value: untilDayOffset, to: now) ?? now
        return TaskReminder(
            actionItemId: "item",
            timeOfDay: timeOfDay,
            untilDate: untilDate,
            recurringDaily: recurringDaily
        )
    }

    // MARK: - Reference: the first day a reminder can fire (today or tomorrow)

    /// Independently characterizes the first firing day: today's local day if
    /// the reminder's wall-clock time is still in the future relative to `now`,
    /// otherwise tomorrow (a notification cannot fire at a time already past
    /// today). Returned as a start-of-day instant.
    private static func firstFiringDay(
        timeOfDay: TimeOfDay,
        now: Date,
        calendar: Calendar
    ) -> Date {
        let today = calendar.startOfDay(for: now)
        var components = calendar.dateComponents([.year, .month, .day], from: now)
        components.hour = timeOfDay.hour
        components.minute = timeOfDay.minute
        components.second = 0
        let reminderToday = calendar.date(from: components) ?? today
        if reminderToday >= now { return today }
        return calendar.date(byAdding: .day, value: 1, to: today) ?? today
    }

    /// Whole-day distance between two start-of-day instants in `calendar`.
    private static func dayCount(from start: Date, to end: Date, calendar: Calendar) -> Int {
        (calendar.dateComponents([.day], from: start, to: end).day ?? 0) + 1
    }

    // MARK: - Property 10.1 — completed item has no occurrences (Req 7.9)

    /// Once the owning Action_Item is completed the day-set is empty for *every*
    /// reminder shape and until-date, mirroring that completion cancels pending
    /// reminders (Req 7.8/7.9).
    func testCompletedItemHasNoOccurrences() {
        property(
            "completed ⇒ empty day-set (Property 10, Req 7.9)",
            arguments: Self.checkerArguments
        ) <- forAll(
            Self.timeOfDayGen, Bool.arbitrary, Self.nowGen, Self.untilDayOffsetGen, Self.timeZoneGen
        ) { (time: TimeOfDay, recurring: Bool, now: Date, offset: Int, zone: TimeZone) in
            let calendar = Self.calendar(zone)
            let reminder = Self.reminder(
                timeOfDay: time, recurringDaily: recurring,
                now: now, untilDayOffset: offset, calendar: calendar
            )

            let days = ReminderOccurrences.occurrenceDays(
                for: reminder, isCompleted: true, now: now, calendar: calendar
            )

            return days.isEmpty
                <?> "completed reminder produced \(days.count) day(s) (expected 0)"
        }
    }

    // MARK: - Property 10.2 — one-shot is the single next firing day ≤ until (Req 7.6/7.9)

    /// A one-shot reminder yields exactly the first firing day when that day is
    /// on or before the until-date, and nothing once the until-date has already
    /// passed — never more than one day, and never a day after the until-date.
    func testOneShotIsSingleNextFiringDayOnOrBeforeUntil() {
        property(
            "one-shot ⇒ single next firing day on/before until, else empty (Property 10, Req 7.6/7.9)",
            arguments: Self.checkerArguments
        ) <- forAll(
            Self.timeOfDayGen, Self.nowGen, Self.untilDayOffsetGen, Self.timeZoneGen
        ) { (time: TimeOfDay, now: Date, offset: Int, zone: TimeZone) in
            let calendar = Self.calendar(zone)
            let reminder = Self.reminder(
                timeOfDay: time, recurringDaily: false,
                now: now, untilDayOffset: offset, calendar: calendar
            )

            let days = ReminderOccurrences.occurrenceDays(
                for: reminder, isCompleted: false, now: now, calendar: calendar
            )

            let firstDay = Self.firstFiringDay(timeOfDay: time, now: now, calendar: calendar)
            let untilDay = calendar.startOfDay(for: reminder.untilDate)

            if firstDay <= untilDay {
                // Exactly the first firing day, which is itself ≤ until.
                return (days == [firstDay])
                    <?> "expected [\(firstDay)] got \(days)"
            } else {
                // The only possible day is already past the until-date.
                return days.isEmpty
                    <?> "firstDay \(firstDay) > untilDay \(untilDay) but got \(days)"
            }
        }
    }

    // MARK: - Property 10.3 — recurring covers every day through until inclusive (Req 7.7)

    /// A daily-recurring reminder yields a contiguous, strictly-ascending,
    /// gap-free run of start-of-day instants beginning at the first firing day
    /// and ending exactly on the until-date's local day — one entry per day with
    /// no day skipped and none beyond the until-date. Empty when the first
    /// firing day is already past the until-date.
    func testRecurringCoversEveryDayThroughUntilInclusive() {
        property(
            "recurring ⇒ every day [firstFiringDay, until] inclusive, contiguous (Property 10, Req 7.7)",
            arguments: Self.checkerArguments
        ) <- forAll(
            Self.timeOfDayGen, Self.nowGen, Self.untilDayOffsetGen, Self.timeZoneGen
        ) { (time: TimeOfDay, now: Date, offset: Int, zone: TimeZone) in
            let calendar = Self.calendar(zone)
            let reminder = Self.reminder(
                timeOfDay: time, recurringDaily: true,
                now: now, untilDayOffset: offset, calendar: calendar
            )

            let days = ReminderOccurrences.occurrenceDays(
                for: reminder, isCompleted: false, now: now, calendar: calendar
            )

            let firstDay = Self.firstFiringDay(timeOfDay: time, now: now, calendar: calendar)
            let untilDay = calendar.startOfDay(for: reminder.untilDate)

            guard firstDay <= untilDay else {
                return days.isEmpty
                    <?> "firstDay \(firstDay) > untilDay \(untilDay) but got \(days.count) day(s)"
            }

            // Boundaries: starts at the first firing day, ends on the until-date.
            let startsAtFirst = days.first == firstDay
            let endsAtUntil = days.last == untilDay
            // One entry per calendar day in the inclusive span, none skipped.
            let expectedCount = Self.dayCount(from: firstDay, to: untilDay, calendar: calendar)
            let correctCount = days.count == expectedCount
            // Every entry is a start-of-day instant, contiguous and ascending.
            var contiguousAscending = true
            for (index, day) in days.enumerated() {
                if calendar.startOfDay(for: day) != day { contiguousAscending = false; break }
                if index > 0 {
                    let prevPlusOne = calendar.date(byAdding: .day, value: 1, to: days[index - 1])
                    if prevPlusOne != day { contiguousAscending = false; break }
                }
            }

            return (startsAtFirst && endsAtUntil && correctCount && contiguousAscending)
                <?> "first=\(String(describing: days.first)) expectedFirst=\(firstDay) "
                    + "last=\(String(describing: days.last)) expectedLast=\(untilDay) "
                    + "count=\(days.count) expectedCount=\(expectedCount)"
        }
    }

    // MARK: - Property 10.4 — no occurrence ever after the until-date (Req 7.9)

    /// Across *any* reminder shape (one-shot or recurring) and completion state,
    /// no returned day is after the until-date's local day, and a completed item
    /// is always empty. This is the universal "never fires past the until-date"
    /// guarantee, independent of the start-side reasoning above.
    func testNoOccurrenceAfterUntilDate() {
        property(
            "no day after until-date for any reminder (Property 10, Req 7.9)",
            arguments: Self.checkerArguments
        ) <- forAll(
            Self.timeOfDayGen, Bool.arbitrary, Bool.arbitrary,
            Self.nowGen, Self.untilDayOffsetGen, Self.timeZoneGen
        ) { (time: TimeOfDay, recurring: Bool, completed: Bool, now: Date, offset: Int, zone: TimeZone) in
            let calendar = Self.calendar(zone)
            let reminder = Self.reminder(
                timeOfDay: time, recurringDaily: recurring,
                now: now, untilDayOffset: offset, calendar: calendar
            )

            let days = ReminderOccurrences.occurrenceDays(
                for: reminder, isCompleted: completed, now: now, calendar: calendar
            )

            let untilDay = calendar.startOfDay(for: reminder.untilDate)
            let noneAfterUntil = days.allSatisfy { $0 <= untilDay }
            let emptyWhenCompleted = !completed || days.isEmpty

            return (noneAfterUntil && emptyWhenCompleted)
                <?> "untilDay=\(untilDay) completed=\(completed) days=\(days)"
        }
    }
}
