import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Property 13 — "Until-date selection is accepted
/// exactly within the valid window"** (iOS design.md), validating
/// **Requirements 7.4**.
///
/// Property 13 statement (design.md):
///
/// > *For any* candidate until-date, the selection is accepted if and only if
/// > it is on or after the current local date and no more than 365 days after
/// > the current local date; rejected selections leave the user's other
/// > Task_Reminder values unchanged.
///
/// Subjects under test are the pure, I/O-free validators in
/// `Sources/SideQuestKit/Domain/TaskReminderValidation.swift`:
///
///  * `Domain.isUntilDateWithinWindow(_:now:calendar:)` — the accept/reject
///    predicate, targeted directly so the window boundaries can be exercised
///    without going through the full reminder construction.
///  * `Domain.validateTaskReminder(actionItemId:timeOfDay:untilDate:recurringDaily:now:calendar:)`
///    — the validator that wraps the predicate, used to confirm the window
///    decision propagates to the reminder outcome *and* that a rejected
///    selection echoes the corrective message while a successful one carries
///    the user's other values (time / until-date / recurrence) unchanged.
///
/// The `now` instant and the `Calendar` (which carries the time zone) are
/// injected on both functions, making every run deterministic and runnable on
/// any host (no clock is read and no iOS-only scheduling layer is touched).
///
/// The statement decomposes into two independently-checkable facts, one test
/// method each:
///
///  1. **Predicate is exactly the inclusive `[today, today + 365]` window** —
///     `isUntilDateWithinWindow` returns `true` iff the until-date's local
///     calendar day is at least today and at most today + 365 days, regardless
///     of the time component within that day.
///  2. **The validator mirrors the predicate and preserves other values** — a
///     reminder validates iff the until-date is in the window; on rejection the
///     result is `.untilDateOutOfWindow` with the corrective message (and the
///     caller's draft is untouched, which purity guarantees), and on success
///     the constructed `TaskReminder` carries the supplied time, until-date and
///     recurrence verbatim.
///
/// Generators draw `now` across a century, a device time zone spanning the most
/// extreme UTC offsets and fractional/DST offsets (so day-boundary arithmetic
/// is exercised where an off-by-one is most likely), and a day offset whose
/// sampling deliberately includes the window boundaries: yesterday (−1), today
/// (0), today + 365 (the inclusive upper edge) and today + 366 (just outside).
/// A random time-of-day within the until day proves the time component is
/// ignored. SwiftCheck is configured for **200 successful tests** per property,
/// above the design minimum of 100.
final class UntilDateWindowPropertyTests: XCTestCase {

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

    /// A random instant spanning 2000-01-01 .. 2100-01-01 (seconds since the
    /// Unix epoch), giving a broad spread of calendar days and clock readings.
    private static let nowGen: Gen<Date> = Gen<Int>
        .choose((946_684_800, 4_102_444_800))
        .map { Date(timeIntervalSince1970: TimeInterval($0)) }

    /// Offset, in whole local days, from `now`'s calendar day to the
    /// until-date's calendar day. Sampling blends the exact window boundaries
    /// (−1 = yesterday → reject, 0 = today → accept, 365 = upper edge → accept,
    /// 366 = just past → reject) with neighbourhoods of both edges and a broad
    /// range that straddles the window on both sides.
    private static let dayOffsetGen: Gen<Int> = Gen<Int>.one(of: [
        Gen<Int>.fromElements(of: [-1, 0, 1, 364, 365, 366]), // exact boundaries
        Gen<Int>.fromElements(in: -30...30),                  // around the lower edge
        Gen<Int>.fromElements(in: 335...400),                 // around the upper edge
        Gen<Int>.fromElements(in: -400...400)                 // broad straddle
    ])

    /// A whole-second offset into the until day. Capped below 23 hours
    /// (82 800 s) so it never crosses into the next local day even on a
    /// "spring-forward" DST day, proving the predicate ignores the until-date's
    /// time component (only its calendar day matters).
    private static let secondsIntoDayGen: Gen<Int> = Gen<Int>.fromElements(in: 0...82_799)

    /// A `TimeOfDay` spanning the full valid hour/minute range, used as the
    /// reminder's (non-nil) time so the window check is the only decision.
    private static let timeOfDayGen: Gen<TimeOfDay> = Gen
        .zip(Gen<Int>.fromElements(in: 0...23), Gen<Int>.fromElements(in: 0...59))
        .map { TimeOfDay(hour: $0, minute: $1) }

    // MARK: - Calendars & until-date construction

    /// A Gregorian/POSIX calendar pinned to `timeZone`, matching how the
    /// validator resolves local calendar days.
    private static func calendar(_ timeZone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return calendar
    }

