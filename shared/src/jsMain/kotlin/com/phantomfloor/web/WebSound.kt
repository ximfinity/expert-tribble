package com.phantomfloor.web

import com.phantomfloor.audio.Ambience
import com.phantomfloor.audio.SAMPLE_RATE
import com.phantomfloor.audio.Sfx
import com.phantomfloor.audio.SoundMixer
import com.phantomfloor.audio.SoundOutput

/**
 * Plays the shared [SoundMixer] through Web Audio. Browsers only allow audio after a user
 * gesture, so [start] is called from the "click to start" handler. The mixer is pulled from a
 * ScriptProcessorNode callback on the main thread, which is plenty for a synth this light.
 */
class WebSound : SoundOutput {

    val mixer = SoundMixer()
    private var context: dynamic = null
    private var node: dynamic = null
    private var left = FloatArray(BLOCK)
    private var right = FloatArray(BLOCK)

    val running get() = context != null

    override fun play(sfx: Sfx, volume: Float, pan: Float) {
        // Before audio starts, drop effects instead of queueing a burst for later.
        if (running) mixer.play(sfx, volume, pan)
    }

    override fun setAmbience(ambience: Ambience) = mixer.setAmbience(ambience)

    fun start() {
        if (running) return
        val options: dynamic = js("({})")
        options.sampleRate = SAMPLE_RATE
        options.latencyHint = "interactive"
        val ctor: dynamic = js("window.AudioContext || window.webkitAudioContext")
        val ctx: dynamic = construct(ctor, options)
        val proc: dynamic = ctx.createScriptProcessor(BLOCK, 0, 2)
        proc.onaudioprocess = { e: dynamic ->
            val outL: dynamic = e.outputBuffer.getChannelData(0)
            val outR: dynamic = e.outputBuffer.getChannelData(1)
            val n = (outL.length as Number).toInt()
            if (left.size < n) { left = FloatArray(n); right = FloatArray(n) }
            mixer.render(left, right, n)
            // Kotlin FloatArrays are Float32Arrays: copy the first n samples straight across.
            outL.set(left.asDynamic().subarray(0, n))
            outR.set(right.asDynamic().subarray(0, n))
        }
        proc.connect(ctx.destination)
        ctx.resume()
        context = ctx
        node = proc
    }

    private fun construct(ctor: dynamic, arg: dynamic): dynamic = js("new ctor(arg)")

    private companion object {
        const val BLOCK = 1024
    }
}
