package com.actiontracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SideQuest brand palette, extracted from the Stitch design system
 * (`design/stitch/*.html` Tailwind configs). It is a Material 3 Expressive
 * scheme seeded from a warm terracotta primary (`#9f4122`) with a violet
 * secondary and teal tertiary, set on cool blue-white surfaces for a warm-on-
 * cool contrast.
 *
 * These named tokens map onto Material 3 [androidx.compose.material3.ColorScheme]
 * roles in [com.actiontracker.ui.theme.ActionTrackerTheme].
 */

// --- Light scheme (from the Stitch "Light" screens) ---
val LightPrimary = Color(0xFF9F4122)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFF8A65)
val LightOnPrimaryContainer = Color(0xFF752305)

val LightSecondary = Color(0xFF6D4EA2)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFC5A3FF)
val LightOnSecondaryContainer = Color(0xFF533487)

val LightTertiary = Color(0xFF006A63)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFF53BBB1)
val LightOnTertiaryContainer = Color(0xFF004842)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF93000A)

val LightBackground = Color(0xFFF4FAFF)
val LightOnBackground = Color(0xFF001F2A)
val LightSurface = Color(0xFFF4FAFF)
val LightOnSurface = Color(0xFF001F2A)
val LightSurfaceVariant = Color(0xFFC9E7F7)
val LightOnSurfaceVariant = Color(0xFF56423C)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFE6F6FF)
val LightSurfaceContainer = Color(0xFFD9F2FF)
val LightSurfaceContainerHigh = Color(0xFFCEEDFD)
val LightSurfaceContainerHighest = Color(0xFFC9E7F7)

val LightOutline = Color(0xFF89726B)
val LightOutlineVariant = Color(0xFFDDC0B8)
val LightInverseSurface = Color(0xFF163440)
val LightInverseOnSurface = Color(0xFFE0F4FF)
val LightInversePrimary = Color(0xFFFFB59E)
val LightSurfaceTint = Color(0xFF9F4122)
val LightScrim = Color(0xFF000000)

// --- Dark scheme (generated from the same seed; the Stitch "Dark" screens
// reuse the light token map via Tailwind `dark:` utilities, so these are tuned
// dark anchors that keep the SideQuest brand recognizable). ---
val DarkPrimary = Color(0xFFFFB59E)
val DarkOnPrimary = Color(0xFF5E1700)
val DarkPrimaryContainer = Color(0xFF7F2A0D)
val DarkOnPrimaryContainer = Color(0xFFFFDBD0)

val DarkSecondary = Color(0xFFD4BBFF)
val DarkOnSecondary = Color(0xFF3B1C71)
val DarkSecondaryContainer = Color(0xFF543589)
val DarkOnSecondaryContainer = Color(0xFFEBDCFF)

val DarkTertiary = Color(0xFF71D7CD)
val DarkOnTertiary = Color(0xFF00382F)
val DarkTertiaryContainer = Color(0xFF005048)
val DarkOnTertiaryContainer = Color(0xFF8EF4E9)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF0F1417)
val DarkOnBackground = Color(0xFFDFE3E7)
val DarkSurface = Color(0xFF0F1417)
val DarkOnSurface = Color(0xFFDFE3E7)
val DarkSurfaceVariant = Color(0xFF463A36)
val DarkOnSurfaceVariant = Color(0xFFDDC0B8)
val DarkSurfaceContainerLowest = Color(0xFF0A0F12)
val DarkSurfaceContainerLow = Color(0xFF171D21)
val DarkSurfaceContainer = Color(0xFF1B2127)
val DarkSurfaceContainerHigh = Color(0xFF252B31)
val DarkSurfaceContainerHighest = Color(0xFF30363C)

val DarkOutline = Color(0xFFA08C84)
val DarkOutlineVariant = Color(0xFF53433D)
val DarkInverseSurface = Color(0xFFDFE3E7)
val DarkInverseOnSurface = Color(0xFF2D3135)
val DarkInversePrimary = Color(0xFF9F4122)
val DarkSurfaceTint = Color(0xFFFFB59E)
val DarkScrim = Color(0xFF000000)

/**
 * Game-specific accent colors used by Word Guess key/tile states and Spelling
 * Bee feedback. These map onto the brand's tertiary (success/correct),
 * secondary (present), and outline (absent) roles, exposed as semantic names so
 * the game screens read clearly. See [com.actiontracker.ui.theme.GameColors].
 */
val GuessCorrectLight = Color(0xFF006A63)
val GuessPresentLight = Color(0xFF6D4EA2)
val GuessAbsentLight = Color(0xFF89726B)

val GuessCorrectDark = Color(0xFF53BBB1)
val GuessPresentDark = Color(0xFFC5A3FF)
val GuessAbsentDark = Color(0xFF53433D)
