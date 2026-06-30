import Foundation
import SideQuestKit

/// Drives the action-plan screen (Req 9.8, 9.9, 9.10).
///
/// An Action_Plan breaks an `ActionItem` into 1...100 ordered sub-actions
/// (Req 9.8). This view model owns an editable draft of those sub-actions,
/// seeded once from the persisted plan (task 6.1's `ActionPlanRepository`). It
/// supports adding, editing, completing, reordering (via the portable
/// `Domain.reorder`, task 4.13), and removing sub-actions; it surfaces the
/// completed/total progress (`Domain.progress`) for display (Req 9.9); and it
/// raises the "mark the item completed" prompt exactly when every sub-action is
/// done (`Domain.shouldPromptParentComplete`, Req 9.10).
///
/// Edits are batched into the draft and committed on ``save()`` so partial text
/// entry never persists; a commit failure leaves the prior persisted state
/// intact and surfaces a "not saved" indication (Req 5.8).
@MainActor
final class ActionPlanViewModel: ObservableObject {

    /// The smallest and largest number of sub-actions an Action_Plan may hold
    /// (Req 9.8).
    static let minSubActions = 1
    static let maxSubActions = 100

    /// The editable, ordered sub-actions. Bound directly by the view for inline
    /// text editing; structural changes go through the methods below so the
    /// ordering and completion-prompt invariants are maintained.
    @Published var subActions: [SubAction]

    /// The text of the not-yet-added sub-action in the "add" field.
    @Published var newSubActionText: String = ""

    /// A "not saved" indication shown when a repository write fails (Req 5.8).
    @Published var errorMessage: String?

    /// Drives the "all steps done — mark the item completed?" prompt (Req 9.10).
    /// Set when the draft transitions to all-sub-actions-completed.
    @Published var showCompletionPrompt: Bool = false

    /// Flips to `true` once the parent item has been marked completed, so the
    /// host can reflect/dismiss.
    @Published private(set) var didMarkItemCompleted = false

    let item: ActionItem
    private let planRepository: ActionPlanRepository
    private let itemRepository: ActionItemRepository

    /// The id used when persisting the plan: the existing plan's id, or a fresh
    /// client-generated id (Req 5.7) for a plan created on first save. Stable
    /// across the editing session so create-then-edit targets one row.
    private let planId: String
    private let existingSync: SyncMeta?
    private let existedOnLoad: Bool

    init(
        item: ActionItem,
        planRepository: ActionPlanRepository,
        itemRepository: ActionItemRepository
    ) {
        self.item = item
        self.planRepository = planRepository
        self.itemRepository = itemRepository

        let existing = (try? planRepository.plan(forItem: item.id)) ?? nil
        self.existedOnLoad = existing != nil
        self.planId = existing?.id ?? planRepository.newIdentifier()
        self.existingSync = existing?.sync
        self.subActions = (existing?.subActions ?? []).sorted { $0.order < $1.order }
    }

    // MARK: - Derived state (Req 9.9, 9.10)

    /// The plan the current draft represents, used to drive the portable domain
    /// logic and (on save) persistence.
    private var currentPlan: ActionPlan {
        ActionPlan(
            id: planId,
            actionItemId: item.id,
            subActions: subActions,
            sync: existingSync ?? SyncMeta(updatedAt: Date(), version: 0, deleted: false, dirty: false)
        )
    }

    /// Completed-vs-total counts for display (Req 9.9). `0 <= completed <= total`
    /// always holds (reused Property 16).
    var progress: ActionPlanProgress {
        Domain.progress(currentPlan)
    }

