package com.sidequest.ui.capture

import com.sidequest.domain.capture.CaptureDraft
import com.sidequest.domain.model.Bucket
import com.sidequest.domain.model.Timeframe
import java.time.LocalDate

/**
 * The timeframe options offered by the categorization sheet (Req 3.1).
 *
 * The relative options carry no payload; [SpecificDate] carries the user-picked
 * calendar [date] (null until the user picks one). Keeping this UI-level enum
 * separate from the domain [Timeframe] lets the sheet render a stable list of
 * choices and defer building the domain value until confirm.
 */
sealed interface TimeframeOption {
    data object Today : TimeframeOption
    data object WithinADay : TimeframeOption
    data object WithinAWeek : TimeframeOption
    data class SpecificDate(val date: LocalDate? = null) : TimeframeOption
}

/**
 * Converts a selected [TimeframeOption] into the domain [Timeframe], or null
 * when a specific date is selected but no date has been picked yet.
 */
fun TimeframeOption.toTimeframeOrNull(): Timeframe? = when (this) {
    TimeframeOption.Today -> Timeframe.Today
    TimeframeOption.WithinADay -> Timeframe.WithinADay
    TimeframeOption.WithinAWeek -> Timeframe.WithinAWeek
    is TimeframeOption.SpecificDate -> date?.let { Timeframe.SpecificDate(it) }
}

/**
 * Screen state for [ShareTargetActivity], exposed as a single immutable value
 * from [CaptureViewModel].
 *
 * The states model the capture flow described in the design's Capture_Service
 * section: load the shared content, classify it, then either reject unsupported
 * content (Req 1.4) or show the bucket + timeframe categorization sheet
 * (Req 1.3). [Saved] signals the activity to finish after a confirmed capture
 * (Req 1.5).
 */
sealed interface CaptureUiState {

    /** Shared content is being classified and prepared. */
    data object Loading : CaptureUiState

    /**
     * The shared content is unsupported; show the not-supported message and a
     * discard action that closes the flow without persisting anything (Req 1.4).
     */
    data object Unsupported : CaptureUiState

    /**
     * The shared content is supported; render the categorization sheet.
     *
     * @property draft the prepared capture draft awaiting bucket + timeframe.
     * @property buckets the account's selectable buckets (Req 1.3).
     * @property selectedBucketId the chosen bucket, or null until one is picked.
     * @property selectedTimeframe the chosen timeframe option (Req 3.1).
     * @property dateError corrective message shown when a past specific date is
     *   rejected (Req 3.3); null when there is no error.
     * @property isSaving true while the confirmed capture is being persisted.
     */
    data class Categorizing(
        val draft: CaptureDraft,
        val buckets: List<Bucket> = emptyList(),
        val selectedBucketId: String? = null,
        val selectedTimeframe: TimeframeOption = TimeframeOption.Today,
        val dateError: String? = null,
        val isSaving: Boolean = false,
        /** True for an in-app manual "new task" entry (shows a title field). */
        val isManual: Boolean = false,
        /** The user-typed title for a manual task entry. */
        val manualTitle: String = "",
    ) : CaptureUiState {

        /** True when a bucket and a fully-specified timeframe are selected. */
        val canConfirm: Boolean
            get() = !isSaving &&
                selectedBucketId != null &&
                selectedTimeframe.toTimeframeOrNull() != null &&
                (!isManual || manualTitle.isNotBlank())
    }

    /** The capture was confirmed and persisted; the activity should finish (Req 1.5). */
    data object Saved : CaptureUiState
}
