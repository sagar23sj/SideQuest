import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Property 23 — Thought-of-the-day selection is
/// deterministic by local date and fails soft** (iOS design.md), validating
/// **Requirements 12.2, 12.5** (task 17.2).
///
/// Property 23 statement (design.md):
///
/// > *For any* local calendar date, thought-of-the-day selection is
/// > deterministic — every selection on the same local date yields the same
/// > thought, and a date crossing a local calendar-day boundary may yield a
/// > different thought — and if the selected thought cannot be retrieved, a
/// > non-empty default fallback thought is returned without surfacing an error.
///
/// Requirement 12.2: the thought is selected deterministically from the device's
/// local calendar date — the same thought for every launch on the same local
/// date, and the selection advances at the next local calendar-date boundary.
///
/// Requirement 12.5: if the deterministically selected thought cannot be
/// retrieved, a default fallback thought is shown and the loading experience
/// completes without surfacing an error.
///
/// Subject under test: ``BuiltInThoughtProvider/thought(forLocalDate:)`` in
/// `Sources/SideQuestKit/Services/ThoughtProvider.swift`. The provider is pure
/// and host-testable: the `Calendar` (carrying the time zone) is injected and
/// the date to resolve is passed in, so selection is deterministic with no
/// clock, network, or platform UI, and — being non-throwing — its fail-soft
/// contract is expressed by *which* ``Thought`` it returns.
///
/// The property is checked in three parts:
///
///  1. **Determinism within a local day** (Req 12.2) — any two instants that
///     fall in the *same* local calendar day yield the same thought, across a
///     spread of device time zones (extreme UTC offsets and DST zones).
///  2. **Advance at the day boundary** (Req 12.2) — two adjacent local days
///     yield *different* thoughts from the shipped built-in corpus. The provider
///     advances its selection by one corpus entry at each local-date boundary,
///     and the built-in corpus has ≥30 distinct entries, so consecutive days
///     never collide.
///  3. **Fail-soft totality** (Req 12.5) — for *any* corpus (including an empty
///     corpus and corpora whose selected entry is malformed) and any date, the
///     returned thought always has non-empty, in-bounds text and never throws;
///     and when the selected thought cannot be retrieved (empty or all-malformed
///     corpus) the result is exactly ``Thought/defaultFallback``.
///
/// SwiftCheck is configured for **200 successful tests** per property, above the
/// design's minimum of 100 iterations.
final class ThoughtSelectionPropertyTests: XCTestCase {

    // MARK: - Configuration (design: ≥100 iterations per property)

    private static let checkerArguments = CheckerArguments(
        maxAllowableSuccessfulTests: 200
    )

    // MARK: - Generators: time zones (mirrors TimeframeValidationPropertyTests)

    /// A spread of device time zones — the most extreme positive/negative UTC
    /// offsets, quarter/half-hour offsets, and DST zones — so local-day boundary
    /// arithmetic is exercised where a day mismatch is most likely.
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
    /// Unix epoch), giving a broad spread of dates and times of day.
    private static let instantGen: Gen<Date> = Gen<Int>
        .choose((946_684_800, 4_102_444_800))
        .map { Date(timeIntervalSince1970: TimeInterval($0)) }

    /// A fraction in `[0, 1)` used to place an instant somewhere inside a single
    /// local calendar day without reaching the next day's boundary.
    private static let fractionGen: Gen<Double> = Gen<Int>
        .choose((0, 999_999))
        .map { Double($0) / 1_000_000.0 }

    // MARK: - Calendars

    /// A Gregorian/POSIX calendar in `timeZone`, matching how the provider reads
    /// the local calendar day of a date.
    private static func localCalendar(_ timeZone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return calendar
    }

