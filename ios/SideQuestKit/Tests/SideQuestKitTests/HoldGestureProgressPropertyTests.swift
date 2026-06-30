import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property tests for the pure press-and-hold completion math in
/// `Domain.holdFillProportion(elapsed:threshold:)` and
/// `Domain.holdReachesCompletion(elapsed:threshold:)`
/// (`Sources/SideQuestKit/Domain/HoldProgress.swift`).
///
/// ## Property 18: Press-and-hold progress is proportional and completes only at the threshold
///
/// *For any* elapsed continuous hold time `t`, the progressive fill proportion
/// equals `min(t / threshold, 1)` clamped to `[0, 1]`, completion occurs if and
/// only if `t >= threshold`, and any release with `t < threshold` resets the
/// fill toward empty and leaves the Action_Status unchanged (the caller simply
/// never observes `holdReachesCompletion == true`).
///
/// **Validates: Requirements 8.7, 8.9**
///
/// Req 8.7: a progressive fill whose proportion equals `min(elapsedHold / 800ms, 1)`.
/// Req 8.9: releasing before the threshold cancels and resets with no status change.
///
/// The functions are parameterized on `threshold` so the math is exercised
/// across many thresholds without depending on the production 800 ms constant.
///
/// Following the repo convention (see `ThoughtSelectionPropertyTests`,
/// `TimeframeValidationPropertyTests`), `TimeInterval` (Double) values are
/// generated from integer milliseconds mapped to seconds. This keeps generators
/// finite and free of `NaN`/`±∞` for the algebraic properties; the degenerate
/// (non-finite, negative, zero, non-positive-threshold) inputs are covered
/// explicitly by the example tests at the bottom so the total-function contract
/// is pinned down precisely.
///
/// SwiftCheck is configured for **200 successful tests** per property, above the
/// design's minimum of 100 iterations.
final class HoldGestureProgressPropertyTests: XCTestCase {

    // MARK: - Configuration

    /// Run more than the design-mandated minimum of 100 iterations.
    private static let checkerArguments = CheckerArguments(
        maxAllowableSuccessfulTests: 200
    )

    // MARK: - Generators

    /// A finite, non-negative hold time in seconds, drawn from 0...4000 ms.
    /// Weighted to also hit `0` (empty hold) frequently.
    private static let elapsedSecondsGen: Gen<TimeInterval> = Gen<Int>.frequency([
        (1, Gen.pure(0)),
        (5, Gen<Int>.choose((0, 4_000))),
    ]).map { TimeInterval($0) / 1_000.0 }

    /// A finite, strictly-positive threshold in seconds, drawn from 1...3000 ms,
    /// always including the production 800 ms value.
    private static let positiveThresholdSecondsGen: Gen<TimeInterval> = Gen<Int>.frequency([
        (1, Gen.pure(800)),
        (5, Gen<Int>.choose((1, 3_000))),
    ]).map { TimeInterval($0) / 1_000.0 }

    /// A signed offset in milliseconds used to place `elapsed` relative to the
    /// threshold: negative ⇒ before threshold, zero ⇒ exactly at threshold,
    /// positive ⇒ past the threshold. Weighted to hit the exact boundary.
    private static let offsetMsGen: Gen<Int> = Gen<Int>.frequency([
        (2, Gen.pure(0)),                       // exactly at the threshold
        (3, Gen<Int>.choose((-3_000, -1))),     // before the threshold
        (3, Gen<Int>.choose((1, 3_000))),       // past the threshold
    ])

    // MARK: - Property 18 (proportional fill)

    /// Req 8.7 / Property 18: for a finite, non-negative `elapsed` and a finite,
    /// positive `threshold`, the fill proportion equals
    /// `min(max(elapsed / threshold, 0), 1)` and always lies in `[0, 1]`.
    func testFillProportionEqualsClampedRatio() {
        property(
            "fill == clamp(elapsed / threshold, 0, 1) and stays in [0, 1]",
            arguments: Self.checkerArguments
        ) <- forAll(Self.elapsedSecondsGen, Self.positiveThresholdSecondsGen) {
            (elapsed: TimeInterval, threshold: TimeInterval) in

            let actual = Domain.holdFillProportion(elapsed: elapsed, threshold: threshold)
            let expected = min(max(elapsed / threshold, 0), 1)

            let inRange = (actual >= 0) && (actual <= 1)
            return (actual == expected && inRange)
                <?> "elapsed=\(elapsed) threshold=\(threshold) expected=\(expected) got=\(actual)"
        }
    }

