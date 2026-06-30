import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Property 17 — "Status-to-color mapping is
/// injective per bucket"** (iOS design Correctness Properties).
///
/// **Validates: Requirements 8.2**
///
/// Property 17 statement (as it applies to the iOS Swift domain logic): for a
/// bucket whose three per-status colors (`notStartedColor`, `inProgressColor`,
/// `completedColor`) are pairwise distinct, the status→color map is injective —
/// `Domain.statusColorsAreInjective(in:) == true`, `Domain.statusColors(in:)`
/// has exactly three distinct values, and each ``ActionStatus`` is sent to its
/// own bucket field. When two or more of the bucket's colors coincide, the map
/// collapses: `statusColorsAreInjective(in:) == false` and `statusColors(in:)`
/// holds fewer than three distinct values.
///
/// Subject under test (see `Sources/SideQuestKit/Domain/StatusColor.swift`):
///   * `Domain.statusColor(for:in:)`
///   * `Domain.statusColors(in:)`
///   * `Domain.statusColorsAreInjective(in:)`
///
/// Buckets are generated two ways, as the task specifies:
///   (a) three colors drawn so they are *guaranteed distinct* — assert the map
///       is injective, sends each status to its own field, and yields three
///       distinct values; and
///   (b) deliberately *colliding* colors (at least two forced equal) — assert
///       the map is not injective and yields fewer than three distinct values.
/// A third property checks the injectivity predicate against an independent
/// oracle over arbitrary (unconstrained) color triples.
///
/// SwiftCheck is configured for **200 successful tests** per property, above
/// the design's minimum of 100 iterations.
final class StatusColorInjectivityPropertyTests: XCTestCase {

    // MARK: - Configuration (design: ≥100 iterations per property)

    private static let checkerArguments = CheckerArguments(
        maxAllowableSuccessfulTests: 200
    )

    private static let epoch = Date(timeIntervalSince1970: 0)

    // MARK: - Color pool

    /// A small pool of raw color strings. Kept short so that, for the
    /// collision generator, drawing three from the pool frequently repeats a
    /// value, and so that the unconstrained generator (oracle property) hits
    /// both injective and non-injective triples often.
    private static let colorPool = [
        "#FF0000", "#00FF00", "#0000FF", "#FFFF00",
        "#FF00FF", "#00FFFF", "#000000", "#FFFFFF"
    ]

    private static let colorGen: Gen<String> = Gen.fromElements(of: colorPool)

    // MARK: - Generators

    /// Three colors guaranteed pairwise distinct: shuffle the pool and take the
    /// first three, so no two can be equal.
    private static let distinctTripleGen: Gen<(String, String, String)> =
        shuffled(colorPool).map { permuted in (permuted[0], permuted[1], permuted[2]) }

    /// Three colors with at least two forced equal, so the map must collapse.
    /// A "collision kind" decides which pair (or all three) share a value;
    /// the remaining color is drawn freely from the pool (it may or may not
    /// also coincide — either way at least two are equal, so injectivity fails).
    private static let collidingTripleGen: Gen<(String, String, String)> =
        Gen.zip(colorGen, colorGen, Gen<Int>.fromElements(in: 0...2))
            .map { shared, other, kind in
                switch kind {
                case 0: return (shared, shared, other)   // notStarted == inProgress
                case 1: return (shared, other, shared)   // notStarted == completed
                default: return (other, shared, shared)  // inProgress == completed
                }
            }

    /// Unconstrained triple: each color drawn independently from the pool. May
    /// be injective or not — used to validate the predicate against the oracle.
    private static let arbitraryTripleGen: Gen<(String, String, String)> =
        Gen.zip(colorGen, colorGen, colorGen)

