package com.sidequest.domain.llm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based test for LLM fail-soft behavior (Property 13).
 *
 * For any request, when the LLM result is unavailable or timed out, the
 * operation still completes rather than blocking or erroring (Req 7.4, 7.5):
 *
 * - [LlmFailSoft.notificationTextOrDefault] always returns non-blank text. When
 *   the result is [LlmResult.Ok] with a non-blank value, it returns that value;
 *   otherwise (unavailable, timed out, or Ok-but-blank) it returns the supplied
 *   default if non-blank, else the constant [LlmFailSoft.GENERIC_NOTIFICATION_TEXT].
 * - [LlmFailSoft.listOrUnavailable] completes with the payload and
 *   `unavailable == false` on [LlmResult.Ok]; otherwise an empty list and
 *   `unavailable == true`.
 * - [LlmFailSoft.descriptionOrUnavailable] completes with the payload and
 *   `unavailable == false` on [LlmResult.Ok]; otherwise a null value and
 *   `unavailable == true`.
 *
 * The generators cover all three [LlmResult] variants with arbitrary payloads —
 * including blank/whitespace strings and blank defaults — so the fallback chain
 * is exercised in every direction. That the helpers never throw and always
 * return a usable result is itself the "fails soft / completes" guarantee; the
 * value assertions below capture the specific soft-fail behavior.
 *
 * _Requirements: 7.4, 7.5_
 */
class LlmFailSoftPropertyTest : StringSpec({

    // Strings spanning the interesting cases: blank/whitespace-only (so the
    // blank fallback paths fire) and arbitrary non-blank content.
    val arbBlank: Arb<String> = Arb.of("", " ", "   ", "\t", "\n", "  \t \n ")
    val arbNonBlank: Arb<String> = Arb.string(1..40).map { "x$it" }
    val arbAnyString: Arb<String> = Arb.choice(arbBlank, arbNonBlank)

    // LlmResult<String> across all three variants, with payloads that may be
    // blank to exercise the Ok-but-blank fallback in notificationTextOrDefault.
    val arbStringResult: Arb<LlmResult<String>> = Arb.choice(
        arbAnyString.map { LlmResult.Ok(it) },
        Arb.constant(LlmResult.Unavailable),
        Arb.constant(LlmResult.TimedOut),
    )

    // LlmResult<List<String>> across all three variants, with arbitrary payloads
    // including the empty list.
    val arbListResult: Arb<LlmResult<List<String>>> = Arb.choice(
        Arb.list(arbAnyString, 0..6).map { LlmResult.Ok(it) },
        Arb.constant(LlmResult.Unavailable),
        Arb.constant(LlmResult.TimedOut),
    )

    // Feature: action-tracker-app, Property 13: LLM features fail soft
    "Property 13: LLM features fail soft" {
        checkAll(
            100,
            arbStringResult,
            arbAnyString,
            arbListResult,
            arbStringResult,
        ) { textResult, default, listResult, descResult ->

            // --- notificationTextOrDefault: always non-blank, correct fallback ---
            val text = LlmFailSoft.notificationTextOrDefault(textResult, default)

            // The key invariant (Req 7.4): never empty/blank, regardless of input.
            text.isNotBlank().shouldBeTrue()

            val okValue = (textResult as? LlmResult.Ok)?.value
            val expectedText = when {
                okValue != null && okValue.isNotBlank() -> okValue
                default.isNotBlank() -> default
                else -> LlmFailSoft.GENERIC_NOTIFICATION_TEXT
            }
            text shouldBe expectedText

            // --- listOrUnavailable: completes with payload or unavailable signal ---
            val listOutcome = LlmFailSoft.listOrUnavailable(listResult)
            when (listResult) {
                is LlmResult.Ok -> {
                    listOutcome.unavailable shouldBe false
                    listOutcome.values shouldBe listResult.value
                }
                LlmResult.Unavailable, LlmResult.TimedOut -> {
                    listOutcome.unavailable shouldBe true
                    listOutcome.values.shouldBeEmpty()
                }
            }

            // --- descriptionOrUnavailable: completes with payload or unavailable ---
            val descOutcome = LlmFailSoft.descriptionOrUnavailable(descResult)
            when (descResult) {
                is LlmResult.Ok -> {
                    descOutcome.unavailable shouldBe false
                    descOutcome.value shouldBe descResult.value
                }
                LlmResult.Unavailable, LlmResult.TimedOut -> {
                    descOutcome.unavailable shouldBe true
                    descOutcome.value.shouldBeNull()
                }
            }
        }
    }
})
