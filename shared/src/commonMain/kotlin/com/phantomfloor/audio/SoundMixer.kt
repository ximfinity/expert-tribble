package com.phantomfloor.audio

import com.phantomfloor.util.locked
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/** Where the frame loop sends sound: effects to play now and the scene's background bed. */
interface SoundOutput : SoundSink {
    fun setAmbience(ambience: Ambience)
}

/**
 * The real-time synthesizer, independent of any audio API: scenes call [play] (from any thread)
 * and the platform's audio callback calls [render] to mix effects and the crossfading [Ambience]
 * into float buffers. Android feeds it to an AudioTrack, the browser to Web Audio.
 */
class SoundMixer : SoundOutput {

    @Volatile var enabled = true
    @Volatile var volume = 0.8f

    /** Called (on the caller's thread) whenever a loud effect plays, so a mic analyzer can ignore it. */
    @Volatile var onLoudSound: (() -> Unit)? = null

    private class Request(val sfx: Sfx, val volume: Float, val pan: Float)

    private val requests = ArrayList<Request>()
    @Volatile private var target = Ambience.SILENT
    private val voices = ArrayList<Voice>()
    private val ambient = AmbientGenerator()
    private val rng = Random(5)

    override fun play(sfx: Sfx, volume: Float, pan: Float) {
        if (!enabled) return
        locked(requests) { if (requests.size < 64) requests.add(Request(sfx, volume, pan)) }
        if (sfx.loud) onLoudSound?.invoke()
    }

    override fun setAmbience(ambience: Ambience) {
        target = ambience
    }

    /** Drops queued effects and playing voices (e.g. when audio output stops). */
    fun reset() {
        locked(requests) { requests.clear() }
        voices.clear()
    }

    /**
     * Mixes the next [n] samples into [left] and [right] (overwriting them), soft-clipped into
     * -1..1 so stacked effects saturate gently instead of distorting. Audio thread only.
     */
    fun render(left: FloatArray, right: FloatArray, n: Int) {
        val queued = locked(requests) { if (requests.isEmpty()) null else requests.toList().also { requests.clear() } }
        queued?.forEach { voices.addAll(it.sfx.voices(it.volume, it.pan, rng)) }
        for (i in 0 until n) { left[i] = 0f; right[i] = 0f }
        ambient.render(left, right, n, target, voices, rng)
        val it = voices.iterator()
        while (it.hasNext()) if (!it.next().render(left, right, n)) it.remove()
        while (voices.size > MAX_VOICES) voices.removeAt(0)
        val v = if (enabled) volume else 0f
        for (i in 0 until n) {
            left[i] = tanh(left[i] * v)
            right[i] = tanh(right[i] * v)
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

    private companion object {
        const val MAX_VOICES = 48
        const val TAU = (2 * PI).toFloat()
    }
}
