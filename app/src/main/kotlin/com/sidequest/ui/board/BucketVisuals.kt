package com.sidequest.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ReceiptLong
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
import com.sidequest.R
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
        n.contains("movie") || n.contains("film") || n.contains("cinema") || n.contains("series") -> Icons.Filled.Movie
        n.contains("vault") || n.contains("keep") || n.contains("ticket") || n.contains("document") -> Icons.Filled.Lock
        n.contains("appointment") || n.contains("calendar") || n.contains("schedule") || n.contains("meeting") -> Icons.Filled.Event
        n.contains("bill") || n.contains("payment") || n.contains("subscription") -> Icons.Filled.ReceiptLong
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
    val (start, end, _) = coverPalette(name, scheme)
    val userImage = imageRef?.takeIf { it.isNotBlank() }
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        if (userImage == null) {
            // Bundled topical photo for the domain (a branded default for
            // unknown names). Ships in the app, so it's offline and instant; the
            // gradient shows underneath only while the bitmap decodes.
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(coverDrawableFor(name)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // A user-chosen photo overrides the default cover entirely.
            coil.compose.AsyncImage(
                model = if (userImage.startsWith("content:") || userImage.startsWith("http")) {
                    userImage
                } else {
                    File(userImage)
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A task "tile" backdrop for poster cards whose task has no thumbnail of its
 * own: the bucket's domain gradient with a large, faint topical icon watermark.
 *
 * This is deliberately distinct from [BucketCover] (which shows a photo): task
 * cards read as colored tiles, bucket cards read as photos, so a task never
 * echoes its bucket's cover image. The caller overlays the title/status.
 */
@Composable
fun TaskTileBackdrop(
    name: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 72.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val (start, end, _) = coverPalette(name, scheme)
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = bucketIconFor(name),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.22f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The bundled illustrated cover drawable for a bucket [name], matched on the
 * same domain keywords as [iconFor]. Always returns a drawable (a branded
 * default for unrecognized names), so every bucket has an attractive,
 * ship-with-the-app cover even offline.
 */
private fun coverDrawableFor(name: String): Int {
    val n = name.lowercase()
    return when {
        n.contains("travel") || n.contains("trip") || n.contains("flight") -> R.drawable.bucket_photo_travel
        n.contains("cook") || n.contains("food") || n.contains("recipe") -> R.drawable.bucket_photo_cooking
        n.contains("shop") || n.contains("wishlist") || n.contains("buy") -> R.drawable.bucket_photo_shopping
        n.contains("ritual") || n.contains("habit") || n.contains("wellness") ||
            n.contains("fitness") || n.contains("workout") || n.contains("exercise") -> R.drawable.bucket_photo_wellness
        n.contains("learn") || n.contains("study") || n.contains("read") -> R.drawable.bucket_photo_learning
        n.contains("home") || n.contains("house") -> R.drawable.bucket_photo_home
        n.contains("art") || n.contains("design") || n.contains("create") -> R.drawable.bucket_photo_art
        n.contains("stock") || n.contains("invest") || n.contains("finance") -> R.drawable.bucket_photo_finance
        n.contains("coffee") || n.contains("cafe") || n.contains("drink") -> R.drawable.bucket_photo_coffee
        n.contains("movie") || n.contains("film") || n.contains("cinema") || n.contains("series") -> R.drawable.bucket_photo_movies
        n.contains("vault") || n.contains("keep") || n.contains("ticket") || n.contains("document") -> R.drawable.bucket_photo_vault
        n.contains("appointment") || n.contains("calendar") || n.contains("schedule") || n.contains("meeting") -> R.drawable.bucket_photo_appointments
        n.contains("bill") || n.contains("payment") || n.contains("subscription") -> R.drawable.bucket_photo_bills
        else -> R.drawable.bucket_photo_default
    }
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
        n.contains("vault") || n.contains("keep") || n.contains("ticket") || n.contains("document") ->
            Triple(Color(0xFF37474F), Color(0xFF78909C), white) // slate / steel
        n.contains("appointment") || n.contains("calendar") || n.contains("schedule") || n.contains("meeting") ->
            Triple(Color(0xFF2B6CB0), Color(0xFF6FA8DC), white) // calendar blue
        n.contains("bill") || n.contains("payment") || n.contains("subscription") ->
            Triple(Color(0xFF8A6D1F), Color(0xFFD4B45A), white) // gold / receipt
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
