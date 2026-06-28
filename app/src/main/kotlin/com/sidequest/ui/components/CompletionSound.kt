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
    private const val DURATION_SEC = 0.62

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
     * Builds the PCM for a "party popper": a few quick clap-like noise bursts
     * (applause feel) followed by a confetti "shower" — a softly flickering
     * noise tail that rises then fades — plus a tiny bright sparkle to finish.
     * A one-pole low-pass softens the hiss, and the mix is peak-normalized.
     */
    private fun synthesize(): ShortArray {
        val total = (SAMPLE_RATE * DURATION_SEC).toInt()
        val buf = FloatArray(total)
        val rnd = java.util.Random(11)

        // --- Claps: short, snappy noise bursts spaced like a quick applause. ---
        val claps = arrayOf(0.0 to 1.0, 0.07 to 0.9, 0.15 to 0.8)
        for ((startSec, amp) in claps) {
            val start = (SAMPLE_RATE * startSec).toInt()
            val len = (SAMPLE_RATE * 0.05).toInt()
            for (j in 0 until len) {
                val n = start + j
                if (n >= total) break
                val env = exp(-30.0 * j / len).toFloat() // fast decay = snappy
                buf[n] += (rnd.nextFloat() * 2f - 1f) * env * amp.toFloat() * 0.7f
            }
        }

        // --- Confetti shower: flickering noise that swells then decays. ---
        val showerStart = (SAMPLE_RATE * 0.16).toInt()
        for (n in showerStart until total) {
            val p = (n - showerStart).toFloat() / (total - showerStart)
            val env = (exp(-3.2 * p) * (1.0 - exp(-22.0 * p))).toFloat()
            val flicker = 0.55f + 0.45f * rnd.nextFloat()
            buf[n] += (rnd.nextFloat() * 2f - 1f) * env * flicker * 0.2f
        }

        // --- A small bright sparkle accent near the start of the shower. ---
        val sparkleFreqs = floatArrayOf(1568.0f, 2093.0f) // G6, C7
        sparkleFreqs.forEachIndexed { idx, freq ->
            val start = (SAMPLE_RATE * (0.12 + idx * 0.06)).toInt()
            val len = (SAMPLE_RATE * 0.22).toInt()
            for (j in 0 until len) {
                val n = start + j
                if (n >= total) break
                val t = j.toFloat() / SAMPLE_RATE
                val env = exp(-6.0 * j / len).toFloat()
                buf[n] += sin(2.0 * PI * freq * t).toFloat() * env * 0.12f
            }
        }

        // --- Gentle one-pole low-pass to soften harsh hiss. ---
        var prev = 0f
        val a = 0.4f
        for (i in buf.indices) {
            prev += a * (buf[i] - prev)
            buf[i] = prev
        }

        // --- Peak-normalize, soft-clamp, to 16-bit PCM. ---
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
