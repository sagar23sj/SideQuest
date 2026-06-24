package com.actiontracker.data.preview

import com.actiontracker.domain.preview.PreviewResult

/**
 * Retrieves Open Graph / Twitter Card metadata for a shared link (Req 1a.1).
 *
 * The service runs off the capture critical path with a configurable timeout
 * and always returns a [PreviewResult] rather than throwing, so callers can
 * merge the outcome into an Action_Item via
 * [com.actiontracker.domain.preview.PreviewMerge] without blocking capture
 * (Req 1a.5). The pure success/failure/timeout → [com.actiontracker.domain.model.LinkPreview]
 * mapping lives in `:domain`; this interface is the network seam in `:app`.
 */
interface PreviewService {

    /**
     * Fetches link metadata for [url], returning within roughly [timeoutMs].
     *
     * @return [PreviewResult.Success] with title/thumbnail/source when metadata
     *   is parsed, [PreviewResult.Timeout] when the fetch exceeds [timeoutMs]
     *   (Req 1a.5), or [PreviewResult.Failure] for any other error (Req 1a.4).
     *   [PreviewResult.Failure.rawUrl] and [PreviewResult.Timeout.rawUrl] echo
     *   [url].
     */
    suspend fun fetchPreview(url: String, timeoutMs: Long = 5_000): PreviewResult
}
