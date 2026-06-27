package com.sidequest.ui.board

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Visual treatment for a bucket "Quest" card: the tonal container colors plus a
 * topical icon. Buckets cycle through the brand's secondary / tertiary / primary
 * tonal palettes so each life area feels distinct and vibrant, matching the
 * `DESIGN.md` "Collection Chips" guidance.
 */
data class BucketVisual(
    val container: Color,
    val onContainer: Color,
    val iconContainer: Color,
    val onIconContainer: Color,
    val icon: ImageVector,
)

/**
 * Resolves a [BucketVisual] for a bucket from its [name] and stable [index].
 *
 * The icon is matched on common bucket keywords (travel, cooking, shopping,
 * etc.) with a friendly star fallback; the tonal palette is chosen by [index]
 * so adjacent quest cards alternate colors deterministically.
 */
fun bucketVisual(name: String, index: Int, scheme: ColorScheme): BucketVisual {
    val palette = when (index % 3) {
        0 -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary)
        1 -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary)
        else -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
    }
    val onIcon = when (index % 3) {
        0 -> scheme.onSecondary
        1 -> scheme.onTertiary
        else -> scheme.onPrimary
    }
    return BucketVisual(
        container = palette.first,
        onContainer = palette.second,
        iconContainer = palette.third,
        onIconContainer = onIcon,
        icon = iconFor(name),
    )
}

private fun iconFor(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        n.contains("travel") || n.contains("trip") || n.contains("flight") -> Icons.Filled.Flight
        n.contains("cook") || n.contains("food") || n.contains("recipe") -> Icons.Filled.Restaurant
        n.contains("shop") || n.contains("wishlist") || n.contains("buy") -> Icons.Filled.ShoppingBag
        n.contains("ritual") || n.contains("habit") || n.contains("wellness") -> Icons.Filled.SelfImprovement
        n.contains("learn") || n.contains("study") || n.contains("read") -> Icons.Filled.AutoStories
        n.contains("home") || n.contains("house") -> Icons.Filled.Home
        n.contains("art") || n.contains("design") || n.contains("create") -> Icons.Filled.Brush
        n.contains("stock") || n.contains("invest") || n.contains("finance") -> Icons.AutoMirrored.Filled.TrendingUp
        n.contains("coffee") || n.contains("cafe") || n.contains("drink") -> Icons.Filled.LocalCafe
        else -> Icons.Filled.Star
    }
}
