package com.actiontracker.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actiontracker.data.repository.BeginCaptureResult
import com.actiontracker.data.repository.BucketRepository
import com.actiontracker.data.repository.CaptureRepository
import com.actiontracker.domain.capture.SharedIntentData
import com.actiontracker.domain.capture.TimeframeValidationResult
import com.actiontracker.domain.capture.validateTimeframe
import com.actiontracker.domain.model.Timeframe
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the share-target capture flow (Req 1.2–1.5, 3.x).
 *
 * The activity hands the parsed [SharedIntentData] to [onShared]; the view model
 * classifies it via [CaptureRepository.beginCapture] (Req 1.2). Unsupported
 * content moves to [CaptureUiState.Unsupported] for the not-supported message
 * (Req 1.4); supported content moves to [CaptureUiState.Categorizing] and starts
 * observing the account's buckets for selection (Req 1.3). On confirm it
 * validates the timeframe (Req 3.3) and persists via
 * [CaptureRepository.confirmCapture] (Req 1.5), then signals
 * [CaptureUiState.Saved] so the activity finishes.
 *
 * All screen state is exposed as an immutable [StateFlow]; the composables are
 * stateless and emit intents back to this view model.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val captureRepository: CaptureRepository,
    private val bucketRepository: BucketRepository,
    private val accountProvider: CurrentAccountProvider,
    private val today: () -> LocalDate,
) : ViewModel() {

    /**
     * Hilt entry point. Hilt supplies the repositories and account provider; the
     * [today] clock defaults to the system date and is overridable in tests for
     * deterministic specific-date validation.
     */
    @Inject
    constructor(
        captureRepository: CaptureRepository,
        bucketRepository: BucketRepository,
        accountProvider: CurrentAccountProvider,
    ) : this(
        captureRepository = captureRepository,
        bucketRepository = bucketRepository,
        accountProvider = accountProvider,
        today = { LocalDate.now() },
    )

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Loading)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private var started = false

    /**
     * Begins the capture flow for the [intentData] extracted from the incoming
     * share intent. Idempotent across configuration changes so a recreated
     * activity does not re-classify and reset user selections.
     */
    fun onShared(intentData: SharedIntentData) {
        if (started) return
        started = true

        viewModelScope.launch {
            val accountId = accountProvider.currentAccountId()
            when (val result = captureRepository.beginCapture(accountId, intentData)) {
                is BeginCaptureResult.Draft -> {
                    _uiState.value = CaptureUiState.Categorizing(draft = result.draft)
                    observeBuckets(accountId)
                }

                BeginCaptureResult.Unsupported ->
                    _uiState.value = CaptureUiState.Unsupported
            }
        }
    }

    private fun observeBuckets(accountId: String) {
        viewModelScope.launch {
            bucketRepository.observeBuckets(accountId).collect { buckets ->
                _uiState.update { state ->
                    if (state is CaptureUiState.Categorizing) {
                        state.copy(buckets = buckets)
                    } else {
                        state
                    }
                }
            }
        }
    }

    /** Records the user's bucket selection (Req 1.3). */
    fun onBucketSelected(bucketId: String) = updateCategorizing { it.copy(selectedBucketId = bucketId) }

    /**
     * Records a relative timeframe selection (today / within a day / within a
     * week), clearing any prior specific-date error (Req 3.1).
     */
    fun onTimeframeSelected(option: TimeframeOption) = updateCategorizing {
        it.copy(selectedTimeframe = option, dateError = null)
    }

    /**
     * Records a picked specific date and validates it immediately (Req 3.2,
     * 3.3). A past date keeps the selection but surfaces the corrective message
     * so the user is prompted for a current or future date.
     */
    fun onSpecificDatePicked(date: LocalDate) = updateCategorizing { state ->
        val timeframe = Timeframe.SpecificDate(date)
        val error = when (val validation = validateTimeframe(timeframe, today())) {
            TimeframeValidationResult.Valid -> null
            is TimeframeValidationResult.Invalid -> validation.reason
        }
        state.copy(
            selectedTimeframe = TimeframeOption.SpecificDate(date),
            dateError = error,
        )
    }

    /**
     * Confirms the capture: re-validates the selected timeframe and, when valid,
     * persists a not-started Action_Item with the chosen bucket and timeframe
     * (Req 1.5), then transitions to [CaptureUiState.Saved]. A past specific date
     * is rejected with the corrective message and nothing is persisted (Req 3.3).
     */
    fun onConfirm() {
        val state = _uiState.value as? CaptureUiState.Categorizing ?: return
        val bucketId = state.selectedBucketId ?: return
        val timeframe = state.selectedTimeframe.toTimeframeOrNull() ?: return
        if (state.isSaving) return

        when (val validation = validateTimeframe(timeframe, today())) {
            TimeframeValidationResult.Valid -> Unit
            is TimeframeValidationResult.Invalid -> {
                updateCategorizing { it.copy(dateError = validation.reason) }
                return
            }
        }

        updateCategorizing { it.copy(isSaving = true) }
        viewModelScope.launch {
            captureRepository.confirmCapture(state.draft, bucketId, timeframe)
            _uiState.value = CaptureUiState.Saved
        }
    }

    private inline fun updateCategorizing(
        transform: (CaptureUiState.Categorizing) -> CaptureUiState.Categorizing,
    ) {
        _uiState.update { state ->
            if (state is CaptureUiState.Categorizing) transform(state) else state
        }
    }
}
