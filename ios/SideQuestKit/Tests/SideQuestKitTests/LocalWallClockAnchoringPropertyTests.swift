import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Property 12 — "Scheduled notifications are
/// anchored to local wall-clock time"** (iOS design.md), validating
/// **Requirements 7.10**.
///
/// Property 12 statement (design.md):
///
/// > *For any* scheduled notification (Task_Reminder, evening nudge, global
/// > daily), the trigger is constructed from local time components (hour/minute,
/// > optionally calendar date) rather than a fixed absolute instant, so the fire
/// > time tracks the configured wall-clock time when the device time zone
/// > changes.
///
/// Subject under test: the pure `DateComponents` derivation in
/// `Sources/SideQuestKit/Notifications/NotificationScheduling.swift` —
/// `NotificationScheduling.dailyComponents(at:)` (daily-recurring nudge /
/// global daily) and `NotificationScheduling.components(at:onDayOf:calendar:)`
/// (one-shot / occurrence building block). These feed a
/// `UNCalendarNotificationTrigger`; because the produced components carry **no
/// `timeZone`**, the system evaluates them in the device's *current* local time
/// zone — the behaviour that makes a reminder track its configured wall-clock
/// time across a time-zone change (and persist across reboots, Req 7.11).
///
/// Three observable facts capture "anchored to local wall-clock time":
///
///  1. **Daily components** carry exactly `hour` + `minute` (the configured
///     wall-clock time), with *no* date fields and — crucially — *no*
///     `timeZone` (and no leaked `calendar`).
///  2. **One-shot components** carry the `year`/`month`/`day` that the injected
///     calendar resolves for the target instant, plus the configured
///     `hour`/`minute`, and again *no* `timeZone`/`calendar`.
///  3. **Time-zone independence** — the configured `hour`/`minute` the
///     components carry are identical no matter which time zone the injected
///     calendar uses. The derivation never converts the wall-clock time into an
///     absolute instant, so the same hour/minute fire in whatever local zone the
///     device is in. (The resolved calendar *day* may legitimately differ by
///     zone for the same instant; only the wall-clock time-of-day must be
///     invariant, and the `timeZone` must always be absent.)
///
/// Generators draw `TimeOfDay` across the full `0...23` / `0...59` range, target
/// instants across a century, and time zones spanning the most extreme UTC
/// offsets (Kiritimati +14 through Pago Pago −11, plus quarter/half-hour
/// offsets and DST zones) so day-boundary and offset arithmetic is exercised
/// where a UTC-vs-local mismatch is most likely. SwiftCheck is configured for
/// **200 successful tests** per property, above the design's minimum of 100.
final class LocalWallClockAnchoringPropertyTests: XCTestCase {

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

    /// A pair of independently-drawn device time zones, used to assert the
    /// configured wall-clock time is invariant when the device zone changes.
    private static let timeZonePairGen: Gen<(TimeZone, TimeZone)> =
        Gen.zip(timeZoneGen, timeZoneGen)

    // MARK: - Generators

    /// A `TimeOfDay` spanning the full valid hour/minute range.
    private static let timeOfDayGen: Gen<TimeOfDay> = Gen
        .zip(Gen<Int>.fromElements(in: 0...23), Gen<Int>.fromElements(in: 0...59))
        .map { TimeOfDay(hour: $0, minute: $1) }

    /// A random instant spanning 2000-01-01 .. 2100-01-01 (seconds since the
    /// Unix epoch), giving a broad spread of calendar days and clock readings.
    private static let instantGen: Gen<Date> = Gen<Int>
        .choose((946_684_800, 4_102_444_800))
        .map { Date(timeIntervalSince1970: TimeInterval($0)) }

    /// A Gregorian/POSIX calendar pinned to `timeZone`, matching how the
    /// scheduling layer resolves the calendar day for a one-shot trigger.
    private static func calendar(_ timeZone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return calendar
    }

    // MARK: - Property 12 (daily-recurring components, Req 7.10)