    /// An instant whose local calendar day is `now`'s day shifted by
    /// `dayOffset`, at `secondsIntoDay` seconds past that day's local midnight.
    /// Shifting whole days from a start-of-day instant is DST-safe, and the
    /// capped seconds keep the instant inside the intended day, so the
    /// until-date's calendar day is exactly today + `dayOffset`.
    private static func untilDate(
        now: Date,
        dayOffset: Int,
        secondsIntoDay: Int,
        calendar: Calendar
    ) -> Date {
        let todayStart = calendar.startOfDay(for: now)
        let dayStart = calendar.date(byAdding: .day, value: dayOffset, to: todayStart) ?? todayStart
        return calendar.date(byAdding: .second, value: secondsIntoDay, to: dayStart) ?? dayStart
    }

    // MARK: - Property 13.1 — predicate is exactly the [today, today+365] window

    /// `isUntilDateWithinWindow` returns `true` iff the until-date's local
    /// calendar day is in the inclusive window `[today, today + 365]`, i.e. iff
    /// `0 <= dayOffset <= 365`, independent of the time component within that
    /// day and of the device time zone.
    func testWithinWindowAcceptsExactlyTheInclusiveWindow() {
        property(
            "isUntilDateWithinWindow true iff 0...365 days ahead (Property 13, Req 7.4)",
            arguments: Self.checkerArguments
        ) <- forAll(
            Self.nowGen, Self.timeZoneGen, Self.dayOffsetGen, Self.secondsIntoDayGen
        ) { (now: Date, zone: TimeZone, dayOffset: Int, secondsIntoDay: Int) in
            let calendar = Self.calendar(zone)
            let until = Self.untilDate(
                now: now, dayOffset: dayOffset, secondsIntoDay: secondsIntoDay, calendar: calendar
            )

            let accepted = Domain.isUntilDateWithinWindow(until, now: now, calendar: calendar)
            let expected = (0 <= dayOffset && dayOffset <= Domain.maxReminderUntilDaysAhead)

            return (accepted == expected)
                <?> "offset=\(dayOffset) tz=\(zone.identifier) secs=\(secondsIntoDay) "
                    + "expected=\(expected) got=\(accepted)"
        }
    }

    // MARK: - Property 13.2 — validator mirrors the window and preserves values

    /// `validateTaskReminder` (with a non-nil time, so the window is the only
    /// gate) returns `.valid` iff the until-date is inside the window, and
    /// `.untilDateOutOfWindow` with the corrective message otherwise. On success
    /// the constructed `TaskReminder` carries the supplied action-item id, time,
    /// until-date and recurrence flag unchanged (rejected selections leave the
    /// caller's other values untouched, which purity guarantees).
    func testValidatorMirrorsWindowAndPreservesOtherValues() {
        property(
            "validateTaskReminder valid iff in window, else out-of-window message (Property 13, Req 7.4)",
            arguments: Self.checkerArguments
        ) <- forAll(
            Self.nowGen, Self.timeZoneGen, Self.dayOffsetGen, Self.secondsIntoDayGen,
            Self.timeOfDayGen, Bool.arbitrary
        ) { (now: Date, zone: TimeZone, dayOffset: Int, secondsIntoDay: Int,
             time: TimeOfDay, recurring: Bool) in
            let calendar = Self.calendar(zone)
            let until = Self.untilDate(
                now: now, dayOffset: dayOffset, secondsIntoDay: secondsIntoDay, calendar: calendar
            )

            let result = Domain.validateTaskReminder(
                actionItemId: "item",
                timeOfDay: time,
                untilDate: until,
                recurringDaily: recurring,
                now: now,
                calendar: calendar
            )

            let inWindow = (0 <= dayOffset && dayOffset <= Domain.maxReminderUntilDaysAhead)

            if inWindow {
                // Success carries the user's other values verbatim.
                let expected = TaskReminderValidation.valid(
                    TaskReminder(
                        actionItemId: "item",
                        timeOfDay: time,
                        untilDate: until,
                        recurringDaily: recurring
                    )
                )
                return (result == expected)
                    <?> "offset=\(dayOffset) tz=\(zone.identifier) expected valid+preserved, got \(result)"
            } else {
                // Rejection surfaces the corrective message for the until field.
                let expected = TaskReminderValidation.untilDateOutOfWindow(
                    reason: Domain.untilDateOutOfWindowMessage
                )
                return (result == expected)
                    <?> "offset=\(dayOffset) tz=\(zone.identifier) expected out-of-window, got \(result)"
            }
        }
    }
}
