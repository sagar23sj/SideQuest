import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for `Domain.validateTimeframe(_:now:calendar:)`
/// (task 4.5).
///
/// Reused sibling **Property 7 — "Specific-date timeframe accepts
/// today-or-later, rejects past"** (design.md), validating **Requirements 9.7**:
///
/// > IF the User selects a specific date earlier than the current date in the
/// > device's local time zone, THEN THE App SHALL reject the selection and
/// > SHALL display a message requesting a current or future date.
///
/// The property has two halves, both checked here:
///
///  1. The relative timeframes (`today`, `withinADay`, `withinAWeek`) are
///     *always* `.valid`, independent of `now` and the device time zone
///     (Req 9.6 — the always-valid options behind the same rule).
///  2. A `specificDate` is `.valid` **iff** its calendar day is `now`'s
///     calendar day or later, where `now`'s day is read in the device's local
///     time zone and the chosen day is read in UTC (the `CalendarDate`
///     UTC-midnight convention the payload is encoded with). A strictly
///     earlier day is `.invalid`.
///
/// Generators draw a random `now` instant, a random device time zone (spanning
/// extreme UTC offsets — Kiritimati +14 through Pago Pago −11 — to catch
/// off-by-one day errors), and a random day offset. A negative offset places
/// the chosen day strictly in the past (expected `.invalid`); a zero or
/// positive offset places it today-or-later (expected `.valid`).
///
/// SwiftCheck is configured for **200 successful tests** per property, well
/// above the design's minimum of 100 iterations.
final class TimeframeValidationPropertyTests: XCTestCase {

    // MARK: - Configuration

    /// Run more than the design-mandated minimum of 100 iterations.
    private static let checkerArguments = CheckerArguments(
        maxAllowableSuccessfulTests: 200
    )

    // MARK: - Generators

    /// A spread of device time zones, including the most extreme positive and
    /// negative UTC offsets and a half-hour offset, so day-boundary arithmetic
    /// is exercised where a UTC-vs-local day mismatch is most likely.
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

    /// A random instant spanning 2000-01-01 .. 2100-01-01 (seconds since the
    /// Unix epoch). The wide range also yields a broad spread of times of day,
    /// so same-day selections are checked against many `now` clock readings.
    private static let nowGen: Gen<Date> = Gen<Int>
        .choose((946_684_800, 4_102_444_800))
        .map { Date(timeIntervalSince1970: TimeInterval($0)) }

    /// A day offset from `now`'s local calendar day. Negative ⇒ past ⇒ invalid;
    /// zero or positive ⇒ today-or-later ⇒ valid.
    private static let dayOffsetGen: Gen<Int> = Gen<Int>.choose((-400, 400))

    // MARK: - Calendars

    /// A Gregorian/POSIX calendar in `timeZone`, matching how the implementation
    /// reads `now`'s local calendar day.
    private static func localCalendar(_ timeZone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return calendar
    }

    /// A Gregorian/UTC/POSIX calendar matching `CalendarDate.formatter`, used to
    /// build `specificDate` payloads at the exact UTC-midnight day the
    /// implementation reads back.
    private static func utcCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return calendar
    }

    /// Builds the `specificDate` payload whose calendar day is `now`'s local
    /// calendar day shifted by `dayOffset`, encoded with the same
    /// UTC-midnight / `CalendarDate.formatter` convention the wire model and the
    /// validator use. Round-tripping through `CalendarDate.formatter` guarantees
    /// the payload is a canonical `yyyy-MM-dd` UTC-midnight instant, so the
    /// validator's day comparison is exact.
    private static func specificDate(
        now: Date,
        dayOffset: Int,
        timeZone: TimeZone
    ) -> Date {
        let local = localCalendar(timeZone)
        let utc = utcCalendar()

        // `now`'s calendar day in the device's local time zone.
        let localDay = local.dateComponents([.year, .month, .day], from: now)

        // The same y/m/d at UTC midnight — the encoding the payload carries.
        var midnight = DateComponents()
        midnight.year = localDay.year
        midnight.month = localDay.month
        midnight.day = localDay.day
        let todayUTCMidnight = utc.date(from: midnight)!

        // Shift by the requested number of whole days (UTC has no DST, so day
        // arithmetic is exact) and canonicalize via the shared formatter.
        let shifted = utc.date(byAdding: .day, value: dayOffset, to: todayUTCMidnight)!
        let text = CalendarDate.formatter.string(from: shifted)
        return CalendarDate.formatter.date(from: text)!
    }

    // MARK: - Property 7 (specificDate half)

    /// **Property 7 (Req 9.7):** a `specificDate` is `.valid` iff its calendar
    /// day is today-or-later in the device's local time zone, and `.invalid`
    /// (with the corrective message) when it is strictly in the past.
    func testSpecificDateAcceptsTodayOrLaterRejectsPast() {
        property(
            "specificDate valid iff today-or-later in the local time zone",
            arguments: Self.checkerArguments
        ) <- forAll(Self.nowGen, Self.timeZoneGen, Self.dayOffsetGen) {
            (now: Date, timeZone: TimeZone, dayOffset: Int) in

            let calendar = Self.localCalendar(timeZone)
            let date = Self.specificDate(now: now, dayOffset: dayOffset, timeZone: timeZone)

            let result = Domain.validateTimeframe(
                .specificDate(date),
                now: now,
                calendar: calendar
            )

            // Past day (offset < 0) must be rejected with the corrective
            // message; today-or-later (offset >= 0) must be accepted.
            let expected: TimeframeValidationResult = dayOffset < 0
                ? .invalid(reason: Domain.pastSpecificDateMessage)
                : .valid

            return (result == expected)
                <?> "offset=\(dayOffset) tz=\(timeZone.identifier) expected=\(expected) got=\(result)"
        }
    }

    // MARK: - Property 7 (relative-timeframe half)

    /// **Property 7 / Req 9.6:** the relative timeframes are always `.valid`,
    /// regardless of `now` or the device time zone.
    func testRelativeTimeframesAreAlwaysValid() {
        let relativeGen = Gen<Timeframe>.fromElements(of: [.today, .withinADay, .withinAWeek])

        property(
            "today / withinADay / withinAWeek are always valid",
            arguments: Self.checkerArguments
        ) <- forAll(Self.nowGen, Self.timeZoneGen, relativeGen) {
            (now: Date, timeZone: TimeZone, timeframe: Timeframe) in

            let calendar = Self.localCalendar(timeZone)
            let result = Domain.validateTimeframe(timeframe, now: now, calendar: calendar)

            return (result == .valid)
                <?> "timeframe=\(timeframe) tz=\(timeZone.identifier) got=\(result)"
        }
    }
}
