package com.sidequest.domain.capture

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for [firstUrlOrNull], which pulls a clean URL out of a sentence-style
 * share so the preview fetcher can resolve it.
 */
class FirstUrlTest : StringSpec({

    "extracts the URL from a sentence-style share" {
        firstUrlOrNull("See this instagram post by @travel.with.neeks https://www.instagram.com/p/abc123/") shouldBe
            "https://www.instagram.com/p/abc123/"
    }

    "returns a bare URL unchanged" {
        firstUrlOrNull("https://example.com/path?a=1") shouldBe "https://example.com/path?a=1"
    }

    "trims trailing punctuation that clings to a URL in prose" {
        firstUrlOrNull("Check this out (https://example.com/page).") shouldBe "https://example.com/page"
    }

    "returns the first URL when several are present" {
        firstUrlOrNull("a https://one.com b https://two.com") shouldBe "https://one.com"
    }

    "returns null when there is no URL" {
        firstUrlOrNull("just some plain text") shouldBe null
    }

    "returns null for blank or null input" {
        firstUrlOrNull(null) shouldBe null
        firstUrlOrNull("   ") shouldBe null
    }
})
