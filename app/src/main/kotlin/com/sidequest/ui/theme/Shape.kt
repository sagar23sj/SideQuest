package com.sidequest.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * SideQuest shape scale from the Stitch `DESIGN.md`: the defining "squishy"
 * pill-shaped language. Cards use a 28dp radius for a friendly, tactile look,
 * inputs use 16dp to balance structure with softness, and buttons are fully
 * pill-shaped (applied directly at their call sites via [CircleShape]).
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
