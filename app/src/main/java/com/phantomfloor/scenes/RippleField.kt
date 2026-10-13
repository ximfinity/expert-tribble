package com.phantomfloor.scenes

import android.opengl.GLES30
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.RenderTarget

/**
 * GPU wave-equation simulation in two ping-pong render targets. Scenes drop impulses (footsteps,
 * bubbles, monsters surfacing) and sample [texture] as a signed height field.
 *
 * Uses RGBA16F when the GPU can render to half floats; otherwise falls back to RGBA8 with
 * biased encoding (works, but ripples fade sooner).
 */
class RippleField(private val simHeight: Int = 256) {

    private lateinit var gl: GlResources
    private lateinit var program: GlProgram
    private val targets = arrayOfNulls<RenderTarget>(2)
    private var current = 0
    private var aspect = 1f
    private val drops = FloatArray(MAX_DROPS * 4)
    private var dropCount = 0

    var encoded = false
        private set

    val texture: Int get() = targets[current]?.texture ?: 0

    fun onSurfaceCreated(gl: GlResources) {
        this.gl = gl
        program = gl.shaders.program("fullscreen.vert", "ripple_update.frag")
        targets.fill(null) // old context is gone; handles are invalid
    }

    fun onResize(canvasWidth: Int, canvasHeight: Int) {
        aspect = canvasWidth.toFloat() / canvasHeight
        val w = (simHeight * aspect).toInt()
        targets.forEach { it?.release() }
        var a = if (gl.halfFloatRenderable) RenderTarget.create(w, simHeight, RenderTarget.Format.RGBA16F) else null
        var b = if (a != null) RenderTarget.create(w, simHeight, RenderTarget.Format.RGBA16F) else null
        encoded = a == null || b == null
        if (encoded) {
            a?.release()
            a = RenderTarget.create(w, simHeight, RenderTarget.Format.RGBA8)
            b = RenderTarget.create(w, simHeight, RenderTarget.Format.RGBA8)
        }
        targets[0] = a
        targets[1] = b
        current = 0
        reset()
    }

    fun reset() {
        val clear = if (encoded) 0.5f else 0f
        GLES30.glClearColor(clear, clear, 0f, 1f)
        for (t in targets) {
            t ?: continue
            t.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glClearColor(0f, 0f, 0f, 1f)
    }

    /** Adds an impulse at canvas UV (x, y). [radius] in world units (fraction of canvas height). */
    fun drop(x: Float, y: Float, radius: Float, strength: Float) {
        if (dropCount >= MAX_DROPS) return
        val o = dropCount * 4
        drops[o] = x; drops[o + 1] = y; drops[o + 2] = radius; drops[o + 3] = strength
        dropCount++
    }

    /** Advances the simulation. Leaves the default framebuffer unbound: caller rebinds its target. */
    fun step(damping: Float = 0.985f, iterations: Int = 2) {
        val src0 = targets[0] ?: return
        targets[1] ?: return
        program.use()
        program.set2f("uTexel", 1f / src0.width, 1f / src0.height)
        program.set1f("uDamping", damping)
        program.set1f("uAspect", aspect)
        program.set1f("uEncoded", if (encoded) 1f else 0f)
        repeat(iterations) { iter ->
            val src = targets[current]!!
            val dst = targets[1 - current]!!
            dst.bind()
            program.texture("uState", 0, src.texture)
            val n = if (iter == 0) dropCount else 0
            program.set1i("uDropCount", n)
            if (n > 0) program.set4fv("uDrops", drops, n)
            gl.drawFullscreen()
            current = 1 - current
        }
        dropCount = 0
    }

    companion object {
        const val MAX_DROPS = 24
    }
}
