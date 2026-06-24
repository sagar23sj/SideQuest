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
 * Property-based test for wishlist membership (Property 14).
 *
 * [WishlistOperations.applyShoppingMembership] must enforce the exact iff from
 * Req 8.2: an Action_Item is a wishlist item with wishlist fields present **if
 * and only if** its bucket is a shopping bucket. This test generates an
 * arbitrary [ActionItem] (covering every [Timeframe] variant, optional preview
 * and wishlist fields, and all statuses) together with an arbitrary
 * `isShoppingBucket` flag, applies the operation, and asserts the biconditional
 * holds in both directions.
 *
 * _Requirements: 8.2_
 */
class WishlistMembershipPropertyTest : StringSpec({

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

    val arbItem: Arb<ActionItem> = arbitrary {
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
            isWishlistItem = Arb.boolean().bind(),
            wishlist = arbWishlist.orNull().bind(),
            sync = arbSync.bind(),
        )
    }

    // Feature: action-tracker-app, Property 14: Items in a shopping bucket are wishlist items
    "Property 14: items in a shopping bucket are wishlist items" {
        checkAll(
            100,
            arbItem,
            Arb.boolean(),
            Arb.string(0..40).orNull(),
        ) { item, isShoppingBucket, defaultProductName ->
            val result = WishlistOperations.applyShoppingMembership(
                item = item,
                isShoppingBucket = isShoppingBucket,
                defaultProductName = defaultProductName,
            )

            // Property 14: the iff between wishlist membership and shopping bucket.
            (result.isWishlistItem && result.wishlist != null) shouldBe isShoppingBucket

            // Each direction made explicit for clarity.
            if (isShoppingBucket) {
                result.isWishlistItem shouldBe true
                (result.wishlist != null) shouldBe true
            } else {
                result.isWishlistItem shouldBe false
                result.wishlist shouldBe null
            }
        }
    }
})