    /// Daily components carry exactly the configured `hour`/`minute` and no
    /// positioning fields — no date, no `timeZone`, no `calendar` — so a
    /// repeating trigger fires at that local wall-clock time every day in the
    /// device's current zone.
    func testDailyComponentsCarryOnlyWallClockTimeWithNoZone() {
        property(
            "dailyComponents carries only hour/minute, no zone (Property 12, Req 7.10)",
            arguments: Self.checkerArguments
        ) <- forAll(Self.timeOfDayGen) { (time: TimeOfDay) in
            let c = NotificationScheduling.dailyComponents(at: time)

            let carriesWallClock = (c.hour == time.hour) && (c.minute == time.minute)
            // No fixed instant / time zone: anchoring is purely wall-clock.
            let noAbsoluteAnchor =
                (c.timeZone == nil) && (c.calendar == nil)
                && (c.year == nil) && (c.month == nil) && (c.day == nil)
                && (c.era == nil) && (c.second == nil) && (c.nanosecond == nil)

            return (carriesWallClock && noAbsoluteAnchor)
                <?> "time=\(time) got hour=\(String(describing: c.hour)) "
                    + "minute=\(String(describing: c.minute)) tz=\(String(describing: c.timeZone))"
        }
    }

    // MARK: - Property 12 (one-shot components, Req 7.10)

    /// One-shot components carry the calendar day the injected calendar resolves
    /// for the instant, plus the configured `hour`/`minute`, and never a
    /// `timeZone`/`calendar` — so the single fire is at that local wall-clock
    /// time in the device's current zone.
    func testOnDayComponentsCarryResolvedDateAndWallClockTimeWithNoZone() {
        property(
            "components(onDayOf:) carries resolved y/m/d + hour/minute, no zone (Property 12, Req 7.10)",
            arguments: Self.checkerArguments
        ) <- forAll(Self.timeOfDayGen, Self.instantGen, Self.timeZoneGen) {
            (time: TimeOfDay, instant: Date, timeZone: TimeZone) in

            let calendar = Self.calendar(timeZone)
            let c = NotificationScheduling.components(at: time, onDayOf: instant, calendar: calendar)

            // The calendar day resolved in the injected zone (the wall-clock day).
            let day = calendar.dateComponents([.year, .month, .day], from: instant)

            let carriesResolvedDay =
                (c.year == day.year) && (c.month == day.month) && (c.day == day.day)
            let carriesWallClock = (c.hour == time.hour) && (c.minute == time.minute)
            // Still no fixed time zone / calendar leak → wall-clock anchored.
            let noZoneLeak = (c.timeZone == nil) && (c.calendar == nil)

            return (carriesResolvedDay && carriesWallClock && noZoneLeak)
                <?> "time=\(time) tz=\(timeZone.identifier) "
                    + "y/m/d=\(String(describing: c.year))/\(String(describing: c.month))/\(String(describing: c.day)) "
                    + "hour=\(String(describing: c.hour)) minute=\(String(describing: c.minute)) "
                    + "tz=\(String(describing: c.timeZone))"
        }
    }

    // MARK: - Property 12 (time-zone independence of wall-clock time, Req 7.10)

    /// The heart of wall-clock anchoring: for the *same* configured time and the
    /// *same* instant, the `hour`/`minute` the components carry are identical no
    /// matter which time zone the device calendar uses, and the `timeZone` is
    /// always absent. The derivation never converts the configured time into a
    /// fixed absolute instant, so changing the device zone never shifts the
    /// fire's wall-clock time. (The resolved calendar *day* may differ between
    /// zones for the same instant — that is correct and intentionally not
    /// asserted equal here.)
    func testWallClockTimePreservedAcrossTimeZones() {
        property(
            "configured hour/minute invariant across device time zones (Property 12, Req 7.10)",
            arguments: Self.checkerArguments
        ) <- forAll(Self.timeOfDayGen, Self.instantGen, Self.timeZonePairGen) {
            (time: TimeOfDay, instant: Date, zones: (TimeZone, TimeZone)) in
            let (zoneA, zoneB) = zones

            let a = NotificationScheduling.components(at: time, onDayOf: instant, calendar: Self.calendar(zoneA))
            let b = NotificationScheduling.components(at: time, onDayOf: instant, calendar: Self.calendar(zoneB))
            let daily = NotificationScheduling.dailyComponents(at: time)

            // Same wall-clock time of day under every zone, and equal to input.
            let timeInvariant =
                (a.hour == time.hour) && (a.minute == time.minute)
                && (b.hour == time.hour) && (b.minute == time.minute)
                && (daily.hour == time.hour) && (daily.minute == time.minute)
            // No zone is ever baked in, so the system uses the device's current
            // local zone at fire time.
            let neverPinsZone = (a.timeZone == nil) && (b.timeZone == nil) && (daily.timeZone == nil)

            return (timeInvariant && neverPinsZone)
                <?> "time=\(time) zoneA=\(zoneA.identifier) zoneB=\(zoneB.identifier) "
                    + "a.hour=\(String(describing: a.hour)) b.hour=\(String(describing: b.hour))"
        }
    }
}
