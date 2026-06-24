package com.actiontracker.domain.capture

import com.actiontracker.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.time.LocalDate

/**
 * Property-based test for the specific-date timeframe acceptance rule.
 *
 * A [Timeframe.SpecificDate] is acceptable if and only if its date is the
 * current date ([today]) or later; any earlier date must be rejected so the
 * user is asked for a current or future date (Req 3.3). This test exercises
 * candidate dates spanning the past, the current day, and the future relative
 * to a generated [today] and asserts the iff relationship against
 * [validateTimeframe].
 *
 * _Requirements: 3.3_
 */
class SpecificDateTimeframePropertyTest : StringSpec({

    // Days an arbitrary LocalDate can represent without overflow.
    val minEpochDay = LocalDate.MIN.toEpochDay()
    val maxEpochDay = LocalDate.MAX.toEpochDay()

    // A "today" comfortably away from the epoch-day bounds so we can shift it by
    // a wide offset in either direction without overflowing LocalDate.
    val today: Arb<LocalDate> =
        Arb.long(minEpochDay + 400_000L, maxEpochDay - 400_000L).map(LocalDate::ofEpochDay)

    // Offset in days spanning the past (negative), today (zero), and the future.
    val offsetDays: Arb<Int> = Arb.int(-365_000..365_000)

    // Feature: action-tracker-app, Property 7: Specific-date timeframes accept today or later and reject the past
    "Property 7: specific-date timeframes are valid iff the date is today or later" {
        checkAll(100, today, offsetDays) { todayDate, offset ->
            val candidate = todayDate.plusDays(offset.toLong())
            val result = validateTimeframe(Timeframe.SpecificDate(candidate), todayDate)

            val expected = if (candidate.isBefore(todayDate)) {
                TimeframeValidationResult.Invalid(PAST_SPECIFIC_DATE_MESSAGE)
            } else {
                TimeframeValidationResult.Valid
            }

            result shouldBe expected
        }
    }
})
