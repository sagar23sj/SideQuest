package com.sidequest.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * When an [ActionItem] is intended to be acted on.
 *
 * Serialized as a discriminated union: a `type` discriminator selects the
 * variant and the [SpecificDate] variant carries a `date` payload. This mirrors
 * the `oneOf` + discriminator representation in the shared OpenAPI schema so the
 * Go backend and a future iOS client interpret timeframes identically.
 */
@Serializable
sealed interface Timeframe {

    /** Due on the current day. */
    @Serializable
    @SerialName("today")
    data object Today : Timeframe

    /** Due within a day of capture. */
    @Serializable
    @SerialName("within_a_day")
    data object WithinADay : Timeframe

    /** Due within a week of capture. */
    @Serializable
    @SerialName("within_a_week")
    data object WithinAWeek : Timeframe

    /**
     * Due on an explicit calendar [date], which must be today or later. The
     * not-in-the-past constraint is enforced by timeframe validation logic
     * (added in a later task), not by this data carrier.
     */
    @Serializable
    @SerialName("specific_date")
    data class SpecificDate(
        @Serializable(with = LocalDateIso8601Serializer::class)
        val date: LocalDate,
    ) : Timeframe
}
