package com.actiontracker.domain.model

import kotlinx.serialization.Serializable

/**
 * Open Graph / Twitter Card metadata for a shared link.
 *
 * When [resolved] is false the enrichment failed or timed out and the UI should
 * fall back to displaying [rawUrl].
 */
@Serializable
data class LinkPreview(
    val title: String?,
    val thumbnailUrl: String?,
    val sourceName: String?,
    val rawUrl: String,
    val resolved: Boolean,
)
