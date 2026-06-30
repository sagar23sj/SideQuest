import Foundation

// MARK: - Action-plan progress, prompt, and reorder (Req 9.8, 9.9, 9.10)
//
// Pure, portable action-plan logic. Mirrors the Android client's
// `com.sidequest.domain.plan.ActionPlanOperations` so the iOS Swift
// implementation produces field-by-field, ordering-exact equivalent results
// (Req 3.3, cross-implementation equivalence validated by task 4.19).
//
// Scope of this task (4.13):
//   * Progress — completed-vs-total sub-action counts (Req 9.9, reused
//     Property 16).
//   * The "mark complete" prompt — signalled exactly when a non-empty plan has
//     every sub-action completed (Req 9.10, reused sibling Property 17).
//   * Reorder — a permutation of the sub-actions that preserves the relative
//     order of the sub-actions not moved and reassigns `order` to stay
//     contiguous (Req 9.8, reused Property 18).
//
// Every function here is pure and total: it never mutates its inputs and never
// throws for any input.
//
// ## Ordering convention
// Sub-actions within a plan use **contiguous order indices starting at 0**: a
// plan with `n` sub-actions has `order` values forming the set `0..<n`, and the
// list is kept sorted so element `i` has `order == i`. The reorder operation
// re-establishes this invariant on the value it returns, so callers can rely on
// contiguous, gap-free ordering regardless of the input plan's ordering.

/// The progress of an ``ActionPlan``: how many of its sub-actions are completed
/// out of the total (Req 9.9). Mirrors the Android `Progress` data class.
///
/// The invariant `0 <= completed <= total` always holds for a value produced by
/// ``Domain/progress(_:)`` (reused Property 16): ``completed`` counts completed
/// sub-actions and ``total`` is the number of sub-actions, so ``completed`` can
/// never exceed ``total`` and neither is negative.
public struct ActionPlanProgress: Equatable {

    /// The number of completed sub-actions.
    public let completed: Int

    /// The total number of sub-actions.
    public let total: Int

    public init(completed: Int, total: Int) {
        self.completed = completed
        self.total = total
    }
}

extension Domain {

    /// Computes the ``ActionPlanProgress`` of `plan` (Req 9.9, reused
    /// Property 16): `completed` is the number of completed sub-actions and
    /// `total` is the number of sub-actions, so `0 <= completed <= total`
    /// always holds. Pure and total.
    public static func progress(_ plan: ActionPlan) -> ActionPlanProgress {
        ActionPlanProgress(
            completed: plan.subActions.lazy.filter(\.completed).count,
            total: plan.subActions.count
        )
    }

    /// Whether the UI should prompt the user to mark the parent Action_Item
    /// completed (Req 9.10, reused sibling Property 17).
    ///
    /// Returns `true` iff the plan is **non-empty** and **every** sub-action is
    /// completed. An empty plan returns `false`: there is nothing to have
    /// completed, so no prompt is surfaced. Pure and total.
    public static func shouldPromptParentComplete(_ plan: ActionPlan) -> Bool {
        !plan.subActions.isEmpty && plan.subActions.allSatisfy(\.completed)
    }

    /// Reorders the sub-actions of `plan` to follow `orderedSubActionIds`
    /// (Req 9.8, reused Property 18).
    ///
    /// The result is guaranteed to be a permutation of the existing sub-actions
    /// with contiguous ordering regardless of the input, by this total
    /// contract:
    /// 1. Sub-actions whose ids appear in `orderedSubActionIds` are placed
    ///    first, in the order their ids are listed. Ids that do not match any
    ///    existing sub-action are ignored, and duplicate ids in the list select
    ///    a sub-action only once (first occurrence).
    /// 2. Any existing sub-actions not mentioned in `orderedSubActionIds` are
    ///    appended afterward, preserving their original relative order, so none
    ///    are ever lost and the relative contiguous ordering of the unmoved
    ///    sub-actions is preserved.
    /// 3. The `order` field of every sub-action is reassigned to its final
    ///    position, yielding a contiguous `0..<n` sequence.
    ///
    /// Because step 2 reattaches any omitted sub-actions and no new sub-actions
    /// are introduced, the returned plan always contains exactly the same set
    /// of sub-actions as `plan`. Mirrors the Android
    /// `ActionPlanOperations.reorder`. Pure and total.
    public static func reorder(_ plan: ActionPlan, orderedSubActionIds: [String]) -> ActionPlan {
        let byId = Dictionary(plan.subActions.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })

        var mentioned: Set<String> = []
        var ordered: [SubAction] = []
        for id in orderedSubActionIds {
            if let subAction = byId[id], mentioned.insert(id).inserted {
                ordered.append(subAction)
            }
        }

        // Append existing sub-actions not mentioned, preserving relative order,
        // so the unmoved sub-actions keep their relative sequence (Property 18).
        for subAction in plan.subActions where !mentioned.contains(subAction.id) {
            ordered.append(subAction)
        }

        var result = plan
        result.subActions = reindex(ordered)
        return result
    }

    // MARK: - Helpers

    /// Reassigns each sub-action's `order` to its index in the list, producing a
    /// contiguous `0..<n` ordering in list order. Mirrors the Android
    /// `ActionPlanOperations.reindex`.
    private static func reindex(_ subActions: [SubAction]) -> [SubAction] {
        subActions.enumerated().map { index, subAction in
            var copy = subAction
            copy.order = index
            return copy
        }
    }
}
