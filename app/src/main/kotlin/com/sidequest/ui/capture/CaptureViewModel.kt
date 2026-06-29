package com.sidequest.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.data.repository.BeginCaptureResult
import com.sidequest.data.repository.BucketRepository
import com.sidequest.data.repository.CaptureRepository
import com.sidequest.domain.capture.SharedIntentData
import com.sidequest.domain.capture.TimeframeValidationResult
import com.sidequest.domain.capture.validateTimeframe
import com.sidequest.domain.model.Timeframe
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
class CaptureViewModel(
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
                    val draft = result.draft
                    val contentType = draft.contentType
                    val isTextual = contentType == com.sidequest.domain.model.ContentType.LINK ||
                        contentType == com.sidequest.domain.model.ContentType.TEXT
                    // Treat every shared text as text: the whole shared content
                    // becomes the description, the first URL within it becomes
                    // the link, and the user always names the quest themselves.
                    val fullText = if (isTextual) draft.sourceContent.orEmpty() else ""
                    val link = com.sidequest.domain.capture.firstUrlOrNull(fullText).orEmpty()
                    _uiState.value = CaptureUiState.Categorizing(
                        draft = draft,
                        title = "",
                        description = fullText,
                        link = link,
                    )
                    observeBuckets(accountId)
                }

                BeginCaptureResult.Unsupported ->
                    _uiState.value = CaptureUiState.Unsupported
            }
        }
    }

    /**
     * Begins a manual in-app "new task" entry (the capture FAB). There is no
     * shared content, so the user types the task title in the categorization
     * sheet; bucket + timeframe selection is identical to a shared capture. When
     * [preselectedBucketId] is provided (launched from a bucket), it is
     * pre-selected.
     */
    fun startManual(preselectedBucketId: String? = null) {
        if (started) return
        started = true

        viewModelScope.launch {
            val accountId = accountProvider.currentAccountId()
            val draft = com.sidequest.domain.capture.CaptureDraft(
                accountId = accountId,
                title = "",
                contentType = com.sidequest.domain.model.ContentType.TEXT,
                sourceContent = null,
                preview = null,
            )
            _uiState.value = CaptureUiState.Categorizing(
                draft = draft,
                isManual = true,
                selectedBucketId = preselectedBucketId,
            )
            observeBuckets(accountId)
        }
    }

    /** Updates the typed task name. */
    fun onTitleChange(value: String) = updateCategorizing { it.copy(title = value) }

    /** Updates the optional description / details. */
    fun onDescriptionChange(value: String) = updateCategorizing { it.copy(description = value) }

    /** Updates the optional link. */
    fun onLinkChange(value: String) = updateCategorizing { it.copy(link = value) }

    /**
     * Creates a new bucket with [name] for the current account and immediately
     * selects it, so the user can add a task to a brand-new bucket without
     * leaving the capture flow. Blank names are ignored.
     */
    fun createAndSelectBucket(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val accountId = accountProvider.currentAccountId()
            when (val result = bucketRepository.createBucket(
                accountId = accountId,
                name = trimmed,
                notStartedColor = "#89726B",
                inProgressColor = "#6D4EA2",
                completedColor = "#006A63",
            )) {
                is com.sidequest.domain.bucket.BucketResult.Created ->
                    updateCategorizing { it.copy(selectedBucketId = result.bucket.id) }
                else -> Unit
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
            // Fold the edited name/description/link into the draft. A non-blank
            // link makes the item a clickable LINK; plain text is stored as the
            // description (no redundant source); media keeps its original type.
            val link = state.link.trim()
            val hasLink = link.isNotBlank()
            val original = state.draft
            val isMedia = original.contentType == com.sidequest.domain.model.ContentType.IMAGE ||
                original.contentType == com.sidequest.domain.model.ContentType.VIDEO_REF
            val draft = original.copy(
                title = state.title.trim(),
                description = state.description.trim().ifBlank { null },
                contentType = when {
                    isMedia -> original.contentType
                    hasLink -> com.sidequest.domain.model.ContentType.LINK
                    else -> com.sidequest.domain.model.ContentType.TEXT
                },
                sourceContent = when {
                    isMedia -> original.sourceContent
                    hasLink -> link
                    else -> null
                },
            )
            captureRepository.confirmCapture(draft, bucketId, timeframe)
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
