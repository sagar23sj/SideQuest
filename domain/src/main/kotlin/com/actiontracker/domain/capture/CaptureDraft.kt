package com.actiontracker.domain.capture

import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.LinkPreview

/**
 * Everything known about a capture before the user selects a bucket and a
 * timeframe.
 *
 * A draft is produced from a [SharedIntentData] (via the capture repository's
 * `beginCapture`) once the content has been classified into a supported
 * [ContentType]. It is a pure data carrier with no Android dependency so the
 * confirm-capture computation that turns it into an Action_Item can live in
 * `:domain` and be validated with property tests (Property 2).
 *
 * @property accountId the signed-in account the resulting Action_Item belongs to.
 * @property title a short, user-recognizable label derived from the shared
 *   content (for example the shared text, a URL, or a placeholder for media).
 * @property contentType the classified type of the shared content.
 * @property sourceContent the raw shared payload (text, link, or media
 *   reference), preserved on the Action_Item for later display/enrichment.
 * @property preview link metadata when already available. This is normally
 *   `null` at draft time because preview enrichment runs off the capture
 *   critical path (Req 1a, a later task); the field exists so a pre-resolved
 *   preview can be carried through when one is available.
 */
data class CaptureDraft(
    val accountId: String,
    val title: String,
    val contentType: ContentType,
    val sourceContent: String? = null,
    val preview: LinkPreview? = null,
)
