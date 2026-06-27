package com.sidequest.domain.capture

import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.ContentType
import com.sidequest.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
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
 * Property-based test for the confirm-capture invariants (Property 2).
 *
 * When a user confirms a [CaptureDraft] with a selected bucket and timeframe,
 * [CaptureOperations.buildActionItem] must produce an Action_Item that starts
 * as [ActionStatus.NOT_STARTED] and stores the selected bucket and timeframe
 * exactly as chosen (Req 1.5). This test generates arbitrary drafts (varying
 * content type, title, source content, and account), an arbitrary bucket id,
 * and an arbitrary timeframe (covering every variant including
 * [Timeframe.SpecificDate]) and asserts those invariants hold, along with
 * sanity checks on the carried-over account, creation time, and fresh sync
 * metadata.
 *
 * _Requirements: 1.5_
 */
class ConfirmCapturePropertyTest : StringSpec({

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

    val arbDraft: Arb<CaptureDraft> = arbitrary {
        CaptureDraft(
            accountId = Arb.string(1..24).bind(),
            title = Arb.string(0..60).bind(),
            contentType = Arb.enum<ContentType>().bind(),
            sourceContent = Arb.string(0..120).orNull().bind(),
        )
    }

    // Feature: action-tracker-app, Property 2: Confirming capture creates a not-started item preserving its bucket and timeframe
    "Property 2: confirming capture creates a not-started item preserving its bucket and timeframe" {
        checkAll(
            100,
            arbDraft,
            Arb.string(1..24),
            arbTimeframe,
            Arb.string(1..36),
            Arb.long(0L, Long.MAX_VALUE),
        ) { draft, bucketId, timeframe, id, now ->
            val item = CaptureOperations.buildActionItem(
                draft = draft,
                bucketId = bucketId,
                timeframe = timeframe,
                id = id,
                now = now,
            )

            // Core invariants (Property 2).
            item.status shouldBe ActionStatus.NOT_STARTED
            item.bucketId shouldBe bucketId
            item.timeframe shouldBe timeframe

            // Sanity checks: content/account carried over, fresh dirty sync record.
            item.accountId shouldBe draft.accountId
            item.createdAt shouldBe now
            item.sync.version shouldBe 1
            item.sync.deleted shouldBe false
            item.sync.dirty shouldBe true
        }
    }
})
