package com.actiontracker.domain.wishlist

import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.WishlistFields

/**
 * Pure shopping-wishlist logic (Req 8.1–8.5). Lives in `:domain` so it is
 * portable and validated by the shared Correctness Properties (14, 15) without
 * any Android/Room dependency. The app's wishlist/bucket repositories load the
 * current item, apply one of these operations, bump the sync metadata, and
 * persist the result; this object never touches [com.actiontracker.domain.model.SyncMeta]
 * because the app layer owns sync bookkeeping on persist.
 *
 * Every function here is pure and total: it never mutates its inputs and never
 * throws for any input.
 */
object WishlistOperations {

    /**
     * Enforces the wishlist membership invariant for [item] given whether its
     * bucket is a shopping bucket (Req 8.2, Property 14).
     *
     * The invariant kept by this function is exactly Property 14:
     * `isWishlistItem == true && wishlist != null` **if and only if**
     * [isShoppingBucket] is true. Concretely:
     *
     *  - **In a shopping bucket** (`isShoppingBucket == true`): the result has
     *    `isWishlistItem = true` and a non-null [WishlistFields]. Existing
     *    wishlist fields are preserved as-is; if the item has none, fields are
     *    created with:
     *      - `productName` = [defaultProductName] when provided and non-blank,
     *        otherwise the item's [ActionItem.title] (the natural product label
     *        for a captured item);
     *      - `sourceLink` = the item's [ActionItem.sourceContent] when it looks
     *        like an `http`/`https` link, otherwise null;
     *      - `purchased` = false (a freshly designated wishlist item has not
     *        been bought yet).
     *  - **Not in a shopping bucket** (`isShoppingBucket == false`): the result
     *    has `isWishlistItem = false` and `wishlist = null`, dropping any
     *    previously attached wishlist fields so the invariant's "only if"
     *    direction holds.
     *
     * @param item the item to normalize; not mutated.
     * @param isShoppingBucket whether the item's bucket is a shopping bucket.
     * @param defaultProductName optional product name used when creating fresh
     *   wishlist fields; falls back to the item title when null or blank.
     */
    fun applyShoppingMembership(
        item: ActionItem,
        isShoppingBucket: Boolean,
        defaultProductName: String? = null,
    ): ActionItem {
        if (!isShoppingBucket) {
            return item.copy(isWishlistItem = false, wishlist = null)
        }

        val fields = item.wishlist ?: WishlistFields(
            productName = defaultProductName?.takeIf { it.isNotBlank() } ?: item.title,
            sourceLink = item.sourceContent?.takeIf { isLink(it) },
            purchased = false,
        )
        return item.copy(isWishlistItem = true, wishlist = fields)
    }

    /**
     * Records or updates the product name and optional source link for a
     * wishlist [item] (Req 8.3).
     *
     * Only meaningful for wishlist items: when [item] is not a wishlist item
     * (`isWishlistItem == false` or `wishlist == null`) the item is returned
     * unchanged, keeping the function total. Otherwise the existing
     * [WishlistFields] are copied with the new [productName] and [sourceLink],
     * leaving the `purchased` flag untouched.
     *
     * @param item the wishlist item to edit; not mutated.
     * @param productName the new product name to store.
     * @param sourceLink the new optional source link to store (null clears it).
     */
    fun recordWishlistFields(
        item: ActionItem,
        productName: String,
        sourceLink: String?,
    ): ActionItem {
        val existing = item.wishlist
        if (!item.isWishlistItem || existing == null) {
            return item
        }
        return item.copy(
            wishlist = existing.copy(productName = productName, sourceLink = sourceLink),
        )
    }

    /**
     * Marks a wishlist [item] as purchased (Req 8.5, Property 15).
     *
     * Sets `wishlist.purchased = true` **and** the item's [ActionItem.status] to
     * [ActionStatus.COMPLETED], which is the conjunction Property 15 asserts.
     * Property 15 is stated about wishlist items, so this assumes [item] is one;
     * to stay total when the item has no [WishlistFields] yet, fresh purchased
     * fields are created (productName defaults to the item title) and the item is
     * flagged a wishlist item. The status is always set to completed regardless.
     *
     * @param item the wishlist item to mark purchased; not mutated.
     */
    fun markPurchased(item: ActionItem): ActionItem {
        val fields = item.wishlist?.copy(purchased = true)
            ?: WishlistFields(productName = item.title, sourceLink = null, purchased = true)
        return item.copy(
            isWishlistItem = true,
            wishlist = fields,
            status = ActionStatus.COMPLETED,
        )
    }

    /** Whether [text] looks like an `http`/`https` link usable as a source link. */
    private fun isLink(text: String): Boolean = LINK_REGEX.containsMatchIn(text)

    /** Matches an `http`/`https` URL token, mirroring the capture classifier. */
    private val LINK_REGEX = Regex("""\bhttps?://\S+""", RegexOption.IGNORE_CASE)
}
