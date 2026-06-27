package com.sidequest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
 * A section header: an expressive Outfit title with an optional trailing action
 * (e.g. "View all"). Used to break the board and other screens into the
 * generously-spaced sections the `DESIGN.md` calls for.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .clip(CircleShape)
                    .padding(4.dp),
            )
        }
    }
}

/**
 * A "Quest" bucket card for the horizontal carousel: a tall rounded card tinted
 * with the bucket's tonal color, a circular icon badge, the bucket name, and an
 * uppercase item count. Matches the `DESIGN.md` "Collection" cards.
 *
 * @param container the card's tonal background (e.g. secondary/tertiary container).
 * @param onContainer text/icon color that contrasts with [container].
 */
@Composable
fun QuestCard(
    name: String,
    itemCountLabel: String,
    icon: ImageVector,
    container: Color,
    onContainer: Color,
    iconContainer: Color,
    onIconContainer: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .width(168.dp)
            .height(200.dp),
        shape = RoundedCornerShape(28.dp),
        color = container,
        onClick = onClick ?: {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = onIconContainer,
                )
            }
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = itemCountLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * A settings/navigation row inside a grouped list: a circular tonal icon badge,
 * a title with optional subtitle, and an optional trailing slot (chevron, pill,
 * or switch). Matches the `DESIGN.md` profile settings list.
 */
@Composable
fun SettingsRow(
    title: String,
    icon: ImageVector,
    iconContainer: Color,
    onIconContainer: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val base = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    Row(
        modifier = base
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = onIconContainer)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) trailing()
    }
}
