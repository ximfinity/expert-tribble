package com.phantomfloor.scenes

import com.phantomfloor.audio.Sfx
import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * GROUND SMOKE: low fog-machine smoke, simulated as a real fluid. Emitters around the edges
 * pour smoke in; feet shove it aside and stir it into curls that drift on after them. Moving
 * feet tint the smoke they disturb (faster = more colour), and every step leaves a glowing
 * footprint that fades away.
 */
class SmokeScene : Scene("smoke", "Ground Smoke") {

    companion object {
        private const val PRINT_LENGTH = 0.075f
    }

    private val amount = SceneParam("amount", "Smoke output", 0f, 2f, 1f)
    private val emitters = SceneParam("emitters", "Fog machines", 1f, 6f, 3f, step = 1f)
    private val linger = SceneParam("linger", "How long it lingers", 0f, 1f, 0.6f)
    private val swirl = SceneParam("swirl", "Swirl", 0f, 2f, 1f)
    private val push = SceneParam("push", "Push from feet", 0f, 2f, 1f)
    private val tint = SceneParam("tint", "White · Green · Purple", 0f, 2f, 0f, step = 1f)
    private val brightness = SceneParam("bright", "Brightness", 0.2f, 1.5f, 0.85f)
    private val stirColor = SceneParam("stirColor", "Footstep colour: Rainbow·Green·Purple·Red·Off", 0f, 4f, 0f, step = 1f)
    private val stirStrength = SceneParam("stir", "Colour from motion", 0f, 2f, 1f)
    private val printGlow = SceneParam("prints", "Glowing footprints", 0f, 1.5f, 0.8f)

    override val params = listOf(amount, emitters, linger, swirl, push, tint, brightness, stirColor, stirStrength, printGlow)
    override val resolutionScale = 0.75f

    private lateinit var display: GlProgram
    private val fluid = FluidSolver()
    private val prints = StampField("footprint_glow.frag", simHeight = 200)
    private val footsteps = FootstepTracker()
    private val steps = ArrayList<FootstepTracker.Footprint>()
    private var pendingFade = 0f

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        display = gl.shaders.program("fullscreen.vert", "smoke.frag")
        fluid.onSurfaceCreated(gl)
        prints.onSurfaceCreated(gl)
    }

    override fun onResize(width: Int, height: Int) {
        super.onResize(width, height)
        fluid.onResize(width, height)
        prints.onResize(width, height)
    }

    /** Colour for a person's footsteps; rainbow mode drifts through hues over time. */
    private fun hueFor(id: Int, time: Float): Float = when (stirColor.int) {
        0 -> ((time * 0.08f + id * 0.37f) % 1f + 1f) % 1f
        1 -> 0.36f
        2 -> 0.78f
        else -> 0.0f
    }

    override fun update(frame: FrameState) {
        updatePrints(frame)
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

        // Feet: drag smoke along with their motion and shove it outward; a little clears away,
        // and the disturbed smoke picks up the person's colour (more colour the faster they move).
        val colourOn = stirColor.int < 4
        for (p in frame.people) {
            val k = push.value
            val speed = hypot(p.vx * frame.aspect, p.vy)
            fluid.splatVelocity(p.x, p.y, 0.05f, p.vx * w * k * 0.6f, p.vy * h * k * 0.6f, radial = (20f + speed * 60f) * k * dt * 6f)
            val c = hsv(hueFor(p.id, frame.time))
            val tint = if (colourOn) (0.15f + speed * 3f).coerceAtMost(2.5f) * dt * 6f else 0f
            fluid.splatDye(p.x, p.y, 0.045f, -dt * 1.5f * k, c[0] * tint, c[1] * tint, c[2] * tint)
        }
        for (f in steps) {
            if (!colourOn) break
            val c = hsv(hueFor(f.personId, frame.time))
            fluid.splatDye(f.x, f.y, 0.035f, 0f, c[0] * 0.4f, c[1] * 0.4f, c[2] * 0.4f)
        }

        val keep = 0.6f + 0.38f * linger.value // fraction of smoke left after one second
        fluid.step(dt, frame.time, curlStrength = 25f * swirl.value, breeze = 30f * swirl.value, velocityKeep = 0.85f, dyeKeep = keep)
    }

    private fun updatePrints(frame: FrameState) {
        steps.clear()
        footsteps.update(frame.people, frame.aspect, frame.time, steps)
        for (f in steps) {
            prints.stamp(f.x, f.y, PRINT_LENGTH, 1f, f.dirX, f.dirY, f.side, hueFor(f.personId, frame.time))
            frame.sound.play(Sfx.WHOOSH, 0.25f + f.speed.coerceAtMost(1f) * 0.4f, f.x * 2f - 1f)
        }
        // Prints fade over ~6 s; batch the fade so 8-bit fields still reach zero.
        pendingFade += frame.dt / 6f
        val minStep = if (prints.lowPrecision) 2f / 255f else 0f
        val fade = if (pendingFade >= minStep) pendingFade.also { pendingFade = 0f } else 0f
        prints.step { it.set1f("uFade", fade) }
    }

    private fun hsv(h: Float): FloatArray {
        fun c(n: Float): Float = ((kotlin.math.abs(((h * 6f + n) % 6f + 6f) % 6f - 3f) - 1f)).coerceIn(0f, 1f)
        return floatArrayOf(c(0f), c(4f), c(2f))
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
        display.texture("uPrints", 2, prints.texture)
        display.set1f("uStir", if (stirColor.int < 4) stirStrength.value else 0f)
        display.set1f("uPrintGlow", printGlow.value)
        gl.drawFullscreen()
    }
}
