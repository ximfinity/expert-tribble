package com.phantomfloor.scenes

import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * GROUND SMOKE: low fog-machine smoke, simulated as a real fluid. Emitters around the edges
 * pour smoke in; feet shove it aside and stir it into curls that drift on after them.
 */
class SmokeScene : Scene("smoke", "Ground Smoke") {

    private val amount = SceneParam("amount", "Smoke output", 0f, 2f, 1f)
    private val emitters = SceneParam("emitters", "Fog machines", 1f, 6f, 3f, step = 1f)
    private val linger = SceneParam("linger", "How long it lingers", 0f, 1f, 0.6f)
    private val swirl = SceneParam("swirl", "Swirl", 0f, 2f, 1f)
    private val push = SceneParam("push", "Push from feet", 0f, 2f, 1f)
    private val tint = SceneParam("tint", "White · Green · Purple", 0f, 2f, 0f, step = 1f)
    private val brightness = SceneParam("bright", "Brightness", 0.2f, 1.5f, 0.85f)

    override val params = listOf(amount, emitters, linger, swirl, push, tint, brightness)
    override val resolutionScale = 0.75f

    private lateinit var display: GlProgram
    private val fluid = FluidSolver()

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        display = gl.shaders.program("fullscreen.vert", "smoke.frag")
        fluid.onSurfaceCreated(gl)
    }

    override fun onResize(width: Int, height: Int) {
        super.onResize(width, height)
        fluid.onResize(width, height)
    }

    override fun update(frame: FrameState) {
        if (!fluid.supported) return
        val dt = frame.dt.coerceIn(1f / 120f, 1f / 30f)
        val w = fluid.width.toFloat()
        val h = fluid.height.toFloat()

        // Fog machines sit on the edges, nozzles slowly sweeping, puffing harder on loud sounds.
        val n = emitters.int
        val output = amount.value * (1f + frame.bass * 0.8f)
        for (i in 0 until n) {
            val along = (i + 0.5f) / n
            val wobble = 0.08f * sin(frame.time * 0.3f + i * 2.1f)
            val (x, y, nx, ny) = edgePoint((along + wobble + 1f) % 1f)
            val sweep = 0.6f * sin(frame.time * 0.4f + i * 1.3f)
            val dx = nx * cos(sweep) - ny * sin(sweep)
            val dy = nx * sin(sweep) + ny * cos(sweep)
            fluid.splatDye(x, y, 0.08f, output * dt * 7f)
            fluid.splatVelocity(x, y, 0.07f, dx * 600f * output * dt, dy * 600f * output * dt)
        }

        // Feet: drag smoke along with their motion and shove it outward; a little clears away.
        for (p in frame.people) {
            val k = push.value
            val speed = hypot(p.vx * frame.aspect, p.vy)
            fluid.splatVelocity(p.x, p.y, 0.05f, p.vx * w * k * 0.6f, p.vy * h * k * 0.6f, radial = (20f + speed * 60f) * k * dt * 6f)
            fluid.splatDye(p.x, p.y, 0.035f, -dt * 1.5f * k)
        }

        val keep = 0.6f + 0.38f * linger.value // fraction of smoke left after one second
        fluid.step(dt, frame.time, curlStrength = 25f * swirl.value, breeze = 30f * swirl.value, velocityKeep = 0.85f, dyeKeep = keep)
    }

    /** Point on the canvas border (t in 0..1 around the perimeter) and its inward normal. */
    private fun edgePoint(t: Float): FloatArray {
        val s = t * 4f
        return when (s.toInt().coerceIn(0, 3)) {
            0 -> floatArrayOf(s, 0.01f, 0f, 1f)
            1 -> floatArrayOf(0.99f, s - 1f, -1f, 0f)
            2 -> floatArrayOf(1f - (s - 2f), 0.99f, 0f, -1f)
            else -> floatArrayOf(0.01f, 1f - (s - 3f), 1f, 0f)
        }
    }

    override fun draw(frame: FrameState) {
        display.use()
        display.setCommon(frame)
        display.set1f("uSimulated", if (fluid.supported) 1f else 0f)
        display.set2f("uSimTexel", 1f / fluid.width, 1f / fluid.height)
        val tints = arrayOf(floatArrayOf(0.82f, 0.85f, 0.9f), floatArrayOf(0.45f, 1f, 0.55f), floatArrayOf(0.7f, 0.45f, 1f))
        val c = tints[tint.int.coerceIn(0, 2)]
        display.set3f("uTint", c[0], c[1], c[2])
        display.set1f("uBrightness", brightness.value)
        display.texture("uDensity", 0, fluid.dyeTexture)
        display.texture("uVelocity", 1, fluid.velocityTexture)
        gl.drawFullscreen()
    }
}
