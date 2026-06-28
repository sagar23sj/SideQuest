package com.sidequest.ui.board

import com.sidequest.domain.board.BoardItem
import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.ContentType
import com.sidequest.domain.model.LinkPreview
import com.sidequest.domain.model.SyncMeta
import com.sidequest.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Example test for the board row preview-display decision (Req 1a.3).
 *
 * The board row renders the link preview title + thumbnail for a LINK item with
 * a resolved [LinkPreview], and falls back to the raw link when the preview is
 * absent or unresolved (Req 1a.4). That decision lives in the pure
 * [previewDisplay] helper that the Compose row consumes, so asserting the helper
 * exercises the real rendering logic without Compose UI-test tooling (which is
 * not available for local JVM unit tests in this module).
 *
 * _Requirements: 1a.3_
 */
class PreviewDisplayTest : StringSpec({

    fun syncMeta(): SyncMeta =
        SyncMeta(updatedAt = 0L, version = 1L, deleted = false, dirty = false)

    fun linkItem(
        title: String = "Saved link",
        sourceContent: String? = "https://example.com/article",
        preview: LinkPreview? = null,
        contentType: ContentType = ContentType.LINK,
    ): ActionItem =
        ActionItem(
            id = "item-1",
            accountId = "account-1",
            bucketId = "bucket-1",
            title = title,
            contentType = contentType,
            sourceContent = sourceContent,
            preview = preview,
            timeframe = Timeframe.Today,
            status = ActionStatus.NOT_STARTED,
            createdAt = 0L,
            sync = syncMeta(),
        )

    fun boardItem(item: ActionItem): BoardItem =
        BoardItem(item = item, statusColor = "#112233")

    "a link item with a resolved preview displays the preview title and thumbnail" {
        val preview = LinkPreview(
            title = "How to plan a trip to Japan",
            thumbnailUrl = "https://cdn.example.com/japan.jpg",
            sourceName = "example.com",
            rawUrl = "https://example.com/article",
            resolved = true,
        )
        val display = previewDisplay(boardItem(linkItem(preview = preview)))

        // The user's name stays the headline (link metadata is supporting info).
        display.title shouldBe "Saved link"
        // ... the thumbnail is presented (Req 1a.3) ...
        display.hasThumbnail shouldBe true
        display.thumbnailUrl shouldBe "https://cdn.example.com/japan.jpg"
        // ... and the link's own page title becomes the subtitle.
        display.rawSource shouldBe "How to plan a trip to Japan"
    }

    "a resolved preview with a blank title falls back to the item title but keeps the thumbnail" {
        val preview = LinkPreview(
            title = "   ",
            thumbnailUrl = "https://cdn.example.com/thumb.png",
            sourceName = "example.com",
            rawUrl = "https://example.com/article",
            resolved = true,
        )
        val display = previewDisplay(boardItem(linkItem(title = "Saved link", preview = preview)))

        display.title shouldBe "Saved link"
        display.thumbnailUrl shouldBe "https://cdn.example.com/thumb.png"
    }

    "a resolved preview with no thumbnail still shows the preview title without a thumbnail" {
        val preview = LinkPreview(
            title = "An article with no image",
            thumbnailUrl = null,
            sourceName = "example.com",
            rawUrl = "https://example.com/article",
            resolved = true,
        )
        val display = previewDisplay(boardItem(linkItem(preview = preview)))

        display.title shouldBe "Saved link"
        display.hasThumbnail shouldBe false
        display.thumbnailUrl shouldBe null
        // With no thumbnail, the link's page title is surfaced as the subtitle.
        display.rawSource shouldBe "An article with no image"
    }

    "an unresolved preview link item shows the raw link instead of a preview" {
        val preview = LinkPreview(
            title = null,
            thumbnailUrl = null,
            sourceName = null,
            rawUrl = "https://example.com/article",
            resolved = false,
        )
        val display = previewDisplay(
            boardItem(linkItem(sourceContent = "https://example.com/article", preview = preview)),
        )

        // Fallback to the raw link (Req 1a.4): no preview title/thumbnail used.
        display.title shouldBe "Saved link"
        display.hasThumbnail shouldBe false
        display.rawSource.shouldNotBeNull() shouldBe "https://example.com/article"
    }

    "a link item with no preview shows the raw link" {
        val display = previewDisplay(
            boardItem(linkItem(sourceContent = "https://example.com/article", preview = null)),
        )

        display.title shouldBe "Saved link"
        display.hasThumbnail shouldBe false
        display.rawSource shouldBe "https://example.com/article"
    }
})