    /// An instant `fraction` of the way through the local calendar day that
    /// contains `instant`. Using `startOfDay` and the start of the *next* day to
    /// bound the interval makes this correct across DST days (23h/25h), and a
    /// `fraction < 1` guarantees the result stays within the same local day.
    private static func instant(
        inSameLocalDayAs instant: Date,
        fraction: Double,
        calendar: Calendar
    ) -> Date {
        let startOfDay = calendar.startOfDay(for: instant)
        let startOfNextDay = calendar.date(byAdding: .day, value: 1, to: startOfDay)!
        let span = startOfNextDay.timeIntervalSince(startOfDay)
        return startOfDay.addingTimeInterval(span * fraction)
    }

    // MARK: - Generators: corpora for the fail-soft property

    /// Valid thought text: 1...280 characters (Req 12.1).
    private static let validTextGen: Gen<String> = Gen<Int>
        .choose((1, 280))
        .map { String(repeating: "a", count: $0) }

    /// Malformed thought text: empty (0 chars) or just over the limit (281),
    /// which the provider must treat as "cannot be retrieved" and fail soft.
    private static let malformedTextGen: Gen<String> = Gen<String>.fromElements(of: [
        "",
        String(repeating: "a", count: 281)
    ])

    private static let idGen: Gen<Int> = Gen<Int>.choose((1, 10_000))

    /// A thought whose text is sometimes valid and sometimes malformed, so the
    /// generated corpus exercises both the in-bounds and the fail-soft branch.
    private static let mixedThoughtGen: Gen<Thought> = Gen<Bool>.fromElements(of: [true, false])
        .flatMap { isValid in
            Gen.zip(idGen, isValid ? validTextGen : malformedTextGen)
                .map { Thought(id: $0, text: $1) }
        }

    /// A possibly-empty corpus (0...12 entries) mixing valid and malformed
    /// thoughts. Includes the empty corpus, the strongest fail-soft trigger.
    private static let arbitraryCorpusGen: Gen<[Thought]> = Gen<Int>
        .fromElements(in: 0...12)
        .flatMap { count in Gen.sequence(Array(repeating: mixedThoughtGen, count: count)) }

    /// A non-empty corpus whose every entry is malformed, so whichever index the
    /// provider deterministically selects, the entry cannot be retrieved and the
    /// default fallback must be returned (Req 12.5).
    private static let allMalformedCorpusGen: Gen<[Thought]> = Gen<Int>
        .fromElements(in: 1...12)
        .flatMap { count in
            Gen.sequence(Array(repeating:
                Gen.zip(idGen, malformedTextGen).map { Thought(id: $0, text: $1) },
                count: count))
        }

    // MARK: - Property 23a: deterministic within a local day (Req 12.2)

    /// Two instants that fall in the same local calendar day select the same
    /// thought, regardless of time of day or device time zone.
    func testSelectionIsDeterministicWithinALocalDay() {
        property(
            "same local day ⇒ same thought (Property 23, Req 12.2)",
            arguments: Self.checkerArguments
        ) <- forAll(Self.timeZoneGen, Self.instantGen, Self.fractionGen, Self.fractionGen) {
            (timeZone: TimeZone, base: Date, f1: Double, f2: Double) in

            let calendar = Self.localCalendar(timeZone)
            let provider = BuiltInThoughtProvider(calendar: calendar)

            let t1 = Self.instant(inSameLocalDayAs: base, fraction: f1, calendar: calendar)
            let t2 = Self.instant(inSameLocalDayAs: base, fraction: f2, calendar: calendar)

            let a = provider.thought(forLocalDate: t1)
            let b = provider.thought(forLocalDate: t2)

            return (a == b)
                <?> "tz=\(timeZone.identifier) f1=\(f1) f2=\(f2) a=\(a.id) b=\(b.id)"
        }
    }

    // MARK: - Property 23b: advances at the day boundary (Req 12.2)

