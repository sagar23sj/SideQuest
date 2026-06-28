package com.sidequest.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * A confetti **shower** overlay (Req 6c.3): mixed circle/rectangle confetti rain
 * down from above the top edge, drifting side to side with a gentle sway and
 * tumbling as they fall, then fade near the bottom. Drawn on a full-screen
 * [Canvas] so it floats above content without affecting layout. Trigger via
 * [ConfettiController.celebrate].
 *
 * Pure Compose animation + Canvas (no third-party dependency).
 */
class ConfettiController {
    internal var trigger by mutableStateOf(0)
        private set

    /** Fires a confetti shower. */
    fun celebrate() {
        trigger++
    }
}

@Composable
fun rememberConfettiController(): ConfettiController = remember { ConfettiController() }

private data class Flake(
    val xFraction: Float,
    val delay: Float,
    val fallSpeed: Float,
    val swayAmplitude: Float,
    val swayFreq: Float,
    val swayPhase: Float,
    val color: Color,
    val size: Float,
    val isRect: Boolean,
    val spin: Float,
    val spinSpeed: Float,
)

/**
 * Renders the confetti shower. Place it as the last child of a screen's root Box
 * so it draws on top. Each time [controller]'s trigger changes, a fresh shower
 * rains down.
 */
@Composable
fun ConfettiOverlay(
    controller: ConfettiController,
    modifier: Modifier = Modifier,
    colors: List<Color> = DefaultConfettiColors,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var flakes by remember { mutableStateOf<List<Flake>>(emptyList()) }

    androidx.compose.runtime.LaunchedEffect(controller.trigger) {
        if (controller.trigger == 0) return@LaunchedEffect
        flakes = List(160) {
            Flake(
                xFraction = Random.nextFloat(),
                delay = Random.nextFloat() * 0.45f,
                fallSpeed = 0.8f + Random.nextFloat() * 0.5f,
                swayAmplitude = 12f + Random.nextFloat() * 26f,
                swayFreq = 2f + Random.nextFloat() * 3f,
                swayPhase = Random.nextFloat() * 6.28f,
                color = colors[Random.nextInt(colors.size)],
                size = 9f + Random.nextFloat() * 13f,
                isRect = Random.nextBoolean(),
                spin = Random.nextFloat() * 360f,
                spinSpeed = (Random.nextFloat() - 0.5f) * 6f,
            )
        }
        scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(2200, easing = LinearEasing))
            flakes = emptyList()
        }
    }

    if (flakes.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        val h = size.height
        val w = size.width

        flakes.forEach { f ->
            // Each flake has its own staggered window so they keep arriving.
            val span = (1f - f.delay).coerceAtLeast(0.2f)
            val localT = ((t - f.delay) / span).coerceIn(0f, 1f)
            if (localT <= 0f) return@forEach

            // Fall from just above the top to just past the bottom.
            val y = -f.size + (h + f.size * 2f) * localT * f.fallSpeed
            if (y > h + f.size) return@forEach
            val x = w * f.xFraction + f.swayAmplitude * sin(f.swayPhase + localT * f.swayFreq * 6.28f)
            val rot = f.spin + f.spinSpeed * localT * 360f
            // Fade only in the last 12% of the flake's travel.
            val alpha = if (localT < 0.88f) 1f else ((1f - localT) / 0.12f).coerceIn(0f, 1f)
            val color = f.color.copy(alpha = alpha)

            if (f.isRect) {
                rotate(degrees = rot, pivot = Offset(x, y)) {
                    drawRect(
                        color = color,
                        topLeft = Offset(x - f.size / 2f, y - f.size / 4f),
                        size = Size(f.size, f.size / 2f),
                    )
                }
            } else {
                drawCircle(color = color, radius = f.size / 2f, center = Offset(x, y))
            }
        }
    }
}

private val DefaultConfettiColors = listOf(
    Color(0xFFFF8A65), // primary container coral
    Color(0xFF9F4122), // primary terracotta
    Color(0xFFC5A3FF), // secondary container violet
    Color(0xFF53BBB1), // tertiary container teal
    Color(0xFF8EF4E9), // tertiary fixed
    Color(0xFFFFB59E), // primary fixed dim
    Color(0xFFFFD54F), // warm gold accent
)
