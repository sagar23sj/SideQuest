package com.actiontracker.ui.detail

import com.actiontracker.domain.model.SubAction
import com.actiontracker.domain.plan.Progress

/**
 * Screen state for the Action_Item detail / Action_Plan view, exposed as a
 * single immutable value from [ItemDetailViewModel].
 *
 * The detail screen renders the item's Action_Plan: its ordered sub-actions
 * with completion toggles (Req 9.2), the add-sub-action input (Req 9.1), the
 * progress "completed / total" display (Req 9.3), reorder controls (Req 9.5),
 * and the parent-complete prompt (Req 9.4). Until the first plan state arrives
 * from the Room-backed flow the screen shows [Loading]; thereafter [Ready]
 * carries the live plan view, which re-emits whenever a sub-action is added,
 * toggled, or reordered.
 */
sealed interface ItemDetailUiState {

    /** The plan is being loaded from local storage. */
    data object Loading : ItemDetailUiState

    /**
     * The live Action_Plan view is available.
     *
     * @property subActions the plan's sub-actions in display order; empty when
     *   the item has no plan yet or its plan has no steps.
     * @property progress the completed / total counts for the progress display
     *   (Req 9.3).
     * @property showParentCompletePrompt whether to surface the prompt to mark
     *   the parent Action_Item completed — true iff the plan is non-empty and
     *   every sub-action is completed (Req 9.4).
     */
    data class Ready(
        val subActions: List<SubAction>,
        val progress: Progress,
        val showParentCompletePrompt: Boolean,
    ) : ItemDetailUiState
}
