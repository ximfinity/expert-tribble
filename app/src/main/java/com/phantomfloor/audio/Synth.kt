package com.phantomfloor.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Building blocks for procedural sound: every effect is generated live, no audio files. */
internal const val SAMPLE_RATE = 44_100
private const val TWO_PI = (2 * PI).toFloat()

/** Fast xorshift white noise in [-1, 1]. */
internal class Noise(seed: Int) {
    private var s = if (seed == 0) 0x9E3779B9.toInt() else seed
    fun next(): Float {
        s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5)
        return (s and 0xFFFFFF) / 8388608f - 1f
    }
}

/** One-pole low-pass filter. */
internal class OnePole {
    private var z = 0f
    fun lp(x: Float, cutoffHz: Float): Float {
        val a = 1f - exp(-TWO_PI * cutoffHz / SAMPLE_RATE)
        z += (x - z) * a
        return z
    }
}

/** A sound being played. Renders additively into stereo buffers; returns false when finished. */
internal abstract class Voice(val gain: Float, pan: Float, var delaySamples: Int = 0) {
    private val l = sqrt((1f - pan.coerceIn(-1f, 1f)) * 0.5f)
    private val r = sqrt((1f + pan.coerceIn(-1f, 1f)) * 0.5f)
    protected var t = 0 // samples rendered

    fun render(left: FloatArray, right: FloatArray, n: Int): Boolean {
        var i = 0
        while (i < n && delaySamples > 0) { delaySamples--; i++ }
        while (i < n) {
            val s = sample() * gain
            left[i] += s * l
            right[i] += s * r
            t++
            i++
            if (done()) return false
        }
        return true
    }

    protected abstract fun sample(): Float
    protected abstract fun done(): Boolean
    protected val seconds get() = t.toFloat() / SAMPLE_RATE
}

/** Attack then exponential decay envelope over [dur] seconds. */
internal fun envelope(time: Float, attack: Float, dur: Float): Float {
    if (time < attack) return time / attack
    val k = (time - attack) / (dur - attack).coerceAtLeast(1e-3f)
    return exp(-k * 5f)
}

/** Filtered noise burst whose low-pass cutoff glides from [cutFrom] to [cutTo]. */
internal class NoiseVoice(
    private val dur: Float, private val attack: Float, private val cutFrom: Float, private val cutTo: Float,
    gain: Float, pan: Float, private val highpass: Boolean = false, seed: Int = 1,
) : Voice(gain, pan) {
    private val noise = Noise(seed)
    private val f = OnePole()
    override fun sample(): Float {
        val k = (seconds / dur).coerceIn(0f, 1f)
        val cut = cutFrom + (cutTo - cutFrom) * k
        val n = noise.next()
        val lp = f.lp(n, cut)
        return (if (highpass) n - lp else lp) * envelope(seconds, attack, dur)
    }
    override fun done() = seconds >= dur
}

enum class Wave { SINE, SQUARE, TRIANGLE, SAW }

/** Oscillator with a pitch glide and envelope. */
internal class ToneVoice(
    private val from: Float, private val to: Float, private val dur: Float, private val attack: Float,
    private val wave: Wave, gain: Float, pan: Float, delay: Int = 0,
) : Voice(gain, pan, delay) {
    private var phase = 0f
    override fun sample(): Float {
        val k = (seconds / dur).coerceIn(0f, 1f)
        phase += (from + (to - from) * k) / SAMPLE_RATE
        if (phase > 1f) phase -= 1f
        val s = when (wave) {
            Wave.SINE -> sin(phase * TWO_PI)
            Wave.SQUARE -> if (phase < 0.5f) 0.6f else -0.6f
            Wave.TRIANGLE -> 4f * kotlin.math.abs(phase - 0.5f) - 1f
            Wave.SAW -> (2f * phase - 1f) * 0.7f
        }
        return s * envelope(seconds, attack, dur)
    }
    override fun done() = seconds >= dur
}

/** Sparse random clicks: crackle, skitter, rustle, sizzle. [rate] = clicks per second. */
internal class CrackleVoice(
    private val dur: Float, private val rate: Float, private val cutoff: Float, gain: Float, pan: Float,
    seed: Int = 7, delay: Int = 0,
) : Voice(gain, pan, delay) {
    private val noise = Noise(seed)
    private val f = OnePole()
    private var click = 0f
    override fun sample(): Float {
        if ((noise.next() + 1f) * 0.5f < rate / SAMPLE_RATE) click = 0.6f + 0.4f * noise.next()
        click *= 0.993f
        val fade = 1f - (seconds / dur).coerceIn(0f, 1f)
        return f.lp(noise.next() * click, cutoff) * fade
    }
    override fun done() = seconds >= dur
}

/** Low, rough growl: detuned saws through a wobbling filter. */
internal class GrowlVoice(private val dur: Float, gain: Float, pan: Float) : Voice(gain, pan) {
    private var p1 = 0f
    private var p2 = 0f
    private val noise = Noise(31)
    private val f = OnePole()
    override fun sample(): Float {
        val wob = 1f + 0.3f * sin(seconds * 9f) + 0.2f * noise.next()
        p1 += 55f * wob / SAMPLE_RATE; if (p1 > 1f) p1 -= 1f
        p2 += 82f * wob / SAMPLE_RATE; if (p2 > 1f) p2 -= 1f
        val s = (2f * p1 - 1f) + 0.6f * (2f * p2 - 1f) + 0.4f * noise.next()
        return f.lp(s, 500f + 300f * sin(seconds * 3f)) * envelope(seconds, 0.25f, dur) * 0.6f
    }
    override fun done() = seconds >= dur
}
