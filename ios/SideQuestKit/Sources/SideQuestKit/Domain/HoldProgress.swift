import Foundation

// MARK: - Press-and-hold completion progress (Req 8.7, 8.8, 8.9)
//
// Pure, portable math for the press-and-hold completion control. The SwiftUI
// control (`HoldToCompleteButton` in the app target) is intentionally a thin
// shell over these functions: it samples the elapsed continuous hold time and
// asks this layer for the fill proportion to render and whether the hold has
// reached the completion threshold. Keeping the math here makes it unit- and
// property-testable with no UI, clock, or gesture machinery (task 11.4,
// Property 18).
//
// Scope of task 11.3 (the pure part):
//   * `holdFillProportion(elapsed:threshold:)` — the filled proportion of the
//     progressive fill, equal to `min(elapsed / 800ms, 1)` and clamped to
//     `[0, 1]` (Req 8.7).
//   * `holdReachesCompletion(elapsed:threshold:)` — whether a continuous hold of
//     `elapsed` has met the completion threshold, i.e. `elapsed >= 800ms`
//     (Req 8.8). A release before the threshold simply never reaches this
//     condition, so the caller resets the fill and leaves the status unchanged
//     (Req 8.9).
//
// These functions are pure and total: they never mutate inputs and never throw
// for any input (including negative, zero, infinite, or NaN durations).

extension Domain {

    /// The continuous hold duration, in seconds, required to complete an
    /// Action_Item via the press-and-hold control: 800 milliseconds (Req 8.7,
    /// 8.8).
    ///
    /// Exposed as the default `threshold` for the hold functions so the control
    /// and its tests share one source of truth. It is parameterized (rather than
    /// hard-coded inside the functions) so tests can drive the math across many
    /// thresholds without depending on the production constant.
    public static let holdCompletionThreshold: TimeInterval = 0.8

    /// The filled proportion of the progressive fill for a continuous hold of
    /// `elapsed` seconds, equal to `min(elapsed / threshold, 1)` clamped to the
    /// closed interval `[0, 1]` (Req 8.7, Property 18).
    ///
    /// The proportion rises linearly from `0` at `elapsed == 0` to `1` at
    /// `elapsed == threshold`, then stays pinned at `1` for any longer hold so
    /// the fill never overshoots. Degenerate inputs are handled so the function
    /// is total:
    /// - A negative `elapsed` (which can never represent a real hold) clamps to
    ///   `0`, i.e. an empty fill.
    /// - A non-positive `threshold` (no meaningful ramp) yields a full fill for
    ///   any non-negative hold, avoiding a divide-by-zero.
    /// - A non-finite `elapsed` (`NaN`/`±∞`) yields `0` for `NaN` and `1` for
    ///   `+∞`, never a non-finite proportion.
    ///
    /// - Parameters:
    ///   - elapsed: The continuous hold time in seconds.
    ///   - threshold: The hold time at which the fill is full. Defaults to
    ///     ``holdCompletionThreshold`` (800 ms).
    /// - Returns: A proportion in `[0, 1]`.
    public static func holdFillProportion(
        elapsed: TimeInterval,
        threshold: TimeInterval = holdCompletionThreshold
    ) -> Double {
        // Treat NaN and any negative hold as no progress.
        guard elapsed.isFinite, elapsed > 0 else { return 0 }
        // No meaningful ramp: any positive hold is immediately "full".
        guard threshold > 0 else { return 1 }

        let raw = elapsed / threshold
        return min(max(raw, 0), 1)
    }

    /// Whether a continuous hold of `elapsed` seconds has reached the completion
    /// threshold, i.e. `elapsed >= threshold` (Req 8.8, Property 18).
    ///
    /// This is the single predicate that gates completion: the control fires the
    /// status change, haptics, and celebration exactly when this returns `true`,
    /// and a release while it is still `false` cancels with no status change
    /// (Req 8.9). A non-finite `elapsed` is never a valid completed hold, so
    /// `NaN`/`±∞` return `false` (a `+∞` hold cannot occur from a real gesture).
    ///
    /// - Parameters:
    ///   - elapsed: The continuous hold time in seconds.
    ///   - threshold: The hold time required to complete. Defaults to
    ///     ``holdCompletionThreshold`` (800 ms).
    /// - Returns: `true` if and only if `elapsed >= threshold`.
    public static func holdReachesCompletion(
        elapsed: TimeInterval,
        threshold: TimeInterval = holdCompletionThreshold
    ) -> Bool {
        guard elapsed.isFinite else { return false }
        return elapsed >= threshold
    }
}
