package com.actiontracker.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actiontracker.data.repository.ActionPlanRepository
import com.actiontracker.domain.model.ActionPlan
import com.actiontracker.domain.plan.ActionPlanOperations
import com.actiontracker.domain.plan.Progress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the Action_Item detail / Action_Plan screen (Req 9.1–9.5).
 *
 * The view model observes the item's live [ActionPlan] from
 * [ActionPlanRepository.observePlan] and projects it into an immutable
 * [StateFlow] of [ItemDetailUiState], deriving the progress counts (Req 9.3)
 * and the parent-complete prompt flag (Req 9.4) through the pure
 * [ActionPlanOperations]. Because the repository's flow is Room-backed, the
 * screen re-renders whenever a sub-action is added, toggled, or reordered. The
 * composables are stateless and emit intents back through [onAddSubAction],
 * [onToggleSubAction], [onReorder], and [onMarkParentComplete].
 *
 * ## Action item id
 * The id of the Action_Item being viewed is read from [SavedStateHandle] under
 * [KEY_ACTION_ITEM_ID]. There is no navigation graph yet (Board → detail wiring
 * is future work), so callers without a route can seed the id with
 * [setActionItemId]. Until an id is set the screen stays in
 * [ItemDetailUiState.Loading].
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val planRepository: ActionPlanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * The current Action_Item id. Seeded from any navigation argument passed via
     * [SavedStateHandle]; otherwise null until [setActionItemId] is called.
     */
    private val actionItemId = MutableStateFlow(
        savedStateHandle.get<String>(KEY_ACTION_ITEM_ID),
    )

    /**
     * The live detail state. Switches plans reactively whenever the observed
     * Action_Item id changes, and re-emits whenever the underlying plan changes
     * in Room. Stays [ItemDetailUiState.Loading] while no id is set.
     */
    val uiState: StateFlow<ItemDetailUiState> =
        actionItemId
            .flatMapLatest { id ->
                if (id == null) {
                    kotlinx.coroutines.flow.flowOf<ItemDetailUiState>(ItemDetailUiState.Loading)
                } else {
                    planRepository.observePlan(id).map { plan -> plan.toUiState() }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ItemDetailUiState.Loading,
            )

    /**
     * Sets the Action_Item whose plan this screen shows. Used until a navigation
     * graph passes the id as a route argument.
     */
    fun setActionItemId(id: String) {
        actionItemId.value = id
    }

    /**
     * Adds a new sub-action with [text] to the current item's plan (Req 9.1),
     * creating the plan if it does not exist yet. Blank text is ignored so the
     * input cannot create empty steps.
     */
    fun onAddSubAction(text: String) {
        val id = actionItemId.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            planRepository.addSubAction(id, trimmed)
        }
    }

    /**
     * Toggles the completion of the sub-action [subActionId] to [completed]
     * (Req 9.2). The progress display and the parent-complete prompt update
     * reactively via [uiState].
     */
    fun onToggleSubAction(subActionId: String, completed: Boolean) {
        val id = actionItemId.value ?: return
        viewModelScope.launch {
            planRepository.setSubActionCompleted(id, subActionId, completed)
        }
    }

    /**
     * Reorders the current item's sub-actions to follow [orderedIds] (Req 9.5).
     * The screen builds the new id order from move-up / move-down controls and
     * passes it here; the repository persists the resulting permutation.
     */
    fun onReorder(orderedIds: List<String>) {
        val id = actionItemId.value ?: return
        viewModelScope.launch {
            planRepository.reorderSubActions(id, orderedIds)
        }
    }

    /**
     * Marks the parent Action_Item completed (Req 9.4) in response to the
     * all-sub-actions-done prompt.
     */
    fun onMarkParentComplete() {
        val id = actionItemId.value ?: return
        viewModelScope.launch {
            planRepository.markParentComplete(id)
        }
    }

    /**
     * Projects a (possibly null) [ActionPlan] into the [ItemDetailUiState.Ready]
     * view, deriving the progress counts and the parent-complete prompt flag
     * from the pure domain operations. A null plan (no steps yet) yields an
     * empty, zero-progress view with no prompt.
     */
    private fun ActionPlan?.toUiState(): ItemDetailUiState =
        if (this == null) {
            ItemDetailUiState.Ready(
                subActions = emptyList(),
                progress = Progress(completed = 0, total = 0),
                showParentCompletePrompt = false,
            )
        } else {
            ItemDetailUiState.Ready(
                subActions = subActions.sortedBy { it.order },
                progress = ActionPlanOperations.progress(this),
                showParentCompletePrompt =
                    ActionPlanOperations.shouldPromptParentComplete(this),
            )
        }

    companion object {
        /** SavedStateHandle / navigation-argument key for the viewed item id. */
        const val KEY_ACTION_ITEM_ID = "actionItemId"

        /** Keep the upstream flow alive briefly across config changes. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