    /// Whether another sub-action may be added (cap of 100 — Req 9.8).
    var canAddSubAction: Bool {
        subActions.count < Self.maxSubActions
            && !newSubActionText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// Whether the draft can be saved: between 1 and 100 sub-actions (Req 9.8).
    var canSave: Bool {
        (Self.minSubActions...Self.maxSubActions).contains(subActions.count)
    }

    // MARK: - Editing (Req 9.8)

    /// Appends the text in the add-field as a new, not-completed sub-action at
    /// the end of the order (Req 9.8). No-op past the 100-sub-action cap or for
    /// blank text.
    func addSubAction() {
        let text = newSubActionText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, subActions.count < Self.maxSubActions else { return }

        subActions.append(
            SubAction(
                id: planRepository.newIdentifier(),
                text: text,
                order: subActions.count,
                completed: false
            )
        )
        newSubActionText = ""
    }

    /// Toggles the completed state of the sub-action with `id` (Req 9.8), then
    /// re-evaluates whether to prompt to complete the parent item (Req 9.10).
    func toggleCompleted(id: String) {
        guard let index = subActions.firstIndex(where: { $0.id == id }) else { return }
        subActions[index].completed.toggle()
        refreshCompletionPrompt()
    }

    /// Removes the sub-actions at `offsets` and re-establishes contiguous order
    /// (Req 9.8).
    func deleteSubActions(at offsets: IndexSet) {
        subActions.remove(atOffsets: offsets)
        reindex()
        refreshCompletionPrompt()
    }

    /// Reorders the dragged sub-actions to a new position (Req 9.8).
    ///
    /// The move is resolved through the portable `Domain.reorder`, so the result
    /// is a permutation that preserves the relative order of the sub-actions not
    /// moved and reassigns `order` to stay contiguous (reused Property 18).
    func moveSubActions(from source: IndexSet, to destination: Int) {
        var reordered = subActions
        reordered.move(fromOffsets: source, toOffset: destination)
        subActions = Domain.reorder(
            currentPlan,
            orderedSubActionIds: reordered.map(\.id)
        ).subActions
    }

    // MARK: - Completion prompt (Req 9.10)

    /// Sets ``showCompletionPrompt`` when the draft is non-empty and every
    /// sub-action is completed, and the item is not already completed
    /// (Req 9.10). Uses the portable predicate so the prompt fires under the
    /// exact same condition as the Android client (reused sibling Property 17).
    private func refreshCompletionPrompt() {
        if Domain.shouldPromptParentComplete(currentPlan), item.status != .completed {
            showCompletionPrompt = true
        }
    }

    // MARK: - Persistence (Req 5.8)

    /// Persists the draft plan (Req 9.8). Creates the plan on first save, edits
    /// it thereafter, replacing its sub-actions to match the draft. On a commit
    /// failure the store is unchanged and ``errorMessage`` surfaces the "not
    /// saved" indication while the draft is retained for retry (Req 5.8).
    @discardableResult
    func save() -> Bool {
        guard canSave else { return false }
        do {
            if existedOnLoad {
                try planRepository.update(currentPlan)
            } else {
                try planRepository.create(currentPlan)
            }
            errorMessage = nil
            return true
        } catch {
            errorMessage = "Couldn’t save the plan. Your changes weren’t saved — please try again."
            return false
        }
    }

    /// Confirms the completion prompt (Req 9.10): persists the plan and marks
    /// the parent item completed through the repository. On a commit failure the
    /// store is unchanged and ``errorMessage`` is surfaced (Req 5.8).
    func markItemCompleted() {
        showCompletionPrompt = false
        guard save() else { return }

        var updated = item
        updated.status = .completed
        do {
            try itemRepository.update(updated)
            errorMessage = nil
            didMarkItemCompleted = true
        } catch {
            errorMessage = "Couldn’t mark the item completed — please try again."
        }
    }

    /// Dismisses the completion prompt without changing the item's status
    /// (Req 9.10 — the prompt is an offer, not an automatic transition).
    func dismissCompletionPrompt() {
        showCompletionPrompt = false
    }

    /// Clears the error indication once acknowledged.
    func dismissError() {
        errorMessage = nil
    }

    // MARK: - Helpers

    /// Reassigns each sub-action's `order` to its index so the draft keeps a
    /// contiguous `0..<n` ordering after a removal.
    private func reindex() {
        for index in subActions.indices {
            subActions[index].order = index
        }
    }
}
