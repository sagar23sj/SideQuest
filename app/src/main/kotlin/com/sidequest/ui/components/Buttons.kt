package com.sidequest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring

/**
 * The primary "juicy" action button from the SideQuest `DESIGN.md`: fully
 * pill-shaped, high-contrast, with a subtle top-down gradient over the primary
 * color and a springy press-scale for tactile feedback.
 *
 * Buttons are pill-shaped (rounded-full) to maximize tap-target friendliness;
 * the primary action uses a gradient so it appears clickable and "juicy".
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(),
        label = "pillButtonScale",
    )

    val container = MaterialTheme.colorScheme.primary
    val onContainer = MaterialTheme.colorScheme.onPrimary
    val disabledAlpha = if (enabled) 1f else 0.4f

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier
            .scale(scale)
            .heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            container.copy(alpha = disabledAlpha),
                            container.copy(alpha = 0.88f * disabledAlpha),
                        ),
                    ),
                )
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = onContainer,
            )
        }
    }
}

/**
 * Secondary pill button: a softly-tinted container with an outline, used for
 * less-prominent actions beside a [PillButton]. Also pill-shaped with the same
 * springy press feedback for consistency.
 */
@Composable
fun SecondaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(),
        label = "secondaryPillButtonScale",
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .scale(scale)
            .heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * A two-tone gradient pill button used for game "Play" actions and other
 * accent CTAs (`DESIGN.md` "juicy" gradient buttons). The gradient runs from
 * [startColor] to [endColor] with springy press feedback.
 */
@Composable
fun GradientPillButton(
    text: String,
    onClick: () -> Unit,
    startColor: Color,
    endColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(),
        label = "gradientPillScale",
    )
    val alpha = if (enabled) 1f else 0.4f

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier
            .scale(scale)
            .heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(startColor.copy(alpha = alpha), endColor.copy(alpha = alpha)),
                    ),
                )
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
