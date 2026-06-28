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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * A lively confetti celebration overlay (Req 6c.3), drawn on a full-screen
 * [Canvas] so it floats above content without affecting layout. Trigger it via
 * [ConfettiController.celebrate]: two staggered bursts of mixed circle/rectangle
 * confetti fan upward like a popper, tumble with rotation and a little
 * horizontal drift, then fall under gravity and fade out.
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
    val originXFraction: Float,
    val angleRad: Float,
    val speed: Float,
    val color: Color,
    val size: Float,
    val isRect: Boolean,
    val rotationSpeed: Float,
    val drift: Float,
    val spin: Float,
)

/**
 * Renders the confetti overlay. Place it as the last child of a screen's root
 * Box so it draws on top. Each time [controller]'s trigger changes, a fresh
 * burst animates.
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
        particles = buildList {
            // Two launch points (left-of-center, right-of-center) for a fuller,
            // popper-like spread.
            repeat(180) { i ->
                val fromLeft = i % 2 == 0
                // Upward-biased fan: angles roughly between -160° and -20°.
                val baseDeg = if (fromLeft) -120f else -60f
                val spreadDeg = (Random.nextFloat() - 0.5f) * 120f
                add(
                    Particle(
                        originXFraction = if (fromLeft) 0.35f else 0.65f,
                        angleRad = Math.toRadians((baseDeg + spreadDeg).toDouble()).toFloat(),
                        speed = 0.55f + Random.nextFloat() * 0.85f,
                        color = colors[Random.nextInt(colors.size)],
                        size = 10f + Random.nextFloat() * 14f,
                        isRect = Random.nextBoolean(),
                        rotationSpeed = (Random.nextFloat() - 0.5f) * 8f,
                        drift = (Random.nextFloat() - 0.5f) * 0.4f,
                        spin = Random.nextFloat() * 360f,
                    ),
                )
            }
        }
        scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1700, easing = LinearEasing))
            particles = emptyList()
        }
    }

    if (particles.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        val originY = size.height * 0.55f
        val launch = size.minDimension * 1.15f
        // Fade only in the last third so the burst stays vivid.
        val alpha = if (t < 0.66f) 1f else ((1f - t) / 0.34f).coerceIn(0f, 1f)

        particles.forEach { p ->
            val originX = size.width * p.originXFraction
            val distance = launch * p.speed * t
            // Gravity pulls everything down over time (quadratic).
            val gravity = size.height * 1.1f * t * t
            val x = originX + (cos(p.angleRad) * distance).toFloat() + p.drift * size.width * t
            val y = originY + (sin(p.angleRad) * distance).toFloat() + gravity
            val rot = p.spin + p.rotationSpeed * t * 360f
            val color = p.color.copy(alpha = alpha)
            if (p.isRect) {
                rotate(degrees = rot, pivot = Offset(x, y)) {
                    drawRect(
                        color = color,
                        topLeft = Offset(x - p.size / 2f, y - p.size / 4f),
                        size = Size(p.size, p.size / 2f),
                    )
                }
            } else {
                drawCircle(color = color, radius = p.size / 2f, center = Offset(x, y))
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
