package com.phantomfloor.audio

import kotlin.random.Random

/** Every sound effect the scenes and games can play. All are synthesized on the fly. */
enum class Sfx(val loud: Boolean = false) {
    WHOOSH, SKITTER, SQUISH, SPLASH(loud = true), DRIP, BUBBLE, GROWL, BOOM(loud = true), CRACKLE,
    CRUNCH, RUSTLE, CHIME, POP, BONK, BEEP, BUZZ, TICK, EXPLOSION(loud = true), FANFARE(loud = true),
    SIZZLE, CLICK, CHIRP, TONE_1, TONE_2, TONE_3, TONE_4;

    internal fun voices(gain: Float, pan: Float, rng: Random): List<Voice> {
        val seed = rng.nextInt()
        fun ms(x: Float) = (x * SAMPLE_RATE).toInt()
        return when (this) {
            WHOOSH -> listOf(NoiseVoice(0.5f, 0.15f, 300f, 1800f, gain * 0.5f, pan, seed = seed))
            SKITTER -> listOf(CrackleVoice(0.45f, 90f, 5000f, gain * 1.2f, pan, seed))
            SQUISH -> listOf(
                NoiseVoice(0.2f, 0.005f, 1200f, 250f, gain * 0.8f, pan, seed = seed),
                ToneVoice(160f, 60f, 0.18f, 0.005f, Wave.SINE, gain * 0.5f, pan),
            )
            SPLASH -> listOf(
                NoiseVoice(0.7f, 0.004f, 7000f, 700f, gain * 0.7f, pan, seed = seed),
                ToneVoice(700f, 1500f, 0.07f, 0.002f, Wave.SINE, gain * 0.2f, pan, ms(0.25f)),
                ToneVoice(900f, 1800f, 0.06f, 0.002f, Wave.SINE, gain * 0.15f, pan, ms(0.4f)),
            )
            DRIP -> listOf(ToneVoice(900f + rng.nextFloat() * 500f, 1800f, 0.06f, 0.002f, Wave.SINE, gain * 0.35f, pan))
            BUBBLE -> listOf(ToneVoice(250f + rng.nextFloat() * 150f, 650f, 0.09f, 0.004f, Wave.SINE, gain * 0.3f, pan))
            GROWL -> listOf(GrowlVoice(1.4f, gain * 0.9f, pan))
            BOOM -> listOf(
                ToneVoice(75f, 32f, 1.1f, 0.004f, Wave.SINE, gain * 0.9f, pan),
                NoiseVoice(0.9f, 0.003f, 900f, 90f, gain * 0.8f, pan, seed = seed),
            )
            CRACKLE -> listOf(CrackleVoice(1.3f, 70f, 6000f, gain * 0.9f, pan, seed, ms(0.25f)))
            CRUNCH -> listOf(
                NoiseVoice(0.14f, 0.003f, 4000f, 1500f, gain * 0.45f, pan, seed = seed),
                CrackleVoice(0.12f, 400f, 6000f, gain * 0.6f, pan, seed + 1),
            )
            RUSTLE -> listOf(
                NoiseVoice(0.45f, 0.05f, 6000f, 4000f, gain * 0.25f, pan, highpass = true, seed = seed),
                CrackleVoice(0.4f, 160f, 7000f, gain * 0.5f, pan, seed + 2),
            )
            CHIME -> {
                val f = PENTATONIC[rng.nextInt(PENTATONIC.size)]
                listOf(
                    ToneVoice(f, f, 1.2f, 0.003f, Wave.SINE, gain * 0.25f, pan),
                    ToneVoice(f * 2.76f, f * 2.76f, 0.6f, 0.003f, Wave.SINE, gain * 0.08f, pan),
                )
            }
            POP -> listOf(
                NoiseVoice(0.04f, 0.001f, 5000f, 2000f, gain * 0.6f, pan, seed = seed),
                ToneVoice(700f, 220f, 0.06f, 0.001f, Wave.SINE, gain * 0.4f, pan),
            )
            BONK -> listOf(ToneVoice(420f, 170f, 0.16f, 0.002f, Wave.TRIANGLE, gain * 0.6f, pan))
            BEEP -> listOf(ToneVoice(880f, 880f, 0.12f, 0.003f, Wave.SQUARE, gain * 0.3f, pan))
            BUZZ -> listOf(ToneVoice(110f, 95f, 0.45f, 0.005f, Wave.SAW, gain * 0.5f, pan))
            TICK, CLICK -> listOf(NoiseVoice(0.015f, 0.001f, 4500f, 4500f, gain * 0.6f, pan, seed = seed))
            EXPLOSION -> listOf(
                ToneVoice(60f, 25f, 1.6f, 0.003f, Wave.SINE, gain, pan),
                NoiseVoice(1.6f, 0.003f, 2500f, 80f, gain, pan, seed = seed),
                CrackleVoice(1.8f, 120f, 5000f, gain * 0.8f, pan, seed + 3, ms(0.1f)),
            )
            FANFARE -> listOf(523f, 659f, 784f, 1047f).mapIndexed { i, f ->
                ToneVoice(f, f, if (i == 3) 0.6f else 0.16f, 0.005f, Wave.SQUARE, gain * 0.22f, pan, ms(i * 0.13f))
            }
            SIZZLE -> listOf(
                NoiseVoice(1.0f, 0.02f, 3000f, 3000f, gain * 0.25f, pan, highpass = true, seed = seed),
                CrackleVoice(1.0f, 220f, 8000f, gain * 0.5f, pan, seed + 4),
            )
            CHIRP -> listOf(
                ToneVoice(2200f, 3600f, 0.06f, 0.003f, Wave.SINE, gain * 0.15f, pan),
                ToneVoice(2400f, 3800f, 0.06f, 0.003f, Wave.SINE, gain * 0.12f, pan, ms(0.09f)),
            )
            TONE_1 -> simon(330f, gain, pan)
            TONE_2 -> simon(392f, gain, pan)
            TONE_3 -> simon(494f, gain, pan)
            TONE_4 -> simon(587f, gain, pan)
        }
    }

