package com.sidequest.domain.preview

import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.ContentType
import com.sidequest.domain.model.SyncMeta
import com.sidequest.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.time.LocalDate

/**
 * Property-based test for resolved link previews (Property 3).
 *
 * For any successful [PreviewResult.Success], [PreviewMerge.toLinkPreview] must
 * produce a [com.sidequest.domain.model.LinkPreview] that carries the same
 * title, thumbnail, and source name, with `resolved == true` and `rawUrl` equal
 * to the original URL (Req 1a.2). [PreviewMerge.mergeInto] must store that same
 * preview on an arbitrary [ActionItem] while leaving every other field of the
 * item unchanged, so a resolved preview is "stored faithfully" without
 * disturbing the captured item.
 *
 * _Requirements: 1a.2_
 */
class ResolvedPreviewPropertyTest : StringSpec({

    // Days an arbitrary LocalDate can represent without overflow.
    val minEpochDay = LocalDate.MIN.toEpochDay()
    val maxEpochDay = LocalDate.MAX.toEpochDay()

    val arbDate: Arb<LocalDate> =
        Arb.long(minEpochDay, maxEpochDay).map(LocalDate::ofEpochDay)

    // An arbitrary timeframe covering every variant, including SpecificDate.
    val arbTimeframe: Arb<Timeframe> = Arb.choice(
        Arb.of<Timeframe>(Timeframe.Today, Timeframe.WithinADay, Timeframe.WithinAWeek),
        arbDate.map { Timeframe.SpecificDate(it) },
    )

    val arbSyncMeta: Arb<SyncMeta> = arbitrary {
        SyncMeta(
            updatedAt = Arb.long(0L, Long.MAX_VALUE).bind(),
            version = Arb.long(0L, Long.MAX_VALUE).bind(),
            deleted = Arb.boolean().bind(),
            dirty = Arb.boolean().bind(),
        )
    }

    // An arbitrary ActionItem with an arbitrary (possibly null) existing preview.
    val arbItem: Arb<ActionItem> = arbitrary {
        ActionItem(
            id = Arb.string(1..36).bind(),
            accountId = Arb.string(1..24).bind(),
            bucketId = Arb.string(1..24).bind(),
            title = Arb.string(0..60).bind(),
            description = Arb.string(0..120).orNull().bind(),
            contentType = Arb.enum<ContentType>().bind(),
            sourceContent = Arb.string(0..120).orNull().bind(),
            preview = arbitrary {
                com.sidequest.domain.model.LinkPreview(
                    title = Arb.string(0..40).orNull().bind(),
                    thumbnailUrl = Arb.string(0..60).orNull().bind(),
                    sourceName = Arb.string(0..40).orNull().bind(),
                    rawUrl = Arb.string(1..80).bind(),
                    resolved = Arb.boolean().bind(),
                )
            }.orNull().bind(),
            timeframe = arbTimeframe.bind(),
            status = Arb.enum<ActionStatus>().bind(),
            createdAt = Arb.long(0L, Long.MAX_VALUE).bind(),
            sync = arbSyncMeta.bind(),
        )
    }

    val arbSuccess: Arb<PreviewResult.Success> = arbitrary {
        PreviewResult.Success(
            title = Arb.string(0..60).bind(),
            thumbnailUrl = Arb.string(0..80).orNull().bind(),
            sourceName = Arb.string(0..40).bind(),
        )
    }

    val arbUrl: Arb<String> = Arb.string(1..80)

    // Feature: action-tracker-app, Property 3: A resolved link preview is stored faithfully
    "Property 3: a resolved link preview is stored faithfully" {
        checkAll(100, arbSuccess, arbUrl, arbItem) { success, url, item ->
            // toLinkPreview carries the success metadata faithfully, resolved.
            val preview = PreviewMerge.toLinkPreview(success, url)
            preview.title shouldBe success.title
            preview.thumbnailUrl shouldBe success.thumbnailUrl
            preview.sourceName shouldBe success.sourceName
            preview.rawUrl shouldBe url
            preview.resolved shouldBe true

            // mergeInto stores that same preview and preserves every other field.
            val merged = PreviewMerge.mergeInto(item, success, url)
            merged.preview shouldBe preview
            merged shouldBe item.copy(preview = preview)
        }
    }
})
