package com.phantomfloor.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Building blocks for procedural sound: every effect is generated live, no audio files. */
const val SAMPLE_RATE = 44_100
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

/** Rises fast, holds, then fades over the last [release] seconds. For sustained sounds like screams. */
internal fun holdEnvelope(time: Float, attack: Float, dur: Float, release: Float): Float = when {
    time < attack -> time / attack
    time > dur - release -> ((dur - time) / release).coerceIn(0f, 1f)
    else -> 1f
}

/**
 * A shrieking scream: a sawtooth voice that leaps up in pitch, wavers with a ragged vibrato, and is
 * shaped by a crude vocal formant (band-pass) plus breath noise. [pitch] scales it (0.8 = lower).
 */
internal class ScreamVoice(private val dur: Float, private val pitch: Float, gain: Float, pan: Float, seed: Int, delay: Int = 0) :
    Voice(gain, pan, delay) {
    private var phase = 0f
    private val noise = Noise(seed)
    private val lo = OnePole()
    private val hi = OnePole()
    private val breath = OnePole()
    private var jitter = 0f
    override fun sample(): Float {
        val s = seconds
        jitter += (noise.next() * 0.5f - jitter) * 0.002f
        val glide = 1f - exp(-s * 18f)
        val f0 = pitch * (480f + 420f * glide - 120f * (s / dur)) * (1f + 0.045f * sin(s * 38f) + 0.06f * jitter)
        phase += f0 / SAMPLE_RATE
        if (phase > 1f) phase -= 1f
        val saw = 2f * phase - 1f
        // Band-pass ≈ difference of two low-passes around a vowel-like formant.
        val voiced = lo.lp(saw, 3200f * pitch) - hi.lp(saw, 750f * pitch)
        val air = breath.lp(noise.next(), 5000f) * 0.35f
        return (voiced * 1.6f + air) * holdEnvelope(s, 0.02f, dur, dur * 0.45f) * 0.6f
    }
    override fun done() = seconds >= dur
}

/** Breathy, syllable-like whispering: band-passed noise with a stuttering amplitude. */
internal class WhisperVoice(private val dur: Float, gain: Float, pan: Float, seed: Int) : Voice(gain, pan) {
    private val noise = Noise(seed)
    private val lo = OnePole()
    private val hi = OnePole()
    private val rate = 4f + (seed and 7) * 0.5f
    private val offset = (seed and 15) * 0.4f
    override fun sample(): Float {
        val s = seconds
        val syllable = (0.5f + 0.5f * sin(s * rate * 6.2832f + sin(s * 2.3f) * 2f)).let { it * it }
        val formant = 2200f + 900f * sin(s * 3.1f + offset)
        val n = noise.next()
        val band = lo.lp(n, formant + 1500f) - hi.lp(n, formant - 800f)
        return band * syllable * holdEnvelope(s, 0.15f, dur, 0.35f) * 1.4f
    }
    override fun done() = seconds >= dur
}
