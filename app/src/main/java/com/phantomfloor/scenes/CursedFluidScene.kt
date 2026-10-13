package com.phantomfloor.scenes

import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import kotlin.math.hypot
import kotlin.random.Random

/** CURSED FLUID: a bubbling, creeping pool of blood or lava that ripples under footsteps. */
class CursedFluidScene : Scene("fluid", "Cursed Fluid") {

    private val mode = SceneParam("mode", "Blood ↔ Lava", 0f, 1f, 0f)
    private val pool = SceneParam("pool", "Pool size", 0f, 1f, 0.45f)
    private val bubbles = SceneParam("bubbles", "Bubbling", 0f, 1f, 0.35f)
    private val flow = SceneParam("flow", "Flow speed", 0f, 3f, 1f)
    private val glow = SceneParam("glow", "Lava glow", 0f, 1f, 0.5f)
    private val splash = SceneParam("splash", "Footstep splash", 0f, 1f, 0.6f)

    override val params = listOf(mode, pool, bubbles, flow, glow, splash)
    override val resolutionScale = 0.75f

    private lateinit var program: GlProgram
    private val ripples = RippleField()
    private var flowTime = 0f
    private val rng = Random(7)
    private val lastSplash = HashMap<Int, Float>()

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        program = gl.shaders.program("fullscreen.vert", "fluid.frag")
        ripples.onSurfaceCreated(gl)
    }

    override fun onResize(width: Int, height: Int) {
        super.onResize(width, height)
        ripples.onResize(width, height)
    }

    override fun update(frame: FrameState) {
        flowTime += frame.dt * flow.value
        val s = splash.value
        for (p in frame.people) {
            // Moving feet make a trail of splashes; standing still still oozes now and then.
            val speed = hypot(p.vx * frame.aspect, p.vy)
            val interval = if (speed > 0.05f) 0.12f else 0.6f
            val last = lastSplash[p.id] ?: -10f
            if (frame.time - last > interval) {
                lastSplash[p.id] = frame.time
                ripples.drop(p.x, p.y, 0.018f, s * (0.25f + speed.coerceAtMost(1f) * 0.6f))
            }
        }
        if (lastSplash.size > 64) lastSplash.clear()
        if (frame.beatTriggered) {
            repeat(3) {
                ripples.drop(0.3f + rng.nextFloat() * 0.4f, 0.3f + rng.nextFloat() * 0.4f, 0.03f, 0.5f * frame.level + 0.2f)
            }
        }
        ripples.step(damping = 0.982f)
    }

    override fun draw(frame: FrameState) {
        program.use()
        program.setCommon(frame)
        program.set1f("uFlowTime", flowTime)
        program.set1f("uMode", mode.value)
        program.set1f("uPool", pool.value)
        program.set1f("uBubbles", bubbles.value)
        program.set1f("uGlow", glow.value)
        program.set1f("uRippleEncoded", if (ripples.encoded) 1f else 0f)
        program.texture("uRipple", 0, ripples.texture)
        gl.drawFullscreen()
    }
}
