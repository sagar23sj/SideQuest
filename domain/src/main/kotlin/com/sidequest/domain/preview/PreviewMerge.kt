package com.sidequest.domain.preview

import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.LinkPreview

/**
 * Pure preview-merge logic: turning a [PreviewResult] plus the original link URL
 * into a [LinkPreview], and merging that preview onto an [ActionItem] (Req 1a.2,
 * 1a.4, 1a.5).
 *
 * This lives in `:domain` so it is portable and validated by the shared
 * Correctness Properties (Property 3 — a resolved preview is stored faithfully;
 * Property 4 — an unresolved/timeout result falls back to the raw link) without
 * any Android or networking dependency. The actual metadata fetch (OkHttp +
 * Open Graph/Twitter Card parsing) lives in `:app` as a thin `PreviewService`
 * implementation that produces the [PreviewResult] this object consumes.
 *
 * Both functions are pure and total: every [PreviewResult] variant maps to a
 * well-defined [LinkPreview], so enrichment never throws and never blocks the
 * capture flow.
 */
object PreviewMerge {

    /**
     * Builds a [LinkPreview] from a [result] and the [originalUrl] that was
     * fetched.
     *
     * - [PreviewResult.Success] → a resolved preview that carries the same
     *   title, thumbnail, and source name, with `resolved == true` and
     *   `rawUrl == originalUrl` (Req 1a.2, Property 3).
     * - [PreviewResult.Failure] / [PreviewResult.Timeout] → an unresolved
     *   preview with null title/thumbnail/source and `resolved == false`, whose
     *   `rawUrl` equals [originalUrl] so the UI falls back to the raw link
     *   (Req 1a.4, 1a.5, Property 4).
     *
     * [originalUrl] is always used for [LinkPreview.rawUrl] so the stored raw URL
     * reflects exactly what was shared, regardless of which `rawUrl` a
     * [PreviewResult.Failure]/[PreviewResult.Timeout] happens to carry.
     */
    fun toLinkPreview(result: PreviewResult, originalUrl: String): LinkPreview =
        when (result) {
            is PreviewResult.Success -> LinkPreview(
                title = result.title,
                thumbnailUrl = result.thumbnailUrl,
                sourceName = result.sourceName,
                rawUrl = originalUrl,
                resolved = true,
            )

            is PreviewResult.Failure,
            is PreviewResult.Timeout,
            -> LinkPreview(
                title = null,
                thumbnailUrl = null,
                sourceName = null,
                rawUrl = originalUrl,
                resolved = false,
            )
        }

    /**
     * Returns a copy of [item] with its [ActionItem.preview] set to the preview
     * built from [result] and [originalUrl] (see [toLinkPreview]).
     *
     * Only the preview field changes; every other field — including status,
     * timeframe, and sync metadata — is preserved, so enrichment that arrives
     * after capture updates the item reactively without disturbing the rest of
     * its state. Bumping sync metadata for the enrichment write is the
     * responsibility of the `:app` persistence layer (a later wiring task), not
     * this pure merge.
     */
    fun mergeInto(
        item: ActionItem,
        result: PreviewResult,
        originalUrl: String,
    ): ActionItem = item.copy(preview = toLinkPreview(result, originalUrl))
}
