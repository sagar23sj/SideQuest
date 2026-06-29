package com.sidequest.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import com.sidequest.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Plays a short, celebratory cue when a quest is completed.
 *
 * Primary cue is the bundled tune in `res/raw/completion_tune.mp3` (played via
 * [MediaPlayer]). If that can't be loaded/played for any reason, it falls back
 * to a synthesized bell arpeggio rendered through [AudioTrack], so a completion
 * always gets a cue and nothing ever throws or blocks the UI.
 */
object CompletionSound {

    private const val SAMPLE_RATE = 44_100
    private const val DURATION_SEC = 1.0

    /**
     * Plays the celebration cue (best-effort; never throws, never blocks UI).
     * Loads/plays the bundled tune on a short-lived background thread; on any
     * failure it plays the synthesized fallback.
     */
    fun play(context: Context) {
        Thread {
            val played = runCatching {
                val player = MediaPlayer.create(context.applicationContext, R.raw.completion_tune)
                if (player != null) {
                    player.setOnCompletionListener { mp -> runCatching { mp.release() } }
                    player.setOnErrorListener { mp, _, _ -> runCatching { mp.release() }; true }
                    player.start()
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
            if (!played) playSynthesized()
        }.apply { isDaemon = true }.start()
    }

    /** Renders and plays the synthesized fallback chime via [AudioTrack]. */
    private fun playSynthesized() {
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
        // Brighter bell timbre: fundamental + several decaying upper partials.
        val partials = floatArrayOf(1.0f, 0.5f, 0.3f, 0.18f)
        val attackSamples = (SAMPLE_RATE * 0.005).toInt()

        // A single struck bell-like note summed into [buf] from [startSample].
        fun addNote(startSample: Int, freq: Float, amp: Float, decayRate: Double) {
            val len = total - startSample
            if (len <= 0) return
            for (j in 0 until len) {
                val n = startSample + j
                val t = j.toFloat() / SAMPLE_RATE
                val attack = if (j < attackSamples) j.toFloat() / attackSamples else 1f
                val env = attack * exp(decayRate * j / len).toFloat()
                var wave = 0f
                for (p in partials.indices) {
                    // A hair of detune on the 2nd partial adds a pleasant shimmer.
                    val detune = if (p == 1) 1.003 else 1.0
                    wave += partials[p] * sin(2.0 * PI * freq * (p + 1) * detune * t).toFloat()
                }
                buf[n] += wave * env * amp
            }
        }

        // Ascending arpeggio that climbs and resolves on the octave.
        val stagger = (SAMPLE_RATE * 0.072).toInt()
        notes.forEachIndexed { idx, freq -> addNote(idx * stagger, freq, 0.13f, -4.2) }

        // Triumphant chord bloom: the full major chord rings out together right
        // after the run — the satisfying "reward" resolve.
        val bloomStart = notes.lastIndex * stagger + (SAMPLE_RATE * 0.02).toInt()
        notes.forEach { freq -> addNote(bloomStart, freq, 0.09f, -2.6) }

        // A short feedback echo adds sparkle and a sense of space.
        val delay = (SAMPLE_RATE * 0.14).toInt()
        val echoGain = 0.28f
        for (n in total - 1 downTo delay) {
            buf[n] += buf[n - delay] * echoGain
        }

        // Peak-normalize, soft-clamp, to 16-bit PCM.
        var peak = 1e-6f
        for (v in buf) {
            val mag = abs(v)
            if (mag > peak) peak = mag
        }
        val gain = (0.92f / peak).coerceAtMost(3.0f)
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
