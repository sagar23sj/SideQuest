package com.sidequest.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sidequest.R
import kotlinx.coroutines.launch

/** How long the user must hold before the task is marked complete (Req 6c.2). */
private const val HOLD_DURATION_MS = 750

/**
 * A circular press-and-hold control that completes a task (Req 6c). While held,
 * a primary-colored ring fills around the button; once the hold is sustained for
 * [HOLD_DURATION_MS] the action fires with haptic feedback (Req 6c.3) and
 * [onCompleted] is invoked. Releasing early cancels and rewinds (Req 6c.4).
 *
 * When [completed] is already true the control shows a filled check and does not
 * re-trigger. Confetti is owned by the screen-level overlay
 * ([com.sidequest.ui.components.rememberConfettiController]); this control calls
 * [onCompleted], and the board triggers the celebration in response.
 */
@Composable
fun HoldToCompleteButton(
    completed: Boolean,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    onUndo: (() -> Unit)? = null,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }

    val ring = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val completeContainer = MaterialTheme.colorScheme.primary
    val idleContainer = MaterialTheme.colorScheme.surfaceContainer

    val holdLabel = stringResource(R.string.board_hold_to_complete_desc)
    val doneLabel = stringResource(R.string.status_completed)
    val undoLabel = stringResource(R.string.board_undo_complete_desc)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (completed) completeContainer else idleContainer)
            .drawBehind {
                if (!completed && progress.value > 0f) {
                    val stroke = 4.dp.toPx()
                    drawArc(
                        color = ring,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(
                            this.size.width - stroke,
                            this.size.height - stroke,
                        ),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            .pointerInput(completed, onUndo) {
                if (completed) {
                    // Tap a completed task to undo its completion (Req: undo).
                    if (onUndo != null) {
                        detectTapGestures(onTap = { onUndo() })
                    }
                    return@pointerInput
                }
                detectTapGestures(
                    onPress = {
                        // Begin filling the ring; commit when it reaches 1f.
                        val job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(HOLD_DURATION_MS, easing = LinearEasing),
                            )
                        }
                        val released = tryAwaitRelease()
                        job.cancel()
                        if (progress.value >= 1f) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCompleted()
                        } else {
                            // Released early (Req 6c.4): rewind, no state change.
                            scope.launch { progress.animateTo(0f, tween(180)) }
                        }
                    },
                )
            }
            .semantics {
                contentDescription = when {
                    completed && onUndo != null -> undoLabel
                    completed -> doneLabel
                    else -> holdLabel
                }
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = if (completed) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
