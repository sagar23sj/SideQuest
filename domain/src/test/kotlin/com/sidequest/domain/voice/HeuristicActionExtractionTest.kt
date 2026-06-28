package com.sidequest.domain.voice

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize

/**
 * Unit tests for the pure on-device [HeuristicActionExtraction] fallback used
 * when the LLM_Service is unavailable. The candidates are confirmation-gated, so
 * the heuristic favors recall (offering tidy candidates) over precision.
 */
class HeuristicActionExtractionTest : StringSpec({

    "a blank transcript yields no candidates" {
        HeuristicActionExtraction.extract("   ") shouldBe emptyList()
    }

    "lead-ins are stripped and clauses split into separate, title-cased actions" {
        val transcript = "I need to buy milk and call mom"
        val titles = HeuristicActionExtraction.extract(transcript).map { it.title }
        titles shouldContainExactly listOf("Buy milk", "Call mom")
    }

    "clauses opening with an action verb are kept even without a lead-in" {
        val titles = HeuristicActionExtraction.extract("Book flights. Renew passport.").map { it.title }
        titles shouldContainExactly listOf("Book flights", "Renew passport")
    }

    "duplicate actions are de-duplicated" {
        val titles = HeuristicActionExtraction.extract("Call mom. call mom.").map { it.title }
        titles shouldContainExactly listOf("Call mom")
    }

    "a domain keyword suggests a bucket" {
        val action = HeuristicActionExtraction.extract("I need to book flights to Japan").first()
        action.suggestedBucketName shouldBe "Travel"
    }

    "a non-task transcript still offers the first sentence as a single candidate" {
        val result = HeuristicActionExtraction.extract("It was a calm and quiet morning")
        result shouldHaveSize 1
    }

    "the number of candidates is capped" {
        val many = (1..20).joinToString(". ") { "buy item$it" }
        HeuristicActionExtraction.extract(many).size shouldBe 6
    }
})
