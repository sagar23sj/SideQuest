package com.sidequest.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * A lightweight confetti celebration overlay (Req 6c.3), drawn on a full-screen
 * [Canvas] so it floats above the content without affecting layout. Trigger it
 * via [ConfettiController.celebrate]; particles burst from the center, fall with
 * a little gravity, fade, and clear themselves.
 *
 * Implemented with Compose animation + Canvas (no third-party dependency) to
 * keep the build lean and fully offline.
 */
class ConfettiController {
    internal var trigger by mutableStateOf(0)
        private set

    /** Fires a confetti burst. */
    fun celebrate() {
        trigger++
    }
}

@Composable
fun rememberConfettiController(): ConfettiController = remember { ConfettiController() }

private data class Particle(
    val angle: Float,
    val speed: Float,
    val color: Color,
    val size: Float,
    val rotationSpeed: Float,
)

/**
 * Renders the confetti overlay. Place it as the last child of a screen's root
 * Box so it draws on top. Each time [controller]'s trigger changes, a fresh
 * burst animates from the center.
 */
@Composable
fun ConfettiOverlay(
    controller: ConfettiController,
    modifier: Modifier = Modifier,
    colors: List<Color> = DefaultConfettiColors,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var particles by remember { mutableStateOf<List<Particle>>(emptyList()) }

    androidx.compose.runtime.LaunchedEffect(controller.trigger) {
        if (controller.trigger == 0) return@LaunchedEffect
        particles = List(80) {
            Particle(
                angle = Random.nextFloat() * 360f,
                speed = 0.4f + Random.nextFloat() * 0.8f,
                color = colors[Random.nextInt(colors.size)],
                size = 6f + Random.nextFloat() * 8f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 20f,
            )
        }
        scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1100))
            particles = emptyList()
        }
    }

    if (particles.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        val originX = size.width / 2f
        val originY = size.height / 3f
        val maxRadius = size.minDimension * 0.9f

        particles.forEach { p ->
            val rad = Math.toRadians(p.angle.toDouble())
            val distance = maxRadius * p.speed * t
            val gravity = size.height * 0.5f * t * t
            val x = originX + (cos(rad) * distance).toFloat()
            val y = originY + (sin(rad) * distance).toFloat() + gravity
            val alpha = (1f - t).coerceIn(0f, 1f)
            drawCircle(
                color = p.color.copy(alpha = alpha),
                radius = p.size * (1f - t * 0.4f),
                center = Offset(x, y),
            )
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
)
