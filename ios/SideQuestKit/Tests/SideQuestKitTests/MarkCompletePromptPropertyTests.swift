import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Reused Property 17 — "Mark complete" prompt
/// appears exactly when all sub-actions done** (iOS design "Reused properties"
/// table; sibling `action-tracker-app` Property 17; task 4.15).
///
/// **Validates: Requirements 9.10**
///
/// Property 17 statement (as it applies to the iOS Swift domain logic): for any
/// `ActionPlan`, the "mark complete" prompt is signalled — that is,
/// `Domain.shouldPromptParentComplete(_:) == true` — *exactly when* the plan is
/// non-empty and **every** sub-action is completed (Req 9.10). Equivalently, the
/// prompt is suppressed for an empty plan and for any plan that still has at
/// least one incomplete sub-action.
///
/// Subject under test (see `Sources/SideQuestKit/Domain/ActionPlanOperations.swift`):
///   * `Domain.shouldPromptParentComplete(_:)`
///
/// ## Strategy
///
/// Plans are generated with randomly varied sub-action lists, including the
/// empty list (so the empty edge case is exercised) and lists where each
/// sub-action's `completed` flag is chosen independently (so both all-complete
/// and partially-complete plans are common). An independent oracle —
/// `!subActions.isEmpty && subActions.allSatisfy(\.completed)` — expresses the
/// property directly from the Req 9.10 statement rather than mirroring the
/// implementation. Each property runs ≥100 iterations (the design mandates a
/// minimum of 100; we configure 200 for extra coverage).
final class MarkCompletePromptPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    // MARK: - Fixed pools

    private static let epoch = Date(timeIntervalSince1970: 1_600_000_000)

    // MARK: - Generators

    /// A single `SubAction` with an independently chosen `completed` flag. The
    /// `id`/`order` are placeholders reassigned per-index by ``assignIdsAndOrder``
    /// so the plan has stable, distinct ids and contiguous ordering.
    private static let subActionGen: Gen<SubAction> =
        Gen<Bool>.fromElements(of: [true, false]).map { completed in
            SubAction(id: "tmp", text: "step", order: 0, completed: completed)
        }

    /// A bounded-length list of sub-actions with distinct ids and contiguous
    /// `order`. Lengths include 0 so the empty-plan edge case is exercised.
    private static var subActionListGen: Gen<[SubAction]> {
        Gen<Int>.choose((0, 12)).flatMap { count in
            Gen.sequence(Array(repeating: subActionGen, count: count))
        }.map(assignIdsAndOrder)
    }

    /// A list of sub-actions guaranteed **non-empty** (1...12), used to probe
    /// the "all completed" and "at least one incomplete" branches directly.
    private static var nonEmptySubActionListGen: Gen<[SubAction]> {
        Gen<Int>.choose((1, 12)).flatMap { count in
            Gen.sequence(Array(repeating: subActionGen, count: count))
        }.map(assignIdsAndOrder)
    }

    /// Wraps a sub-action list into an `ActionPlan`.
    private static func makePlan(_ subActions: [SubAction]) -> ActionPlan {
        ActionPlan(
            id: "plan-1",
            actionItemId: "item-1",
            subActions: subActions,
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false, dirty: false)
        )
    }

    /// Re-keys sub-actions with unique, stable ids and contiguous order so a
    /// plan is well-formed (distinct ids, `order == index`).
    private static func assignIdsAndOrder(_ subActions: [SubAction]) -> [SubAction] {
        subActions.enumerated().map { index, subAction in
            var copy = subAction
            copy.id = "s-\(index)"
            copy.order = index
            return copy
        }
    }

    /// Independent oracle for the prompt, expressed directly from the Req 9.10
    /// statement: prompt iff the plan is non-empty and every sub-action is done.
    private static func shouldPromptOracle(_ plan: ActionPlan) -> Bool {
        !plan.subActions.isEmpty && plan.subActions.allSatisfy(\.completed)
    }

    // MARK: - Property 17: prompt matches the oracle for arbitrary plans (Req 9.10)

    /// For any plan, `shouldPromptParentComplete` agrees with the independent
    /// oracle "non-empty and all sub-actions completed".
    func testPromptMatchesOracle() {
        property("shouldPromptParentComplete == (non-empty && all completed) (Property 17, Req 9.10)",
                 arguments: Self.checkArgs)
            <- forAll(Self.subActionListGen) { (subActions: [SubAction]) in
                let plan = Self.makePlan(subActions)
                return Domain.shouldPromptParentComplete(plan) == Self.shouldPromptOracle(plan)
            }
    }

    // MARK: - Property 17: an empty plan never prompts (Req 9.10)

    /// A plan with no sub-actions never signals the prompt: there is nothing to
    /// have completed.
    func testEmptyPlanNeverPrompts() {
        let emptyPlan = Self.makePlan([])
        XCTAssertFalse(Domain.shouldPromptParentComplete(emptyPlan))
    }

    // MARK: - Property 17: a fully completed non-empty plan always prompts (Req 9.10)

    /// When every sub-action of a non-empty plan is completed, the prompt is
    /// always signalled, regardless of the number of sub-actions.
    func testAllCompletedNonEmptyPlanAlwaysPrompts() {
        property("all completed && non-empty => prompt (Property 17, Req 9.10)",
                 arguments: Self.checkArgs)
            <- forAll(Self.nonEmptySubActionListGen) { (subActions: [SubAction]) in
                let completed = subActions.map { sub -> SubAction in
                    var copy = sub
                    copy.completed = true
                    return copy
                }
                return Domain.shouldPromptParentComplete(Self.makePlan(completed)) == true
            }
    }

    // MARK: - Property 17: a plan with any incomplete sub-action never prompts (Req 9.10)

    /// If at least one sub-action is incomplete, the prompt is suppressed no
    /// matter how many of the others are completed. We force the first
    /// sub-action to be incomplete and assert no prompt.
    func testPlanWithAnIncompleteSubActionNeverPrompts() {
        property("exists incomplete => no prompt (Property 17, Req 9.10)",
                 arguments: Self.checkArgs)
            <- forAll(Self.nonEmptySubActionListGen) { (subActions: [SubAction]) in
                var withIncomplete = subActions
                withIncomplete[0].completed = false
                return Domain.shouldPromptParentComplete(Self.makePlan(withIncomplete)) == false
            }
    }

    // MARK: - Property 17: completing the last remaining sub-action flips the prompt on (Req 9.10)

    /// Starting from an all-completed non-empty plan with exactly one chosen
    /// sub-action toggled incomplete, the prompt is off; completing that last
    /// sub-action turns the prompt on. This exercises the boundary at which the
    /// prompt appears.
    func testCompletingLastSubActionFlipsPromptOn() {
        let scenarioGen: Gen<(subActions: [SubAction], index: Int)> =
            Self.nonEmptySubActionListGen.flatMap { subActions in
                Gen<Int>.choose((0, subActions.count - 1)).map { (subActions, $0) }
            }

        property("completing the final incomplete sub-action turns the prompt on (Property 17, Req 9.10)",
                 arguments: Self.checkArgs)
            <- forAll(scenarioGen) { scenario in
                // Build an all-completed plan, then mark exactly one incomplete.
                var subActions = scenario.subActions.map { sub -> SubAction in
                    var copy = sub
                    copy.completed = true
                    return copy
                }
                subActions[scenario.index].completed = false

                let beforePrompt = Domain.shouldPromptParentComplete(Self.makePlan(subActions))

                subActions[scenario.index].completed = true
                let afterPrompt = Domain.shouldPromptParentComplete(Self.makePlan(subActions))

                return (beforePrompt == false) ^&&^ (afterPrompt == true)
            }
    }
}