    // MARK: - Property 18 (monotonic in elapsed)

    /// Property 18: the fill proportion is non-decreasing in `elapsed` for a
    /// fixed positive threshold — a longer continuous hold never shows a smaller
    /// fill.
    func testFillProportionIsMonotonicInElapsed() {
        // Two finite, non-negative elapsed times; we compare them in both orders.
        let pairGen = Gen.zip(Self.elapsedSecondsGen, Self.elapsedSecondsGen)

        property(
            "elapsed1 <= elapsed2 implies fill(elapsed1) <= fill(elapsed2)",
            arguments: Self.checkerArguments
        ) <- forAll(pairGen, Self.positiveThresholdSecondsGen) {
            (pair: (TimeInterval, TimeInterval), threshold: TimeInterval) in

            let lo = min(pair.0, pair.1)
            let hi = max(pair.0, pair.1)

            let fillLo = Domain.holdFillProportion(elapsed: lo, threshold: threshold)
            let fillHi = Domain.holdFillProportion(elapsed: hi, threshold: threshold)

            return (fillLo <= fillHi)
                <?> "lo=\(lo) hi=\(hi) threshold=\(threshold) fillLo=\(fillLo) fillHi=\(fillHi)"
        }
    }

    // MARK: - Property 18 (full exactly at / after the threshold)

    /// Req 8.7 / Property 18: the fill reaches exactly `1` when `elapsed` is at
    /// or beyond the threshold, and stays strictly below `1` while `elapsed` is
    /// short of the threshold.
    func testFillIsFullExactlyAtOrAfterThreshold() {
        property(
            "fill == 1 iff elapsed >= threshold (for elapsed short of it, fill < 1)",
            arguments: Self.checkerArguments
        ) <- forAll(Self.positiveThresholdSecondsGen, Self.offsetMsGen) {
            (threshold: TimeInterval, offsetMs: Int) in

            let elapsed = threshold + TimeInterval(offsetMs) / 1_000.0
            let fill = Domain.holdFillProportion(elapsed: elapsed, threshold: threshold)

            if offsetMs >= 0 {
                // At or past the threshold: full fill, never overshooting.
                return (fill == 1)
                    <?> "at/after: threshold=\(threshold) offsetMs=\(offsetMs) elapsed=\(elapsed) fill=\(fill)"
            } else {
                // Before the threshold: a partial (or empty) fill, never full.
                return (fill < 1)
                    <?> "before: threshold=\(threshold) offsetMs=\(offsetMs) elapsed=\(elapsed) fill=\(fill)"
            }
        }
    }

    // MARK: - Property 18 (completion iff elapsed >= threshold)

    /// Req 8.7 / 8.9 / Property 18: completion is reached if and only if the
    /// continuous hold has met the threshold. A hold short of the threshold (an
    /// early release) never reports completion, so the status stays unchanged.
    func testCompletionReachedIffElapsedAtLeastThreshold() {
        // A signed elapsed in -1000...4000 ms so early releases (and a zero
        // hold) are represented alongside completed holds.
        let signedElapsedGen: Gen<TimeInterval> = Gen<Int>
            .choose((-1_000, 4_000))
            .map { TimeInterval($0) / 1_000.0 }

        property(
            "holdReachesCompletion == (elapsed >= threshold)",
            arguments: Self.checkerArguments
        ) <- forAll(signedElapsedGen, Self.positiveThresholdSecondsGen) {
            (elapsed: TimeInterval, threshold: TimeInterval) in

            let completed = Domain.holdReachesCompletion(elapsed: elapsed, threshold: threshold)
            let expected = elapsed >= threshold

            return (completed == expected)
                <?> "elapsed=\(elapsed) threshold=\(threshold) expected=\(expected) got=\(completed)"
        }
    }

    /// Req 8.9 / Property 18: a release strictly before the threshold never
    /// completes — the complement of the completion predicate, stated directly
    /// against early releases so the "no status change" guarantee is explicit.
    func testReleaseBeforeThresholdNeverCompletes() {
        // Elapsed strictly less than the threshold by a positive margin.
        let gen = Self.positiveThresholdSecondsGen.flatMap { threshold in
            Gen<Int>.choose((1, 3_000)).map { gapMs -> (TimeInterval, TimeInterval) in
                let elapsed = max(0, threshold - TimeInterval(gapMs) / 1_000.0)
                return (elapsed, threshold)
            }
        }

        property(
            "elapsed < threshold never reaches completion",
            arguments: Self.checkerArguments
        ) <- forAll(gen) { (pair: (TimeInterval, TimeInterval)) in
            let (elapsed, threshold) = pair
            // Only assert for the genuinely-short holds the generator targets.
            guard elapsed < threshold else { return true }

            let completed = Domain.holdReachesCompletion(elapsed: elapsed, threshold: threshold)
            return (completed == false)
                <?> "elapsed=\(elapsed) threshold=\(threshold) unexpectedly completed"
        }
    }

