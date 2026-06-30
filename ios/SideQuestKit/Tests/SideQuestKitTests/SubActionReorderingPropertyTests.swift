import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Reused Property 18 — Reordering sub-actions is a
/// permutation with contiguous ordering** (iOS design "Reused properties"
/// table; sibling `action-tracker-app` Property 18; task 4.16).
///
/// **Validates: Requirements 9.8**
///
/// Property 18 statement (as it applies to the iOS Swift domain logic): *for
/// any* `ActionPlan` and *any* requested ordering, reordering preserves the
/// exact set of sub-actions (none added or lost), yields order indices forming
/// a contiguous `0..<n` sequence in the requested order, and preserves the
/// relative sequence of the sub-actions that were not moved (Req 9.8).
///
/// Subject under test: `Domain.reorder(_:orderedSubActionIds:)` — the pure,
/// portable reorder operation.
///
/// ## Strategy
///
/// Plans are generated with 1...12 sub-actions carrying **unique, stable ids**
/// (`s-0`, `s-1`, …) and randomly varied `completed` flags and `order` fields
/// (deliberately scrambled so the contiguity guarantee is exercised against
/// non-contiguous input). Requested orderings are generated three ways to cover
/// the contract's total behaviour:
///   * arbitrary sublists drawn from the existing ids (with possible duplicates
///     and partial coverage),
///   * lists mixing existing ids with **bogus** ids that match no sub-action,
///   * the empty list (no move requested).
///
/// Each property uses an **independent oracle** that re-expresses the contract
/// from the Property 18 statement rather than mirroring the implementation, and
/// runs ≥100 iterations (the design mandates a minimum of 100; we configure 200
/// for extra coverage).
final class SubActionReorderingPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    // MARK: - Fixtures

    private static let epoch = Date(timeIntervalSince1970: 1_600_000_000)

    // MARK: - Helpers

    /// Builds a plan of `count` sub-actions with unique ids `s-0..s-(count-1)`,
    /// the given completed flags, and scrambled `order` fields (so the input is
    /// not already contiguous in list order).
    private static func makePlan(count: Int, completed: [Bool], scrambledOrders: [Int]) -> ActionPlan {
        let subs = (0..<count).map { i in
            SubAction(
                id: "s-\(i)",
                text: "step-\(i)",
                order: scrambledOrders[i],
                completed: completed[i]
            )
        }
        return ActionPlan(
            id: "plan-1",
            actionItemId: "item-1",
            subActions: subs,
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false, dirty: false)
        )
    }

    /// Independent oracle for the expected final id order from the Property 18
    /// contract: mentioned existing ids first (de-duplicated, first occurrence,
    /// bogus ids dropped), then the remaining sub-actions in their original
    /// relative order.
    private static func expectedIdOrder(_ plan: ActionPlan, _ orderedIds: [String]) -> [String] {
        let existing = Set(plan.subActions.map(\.id))
        var seen = Set<String>()
        var front: [String] = []
        for id in orderedIds where existing.contains(id) {
            if seen.insert(id).inserted { front.append(id) }
        }
        let rest = plan.subActions.map(\.id).filter { !seen.contains($0) }
        return front + rest
    }

    // MARK: - Generators

    /// A plan with unique-id sub-actions, varied completed flags, and scrambled
    /// order fields.
    private static var planGen: Gen<ActionPlan> {
        Gen<Int>.choose((1, 12)).flatMap { count in
            let flagsGen = Gen.sequence(Array(repeating: Gen<Bool>.fromElements(of: [false, true]), count: count))
            let ordersGen = Gen.sequence(Array(repeating: Gen<Int>.choose((-50, 50)), count: count))
            return Gen.zip(flagsGen, ordersGen).map { flags, orders in
                makePlan(count: count, completed: flags, scrambledOrders: orders)
            }
        }
    }

    /// A requested ordering drawn from the plan's existing ids: an arbitrary
    /// length 0...count list, allowing duplicates and partial coverage.
    private static func existingIdsGen(for plan: ActionPlan) -> Gen<[String]> {
        let ids = plan.subActions.map(\.id)
        return Gen<Int>.choose((0, ids.count)).flatMap { len in
            Gen.sequence(Array(repeating: Gen<String>.fromElements(of: ids), count: len))
        }
    }

    /// A requested ordering mixing existing ids with bogus ids that match no
    /// sub-action (must be ignored by the contract).
    private static func mixedIdsGen(for plan: ActionPlan) -> Gen<[String]> {
        let ids = plan.subActions.map(\.id)
        let pool = ids + ["bogus-1", "bogus-2", "missing", ""]
        return Gen<Int>.choose((0, ids.count + 4)).flatMap { len in
            Gen.sequence(Array(repeating: Gen<String>.fromElements(of: pool), count: len))
        }
    }

    /// Plan paired with an arbitrary requested ordering from its existing ids.
    private static var planWithExistingIdsGen: Gen<(ActionPlan, [String])> {
        planGen.flatMap { plan in
            existingIdsGen(for: plan).map { (plan, $0) }
        }
    }

    /// Plan paired with a requested ordering that mixes existing and bogus ids.
    private static var planWithMixedIdsGen: Gen<(ActionPlan, [String])> {
        planGen.flatMap { plan in
            mixedIdsGen(for: plan).map { (plan, $0) }
        }
    }

    // MARK: - Property 18: reorder preserves the exact set of sub-actions (Req 9.8)

    /// The result is a permutation of the original sub-actions: the same set of
    /// ids, the same count, and each sub-action's non-positional fields (`text`,
    /// `completed`) are carried through unchanged — none added, lost, or
    /// corrupted. Holds even when bogus ids are present in the request.
    func testReorderPreservesExactSubActionSet() {
        property("reorder preserves the exact sub-action set (Property 18, Req 9.8)",
                 arguments: Self.checkArgs)
            <- forAll(Self.planWithMixedIdsGen) { (scenario: (ActionPlan, [String])) in
                let (plan, orderedIds) = scenario
                let result = Domain.reorder(plan, orderedSubActionIds: orderedIds)

                let originalById = Dictionary(uniqueKeysWithValues: plan.subActions.map { ($0.id, $0) })
                let sameCount = result.subActions.count == plan.subActions.count
                let sameIdSet = Set(result.subActions.map(\.id)) == Set(plan.subActions.map(\.id))
                // Each surviving sub-action keeps its text/completed (only order changes).
                let fieldsPreserved = result.subActions.allSatisfy { sub in
                    guard let original = originalById[sub.id] else { return false }
                    return sub.text == original.text && sub.completed == original.completed
                }
                return sameCount ^&&^ sameIdSet ^&&^ fieldsPreserved
            }
    }

    // MARK: - Property 18: reorder yields contiguous 0..<n ordering (Req 9.8)

    /// The result's `order` fields form a contiguous `0..<n` sequence in list
    /// order: element at index `i` has `order == i`, regardless of the scrambled
    /// input order fields.
    func testReorderYieldsContiguousOrdering() {
        property("reorder yields contiguous 0..<n order indices (Property 18, Req 9.8)",
                 arguments: Self.checkArgs)
            <- forAll(Self.planWithMixedIdsGen) { (scenario: (ActionPlan, [String])) in
                let (plan, orderedIds) = scenario
                let result = Domain.reorder(plan, orderedSubActionIds: orderedIds)

                let contiguous = result.subActions.enumerated().allSatisfy { index, sub in
                    sub.order == index
                }
                let orderSet = Set(result.subActions.map(\.order))
                let expectedSet = Set(0..<result.subActions.count)
                return contiguous ^&&^ (orderSet == expectedSet)
            }
    }

    // MARK: - Property 18: reorder follows the requested order (Req 9.8)

    /// The final list order matches the contract oracle: requested (existing)
    /// ids first in their requested order (de-duplicated), then the unmentioned
    /// sub-actions preserving their original relative sequence.
    func testReorderFollowsRequestedOrder() {
        property("reorder follows the requested order then preserves the rest (Property 18, Req 9.8)",
                 arguments: Self.checkArgs)
            <- forAll(Self.planWithMixedIdsGen) { (scenario: (ActionPlan, [String])) in
                let (plan, orderedIds) = scenario
                let result = Domain.reorder(plan, orderedSubActionIds: orderedIds)
                let expected = Self.expectedIdOrder(plan, orderedIds)
                return result.subActions.map(\.id) == expected
            }
    }

    // MARK: - Property 18: unmoved sub-actions preserve their relative sequence (Req 9.8)

    /// When only a subset of ids is requested (the "moved" ones), the
    /// sub-actions that were not moved keep their original relative order in the
    /// result — exactly the Req 9.8 wording "preserving the relative sequence of
    /// the sub-actions not moved".
    func testUnmovedSubActionsPreserveRelativeOrder() {
        property("unmoved sub-actions keep their relative order (Property 18, Req 9.8)",
                 arguments: Self.checkArgs)
            <- forAll(Self.planWithExistingIdsGen) { (scenario: (ActionPlan, [String])) in
                let (plan, orderedIds) = scenario
                let result = Domain.reorder(plan, orderedSubActionIds: orderedIds)

                // The set of "moved" ids actually applied (existing + de-duped).
                let existing = Set(plan.subActions.map(\.id))
                var moved = Set<String>()
                for id in orderedIds where existing.contains(id) { moved.insert(id) }

                // Original relative order of the unmoved ids.
                let originalUnmoved = plan.subActions.map(\.id).filter { !moved.contains($0) }
                // Their relative order as it appears in the result.
                let resultUnmoved = result.subActions.map(\.id).filter { !moved.contains($0) }
                return originalUnmoved == resultUnmoved
            }
    }

    // MARK: - Property 18: empty request is a pure reindex preserving order (Req 9.8)

    /// An empty requested ordering moves nothing: the result keeps the original
    /// list order and merely re-establishes contiguous `0..<n` ordering.
    func testEmptyRequestPreservesOriginalOrder() {
        property("empty request preserves original order, reindexed (Property 18, Req 9.8)",
                 arguments: Self.checkArgs)
            <- forAll(Self.planGen) { (plan: ActionPlan) in
                let result = Domain.reorder(plan, orderedSubActionIds: [])
                let sameOrder = result.subActions.map(\.id) == plan.subActions.map(\.id)
                let contiguous = result.subActions.enumerated().allSatisfy { index, sub in
                    sub.order == index
                }
                return sameOrder ^&&^ contiguous
            }
    }

    // MARK: - Property 18: full permutation request realizes that permutation (Req 9.8)

    /// When every id is mentioned exactly once (a true permutation request), the
    /// result is exactly that permutation with contiguous ordering — no item is
    /// dropped and none is appended out of band.
    func testFullPermutationRequestIsRealizedExactly() {
        // Generate a plan and a genuine permutation of its ids (reverse plus a
        // rotation give deterministic, valid permutations under check).
        let scenarioGen: Gen<(ActionPlan, [String])> = Self.planGen.flatMap { plan in
            let ids = plan.subActions.map(\.id)
            return Gen<Int>.choose((0, max(0, ids.count - 1))).map { rotation in
                let reversed = Array(ids.reversed())
                let rotated = Array(reversed[rotation...] + reversed[..<rotation])
                return (plan, rotated)
            }
        }

        property("a full permutation request is realized exactly (Property 18, Req 9.8)",
                 arguments: Self.checkArgs)
            <- forAll(scenarioGen) { (scenario: (ActionPlan, [String])) in
                let (plan, permutation) = scenario
                let result = Domain.reorder(plan, orderedSubActionIds: permutation)
                let idsMatch = result.subActions.map(\.id) == permutation
                let contiguous = result.subActions.enumerated().allSatisfy { index, sub in
                    sub.order == index
                }
                return idsMatch ^&&^ contiguous
            }
    }
}
