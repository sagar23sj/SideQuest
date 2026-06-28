package com.sidequest.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File

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

/** The topical icon for a bucket [name], for use outside the quest-card visual. */
fun bucketIconFor(name: String): ImageVector = iconFor(name)

/**
 * A bucket "cover": the user-chosen [imageRef] photo when present, otherwise a
 * domain-themed gradient with the bucket's topical icon. The gradient palette is
 * chosen deterministically from the [name] so each bucket reads distinctly. The
 * caller controls size and clipping via [modifier].
 */
@Composable
fun BucketCover(
    name: String,
    imageRef: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 40.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val (start, end, onTint) = coverPalette(name, scheme)
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageRef.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = if (imageRef.startsWith("content:") || imageRef.startsWith("http")) {
                    imageRef
                } else {
                    File(imageRef)
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = iconFor(name),
                contentDescription = null,
                tint = onTint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** Deterministic (gradientStart, gradientEnd, onColor) for a themed cover. */
private fun coverPalette(name: String, scheme: ColorScheme): Triple<Color, Color, Color> {
    val idx = (name.lowercase().hashCode() % 3 + 3) % 3
    return when (idx) {
        0 -> Triple(scheme.primary, scheme.primaryContainer, scheme.onPrimary)
        1 -> Triple(scheme.secondary, scheme.secondaryContainer, scheme.onSecondary)
        else -> Triple(scheme.tertiary, scheme.tertiaryContainer, scheme.onTertiary)
    }
}
