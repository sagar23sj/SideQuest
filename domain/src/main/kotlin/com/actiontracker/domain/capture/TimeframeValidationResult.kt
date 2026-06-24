package com.actiontracker.domain.capture

import com.actiontracker.domain.model.Timeframe

/**
 * Outcome of validating a [Timeframe] against the current date.
 *
 * The relative timeframes (today / within a day / within a week) are always
 * valid, while a [Timeframe.SpecificDate] is only valid when its date is the
 * current date or later (Req 3.2, 3.3). This sealed result lets the UI show the
 * corrective message carried by [Invalid] when a past date is rejected (Req 3.3).
 */
sealed interface TimeframeValidationResult {

    /** The timeframe is acceptable and may be assigned to an Action_Item. */
    data object Valid : TimeframeValidationResult

    /**
     * The timeframe is rejected. [reason] explains why and is suitable for
     * display to the user (for example, requesting a current or future date).
     */
    data class Invalid(val reason: String) : TimeframeValidationResult
}
