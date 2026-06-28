package com.sidequest.ui.board

import com.sidequest.domain.board.BoardItem
import com.sidequest.domain.model.ContentType

/**
 * The pure decision of *what* a board row should display for an item's content
 * area, independent of any Compose rendering. Extracting this keeps the
 * link-preview-vs-raw-fallback rule (Req 1a.3, 1a.4) unit-testable on the JVM,
 * where Compose UI-test tooling is unavailable.
 *
 * Exactly one of [thumbnailUrl] / [rawSource] is meaningful at a time:
 * - When a LINK item has a resolved [com.sidequest.domain.model.LinkPreview],
 *   [title] is the preview title (falling back to the item title when the
 *   preview title is blank) and [thumbnailUrl] is the preview thumbnail to
 *   render (Req 1a.3). [rawSource] is null because the preview replaces the
 *   raw link.
 * - Otherwise (no preview, an unresolved preview, or a non-link item) [title]
 *   is the item title, [thumbnailUrl] is null, and [rawSource] carries the raw
 *   link / source content to show in place of a preview (Req 1a.4).
 */
data class PreviewDisplay(
    val title: String,
    val thumbnailUrl: String?,
    val rawSource: String?,
) {
    /** True when a resolved preview thumbnail should be rendered (Req 1a.3). */
    val hasThumbnail: Boolean get() = thumbnailUrl != null
}

/**
 * Resolves the [PreviewDisplay] for a board row.
 *
 * A resolved link preview is shown (title + thumbnail) only when the item is a
 * [ContentType.LINK] with a non-null preview whose `resolved` flag is true
 * (Req 1a.3). A blank preview title falls back to the item title, and a blank
 * thumbnail URL is treated as absent so the row degrades gracefully. In every
 * other case the raw source content is surfaced as the fallback (Req 1a.4),
 * preserving the pre-enrichment behavior.
 */
fun previewDisplay(boardItem: BoardItem): PreviewDisplay {
    val item = boardItem.item
    val preview = item.preview

    val showResolvedPreview =
        item.contentType == ContentType.LINK && preview != null && preview.resolved

    if (showResolvedPreview && preview != null) {
        val thumbnailUrl = preview.thumbnailUrl?.takeIf { it.isNotBlank() }
        // The user-entered name is always the headline. The link's own page
        // title (or its URL) becomes the supporting subtitle — link metadata is
        // additional info, never a replacement for the name the user chose.
        val subtitle = preview.title?.takeIf { it.isNotBlank() }
            ?: item.sourceContent?.takeIf { it.isNotBlank() }
        return PreviewDisplay(
            title = item.title,
            thumbnailUrl = thumbnailUrl,
            rawSource = subtitle,
        )
    }

    return PreviewDisplay(
        title = item.title,
        thumbnailUrl = null,
        rawSource = item.sourceContent?.takeIf { it.isNotBlank() },
    )
}
