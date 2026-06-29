package com.sidequest.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Plays a short, celebratory cue when a quest is completed: a soft confetti
 * "pop" transient followed by a bright ascending sparkle (a major arpeggio with
 * a shimmer). The audio is *synthesized* into PCM at runtime and played through
 * [AudioTrack], so there's no sound file to ship and it works on every device.
 *
 * Fully fail-soft: synthesis and playback are guarded and run on a short-lived
 * background thread, so a busy/unavailable audio path never throws and never
 * blocks completion — you just don't hear the chime.
 */
object CompletionSound {

    private const val SAMPLE_RATE = 44_100
    private const val DURATION_SEC = 0.7

    /** Plays the celebration cue (best-effort; never throws, never blocks UI). */
    fun play() {
        Thread {
            runCatching {
                val samples = synthesize()
                val track = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    samples.size * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE,
                )
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep((DURATION_SEC * 1000).toLong() + 150)
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }.apply { isDaemon = true }.start()
    }

    /**
     * Builds the PCM for a clean, rewarding chime: a major arpeggio (C5–E5–G5–C6)
     * that rises and resolves on the octave. Each note is a soft bell-like tone
     * (fundamental + gently decaying upper partials) with a quick attack and a
     * long exponential ring, so the notes bloom into a consonant chord. No noise
     * — just a bright, happy "success" cue. Peak-normalized to avoid clipping.
     */
    private fun synthesize(): ShortArray {
        val total = (SAMPLE_RATE * DURATION_SEC).toInt()
        val buf = FloatArray(total)

        val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f) // C5, E5, G5, C6
        // Soft bell timbre: fundamental + decaying 2nd/3rd partials.
        val partialAmp = floatArrayOf(1.0f, 0.45f, 0.22f)
        val staggerSamples = (SAMPLE_RATE * 0.078).toInt()
        val attackSamples = (SAMPLE_RATE * 0.006).toInt()

        notes.forEachIndexed { idx, freq ->
            val start = idx * staggerSamples
            // The last (resolving octave) note rings a touch louder and longer.
            val emphasis = if (idx == notes.lastIndex) 1.15f else 1.0f
            val ringSamples = total - start
            for (j in 0 until ringSamples) {
                val n = start + j
                if (n >= total) break
                val t = j.toFloat() / SAMPLE_RATE
                val attack = if (j < attackSamples) j.toFloat() / attackSamples else 1f
                val decay = exp(-4.2 * j / ringSamples).toFloat()
                val env = attack * decay * emphasis
                var wave = 0f
                for (p in partialAmp.indices) {
                    wave += partialAmp[p] * sin(2.0 * PI * freq * (p + 1) * t).toFloat()
                }
                buf[n] += wave * env * 0.16f
            }
        }

        // Peak-normalize, soft-clamp, to 16-bit PCM.
        var peak = 1e-6f
        for (v in buf) {
            val mag = abs(v)
            if (mag > peak) peak = mag
        }
        val gain = (0.9f / peak).coerceAtMost(3.0f)
        val out = ShortArray(total)
        for (i in 0 until total) {
            var s = buf[i] * gain
            if (s > 1f) s = 1f
            if (s < -1f) s = -1f
            out[i] = (s * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
