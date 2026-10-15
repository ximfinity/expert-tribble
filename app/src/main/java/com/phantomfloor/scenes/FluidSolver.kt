package com.phantomfloor.scenes

import android.opengl.GLES30
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.RenderTarget

/**
 * Grid-based incompressible fluid on the GPU (Stam's "stable fluids"): splat forces and dye,
 * vorticity confinement, pressure projection by Jacobi iteration, then semi-Lagrangian
 * advection of velocity and dye. Velocity is stored in sim texels per second.
 *
 * Needs half-float render targets; [supported] is false when the GPU can't render to them.
 */
class FluidSolver(private val simHeight: Int = 160) {

    private class PingPong(var read: RenderTarget, var write: RenderTarget) {
        fun swap() { val t = read; read = write; write = t }
        fun release() { read.release(); write.release() }
    }

    private lateinit var gl: GlResources
    private lateinit var advect: GlProgram
    private lateinit var splat: GlProgram
    private lateinit var curlProgram: GlProgram
    private lateinit var vorticity: GlProgram
    private lateinit var divergenceProgram: GlProgram
    private lateinit var pressureProgram: GlProgram
    private lateinit var gradient: GlProgram
    private lateinit var scale: GlProgram

    private var velocity: PingPong? = null
    private var dye: PingPong? = null
    private var pressure: PingPong? = null
    private var divergence: RenderTarget? = null
    private var curl: RenderTarget? = null

    var width = 1
        private set
    var height = 1
        private set
    private var aspect = 1f

    val supported get() = velocity != null

    val dyeTexture get() = dye?.read?.texture ?: 0
    val velocityTexture get() = velocity?.read?.texture ?: 0

    private val velPos = FloatArray(MAX_SPLATS * 4)
    private val velVal = FloatArray(MAX_SPLATS * 4)
    private var velCount = 0
    private val dyePos = FloatArray(MAX_SPLATS * 4)
    private val dyeVal = FloatArray(MAX_SPLATS * 4)
    private var dyeCount = 0

    fun onSurfaceCreated(gl: GlResources) {
        this.gl = gl
        val s = gl.shaders
        advect = s.program("fullscreen.vert", "fluid_advect.frag")
        splat = s.program("fullscreen.vert", "fluid_splat.frag")
        curlProgram = s.program("fullscreen.vert", "fluid_curl.frag")
        vorticity = s.program("fullscreen.vert", "fluid_vorticity.frag")
        divergenceProgram = s.program("fullscreen.vert", "fluid_divergence.frag")
        pressureProgram = s.program("fullscreen.vert", "fluid_pressure.frag")
        gradient = s.program("fullscreen.vert", "fluid_gradient.frag")
        scale = s.program("fullscreen.vert", "fluid_scale.frag")
        velocity = null; dye = null; pressure = null; divergence = null; curl = null
    }

    fun onResize(canvasWidth: Int, canvasHeight: Int) {
        release()
        aspect = canvasWidth.toFloat() / canvasHeight
        height = simHeight
        width = (simHeight * aspect).toInt().coerceAtLeast(1)
        if (!gl.halfFloatRenderable) return
        fun target() = RenderTarget.create(width, height, RenderTarget.Format.RGBA16F)
        val all = List(8) { target() }
        if (all.any { it == null }) {
            all.forEach { it?.release() }
            return
        }
        velocity = PingPong(all[0]!!, all[1]!!)
        dye = PingPong(all[2]!!, all[3]!!)
        pressure = PingPong(all[4]!!, all[5]!!)
        divergence = all[6]
        curl = all[7]
    }

    fun release() {
        velocity?.release(); dye?.release(); pressure?.release()
        divergence?.release(); curl?.release()
        velocity = null; dye = null; pressure = null; divergence = null; curl = null
    }

    /** Adds velocity (texels/s) at canvas UV, plus an optional outward [radial] push. */
    fun splatVelocity(x: Float, y: Float, radius: Float, vx: Float, vy: Float, radial: Float = 0f) {
        if (velCount >= MAX_SPLATS) return
        val o = velCount * 4
        velPos[o] = x; velPos[o + 1] = y; velPos[o + 2] = radius
        velVal[o] = vx; velVal[o + 1] = vy; velVal[o + 2] = 0f; velVal[o + 3] = radial
        velCount++
    }

