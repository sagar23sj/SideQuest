package com.sidequest.ui.voice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.data.repository.BucketRepository
import com.sidequest.data.repository.VoiceJournalRepository
import com.sidequest.domain.llm.ExtractedAction
import com.sidequest.domain.model.Bucket
import com.sidequest.domain.model.Timeframe
import com.sidequest.domain.voice.ConfirmedExtraction
import com.sidequest.ui.capture.CurrentAccountProvider
import com.sidequest.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A single suggested action the user can confirm, plus its selected state and
 * chosen bucket. Timeframe defaults to "today" to keep the review flow fast;
 * the user can re-categorize on the board afterward.
 *
 * @property action the LLM-extracted candidate (Req 10.5).
 * @property selected whether the user has ticked this action for creation.
 * @property bucketId the bucket chosen for this action (defaults to the first
 *   available bucket, or a name-matched suggestion when present).
 */
data class ReviewAction(
    val action: ExtractedAction,
    val selected: Boolean,
    val bucketId: String?,
)

/**
 * State for the voice-journal review screen (Req 10.5–10.7).
 *
 * @property loading true while the transcript is loaded and extraction runs.
 * @property transcript the entry transcript, or null if none yet.
 * @property actions the extracted candidate actions with their selection state.
 * @property suggestionsUnavailable true when the LLM was unavailable and no
 *   suggestions could be produced (fail-soft, Req 10.5).
 * @property buckets the account's buckets, offered as the per-action target.
 * @property done set true once confirmed items are created so the screen pops.
 */
data class VoiceReviewUiState(
    val loading: Boolean = true,
    val transcript: String? = null,
    val audioRef: String? = null,
    val actions: List<ReviewAction> = emptyList(),
    val suggestionsUnavailable: Boolean = false,
    val buckets: List<Bucket> = emptyList(),
    val done: Boolean = false,
)

/**
 * Drives the voice-journal transcript review and extracted-action confirmation
 * flow (Req 10.5–10.7).
 *
 * On open it loads the entry's buckets, then requests LLM action extraction for
 * the entry's transcript (Req 10.5). The extracted candidates are presented for
 * confirmation and create nothing on their own (Req 10.6); only the user-ticked
 * subset — each paired with a chosen bucket and a default "today" timeframe — is
 * turned into Action_Items via [VoiceJournalRepository.confirmExtractedActions]
 * (Req 10.7). Extraction is fail-soft: an unavailable LLM yields an empty list
 * with [VoiceReviewUiState.suggestionsUnavailable] rather than an error.
 */
@HiltViewModel
class VoiceReviewViewModel @Inject constructor(
    private val voiceRepository: VoiceJournalRepository,
    private val bucketRepository: BucketRepository,
    private val accountProvider: CurrentAccountProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val entryId: String? = savedStateHandle[Routes.VOICE_REVIEW_ARG]

    private val _uiState = MutableStateFlow(VoiceReviewUiState())
    val uiState: StateFlow<VoiceReviewUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val id = entryId
        if (id == null) {
            _uiState.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            val accountId = accountProvider.currentAccountId()
            val buckets = bucketRepository.observeBuckets(accountId).first()
            val entry = voiceRepository.observeEntries(accountId).first()
                .firstOrNull { it.id == id }
            val outcome = voiceRepository.requestExtraction(id)

            val defaultBucketId = buckets.firstOrNull()?.id
            val actions = (outcome?.values ?: emptyList()).map { extracted ->
                ReviewAction(
                    action = extracted,
                    selected = true,
                    bucketId = matchBucket(extracted, buckets)?.id ?: defaultBucketId,
                )
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    transcript = entry?.transcript,
                    audioRef = entry?.audioRef,
                    actions = actions,
                    suggestionsUnavailable = outcome?.unavailable ?: false,
                    buckets = buckets,
                )
            }
        }
    }

    /** Toggles whether the suggested action at [index] is selected for creation. */
    fun toggleSelected(index: Int) = _uiState.update { state ->
        val updated = state.actions.toMutableList()
        updated[index] = updated[index].copy(selected = !updated[index].selected)
        state.copy(actions = updated)
    }

    /** Assigns the bucket for the suggested action at [index]. */
    fun setBucket(index: Int, bucketId: String) = _uiState.update { state ->
        val updated = state.actions.toMutableList()
        updated[index] = updated[index].copy(bucketId = bucketId)
        state.copy(actions = updated)
    }

    /**
     * Creates Action_Items for the selected suggestions (Req 10.7) and signals
     * completion. Selections without a bucket are skipped so no item is created
     * unassigned. With nothing selected the flow simply completes.
     */
    fun confirm() {
        val id = entryId ?: return
        val confirmed = _uiState.value.actions
            .filter { it.selected && it.bucketId != null }
            .map {
                ConfirmedExtraction(
                    action = it.action,
                    bucketId = it.bucketId!!,
                    timeframe = Timeframe.Today,
                )
            }
        viewModelScope.launch {
            if (confirmed.isNotEmpty()) {
                voiceRepository.confirmExtractedActions(id, confirmed)
            }
            _uiState.update { it.copy(done = true) }
        }
    }

    /** Matches an extracted action's suggested bucket name to an existing bucket. */
    private fun matchBucket(action: ExtractedAction, buckets: List<Bucket>): Bucket? {
        val suggested = action.suggestedBucketName?.trim()?.lowercase() ?: return null
        return buckets.firstOrNull { it.name.trim().lowercase() == suggested }
    }
}
