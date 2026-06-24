package com.actiontracker.domain.wishlist

import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.SyncMeta
import com.actiontracker.domain.model.Timeframe
import com.actiontracker.domain.model.WishlistFields
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
 * Property-based test for purchased completion (Property 15).
 *
 * For any wishlist item, [WishlistOperations.markPurchased] must set
 * `wishlist.purchased == true` **and** the item's [ActionStatus] to
 * [ActionStatus.COMPLETED] (Req 8.5). This test generates arbitrary wishlist
 * items — `isWishlistItem == true` with [WishlistFields] always present and a
 * varied starting state (purchased true/false, every status, all [Timeframe]
 * variants) — applies the operation, and asserts the conjunction holds.
 *
 * _Requirements: 8.5_
 */
class PurchasedCompletionPropertyTest : StringSpec({

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

    // Wishlist fields with a varied starting purchased flag so the test is meaningful.
    val arbWishlist: Arb<WishlistFields> = arbitrary {
        WishlistFields(
            productName = Arb.string(0..40).bind(),
            sourceLink = Arb.string(1..80).orNull().bind(),
            purchased = Arb.boolean().bind(),
        )
    }

    val arbSync: Arb<SyncMeta> = arbitrary {
        SyncMeta(
            updatedAt = Arb.long(0L, Long.MAX_VALUE).bind(),
            version = Arb.long(1L, Long.MAX_VALUE).bind(),
            deleted = Arb.boolean().bind(),
            dirty = Arb.boolean().bind(),
        )
    }

    // An arbitrary *wishlist* item: isWishlistItem == true with fields present,
    // and varied starting status (including already-completed and not-started).
    val arbWishlistItem: Arb<ActionItem> = arbitrary {
        ActionItem(
            id = Arb.string(1..36).bind(),
            accountId = Arb.string(1..24).bind(),
            bucketId = Arb.string(1..24).bind(),
            title = Arb.string(0..60).bind(),
            description = Arb.string(0..120).orNull().bind(),
            contentType = Arb.enum<ContentType>().bind(),
            sourceContent = Arb.string(0..120).orNull().bind(),
            preview = null,
            timeframe = arbTimeframe.bind(),
            status = Arb.enum<ActionStatus>().bind(),
            createdAt = Arb.long(0L, Long.MAX_VALUE).bind(),
            isWishlistItem = true,
            wishlist = arbWishlist.bind(),
            sync = arbSync.bind(),
        )
    }

    // Feature: action-tracker-app, Property 15: Marking a wishlist item purchased completes it
    "Property 15: marking a wishlist item purchased completes it" {
        checkAll(100, arbWishlistItem) { item ->
            val result = WishlistOperations.markPurchased(item)

            // Property 15: purchased flag set AND status completed.
            result.wishlist shouldBe item.wishlist!!.copy(purchased = true)
            result.wishlist!!.purchased shouldBe true
            result.status shouldBe ActionStatus.COMPLETED
        }
    }
})
