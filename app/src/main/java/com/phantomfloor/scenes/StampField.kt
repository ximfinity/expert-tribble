package com.phantomfloor.scenes

import android.opengl.GLES30
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.RenderTarget

/**
 * A persistent low-res field (ping-pong) that footsteps "stamp" into: snow depth with
 * footprints, garden growth. The update shader gets the previous state as `uState`, plus
 * `uStamps[]` (x, y uv, radius world, strength) and whatever uniforms [step] sets.
 */
class StampField(private val fragment: String, private val simHeight: Int = 256) {

    private lateinit var gl: GlResources
    private lateinit var program: GlProgram
    private val targets = arrayOfNulls<RenderTarget>(2)
    private var current = 0
    private val stamps = FloatArray(MAX_STAMPS * 4)
    private var stampCount = 0
    var aspect = 1f
        private set

    /** True when the field is 8-bit (small per-frame increments get lost; callers batch them). */
    var lowPrecision = false
        private set

    val texture get() = targets[current]?.texture ?: 0

    fun onSurfaceCreated(gl: GlResources) {
        this.gl = gl
        program = gl.shaders.program("fullscreen.vert", fragment)
        targets.fill(null)
    }

    fun onResize(canvasWidth: Int, canvasHeight: Int, clear: FloatArray = floatArrayOf(0f, 0f, 0f, 0f)) {
        targets.forEach { it?.release() }
        aspect = canvasWidth.toFloat() / canvasHeight
        val w = (simHeight * aspect).toInt().coerceAtLeast(1)
        var a = if (gl.halfFloatRenderable) RenderTarget.create(w, simHeight, RenderTarget.Format.RGBA16F) else null
        var b = if (a != null) RenderTarget.create(w, simHeight, RenderTarget.Format.RGBA16F) else null
        lowPrecision = a == null || b == null
        if (lowPrecision) {
            a?.release()
            a = RenderTarget.create(w, simHeight)
            b = RenderTarget.create(w, simHeight)
        }
        targets[0] = a
        targets[1] = b
        current = 0
        GLES30.glClearColor(clear[0], clear[1], clear[2], clear[3])
        for (t in targets) {
            t ?: continue
            t.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glClearColor(0f, 0f, 0f, 1f)
    }

    fun stamp(x: Float, y: Float, radius: Float, strength: Float) {
        if (stampCount >= MAX_STAMPS) return
        val o = stampCount * 4
        stamps[o] = x; stamps[o + 1] = y; stamps[o + 2] = radius; stamps[o + 3] = strength
        stampCount++
    }

    /** Runs the update shader once. [uniforms] sets scene-specific values on the bound program. */
    fun step(uniforms: (GlProgram) -> Unit) {
        val src = targets[current] ?: return
        val dst = targets[1 - current] ?: return
        dst.bind()
        program.use()
        program.texture("uState", 0, src.texture)
        program.set2f("uTexel", 1f / src.width, 1f / src.height)
        program.set1f("uAspect", aspect)
        program.set1i("uStampCount", stampCount)
        if (stampCount > 0) program.set4fv("uStamps", stamps, stampCount)
        uniforms(program)
        gl.drawFullscreen()
        current = 1 - current
        stampCount = 0
    }

    companion object {
        const val MAX_STAMPS = 16
    }
}
