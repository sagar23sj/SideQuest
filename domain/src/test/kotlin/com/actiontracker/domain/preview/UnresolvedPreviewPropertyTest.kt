package com.actiontracker.domain.preview

import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.LinkPreview
import com.actiontracker.domain.model.SyncMeta
import com.actiontracker.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
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
 * Property-based test for the unresolved / timeout preview fallback (Property 4).
 *
 * For any link URL, when the preview fails ([PreviewResult.Failure]) or times
 * out ([PreviewResult.Timeout]), capture still completes:
 * [PreviewMerge.toLinkPreview] yields a [LinkPreview] with `resolved == false`
 * whose `rawUrl` equals the original URL and whose title/thumbnail/source are
 * null (Req 1a.4, 1a.5). [PreviewMerge.mergeInto] stores that fallback preview
 * on an arbitrary [ActionItem] — the item still exists with every other field
 * unchanged — confirming the preview never blocks or discards the capture.
 *
 * _Requirements: 1a.4, 1a.5_
 */
class UnresolvedPreviewPropertyTest : StringSpec({

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
                LinkPreview(
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

    val arbUrl: Arb<String> = Arb.string(1..80)

    // An unresolved result: Failure or Timeout. The result's own rawUrl is
    // generated independently of the original URL to prove the merge always
    // stores the original URL passed to it.
    val arbUnresolved: Arb<PreviewResult> = Arb.choice(
        arbUrl.map { PreviewResult.Failure(it) },
        arbUrl.map { PreviewResult.Timeout(it) },
    )

    // Feature: action-tracker-app, Property 4: An unresolved preview falls back to the raw link without blocking capture
    "Property 4: an unresolved preview falls back to the raw link without blocking capture" {
        checkAll(100, arbUnresolved, arbUrl, arbItem) { result, url, item ->
            // toLinkPreview falls back to the raw link with no resolved metadata.
            val preview = PreviewMerge.toLinkPreview(result, url)
            preview.resolved shouldBe false
            preview.rawUrl shouldBe url
            preview.title.shouldBeNull()
            preview.thumbnailUrl.shouldBeNull()
            preview.sourceName.shouldBeNull()

            // mergeInto: the item still exists with the fallback preview and
            // every other field unchanged (capture completed, non-blocking).
            val merged = PreviewMerge.mergeInto(item, result, url)
            merged.preview shouldBe preview
            merged.preview?.resolved shouldBe false
            merged.preview?.rawUrl shouldBe url
            merged shouldBe item.copy(preview = preview)
        }
    }
})
