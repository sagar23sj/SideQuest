package com.sidequest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A soft, "squishy" content card — the base surface used across the app. Uses
 * the 28dp card radius, a tonal fill, and a hairline outline for definition
 * (the `DESIGN.md` "Tonal Layers" depth model, avoiding harsh shadows).
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val base = Modifier
        .clip(shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Surface(
        modifier = modifier.then(base),
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        content = content,
    )
}

/**
 * A small status pill: a colored dot plus its label (e.g. "Not started",
 * "In progress"), matching the board's at-a-glance status language.
 */
@Composable
fun StatusPill(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A rich task card: an 72dp rounded thumbnail (real image when [thumbnailUrl] is
 * present, else an [icon] on a tonal background), a status pill, the task title
 * in expressive Outfit type, and an optional [trailing] control slot. This is
 * the primary repeating row on the board (`DESIGN.md` "Rich Task Cards").
 *
 * @param thumbnailUrl optional image URL loaded with Coil; falls back to [icon]
 *   while loading or on error so the row always renders something.
 * @param trailing optional composable rendered at the end of the row (e.g. a
 *   hold-to-complete control).
 */
@Composable
fun RichTaskCard(
    title: String,
    subtitle: String?,
    statusLabel: String,
    statusColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    thumbnailUrl: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    SoftCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StatusPill(label = statusLabel, dotColor = statusColor)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (trailing != null) {
                trailing()
            }
        }
    }
}
