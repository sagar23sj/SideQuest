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
    // The cover to overlay: the user's own image when set, otherwise a topical
    // stock photo for well-known domains (cooking, travel, …). The themed
    // gradient + icon is always drawn underneath as the placeholder, so if the
    // photo is still loading or fails (offline), the bucket still looks themed.
    val overlay = imageRef?.takeIf { it.isNotBlank() } ?: defaultCoverUrl(name)
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        // Themed placeholder: soft translucent circles behind a topical icon.
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = size.minDimension * 0.45f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.2f),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.06f),
                radius = size.minDimension * 0.5f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.95f),
            )
        }
        Icon(
            imageVector = iconFor(name),
            contentDescription = null,
            tint = onTint,
            modifier = Modifier.size(iconSize),
        )

        if (overlay != null) {
            coil.compose.AsyncImage(
                model = if (overlay.startsWith("content:") || overlay.startsWith("http")) {
                    overlay
                } else {
                    File(overlay)
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A stable, topical stock-photo URL for a well-known bucket domain (cooking,
 * travel, shopping, …) or null for an unrecognized name (which then shows the
 * themed gradient + icon). Uses LoremFlickr keyword photos with a fixed lock so
 * each domain gets a consistent image rather than a different one each load.
 */
private fun defaultCoverUrl(name: String): String? {
    val n = name.lowercase()
    val (keyword, lock) = when {
        n.contains("travel") || n.contains("trip") || n.contains("flight") -> "travel,landscape" to 11
        n.contains("cook") || n.contains("food") || n.contains("recipe") -> "cooking,food" to 21
        n.contains("shop") || n.contains("wishlist") || n.contains("buy") -> "shopping" to 31
        n.contains("ritual") || n.contains("habit") || n.contains("wellness") -> "wellness,yoga" to 41
        n.contains("learn") || n.contains("study") || n.contains("read") -> "books,study" to 51
        n.contains("home") || n.contains("house") -> "home,interior" to 61
        n.contains("art") || n.contains("design") || n.contains("create") -> "art,painting" to 71
        n.contains("stock") || n.contains("invest") || n.contains("finance") -> "finance,money" to 81
        n.contains("coffee") || n.contains("cafe") || n.contains("drink") -> "coffee" to 91
        else -> return null
    }
    return "https://loremflickr.com/640/480/$keyword?lock=$lock"
}

/**
 * Deterministic (gradientStart, gradientEnd, onColor) for a themed cover.
 *
 * The gradient is keyed off the bucket's *domain* (matched on the same keywords
 * as [iconFor]) so each life area gets a recognizable, photo-like color theme —
 * warm tones for cooking, sky tones for travel, greens for finance, and so on.
 * Unknown buckets fall back to a stable brand-tinted gradient chosen by name.
 */
private fun coverPalette(name: String, scheme: ColorScheme): Triple<Color, Color, Color> {
    val white = Color.White
    val n = name.lowercase()
    return when {
        n.contains("travel") || n.contains("trip") || n.contains("flight") ->
            Triple(Color(0xFF1F8FB3), Color(0xFF53BBB1), white) // sky → teal
        n.contains("cook") || n.contains("food") || n.contains("recipe") ->
            Triple(Color(0xFFE0552B), Color(0xFFFF8A65), white) // ember → coral
        n.contains("shop") || n.contains("wishlist") || n.contains("buy") ->
            Triple(Color(0xFFD2436A), Color(0xFFFF8A8A), white) // berry → blush
        n.contains("ritual") || n.contains("habit") || n.contains("wellness") ->
            Triple(Color(0xFF6D4EA2), Color(0xFFC5A3FF), white) // violet
        n.contains("learn") || n.contains("study") || n.contains("read") ->
            Triple(Color(0xFF3A4DB3), Color(0xFF7C8CF0), white) // indigo
        n.contains("home") || n.contains("house") ->
            Triple(Color(0xFFB5792B), Color(0xFFE8B873), white) // amber/wood
        n.contains("art") || n.contains("design") || n.contains("create") ->
            Triple(Color(0xFFA1308F), Color(0xFFE07AD0), white) // magenta
        n.contains("stock") || n.contains("invest") || n.contains("finance") ->
            Triple(Color(0xFF1E7D5A), Color(0xFF5FC79A), white) // green
        n.contains("coffee") || n.contains("cafe") || n.contains("drink") ->
            Triple(Color(0xFF6F4A2E), Color(0xFFB98B5E), white) // coffee brown
        else -> {
            val idx = (n.hashCode() % 3 + 3) % 3
            when (idx) {
                0 -> Triple(scheme.primary, scheme.primaryContainer, scheme.onPrimary)
                1 -> Triple(scheme.secondary, scheme.secondaryContainer, scheme.onSecondary)
                else -> Triple(scheme.tertiary, scheme.tertiaryContainer, scheme.onTertiary)
            }
        }
    }
}
