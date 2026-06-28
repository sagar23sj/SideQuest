package com.sidequest.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.data.local.dao.ActionItemDao
import com.sidequest.data.local.dao.BucketDao
import com.sidequest.data.local.entity.toDomain
import com.sidequest.data.repository.ActionPlanRepository
import com.sidequest.data.repository.BoardRepository
import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ActionPlan
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.TaskReminder
import com.sidequest.domain.plan.ActionPlanOperations
import com.sidequest.domain.plan.Progress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val boardRepository: BoardRepository,
    private val actionItemDao: ActionItemDao,
    private val bucketDao: BucketDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val actionItemId = MutableStateFlow(
        savedStateHandle.get<String>(KEY_ACTION_ITEM_ID),
    )

    /** Re-reads the parent item (for its reminder) whenever it may have changed. */
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<ItemDetailUiState> =
        actionItemId
            .flatMapLatest { id ->
                if (id == null) {
                    kotlinx.coroutines.flow.flowOf<ItemDetailUiState>(ItemDetailUiState.Loading)
                } else {
                    combine(planRepository.observePlan(id), refresh) { plan, _ ->
                        val item = actionItemDao.getById(id)?.toDomain()
                        val bucket = item?.let { bucketDao.getById(it.bucketId) }
                        plan.toUiState(item, bucket?.name, bucket?.imageRef)
                    }
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
     * Sets or clears the per-task reminder for the current item (Req 6.2,
     * 6.5–6.8), then triggers a refresh so the detail view reflects it.
     */
    fun onSetReminder(reminder: TaskReminder?) {
        val id = actionItemId.value ?: return
        viewModelScope.launch {
            boardRepository.setReminder(id, reminder)
            refresh.value += 1
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
     * Reverts a completed Action_Item back to "not started" (undo). The detail
     * header reflects the change after the [refresh] re-read.
     */
    fun onUndoComplete() {
        val id = actionItemId.value ?: return
        viewModelScope.launch {
            boardRepository.changeStatus(id, ActionStatus.NOT_STARTED)
            refresh.value += 1
        }
    }

    /**
     * Projects a (possibly null) [ActionPlan] into the [ItemDetailUiState.Ready]
     * view, deriving the progress counts and the parent-complete prompt flag
     * from the pure domain operations. A null plan (no steps yet) yields an
     * empty, zero-progress view with no prompt.
     */
    private fun ActionPlan?.toUiState(item: ActionItem?, bucketName: String?, bucketImageRef: String?): ItemDetailUiState =
        if (this == null) {
            ItemDetailUiState.Ready(
                subActions = emptyList(),
                progress = Progress(completed = 0, total = 0),
                showParentCompletePrompt = false,
                reminder = item?.reminder,
                item = item,
                bucketName = bucketName,
                bucketImageRef = bucketImageRef,
            )
        } else {
            ItemDetailUiState.Ready(
                subActions = subActions.sortedBy { it.order },
                progress = ActionPlanOperations.progress(this),
                showParentCompletePrompt =
                    ActionPlanOperations.shouldPromptParentComplete(this),
                reminder = item?.reminder,
                item = item,
                bucketName = bucketName,
                bucketImageRef = bucketImageRef,
            )
        }

    companion object {
        /** SavedStateHandle / navigation-argument key for the viewed item id. */
        const val KEY_ACTION_ITEM_ID = "actionItemId"

        /** Keep the upstream flow alive briefly across config changes. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
