package com.actiontracker.domain.capture

import com.actiontracker.domain.model.ContentType

/**
 * Outcome of classifying [SharedIntentData].
 *
 * [ContentType] has no "unsupported" member because it models only the content
 * the app can act on, so classification returns this sealed result to represent
 * either a [Supported] content type or [Unsupported] input that must be
 * discarded with a "content type not supported" message (Req 1.4).
 */
sealed interface ClassificationResult {

    /** The shared content maps to a supported [ContentType]. */
    data class Supported(val contentType: ContentType) : ClassificationResult

    /** The shared content is in an unsupported format and must be discarded. */
    data object Unsupported : ClassificationResult
}