    /**
     * Adds dye: [amount] of smoke density (negative removes it) plus [r], [g], [b] of colour that
     * is carried along with the smoke.
     */
    fun splatDye(x: Float, y: Float, radius: Float, amount: Float, r: Float = 0f, g: Float = 0f, b: Float = 0f) {
        if (dyeCount >= MAX_SPLATS) return
        val o = dyeCount * 4
        dyePos[o] = x; dyePos[o + 1] = y; dyePos[o + 2] = radius
        dyeVal[o] = amount; dyeVal[o + 1] = r; dyeVal[o + 2] = g; dyeVal[o + 3] = b
        dyeCount++
    }

    fun step(
        dt: Float,
        time: Float,
        curlStrength: Float,
        breeze: Float,
        velocityKeep: Float,
        dyeKeep: Float,
        iterations: Int = 20,
    ) {
        val vel = velocity ?: return
        val dyeField = dye ?: return
        val p = pressure ?: return
        val div = divergence ?: return
        val curlField = curl ?: return
        val tx = 1f / width
        val ty = 1f / height

        applySplats(vel, velPos, velVal, velCount, clampMin = -1e4f, radial = true)
        applySplats(dyeField, dyePos, dyeVal, dyeCount, clampMin = 0f, radial = false)
        velCount = 0
        dyeCount = 0

        curlField.bind()
        curlProgram.use()
        curlProgram.set2f("uTexel", tx, ty)
        curlProgram.texture("uVelocity", 0, vel.read.texture)
        gl.drawFullscreen()

        vel.write.bind()
        vorticity.use()
        vorticity.set2f("uTexel", tx, ty)
        vorticity.set1f("uDt", dt)
        vorticity.set1f("uCurlStrength", curlStrength)
        vorticity.set1f("uBreeze", breeze)
        vorticity.set1f("uTime", time)
        vorticity.texture("uVelocity", 0, vel.read.texture)
        vorticity.texture("uCurl", 1, curlField.texture)
        gl.drawFullscreen()
        vel.swap()

        div.bind()
        divergenceProgram.use()
        divergenceProgram.set2f("uTexel", tx, ty)
        divergenceProgram.texture("uVelocity", 0, vel.read.texture)
        gl.drawFullscreen()

        // Start from a relaxed copy of last frame's pressure: converges in fewer iterations.
        p.write.bind()
        scale.use()
        scale.set1f("uScale", 0.8f)
        scale.texture("uSource", 0, p.read.texture)
        gl.drawFullscreen()
        p.swap()

        pressureProgram.use()
        pressureProgram.set2f("uTexel", tx, ty)
        pressureProgram.texture("uDivergence", 1, div.texture)
        repeat(iterations) {
            p.write.bind()
            pressureProgram.texture("uPressure", 0, p.read.texture)
            gl.drawFullscreen()
            p.swap()
        }

        vel.write.bind()
        gradient.use()
        gradient.set2f("uTexel", tx, ty)
        gradient.texture("uPressure", 0, p.read.texture)
        gradient.texture("uVelocity", 1, vel.read.texture)
        gl.drawFullscreen()
        vel.swap()

        advect.use()
        advect.set2f("uTexel", tx, ty)
        advect.set1f("uDt", dt)
        vel.write.bind()
        advect.set1f("uDissipation", velocityKeep)
        advect.texture("uVelocity", 0, vel.read.texture)
        advect.texture("uSource", 1, vel.read.texture)
        gl.drawFullscreen()
        vel.swap()

        dyeField.write.bind()
        advect.set1f("uDissipation", dyeKeep)
        advect.texture("uVelocity", 0, vel.read.texture)
        advect.texture("uSource", 1, dyeField.read.texture)
        gl.drawFullscreen()
        dyeField.swap()
    }

    private fun applySplats(field: PingPong, pos: FloatArray, value: FloatArray, count: Int, clampMin: Float, radial: Boolean) {
        if (count == 0) return
        field.write.bind()
        splat.use()
        splat.set1f("uAspect", aspect)
        splat.set1f("uClampMin", clampMin)
        splat.set1f("uRadialMode", if (radial) 1f else 0f)
        splat.set1i("uSplatCount", count)
        splat.set4fv("uSplatPos", pos, count)
        splat.set4fv("uSplatVal", value, count)
        splat.texture("uTarget", 0, field.read.texture)
        gl.drawFullscreen()
        field.swap()
    }

    companion object {
        const val MAX_SPLATS = 16
    }
}
