package com.sidequest.ui.voice

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sidequest.R
import java.io.File

/**
 * A compact play/pause control for a recorded voice-journal clip stored at
 * [audioPath] (an app-internal `.m4a` file). Owns its own [MediaPlayer] and
 * releases it when it leaves composition, so playback is self-contained per
 * entry. Missing files are ignored (the button simply does nothing), which can
 * happen for entries whose audio was uploaded and pruned by sync later.
 */
@Composable
fun AudioPlayerButton(
    audioPath: String,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var prepared by remember(audioPath) { mutableStateOf(false) }
    val player = remember { MediaPlayer() }
    val exists = remember(audioPath) { runCatching { File(audioPath).exists() }.getOrDefault(false) }

    DisposableEffect(Unit) {
        player.setOnCompletionListener {
            isPlaying = false
            runCatching { player.seekTo(0) }
        }
        onDispose { runCatching { player.release() } }
    }

    fun toggle() {
        if (!exists) return
        if (isPlaying) {
            runCatching { player.pause() }
            isPlaying = false
        } else {
            runCatching {
                if (!prepared) {
                    player.reset()
                    // Use a file descriptor rather than a raw path — more robust
                    // across devices for app-internal files.
                    java.io.FileInputStream(File(audioPath)).use { fis ->
                        player.setDataSource(fis.fd)
                    }
                    player.prepare()
                    prepared = true
                }
                player.start()
                isPlaying = true
            }.onFailure { isPlaying = false }
        }
    }

    Surface(
        onClick = ::toggle,
        enabled = exists,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(
                    if (isPlaying) R.string.voice_pause else R.string.voice_play,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
