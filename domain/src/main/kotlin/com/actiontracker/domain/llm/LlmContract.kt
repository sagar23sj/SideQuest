package com.actiontracker.domain.llm

import kotlinx.serialization.Serializable

/**
 * Lightweight, portable input/output types for the LLM_Service contract.
 *
 * These live in `:domain` (rather than `:app`) so the request/response shapes
 * the backend LLM Proxy speaks are shared, serializable (aligning with the
 * shared OpenAPI schema the Go backend generates from), and usable by the pure
 * extraction/description flows that later tasks validate. The `:app` network
 * client maps these to/from the proxy's JSON.
 */

/**
 * A compact summary of an [com.actiontracker.domain.model.ActionItem] sent to
 * the LLM_Service when generating reminder notification text (Req 7.1).
 *
 * Only the fields useful for prose generation are included so the request stays
 * small and free of sync/identity noise.
 *
 * @property title the item's title.
 * @property bucketName the name of the bucket the item belongs to.
 * @property dueDescription a human-readable due hint (e.g. "today",
 *   "within a week"), or null when not relevant.
 */
@Serializable
data class ActionItemSummary(
    val title: String,
    val bucketName: String,
    val dueDescription: String? = null,
)

/**
 * The shared content the LLM_Service is asked to describe (Req 7.3).
 *
 * @property contentType the kind of content (e.g. "link", "text", "video_ref").
 * @property rawContent the raw shared text/link/media reference.
 * @property title an optional known title (e.g. from a resolved link preview).
 */
@Serializable
data class SharedItemContent(
    val contentType: String,
    val rawContent: String,
    val title: String? = null,
)

/**
 * An actionable item the LLM_Service extracted from a voice-journal transcript
 * (Req 10.5). Presented to the user for confirmation before any Action_Item is
 * created (Req 10.6); see the extraction/confirmation flow in Milestone D.
 *
 * @property title the suggested action title.
 * @property suggestedBucketName an optional bucket suggestion, or null.
 */
@Serializable
data class ExtractedAction(
    val title: String,
    val suggestedBucketName: String? = null,
)