    private fun simon(f: Float, gain: Float, pan: Float) = listOf(
        ToneVoice(f, f, 0.45f, 0.01f, Wave.TRIANGLE, gain * 0.45f, pan),
        ToneVoice(f * 2f, f * 2f, 0.3f, 0.01f, Wave.SINE, gain * 0.1f, pan),
    )

    companion object {
        private val PENTATONIC = floatArrayOf(784f, 880f, 1047f, 1175f, 1319f, 1568f)
    }
}

/** Continuous background sound for a scene. Each field is a level from 0 (off) upward. */
data class Ambience(
    val wind: Float = 0f,
    val drone: Float = 0f,
    val droneHz: Float = 55f,
    val hiss: Float = 0f,
    val water: Float = 0f,
    val bubbles: Float = 0f,  // bubble pops per second
    val crickets: Float = 0f,
    val birds: Float = 0f,    // chirps per second
    val chimes: Float = 0f,   // chimes per second
    val skitter: Float = 0f,  // skitters per second
) {
    companion object {
        val SILENT = Ambience()
    }
}

/** Where scenes send sound events. */
interface SoundSink {
    /** [pan] -1 (left) .. 1 (right). */
    fun play(sfx: Sfx, volume: Float = 1f, pan: Float = 0f)

    object None : SoundSink {
        override fun play(sfx: Sfx, volume: Float, pan: Float) = Unit
    }
}

/** Renders one effect without an audio device (for tests). Returns the left channel and whether all voices ended. */
internal fun renderOffline(sfx: Sfx, seconds: Float): Pair<FloatArray, Boolean> {
    val n = (seconds * SAMPLE_RATE).toInt()
    val left = FloatArray(n)
    val right = FloatArray(n)
    val voices = sfx.voices(1f, 0f, Random(1)).toMutableList()
    var pos = 0
    val block = 512
    val l = FloatArray(block)
    val r = FloatArray(block)
    while (pos < n && voices.isNotEmpty()) {
        l.fill(0f); r.fill(0f)
        val it = voices.iterator()
        while (it.hasNext()) if (!it.next().render(l, r, block)) it.remove()
        for (i in 0 until minOf(block, n - pos)) { left[pos + i] = l[i]; right[pos + i] = r[i] }
        pos += block
    }
    return left to voices.isEmpty()
}
