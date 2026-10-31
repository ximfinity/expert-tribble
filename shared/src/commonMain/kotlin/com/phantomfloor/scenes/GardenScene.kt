package com.phantomfloor.scenes

import com.phantomfloor.audio.Ambience
import com.phantomfloor.audio.Sfx
import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources

/**
 * SPRING GARDEN: grass and flowers sprout, leaf out and bloom wherever people walk, then
 * slowly wilt away so the floor is ready for new paths.
 */
class GardenScene : Scene("garden", "Spring Garden", Season.SPRING) {

    private val growSpeed = SceneParam("grow", "Growth speed", 0.2f, 3f, 1f)
    private val lasts = SceneParam("lasts", "Flowers last (minutes)", 0.5f, 10f, 3f)
    private val density = SceneParam("density", "Flower density", 0.1f, 1f, 0.6f)
    private val flowerSize = SceneParam("fsize", "Flower size", 0.5f, 2f, 1f)
    private val grass = SceneParam("grass", "Grass", 0f, 1f, 0.8f)
    private val footprint = SceneParam("reach", "Planting reach", 0.03f, 0.15f, 0.07f)

    override val params = listOf(growSpeed, lasts, density, flowerSize, grass, footprint)
    override val resolutionScale = 0.85f

    override val ambience = Ambience(birds = 0.6f, wind = 0.15f)

    private lateinit var program: GlProgram
    private val growth = StampField("garden_growth.frag")
    private var pendingWilt = 0f

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        program = gl.shaders.program("fullscreen.vert", "garden.frag")
        growth.onSurfaceCreated(gl)
    }

    override fun onResize(width: Int, height: Int) {
        super.onResize(width, height)
        growth.onResize(width, height)
    }

    override fun update(frame: FrameState) {
        val dt = frame.dt
        for (p in frame.people) {
            val v = kotlin.math.hypot(p.vx * frame.aspect, p.vy)
            if (v > 0.08f && ready(p.id.toLong(), frame.time, 0.7f)) frame.sound.play(Sfx.CHIME, 0.35f, pan(p.x))
        }
        for (p in frame.people) growth.stamp(p.x, p.y, footprint.value, (dt * 6f).coerceAtMost(1f))
        // Wilting is slow; batch it so it survives 8-bit storage.
        pendingWilt += dt / (lasts.value * 60f)
        val minStep = if (growth.lowPrecision) 2f / 255f else 0f
        val wilt = if (pendingWilt >= minStep) pendingWilt.also { pendingWilt = 0f } else 0f
        val grow = maxOf(dt * 0.35f * growSpeed.value, minStep)
        growth.step {
            it.set1f("uWilt", wilt)
            it.set1f("uGrow", grow)
            it.set1f("uShrink", maxOf(dt * 0.2f, minStep))
        }
    }

    override fun draw(frame: FrameState) {
        program.use()
        program.setCommon(frame)
        program.texture("uGrowth", 0, growth.texture)
        program.set1f("uDensity", density.value)
        program.set1f("uFlowerSize", flowerSize.value)
        program.set1f("uGrass", grass.value)
        gl.drawFullscreen()
    }
}
