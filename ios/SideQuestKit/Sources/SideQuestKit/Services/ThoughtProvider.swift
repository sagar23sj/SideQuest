import Foundation

// MARK: - Thought of the day provider (Req 12.1–12.5)
//
// The loading experience shows a deterministic "thought of the day" selected
// from a built-in, on-device set of ≥30 motivational messages, keyed on the
// device's local calendar date (Req 12.2, 12.3). Selection is pure and
// host-testable: the `Calendar` (which carries the time zone) is injected, and
// the date to resolve is passed in, so the function is deterministic and
// requires no clock, network, or platform UI (the SwiftUI rendering lives in
// the app target's `LoadingView`).
//
// Fail-soft contract (Req 12.5): selection NEVER throws and NEVER surfaces an
// error. If the deterministically selected thought cannot be retrieved (empty
// corpus, an unresolvable calendar ordinal, or a malformed entry), the provider
// returns a non-empty default fallback ``Thought`` and the loading experience
// still completes.

/// Selects the "thought of the day" deterministically from a built-in set
/// (design: Components → `LoadingExperience`; Req 12).
///
/// Implementations MUST be deterministic with respect to the supplied local
/// calendar date: the same calendar day always yields the same ``Thought``, and
/// the selection changes at the next local-date boundary (Req 12.2). They MUST
/// be total — returning a non-empty ``Thought`` for every input rather than
/// throwing or surfacing an error (Req 12.5).
public protocol ThoughtProvider {

    /// The thought to display for the local calendar day containing `date`.
    ///
    /// - Parameter date: An instant whose local calendar day selects the
    ///   thought. The provider resolves the calendar day in its configured time
    ///   zone, so every launch on the same local date returns the same thought.
    /// - Returns: A ``Thought`` with non-empty text (1...280 characters). On any
    ///   selection failure, a default fallback thought (Req 12.5).
    func thought(forLocalDate date: Date) -> Thought
}

/// A ``ThoughtProvider`` backed by a built-in, on-device corpus of ≥30
/// motivational thoughts, so the loading experience works with no network
/// connection (Req 12.3).
///
/// Selection is keyed on the device's local calendar day via the day-of-era
/// ordinal modulo the corpus count, which advances by one at each local-date
/// boundary — giving a stable thought for the whole day and a different thought
/// the next day (Req 12.2). The `Calendar` is injected (default
/// `Calendar.current`) so selection uses the device's local time zone in
/// production and is fully deterministic in tests.
public struct BuiltInThoughtProvider: ThoughtProvider {

    /// The on-device corpus the provider selects from. Guaranteed to be the
    /// built-in set unless a caller injects an alternative (e.g. in tests).
    public let thoughts: [Thought]

    /// The calendar — including its time zone — used to resolve the local
    /// calendar day of the requested date.
    private let calendar: Calendar

    /// Creates a provider over `thoughts`, resolving local dates with `calendar`.
    ///
    /// - Parameters:
    ///   - thoughts: The corpus to select from. Defaults to ``builtInThoughts``
    ///     (≥30 entries, each 1...280 characters — Req 12.3).
    ///   - calendar: The calendar whose time zone defines the local calendar
    ///     day. Defaults to `Calendar.current` so production selection follows
    ///     the device's local date (Req 12.2).
    public init(
        thoughts: [Thought] = BuiltInThoughtProvider.builtInThoughts,
        calendar: Calendar = .current
    ) {
        self.thoughts = thoughts
        self.calendar = calendar
    }

    /// Deterministically selects the thought for the local calendar day of
    /// `date` (Req 12.2), falling back to ``Thought/defaultFallback`` on any
    /// failure without surfacing an error (Req 12.5).
    ///
    /// Algorithm: take the day-of-era ordinal of `date` in the configured
    /// calendar's time zone (a monotonically increasing integer that advances
    /// by one at each local midnight) and index the corpus at `ordinal % count`.
    /// The result is stable for the whole local day and differs at the next
    /// local-date boundary. The modulo is normalized to a non-negative index so
    /// the lookup is always in-bounds.
    ///
    /// Fail-soft branches (each returns the default fallback, never throws):
    /// - the corpus is empty,
    /// - the calendar cannot produce a day-of-era ordinal for `date`,
    /// - the selected entry's text is not 1...280 characters.
    public func thought(forLocalDate date: Date) -> Thought {
        let count = thoughts.count
        guard count > 0,
              let dayOfEra = calendar.ordinality(of: .day, in: .era, for: date)
        else {
            return .defaultFallback
        }

        // Normalize to a non-negative in-bounds index. `ordinality` is positive
        // for all real dates, but the extra `+ count) % count` keeps the lookup
        // total even for hypothetical non-positive ordinals.
        let index = ((dayOfEra % count) + count) % count
        let selected = thoughts[index]

        guard Thought.isValidText(selected.text) else {
            return .defaultFallback
        }
        return selected
    }
}
