package com.actiontracker.domain.model

import kotlinx.serialization.Serializable

/**
 * Extra fields carried by an [ActionItem] that lives in a shopping bucket.
 */
@Serializable
data class WishlistFields(
    val productName: String,
    val sourceLink: String?,
    val purchased: Boolean,
)