    /// Crossing a local calendar-day boundary changes the selected thought. The
    /// shipped built-in corpus has ≥30 distinct entries and the provider advances
    /// by one entry per day, so two adjacent local days never select the same
    /// thought.
    func testSelectionDiffersAcrossAdjacentLocalDays() {
        property(
            "adjacent local days ⇒ different thought (Property 23, Req 12.2)",
            arguments: Self.checkerArguments
        ) <- forAll(Self.timeZoneGen, Self.instantGen, Self.fractionGen, Self.fractionGen) {
            (timeZone: TimeZone, base: Date, fToday: Double, fTomorrow: Double) in

            let calendar = Self.localCalendar(timeZone)
            let provider = BuiltInThoughtProvider(calendar: calendar)

            // An instant inside today's local day...
            let startOfToday = calendar.startOfDay(for: base)
            let today = Self.instant(inSameLocalDayAs: startOfToday, fraction: fToday, calendar: calendar)
            // ...and an instant inside the next local day.
            let startOfTomorrow = calendar.date(byAdding: .day, value: 1, to: startOfToday)!
            let tomorrow = Self.instant(inSameLocalDayAs: startOfTomorrow, fraction: fTomorrow, calendar: calendar)

            let todayThought = provider.thought(forLocalDate: today)
            let tomorrowThought = provider.thought(forLocalDate: tomorrow)

            return (todayThought != tomorrowThought)
                <?> "tz=\(timeZone.identifier) today=\(todayThought.id) tomorrow=\(tomorrowThought.id)"
        }
    }

    // MARK: - Property 23c: fail-soft totality (Req 12.5)

    /// For any corpus (including empty and partly-malformed corpora) and any
    /// date/time zone, selection always returns a thought with non-empty,
    /// in-bounds (1...280) text and never throws — so the loading experience can
    /// always complete without an error.
    func testSelectionAlwaysReturnsNonEmptyInBoundsText() {
        property(
            "selection text is always 1...280 chars (Property 23, Req 12.5)",
            arguments: Self.checkerArguments
        ) <- forAll(Self.timeZoneGen, Self.instantGen, Self.arbitraryCorpusGen) {
            (timeZone: TimeZone, date: Date, corpus: [Thought]) in

            let calendar = Self.localCalendar(timeZone)
            let provider = BuiltInThoughtProvider(thoughts: corpus, calendar: calendar)

            let result = provider.thought(forLocalDate: date)

            return Thought.isValidText(result.text)
                <?> "tz=\(timeZone.identifier) count=\(corpus.count) text.count=\(result.text.count)"
        }
    }

    /// When the corpus is empty, the selected thought cannot be retrieved, so the
    /// provider returns exactly the non-empty default fallback (Req 12.5).
    func testEmptyCorpusReturnsDefaultFallback() {
        property(
            "empty corpus ⇒ default fallback (Property 23, Req 12.5)",
            arguments: Self.checkerArguments
        ) <- forAll(Self.timeZoneGen, Self.instantGen) {
            (timeZone: TimeZone, date: Date) in

            let calendar = Self.localCalendar(timeZone)
            let provider = BuiltInThoughtProvider(thoughts: [], calendar: calendar)

            let result = provider.thought(forLocalDate: date)

            return (result == Thought.defaultFallback && Thought.isValidText(result.text))
                <?> "tz=\(timeZone.identifier) got id=\(result.id)"
        }
    }

    /// When every corpus entry is malformed, whichever entry the provider
    /// deterministically selects cannot be retrieved, so it returns exactly the
    /// non-empty default fallback (Req 12.5).
    func testAllMalformedCorpusReturnsDefaultFallback() {
        property(
            "all-malformed corpus ⇒ default fallback (Property 23, Req 12.5)",
            arguments: Self.checkerArguments
        ) <- forAll(Self.timeZoneGen, Self.instantGen, Self.allMalformedCorpusGen) {
            (timeZone: TimeZone, date: Date, corpus: [Thought]) in

            let calendar = Self.localCalendar(timeZone)
            let provider = BuiltInThoughtProvider(thoughts: corpus, calendar: calendar)

            let result = provider.thought(forLocalDate: date)

            return (result == Thought.defaultFallback && Thought.isValidText(result.text))
                <?> "tz=\(timeZone.identifier) count=\(corpus.count) got id=\(result.id)"
        }
    }
}
