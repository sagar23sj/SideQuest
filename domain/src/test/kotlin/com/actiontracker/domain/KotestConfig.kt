package com.actiontracker.domain

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.property.PropertyTesting

/**
 * Project-wide Kotest configuration for the :domain module.
 *
 * Property-based tests for the Correctness Properties (design.md) run a minimum
 * of 100 iterations, so the default iteration count is pinned here.
 */
class KotestConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        // Minimum iterations per property test, per the design's testing strategy.
        PropertyTesting.defaultIterationCount = 100
    }
}
