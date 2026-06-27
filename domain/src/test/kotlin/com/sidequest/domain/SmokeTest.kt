package com.sidequest.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Minimal empty test that verifies the Kotest setup and test source set build.
 * Real domain logic and Correctness Property tests land in later tasks.
 */
class SmokeTest : StringSpec({
    "kotest harness runs an empty test" {
        true shouldBe true
    }
})
