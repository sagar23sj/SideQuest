package com.sidequest.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A thick, rounded-cap circular progress ring — the "satisfying progress"
 * element from the SideQuest `DESIGN.md`. The track uses a soft surface tint and
 * the progress arc uses the primary coral, animating smoothly toward [progress].
 *
 * @param progress fraction in 0f..1f.
 * @param label centered text (e.g. "75%").
 */
@Composable
fun ProgressRing(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 12.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "progressRing",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainer
    val progressColor = MaterialTheme.colorScheme.primaryContainer

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                this.size.width - stroke,
                this.size.height - stroke,
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
