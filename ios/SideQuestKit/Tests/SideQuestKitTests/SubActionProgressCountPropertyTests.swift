import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Reused Property 16 — Sub-action progress count is
/// accurate** (iOS design "Reused properties" table; sibling
/// `action-tracker-app` Property 16; task 4.14).
///
/// **Validates: Requirements 9.9**
///
/// Property 16 statement (as it applies to the iOS Swift domain logic): for any
/// `ActionPlan`, the displayed progress is `completed`-of-`total` where
/// `total` is the number of sub-actions and `completed` is the number of
/// sub-actions whose `completed` flag is set, so the invariant
/// `0 <= completed <= total` always holds (Req 9.9).
///
/// Subject under test:
///   * `Domain.progress(_:)` — the pure progress computation that mirrors the
///     Android `ActionPlanOperations.progress`.
///
/// ## Strategy
///
/// Plans are generated with 0...30 sub-actions, each independently flagged
/// completed or not (drawn uniformly), so empty plans, all-incomplete,
/// all-complete, and mixed plans are all common. An independent oracle —
/// `subActions.filter(\.completed).count` and `subActions.count` — expresses the
/// property directly rather than re-deriving the implementation. Each property
/// runs ≥100 iterations (the design mandates a minimum of 100; we configure 200
/// for extra coverage).
final class SubActionProgressCountPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    // MARK: - Fixed pools

    private static let epoch = Date(timeIntervalSince1970: 1_600_000_000)

    // MARK: - Generators

    /// A single sub-action with a varied `completed` flag. The `id`, `text`, and
    /// `order` are placeholders reassigned per index so each sub-action is
    /// distinct and the ordering is the contiguous `0..<n` the model expects.
    private static let completedFlagGen = Gen<Bool>.fromElements(of: [true, false])

    /// A bounded-length plan with 0...30 sub-actions. Lengths include the empty
    /// list so the `total == 0` edge case is exercised.
    private static var planGen: Gen<ActionPlan> {
        Gen<Int>.choose((0, 30)).flatMap { count in
            Gen.sequence(Array(repeating: completedFlagGen, count: count))
        }.map { flags in
            makePlan(completedFlags: flags)
        }
    }

    // MARK: - Helpers

    /// Builds a plan whose sub-actions have the given `completed` flags, with
    /// distinct ids and contiguous `order` values matching their index.
    private static func makePlan(completedFlags: [Bool]) -> ActionPlan {
        let subActions = completedFlags.enumerated().map { index, completed in
            SubAction(id: "sa-\(index)", text: "step \(index)", order: index, completed: completed)
        }
        return ActionPlan(
            id: "plan-1",
            actionItemId: "item-1",
            subActions: subActions,
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false, dirty: false)
        )
    }

    /// Independent oracle for the completed count, expressed directly from the
    /// Property 16 statement.
    private static func completedCount(_ plan: ActionPlan) -> Int {
        plan.subActions.filter { $0.completed }.count
    }

    // MARK: - Property 16: progress equals completed-of-total and is in bounds (Req 9.9)

    /// `progress(_:)` reports `total` equal to the number of sub-actions,
    /// `completed` equal to the number of completed sub-actions, and the
    /// invariant `0 <= completed <= total` always holds.
    func testProgressEqualsCompletedOfTotalAndIsInBounds() {
        property("progress: completed == completed count, total == count, 0 <= completed <= total (Property 16, Req 9.9)",
                 arguments: Self.checkArgs)
            <- forAll(Self.planGen) { (plan: ActionPlan) in
                let progress = Domain.progress(plan)
                let expectedCompleted = Self.completedCount(plan)
                let expectedTotal = plan.subActions.count
                return (progress.completed == expectedCompleted)
                    ^&&^ (progress.total == expectedTotal)
                    ^&&^ (progress.completed >= 0)
                    ^&&^ (progress.completed <= progress.total)
            }
    }

    // MARK: - Property 16: progress is invariant under reordering (Req 9.9)

    /// The progress count depends only on the multiset of `completed` flags, not
    /// on sub-action order: reversing the sub-actions leaves the count unchanged.
    func testProgressInvariantUnderReordering() {
        property("progress is order-independent (Property 16, Req 9.9)",
                 arguments: Self.checkArgs)
            <- forAll(Self.planGen) { (plan: ActionPlan) in
                var reversed = plan
                reversed.subActions = Array(plan.subActions.reversed())
                let original = Domain.progress(plan)
                let flipped = Domain.progress(reversed)
                return (original.completed == flipped.completed)
                    ^&&^ (original.total == flipped.total)
            }
    }

    // MARK: - Property 16: toggling one sub-action changes completed by exactly one (Req 9.9)

    /// Flipping a single sub-action's `completed` flag changes the progress
    /// `completed` count by exactly one in the corresponding direction and never
    /// changes `total`; all other sub-actions are untouched.
    func testTogglingOneSubActionChangesCompletedByExactlyOne() {
        // Generate a non-empty plan plus an index selecting the sub-action to flip.
        let scenarioGen: Gen<(plan: ActionPlan, index: Int)> =
            Gen<Int>.choose((1, 30)).flatMap { count in
                Gen.sequence(Array(repeating: Self.completedFlagGen, count: count))
                    .map(Self.makePlan)
            }.flatMap { plan in
                Gen<Int>.choose((0, plan.subActions.count - 1)).map { (plan, $0) }
            }

        property("toggling one sub-action changes completed by exactly 1, total unchanged (Property 16, Req 9.9)",
                 arguments: Self.checkArgs)
            <- forAll(scenarioGen) { scenario in
                let plan = scenario.plan
                let index = scenario.index
                let before = Domain.progress(plan)

                var flipped = plan
                let wasCompleted = flipped.subActions[index].completed
                flipped.subActions[index].completed = !wasCompleted
                let after = Domain.progress(flipped)

                let expectedDelta = wasCompleted ? -1 : 1
                return ((after.completed - before.completed) == expectedDelta)
                    ^&&^ (after.total == before.total)
            }
    }
}
