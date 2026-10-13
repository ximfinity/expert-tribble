package com.phantomfloor.scenes

import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources

class FogScene : Scene("fog", "Ectoplasmic Fog") {

    private val density = SceneParam("density", "Fog density", 0f, 1f, 0.6f)
    private val speed = SceneParam("speed", "Swirl speed", 0f, 3f, 0.8f)
    private val scale = SceneParam("scale", "Billow size", 0.6f, 4f, 1.6f)
    private val hue = SceneParam("hue", "Purple ↔ Green", 0f, 1f, 0.55f)
    private val clearRadius = SceneParam("clear", "Clearing around feet", 0.05f, 0.4f, 0.17f)
    private val wisps = SceneParam("wisps", "Glowing wisps", 0f, 1f, 0.6f)

    override val params = listOf(density, speed, scale, hue, clearRadius, wisps)

    // The fog is low-frequency and expensive (~30 noise evaluations per pixel): half res is plenty.
    override val resolutionScale = 0.5f

    private lateinit var program: GlProgram
    private var flowTime = 0f

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        program = gl.shaders.program("fullscreen.vert", "fog.frag")
    }

    override fun update(frame: FrameState) {
        // Loud sounds briefly churn the fog faster.
        flowTime += frame.dt * speed.value * (1f + frame.level * 1.5f)
    }

    override fun draw(frame: FrameState) {
        program.use()
        program.setCommon(frame)
        program.set1f("uFlowTime", flowTime)
        program.set1f("uDensity", density.value)
        program.set1f("uScale", scale.value)
        program.set1f("uHue", hue.value)
        program.set1f("uClearRadius", clearRadius.value)
        program.set1f("uWisps", wisps.value)
        gl.drawFullscreen()
    }
}
