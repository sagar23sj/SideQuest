package com.sidequest.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/**
 * A built-in avatar a user can pick instead of uploading a photo: a fun emoji on
 * a soft brand-tinted circle. Stored as the [com.sidequest.data.local.UserPreferences]
 * avatar reference using the [PRESET_PREFIX] scheme so it round-trips through the
 * same single string field as a photo path.
 */
data class AvatarPreset(val emoji: String, val background: Color)

private const val PRESET_PREFIX = "preset:"

/** The selectable preset avatars. Order is stable — the index is what's stored. */
val AVATAR_PRESETS: List<AvatarPreset> = listOf(
    AvatarPreset("🦊", Color(0xFFFFB59E)),
    AvatarPreset("🐼", Color(0xFFC5A3FF)),
    AvatarPreset("🚀", Color(0xFFA5D8FF)),
    AvatarPreset("🌟", Color(0xFFFFD8A8)),
    AvatarPreset("🐱", Color(0xFFFFC4D6)),
    AvatarPreset("🦉", Color(0xFFB39DDB)),
    AvatarPreset("🐯", Color(0xFF90CAF9)),
    AvatarPreset("🍀", Color(0xFF8EF4E9)),
    AvatarPreset("🐶", Color(0xFFE8B873)),
    AvatarPreset("🎮", Color(0xFF53BBB1)),
    AvatarPreset("🎧", Color(0xFFB0BEC5)),
    AvatarPreset("🌈", Color(0xFFFF8A65)),
)

/** The avatar reference string for the preset at [index]. */
fun avatarRefForPreset(index: Int): String = "$PRESET_PREFIX$index"

/** The preset index encoded in [ref], or null if it isn't a preset reference. */
fun presetIndexOf(ref: String?): Int? =
    ref?.removePrefix(PRESET_PREFIX)?.takeIf { ref.startsWith(PRESET_PREFIX) }?.toIntOrNull()

/**
 * Renders a user's avatar from its stored [avatarRef], resolving in priority:
 *  1. a photo (a `file://` path, `content:` or `http` URI),
 *  2. a chosen [AVATAR_PRESETS] emoji (`preset:<index>`),
 *  3. otherwise a **default** auto-avatar derived deterministically from
 *     [displayName] (its initial on a brand-tinted circle), so every user has a
 *     pleasant avatar without choosing one.
 *
 * This is the single source of truth for avatar rendering, shared by the Profile
 * hero and the Home top-bar button, so they always match.
 */
@Composable
fun UserAvatar(
    avatarRef: String?,
    displayName: String?,
    modifier: Modifier = Modifier,
    emojiSize: Dp = 28.dp,
) {
    val ref = avatarRef?.takeIf { it.isNotBlank() }
    val presetIndex = presetIndexOf(ref)
    when {
        presetIndex != null -> {
            val preset = AVATAR_PRESETS[presetIndex % AVATAR_PRESETS.size]
            Box(
                modifier = modifier.clip(CircleShape).background(preset.background),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = preset.emoji, fontSize = emojiToSp(emojiSize))
            }
        }

        ref != null -> {
            AsyncImage(
                model = if (ref.startsWith("content:") || ref.startsWith("http")) ref else File(ref),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.clip(CircleShape),
            )
        }

        else -> DefaultAvatar(displayName = displayName, modifier = modifier, emojiSize = emojiSize)
    }
}

/** The auto-assigned default: the name's initial on a deterministic brand circle. */
@Composable
private fun DefaultAvatar(displayName: String?, modifier: Modifier, emojiSize: Dp) {
    val initial = displayName?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "🙂"
    val palette = listOf(
        Color(0xFFFFB59E), Color(0xFFC5A3FF), Color(0xFFA5D8FF),
        Color(0xFF8EF4E9), Color(0xFFFFD8A8), Color(0xFFB39DDB),
    )
    val bg = palette[((displayName ?: "").hashCode() % palette.size + palette.size) % palette.size]
    Box(
        modifier = modifier.clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            fontSize = emojiToSp(emojiSize),
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3A2B22),
        )
    }
}

/** Maps a circle [size] to a sensible glyph size. */
private fun emojiToSp(size: Dp) = (size.value * 0.62f).sp
