package com.sidequest.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val CARD_HOLD_MS = 700

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
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
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
        border = border,
        content = content,
    )
}

/** The green border shown around a completed task card (Req: completed = green highlight). */
@Composable
private fun completedBorder(completed: Boolean): BorderStroke =
    if (completed) {
        BorderStroke(2.dp, CompletedGreen)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

/** A clear, friendly "done" green used for the completed-task highlight border. */
private val CompletedGreen = Color(0xFF2E7D32)

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
    completed: Boolean = false,
    onClick: (() -> Unit)? = null,
    onHoldComplete: (() -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    // Hold-to-complete mode: the whole card is the press target (Req 6c). A
    // quick tap opens the item; a sustained hold fills a progress overlay and
    // completes. The trailing status circle shows done-state and undoes on tap.
    if (onHoldComplete != null) {
        val haptics = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val progress = remember { Animatable(0f) }
        val shape = RoundedCornerShape(28.dp)
        val ring = MaterialTheme.colorScheme.primary

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .pointerInput(completed) {
                    if (completed) {
                        detectTapGestures(onTap = { onClick?.invoke() })
                        return@pointerInput
                    }
                    detectTapGestures(
                        onPress = {
                            val job = scope.launch {
                                progress.snapTo(0f)
                                progress.animateTo(1f, tween(CARD_HOLD_MS, easing = LinearEasing))
                            }
                            val released = tryAwaitRelease()
                            job.cancel()
                            if (progress.value >= 1f) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onHoldComplete()
                            } else {
                                scope.launch { progress.animateTo(0f, tween(180)) }
                                if (released) onClick?.invoke()
                            }
                        },
                    )
                },
        ) {
            SoftCard(modifier = Modifier.fillMaxWidth(), border = completedBorder(completed)) {
                TaskCardRow(
                    title = title,
                    subtitle = subtitle,
                    statusLabel = statusLabel,
                    statusColor = statusColor,
                    icon = icon,
                    thumbnailUrl = thumbnailUrl,
                    trailing = { StatusCircle(completed = completed, onUndo = onUndo) },
                )
            }
            // Progress fill that grows left→right while holding.
            if (!completed && progress.value > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.value)
                            .background(ring.copy(alpha = 0.16f)),
                    )
                }
            }
        }
        return
    }

    SoftCard(modifier = modifier.fillMaxWidth(), onClick = onClick, border = completedBorder(completed)) {
        TaskCardRow(
            title = title,
            subtitle = subtitle,
            statusLabel = statusLabel,
            statusColor = statusColor,
            icon = icon,
            thumbnailUrl = thumbnailUrl,
            trailing = trailing,
        )
    }
}

/** The shared inner row layout for a task card (thumbnail, texts, trailing). */
@Composable
private fun TaskCardRow(
    title: String,
    subtitle: String?,
    statusLabel: String,
    statusColor: Color,
    icon: ImageVector?,
    thumbnailUrl: String?,
    trailing: (@Composable () -> Unit)?,
) {
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

/**
 * The task card's status circle: a filled primary check when completed (tap to
 * undo), or an empty outlined circle when not (the card body is the hold target).
 */
@Composable
private fun StatusCircle(completed: Boolean, onUndo: (() -> Unit)?) {
    val tapMod = if (completed && onUndo != null) {
        Modifier.clickable(onClick = onUndo)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(tapMod)
            .background(
                if (completed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            .then(
                if (completed) {
                    Modifier
                } else {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (completed) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
