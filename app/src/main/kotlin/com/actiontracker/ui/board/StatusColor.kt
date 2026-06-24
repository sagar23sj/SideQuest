package com.actiontracker.ui.board

import androidx.compose.ui.graphics.Color

/**
 * Parses a bucket-configured status color string into a Compose [Color],
 * falling back to [fallback] when the value is blank or not a parseable color
 * (Req 4.3).
 *
 * The board resolves each item's indicator color in the pure domain layer
 * ([com.actiontracker.domain.board.BoardItem.statusColor]); that value is
 * typically a hex string like `#RRGGBB` or `#AARRGGBB`, but a bucket may carry
 * a blank or placeholder color. Rendering must degrade gracefully rather than
 * crash, so unparseable values resolve to [fallback]. Parsing is done here with
 * a pure hex parser (no `android.graphics.Color`) so the indicator color is
 * unit-testable on the JVM.
 */
fun parseStatusColor(value: String, fallback: Color): Color =
    parseHexColorOrNull(value) ?: fallback

/**
 * Parses a `#RGB`, `#RRGGBB`, or `#AARRGGBB` hex string into a [Color], or
 * returns null when [value] is blank or not a valid hex color.
 */
private fun parseHexColorOrNull(value: String): Color? {
    val hex = value.trim().removePrefix("#")
    if (hex.isEmpty() || hex.any { !it.isHexDigit() }) return null

    return when (hex.length) {
        3 -> {
            // #RGB shorthand: expand each nibble to a full byte.
            val r = hex[0].hexValue() * 0x11
            val g = hex[1].hexValue() * 0x11
            val b = hex[2].hexValue() * 0x11
            Color(red = r, green = g, blue = b)
        }

        6 -> {
            val rgb = hex.toLong(16)
            Color(
                red = ((rgb shr 16) and 0xFF).toInt(),
                green = ((rgb shr 8) and 0xFF).toInt(),
                blue = (rgb and 0xFF).toInt(),
            )
        }

        8 -> {
            val argb = hex.toLong(16)
            Color(
                alpha = ((argb shr 24) and 0xFF).toInt(),
                red = ((argb shr 16) and 0xFF).toInt(),
                green = ((argb shr 8) and 0xFF).toInt(),
                blue = (argb and 0xFF).toInt(),
            )
        }

        else -> null
    }
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun Char.hexValue(): Int = Character.digit(this, 16)