    /// Builds a bucket carrying the given per-status colors. Non-color fields
    /// are irrelevant to the status→color mapping and held fixed.
    private static func makeBucket(
        notStarted: String,
        inProgress: String,
        completed: String
    ) -> Bucket {
        Bucket(
            id: "bucket",
            accountId: "acct",
            name: "Bucket",
            notStartedColor: notStarted,
            inProgressColor: inProgress,
            completedColor: completed,
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false)
        )
    }

    /// A Fisher–Yates shuffle expressed as a `Gen`, so distinct-triple draws
    /// vary across iterations while never repeating an element.
    private static func shuffled(_ elements: [String]) -> Gen<[String]> {
        guard elements.count > 1 else { return Gen.pure(elements) }
        return Gen<Int>.choose((0, elements.count - 1)).flatMap { index in
            var rest = elements
            let picked = rest.remove(at: index)
            return shuffled(rest).map { [picked] + $0 }
        }
    }

    // MARK: - Property 17 (a): distinct colors ⇒ injective (Req 8.2)

    /// For a bucket whose three colors are pairwise distinct, the map is
    /// injective: `statusColorsAreInjective` is `true`, `statusColors(in:)` has
    /// three distinct values, and each status maps to its own bucket field.
    func testDistinctColorsAreInjective() {
        property(
            "distinct per-status colors ⇒ injective map (Property 17, Req 8.2)",
            arguments: Self.checkerArguments
        ) <- forAll(Self.distinctTripleGen) { (triple: (String, String, String)) in
            let (notStarted, inProgress, completed) = triple
            let bucket = Self.makeBucket(
                notStarted: notStarted,
                inProgress: inProgress,
                completed: completed
            )

            let injective = Domain.statusColorsAreInjective(in: bucket)
            let map = Domain.statusColors(in: bucket)
            let distinctValueCount = Set(map.values).count

            // Each status is sent to its own bucket field.
            let mapsToOwnField =
                Domain.statusColor(for: .notStarted, in: bucket) == notStarted
                && Domain.statusColor(for: .inProgress, in: bucket) == inProgress
                && Domain.statusColor(for: .completed, in: bucket) == completed
                && map[.notStarted] == notStarted
                && map[.inProgress] == inProgress
                && map[.completed] == completed

            return (injective == true)
                ^&&^ (distinctValueCount == 3)
                ^&&^ (map.count == 3)
                ^&&^ mapsToOwnField
                <?> "distinct colors=\(triple) injective=\(injective) distinct=\(distinctValueCount)"
        }
    }

    // MARK: - Property 17 (b): colliding colors ⇒ not injective (Req 8.2)

    /// For a bucket where at least two per-status colors coincide, the map is
    /// not injective: `statusColorsAreInjective` is `false` and
    /// `statusColors(in:)` has fewer than three distinct values.
    func testCollidingColorsAreNotInjective() {
        property(
            "colliding per-status colors ⇒ non-injective map (Property 17, Req 8.2)",
            arguments: Self.checkerArguments
        ) <- forAll(Self.collidingTripleGen) { (triple: (String, String, String)) in
            let (notStarted, inProgress, completed) = triple
            let bucket = Self.makeBucket(
                notStarted: notStarted,
                inProgress: inProgress,
                completed: completed
            )

            let injective = Domain.statusColorsAreInjective(in: bucket)
            let distinctValueCount = Set(Domain.statusColors(in: bucket).values).count

            return (injective == false)
                ^&&^ (distinctValueCount < 3)
                <?> "colliding colors=\(triple) injective=\(injective) distinct=\(distinctValueCount)"
        }
    }

    // MARK: - Property 17: predicate agrees with an independent oracle (Req 8.2)

    /// Over arbitrary (unconstrained) color triples, `statusColorsAreInjective`
    /// is `true` exactly when the three colors are pairwise distinct, and the
    /// count of distinct values in `statusColors(in:)` equals the size of the
    /// set of the three raw colors.
    func testInjectivityMatchesDistinctnessOracle() {
        property(
            "injectivity ⇔ three colors pairwise distinct (Property 17, Req 8.2)",
            arguments: Self.checkerArguments
        ) <- forAll(Self.arbitraryTripleGen) { (triple: (String, String, String)) in
            let (notStarted, inProgress, completed) = triple
            let bucket = Self.makeBucket(
                notStarted: notStarted,
                inProgress: inProgress,
                completed: completed
            )

            // Independent oracle: distinct iff the set of the three colors has
            // size three.
            let expectedDistinctCount = Set([notStarted, inProgress, completed]).count
            let expectedInjective = expectedDistinctCount == 3

            let injective = Domain.statusColorsAreInjective(in: bucket)
            let distinctValueCount = Set(Domain.statusColors(in: bucket).values).count

            return (injective == expectedInjective)
                ^&&^ (distinctValueCount == expectedDistinctCount)
                <?> "colors=\(triple) injective=\(injective) expected=\(expectedInjective)"
        }
    }
}