    // MARK: - Degenerate / total-function examples (Property 18)
    //
    // These pin down the documented total-function behavior for inputs the
    // algebraic generators deliberately exclude (negative, zero, non-finite
    // elapsed; non-positive threshold). They assert the *actual* implementation
    // contract in `HoldProgress.swift`.

    /// Default threshold is the 800 ms production constant.
    private let defaultThreshold = Domain.holdCompletionThreshold

    /// A negative elapsed (never a real hold) clamps the fill to empty and never
    /// completes.
    func testNegativeElapsedYieldsEmptyFillAndNoCompletion() {
        XCTAssertEqual(Domain.holdFillProportion(elapsed: -1.0, threshold: defaultThreshold), 0)
        XCTAssertFalse(Domain.holdReachesCompletion(elapsed: -1.0, threshold: defaultThreshold))
    }

    /// A zero hold produces an empty fill and, with a positive threshold, does
    /// not complete.
    func testZeroElapsedYieldsEmptyFillAndNoCompletion() {
        XCTAssertEqual(Domain.holdFillProportion(elapsed: 0, threshold: defaultThreshold), 0)
        XCTAssertFalse(Domain.holdReachesCompletion(elapsed: 0, threshold: defaultThreshold))
    }

    /// Exactly at the default threshold the fill is full and completion fires.
    func testElapsedExactlyAtDefaultThresholdIsFullAndCompletes() {
        XCTAssertEqual(Domain.holdFillProportion(elapsed: defaultThreshold, threshold: defaultThreshold), 1)
        XCTAssertTrue(Domain.holdReachesCompletion(elapsed: defaultThreshold, threshold: defaultThreshold))
    }

    /// A `NaN` hold is treated as no progress and never completes.
    func testNaNElapsedYieldsEmptyFillAndNoCompletion() {
        XCTAssertEqual(Domain.holdFillProportion(elapsed: .nan, threshold: defaultThreshold), 0)
        XCTAssertFalse(Domain.holdReachesCompletion(elapsed: .nan, threshold: defaultThreshold))
    }

    /// A non-finite hold (`±∞`) is not a valid gesture: the fill is clamped to
    /// empty and completion never fires (the `isFinite` guards in
    /// `HoldProgress.swift` short-circuit both functions).
    func testInfiniteElapsedYieldsEmptyFillAndNoCompletion() {
        XCTAssertEqual(Domain.holdFillProportion(elapsed: .infinity, threshold: defaultThreshold), 0)
        XCTAssertEqual(Domain.holdFillProportion(elapsed: -.infinity, threshold: defaultThreshold), 0)
        XCTAssertFalse(Domain.holdReachesCompletion(elapsed: .infinity, threshold: defaultThreshold))
        XCTAssertFalse(Domain.holdReachesCompletion(elapsed: -.infinity, threshold: defaultThreshold))
    }

    /// A non-positive threshold has no meaningful ramp: any positive hold is
    /// immediately full (no divide-by-zero), and completion follows
    /// `elapsed >= threshold`.
    func testNonPositiveThresholdYieldsFullFillForPositiveHold() {
        XCTAssertEqual(Domain.holdFillProportion(elapsed: 0.5, threshold: 0), 1)
        XCTAssertEqual(Domain.holdFillProportion(elapsed: 0.5, threshold: -1), 1)
        // elapsed (0.5) >= threshold (0 or -1) ⇒ completes.
        XCTAssertTrue(Domain.holdReachesCompletion(elapsed: 0.5, threshold: 0))
        XCTAssertTrue(Domain.holdReachesCompletion(elapsed: 0.5, threshold: -1))
        // A zero hold against a zero threshold: empty fill, but 0 >= 0 completes.
        XCTAssertEqual(Domain.holdFillProportion(elapsed: 0, threshold: 0), 0)
        XCTAssertTrue(Domain.holdReachesCompletion(elapsed: 0, threshold: 0))
    }
}
