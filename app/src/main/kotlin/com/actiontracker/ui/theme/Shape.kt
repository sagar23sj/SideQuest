package com.actiontracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * SideQuest shape scale from the Stitch design system: generous rounding
 * (default 16dp, large 32dp, full pills) for the friendly, expressive feel.
 * The capture FAB uses a 20dp squircle defined inline at its call site.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
