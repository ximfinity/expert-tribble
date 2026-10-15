package com.phantomfloor.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Real-time synthesizer for effects and ambience. A dedicated audio thread mixes voices into an
 * [AudioTrack]; scenes call [play] from the GL thread (lock-free queue), and the active scene's
 * [Ambience] crossfades in smoothly.
 */
class SoundEngine : SoundSink {

    @Volatile var enabled = true
    @Volatile var volume = 0.8f

    /** Called (on the caller's thread) whenever a loud effect plays, so the mic analyzer can ignore it. */
    @Volatile var onLoudSound: (() -> Unit)? = null

    private class Request(val sfx: Sfx, val volume: Float, val pan: Float)

    private val requests = ConcurrentLinkedQueue<Request>()
    @Volatile private var target = Ambience.SILENT
    @Volatile private var running = false
    private var thread: Thread? = null

    override fun play(sfx: Sfx, volume: Float, pan: Float) {
        if (!enabled || !running) return
        if (requests.size < 64) requests.add(Request(sfx, volume, pan))
        if (sfx.loud) onLoudSound?.invoke()
    }

    fun setAmbience(ambience: Ambience) {
        target = ambience
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ run() }, "phantom-audio").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        requests.clear()
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuf, BLOCK * 4 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            Log.w(TAG, "AudioTrack unavailable", t)
            running = false
            return
        }

        val left = FloatArray(BLOCK)
        val right = FloatArray(BLOCK)
        val pcm = ShortArray(BLOCK * 2)
        val voices = ArrayList<Voice>()
        val ambient = AmbientGenerator()
        val rng = Random(5)
        track.play()
        try {
            while (running) {
                while (true) {
                    val r = requests.poll() ?: break
                    voices.addAll(r.sfx.voices(r.volume, r.pan, rng))
                }
                left.fill(0f)
                right.fill(0f)
                ambient.render(left, right, BLOCK, target, voices, rng)
                val it = voices.iterator()
                while (it.hasNext()) if (!it.next().render(left, right, BLOCK)) it.remove()
                while (voices.size > MAX_VOICES) voices.removeAt(0)
                val v = if (enabled) volume else 0f
                for (i in 0 until BLOCK) {
                    // Soft clip so stacked effects saturate gently instead of distorting.
                    pcm[i * 2] = (tanh(left[i] * v) * 32000f).toInt().toShort()
                    pcm[i * 2 + 1] = (tanh(right[i] * v) * 32000f).toInt().toShort()
                }
                track.write(pcm, 0, pcm.size)
            }
        } finally {
            track.stop()
            track.release()
        }
    }

    /** Continuous beds (wind, drone, hiss, lapping water, crickets) plus randomly timed events. */
    private class AmbientGenerator {
        private var cur = Ambience.SILENT
        private val noise = Noise(99)
        private val windF = OnePole()
        private val hissF = OnePole()
        private val waterF = OnePole()
        private var t = 0L
        private var dronePhase1 = 0f
        private var dronePhase2 = 0f
        private var dronePhase3 = 0f
        private var cricketPhase = 0f

        fun render(left: FloatArray, right: FloatArray, n: Int, target: Ambience, voices: MutableList<Voice>, rng: Random) {
            // Glide every level toward the target (about a 1.5 s crossfade between scenes).
            val k = n / (1.5f * SAMPLE_RATE)
            cur = Ambience(
                wind = glide(cur.wind, target.wind, k), drone = glide(cur.drone, target.drone, k),
                droneHz = target.droneHz, hiss = glide(cur.hiss, target.hiss, k),
                water = glide(cur.water, target.water, k), bubbles = target.bubbles,
                crickets = glide(cur.crickets, target.crickets, k), birds = target.birds,
                chimes = target.chimes, skitter = target.skitter,
            )
            val a = cur
            val blockSeconds = n.toFloat() / SAMPLE_RATE
            for (i in 0 until n) {
                // Wrap at an hour so float precision stays good during all-night installations.
                val time = ((t + i) % (SAMPLE_RATE * 3600L)).toFloat() / SAMPLE_RATE
                var s = 0f
                if (a.wind > 0.001f) {
                    val gust = 0.55f + 0.45f * sin(time * 0.31f) * sin(time * 0.13f + 1f)
                    s += windF.lp(noise.next(), 250f + 500f * gust) * a.wind * 0.9f * gust
                }
                if (a.drone > 0.001f) {
                    dronePhase1 += a.droneHz / SAMPLE_RATE
                    dronePhase2 += a.droneHz * 1.007f / SAMPLE_RATE
                    dronePhase3 += a.droneHz * 1.5f / SAMPLE_RATE
                    if (dronePhase1 > 1f) dronePhase1 -= 1f
                    if (dronePhase2 > 1f) dronePhase2 -= 1f
                    if (dronePhase3 > 1f) dronePhase3 -= 1f
                    val sw = 0.7f + 0.3f * sin(time * 0.2f)
                    s += (sin(dronePhase1 * TAU) + sin(dronePhase2 * TAU) + 0.5f * sin(dronePhase3 * TAU)) * a.drone * 0.05f * sw
                }
                if (a.hiss > 0.001f) s += hissF.lp(noise.next(), 2500f) * a.hiss * 0.25f
                if (a.water > 0.001f) {
                    val lap = 0.5f + 0.5f * sin(time * 0.9f) * sin(time * 0.37f + 2f)
                    s += waterF.lp(noise.next(), 350f + 300f * lap) * a.water * 0.8f * lap
                }
                if (a.crickets > 0.001f) {
                    cricketPhase += 4600f / SAMPLE_RATE
                    if (cricketPhase > 1f) cricketPhase -= 1f
                    // Three quick pulses roughly every 0.7 s.
                    val cycle = time % 0.7f
                    val pulse = if (cycle < 0.09f && (cycle * 33f) % 1f < 0.5f) 1f else 0f
                    s += sin(cricketPhase * TAU) * pulse * a.crickets * 0.04f
                }
                left[i] += s
                right[i] += s
            }
            t += n
            // Random one-shots, panned around the room.
            fun maybe(rate: Float, sfx: Sfx, gain: Float) {
                if (rate > 0f && rng.nextFloat() < rate * blockSeconds) voices.addAll(sfx.voices(gain, rng.nextFloat() * 2f - 1f, rng))
            }
            maybe(a.bubbles, Sfx.BUBBLE, 0.6f)
            maybe(a.birds, Sfx.CHIRP, 0.7f)
            maybe(a.chimes, Sfx.CHIME, 0.5f)
            maybe(a.skitter, Sfx.SKITTER, 0.25f)
        }

        private fun glide(from: Float, to: Float, k: Float) = from + (to - from) * k.coerceAtMost(1f)
    }

    companion object {
        private const val TAG = "SoundEngine"
        private const val BLOCK = 512
        private const val MAX_VOICES = 48
        private const val TAU = (2 * PI).toFloat()
    }
}
