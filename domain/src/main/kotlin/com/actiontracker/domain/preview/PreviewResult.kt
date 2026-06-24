package com.actiontracker.domain.preview

/**
 * Outcome of fetching Open Graph / Twitter Card metadata for a shared link.
 *
 * Preview enrichment runs off the capture critical path (Req 1a.1), so this
 * result captures the three terminal states the [PreviewMerge] logic turns into
 * a [com.actiontracker.domain.model.LinkPreview]:
 *
 * - [Success] — metadata was retrieved; the resulting preview is stored with
 *   `resolved == true` (Req 1a.2).
 * - [Failure] — metadata could not be retrieved (network/parse error); the raw
 *   link is stored and displayed (`resolved == false`, Req 1a.4).
 * - [Timeout] — the fetch did not complete within the configured timeout; the
 *   item is saved with the raw link without blocking capture (`resolved ==
 *   false`, Req 1a.5).
 *
 * It lives in `:domain` (rather than alongside the network implementation in
 * `:app`) so the pure merge logic and its Correctness Properties (Properties 3
 * and 4) are testable without any Android or networking dependency.
 */
sealed interface PreviewResult {

    /**
     * Metadata was retrieved successfully (Req 1a.2).
     *
     * @property title the link's title (Open Graph `og:title`, Twitter
     *   `twitter:title`, or the HTML `<title>`).
     * @property thumbnailUrl the preview image URL when present, else null.
     * @property sourceName a human-readable source/site name (`og:site_name`).
     */
    data class Success(
        val title: String,
        val thumbnailUrl: String?,
        val sourceName: String,
    ) : PreviewResult

    /**
     * Metadata could not be retrieved (Req 1a.4).
     *
     * @property rawUrl the original shared URL to store and display as-is.
     */
    data class Failure(val rawUrl: String) : PreviewResult

    /**
     * The fetch exceeded the configured timeout (Req 1a.5).
     *
     * @property rawUrl the original shared URL to store and display as-is.
     */
    data class Timeout(val rawUrl: String) : PreviewResult
}
