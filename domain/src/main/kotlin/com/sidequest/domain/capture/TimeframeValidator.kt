package com.sidequest.domain.capture

import com.sidequest.domain.model.Timeframe
import java.time.LocalDate

/**
 * Message shown when a [Timeframe.SpecificDate] in the past is rejected (Req 3.3).
 */
const val PAST_SPECIFIC_DATE_MESSAGE: String =
    "Please choose the current date or a future date."

/**
 * Pure validation of a [Timeframe] against the current date.
 *
 * The current date is passed in as [today] rather than read from a clock so the
 * function stays total, deterministic, and testable without time dependencies.
 *
 * Rules:
 *  - [Timeframe.Today], [Timeframe.WithinADay], and [Timeframe.WithinAWeek] are
 *    always [TimeframeValidationResult.Valid] (Req 3.1).
 *  - [Timeframe.SpecificDate] is [TimeframeValidationResult.Valid] if and only
 *    if its date is [today] or later; dates before [today] are rejected as
 *    [TimeframeValidationResult.Invalid] (Req 3.2, 3.3).
 */
fun validateTimeframe(
    timeframe: Timeframe,
    today: LocalDate,
): TimeframeValidationResult = when (timeframe) {
    Timeframe.Today,
    Timeframe.WithinADay,
    Timeframe.WithinAWeek,
    -> TimeframeValidationResult.Valid

    is Timeframe.SpecificDate ->
        if (timeframe.date.isBefore(today)) {
            TimeframeValidationResult.Invalid(PAST_SPECIFIC_DATE_MESSAGE)
        } else {
            TimeframeValidationResult.Valid
        }
}
