package com.actiontracker.domain.wishlist

import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.SyncMeta
import com.actiontracker.domain.model.Timeframe
import com.actiontracker.domain.model.WishlistFields
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Example/unit tests for concrete wishlist editing branches in
 * [WishlistOperations]. These complement the property-based tests (Properties
 * 14 and 15) by pinning down specific editing scenarios: recording fields on a
 * wishlist item, the non-wishlist no-op boundary, designating/clearing shopping
 * membership, and the purchased toggle (including idempotency).
 *
 * _Requirements: 8.1, 8.3, 8.4_
 */
class WishlistEditingExampleTest : StringSpec({

    val sync = SyncMeta(updatedAt = 1_000L, version = 1L, deleted = false, dirty = false)

    // A baseline wishlist item with concrete, readable fields.
    fun wishlistItem(
        wishlist: WishlistFields? = WishlistFields(
            productName = "Old Name",
            sourceLink = "https://shop.example/old",
            purchased = false,
        ),
        isWishlistItem: Boolean = true,
        status: ActionStatus = ActionStatus.NOT_STARTED,
        title: String = "Captured product",
        sourceContent: String? = "https://shop.example/captured",
    ): ActionItem = ActionItem(
        id = "item-1",
        accountId = "acct-1",
        bucketId = "bucket-1",
        title = title,
        description = null,
        contentType = ContentType.LINK,
        sourceContent = sourceContent,
        preview = null,
        timeframe = Timeframe.Today,
        status = status,
        createdAt = 0L,
        isWishlistItem = isWishlistItem,
        wishlist = wishlist,
        sync = sync,
    )

    // ---- recordWishlistFields (Req 8.3) -------------------------------------

    "recordWishlistFields on a wishlist item updates productName and sourceLink" {
        val item = wishlistItem()

        val result = WishlistOperations.recordWishlistFields(
            item = item,
            productName = "New Name",
            sourceLink = "https://shop.example/new",
        )

        result.wishlist shouldBe WishlistFields(
            productName = "New Name",
            sourceLink = "https://shop.example/new",
            purchased = false,
        )
    }

    "recordWishlistFields with a null sourceLink clears the existing link" {
        val item = wishlistItem()

        val result = WishlistOperations.recordWishlistFields(
            item = item,
            productName = "New Name",
            sourceLink = null,
        )

        result.wishlist!!.sourceLink shouldBe null
        result.wishlist!!.productName shouldBe "New Name"
    }

    "recordWishlistFields leaves the purchased flag unchanged" {
        val purchasedItem = wishlistItem(
            wishlist = WishlistFields(
                productName = "Old Name",
                sourceLink = "https://shop.example/old",
                purchased = true,
            ),
            status = ActionStatus.COMPLETED,
        )

        val result = WishlistOperations.recordWishlistFields(
            item = purchasedItem,
            productName = "Edited Name",
            sourceLink = "https://shop.example/edited",
        )

        // The edit touches only productName and sourceLink.
        result.wishlist!!.purchased shouldBe true
        result.status shouldBe ActionStatus.COMPLETED
    }

    "recordWishlistFields on a non-wishlist item is a no-op (boundary)" {
        val nonWishlist = wishlistItem(wishlist = null, isWishlistItem = false)

        val result = WishlistOperations.recordWishlistFields(
            item = nonWishlist,
            productName = "Should Not Apply",
            sourceLink = "https://shop.example/ignored",
        )

        result shouldBe nonWishlist
    }

    // ---- applyShoppingMembership (Req 8.1) ----------------------------------

    "applyShoppingMembership uses defaultProductName when designating a fresh wishlist item" {
        val plain = wishlistItem(wishlist = null, isWishlistItem = false, sourceContent = null)

        val result = WishlistOperations.applyShoppingMembership(
            item = plain,
            isShoppingBucket = true,
            defaultProductName = "Wireless Mouse",
        )

        result.isWishlistItem shouldBe true
        result.wishlist shouldBe WishlistFields(
            productName = "Wireless Mouse",
            sourceLink = null,
            purchased = false,
        )
    }

    "applyShoppingMembership falls back to the item title and link when no defaultProductName" {
        val plain = wishlistItem(
            wishlist = null,
            isWishlistItem = false,
            title = "Captured product",
            sourceContent = "https://shop.example/captured",
        )

        val result = WishlistOperations.applyShoppingMembership(
            item = plain,
            isShoppingBucket = true,
            defaultProductName = null,
        )

        result.wishlist shouldBe WishlistFields(
            productName = "Captured product",
            sourceLink = "https://shop.example/captured",
            purchased = false,
        )
    }

    "applyShoppingMembership clearing shopping drops the wishlist fields" {
        val item = wishlistItem()

        val result = WishlistOperations.applyShoppingMembership(
            item = item,
            isShoppingBucket = false,
        )

        result.isWishlistItem shouldBe false
        result.wishlist shouldBe null
    }

    // ---- markPurchased (Req 8.4) --------------------------------------------

    "markPurchased toggles an unpurchased wishlist item to purchased and completed" {
        val item = wishlistItem(
            wishlist = WishlistFields(
                productName = "Headphones",
                sourceLink = "https://shop.example/headphones",
                purchased = false,
            ),
            status = ActionStatus.IN_PROGRESS,
        )

        val result = WishlistOperations.markPurchased(item)

        result.wishlist!!.purchased shouldBe true
        result.status shouldBe ActionStatus.COMPLETED
        // Other wishlist fields are preserved.
        result.wishlist!!.productName shouldBe "Headphones"
        result.wishlist!!.sourceLink shouldBe "https://shop.example/headphones"
    }

    "markPurchased is idempotent for an already-purchased item" {
        val alreadyPurchased = wishlistItem(
            wishlist = WishlistFields(
                productName = "Headphones",
                sourceLink = "https://shop.example/headphones",
                purchased = true,
            ),
            status = ActionStatus.COMPLETED,
        )

        val result = WishlistOperations.markPurchased(alreadyPurchased)

        result.wishlist!!.purchased shouldBe true
        result.status shouldBe ActionStatus.COMPLETED
        result shouldBe alreadyPurchased
    }
})
