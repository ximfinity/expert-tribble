package com.phantomfloor.scenes

import android.opengl.GLES30
import com.phantomfloor.audio.Ambience
import com.phantomfloor.audio.Sfx
import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.RenderTarget
import kotlin.math.hypot

/**
 * LIVING WATER: dark water with drifting caustics that ripples under footsteps, and keeps
 * rising up into monsters (tentacles, a grasping hand, a screaming face) near the people
 * standing on it. Eyes open and blink in the water and close when someone approaches.
 *
 * Two passes: monster heights into a half-res target, then the full shading pass.
 */
class LivingWaterScene : Scene("water", "Living Water") {

    private val activity = SceneParam("activity", "Monster activity", 0f, 3f, 1f)
    private val monsterSize = SceneParam("msize", "Monster size", 0.5f, 2f, 1f)
    private val caustics = SceneParam("caustics", "Caustic light", 0f, 2f, 1f)
    private val glow = SceneParam("glow", "Monster glow", 0f, 2f, 1f)
    private val flow = SceneParam("flow", "Current speed", 0f, 3f, 1f)
    private val splash = SceneParam("splash", "Footstep ripples", 0f, 1f, 0.7f)

    override val params = listOf(activity, monsterSize, caustics, glow, flow, splash)
    override val resolutionScale = 0.75f
    override val ambience = Ambience(water = 0.6f, drone = 0.6f, droneHz = 38f, bubbles = 0.5f)
    private var sink: com.phantomfloor.audio.SoundSink = com.phantomfloor.audio.SoundSink.None

    private lateinit var shapeProgram: GlProgram
    private lateinit var waterProgram: GlProgram
    private var shapes: RenderTarget? = null
    private val ripples = RippleField()
    private val director = MonsterDirector().apply {
        onSplash = { x, y, r, s -> ripples.drop(x, y, r, s) }
        onSound = { sfx, x -> sink.play(sfx, 0.9f, pan(x)) }
    }
    private var flowTime = 0f
    private val lastSplash = HashMap<Int, Float>()

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        shapeProgram = gl.shaders.program("fullscreen.vert", "water_shapes.frag")
        waterProgram = gl.shaders.program("fullscreen.vert", "water.frag")
        ripples.onSurfaceCreated(gl)
        shapes = null
    }

    override fun onResize(width: Int, height: Int) {
        super.onResize(width, height)
        ripples.onResize(width, height)
        shapes?.release()
        shapes = RenderTarget.create(width / 2, height / 2)
    }

    override fun update(frame: FrameState) {
        sink = frame.sound
        flowTime += frame.dt * flow.value * (1f + frame.bass)
        val s = splash.value
        for (p in frame.people) {
            val speed = hypot(p.vx * frame.aspect, p.vy)
            val interval = if (speed > 0.05f) 0.1f else 0.8f
            val last = lastSplash[p.id] ?: -10f
            if (frame.time - last > interval) {
                lastSplash[p.id] = frame.time
                ripples.drop(p.x, p.y, 0.016f, s * (0.3f + speed.coerceAtMost(1f) * 0.6f))
                if (speed > 0.05f) frame.sound.play(Sfx.DRIP, 0.3f, pan(p.x))
            }
        }
        if (lastSplash.size > 64) lastSplash.clear()

        director.update(
            frame.dt, frame.aspect, frame.people, activity.value, monsterSize.value,
            frame.beatTriggered, frame.level,
        )
        ripples.step(damping = 0.988f)

        // Monster height pass.
        val target = shapes ?: return
        target.bind()
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        shapeProgram.use()
        shapeProgram.set2f("uResolution", width.toFloat(), height.toFloat())
        shapeProgram.set1f("uTime", frame.time)
        shapeProgram.set1f("uLevel", frame.level)
        shapeProgram.set1i("uMonCount", director.monsterCount)
        shapeProgram.set4fv("uMonA", director.monsterA, MonsterDirector.MAX_MONSTERS)
        shapeProgram.set4fv("uMonB", director.monsterB, MonsterDirector.MAX_MONSTERS)
        shapeProgram.set1i("uEyeCount", director.eyeCount)
        shapeProgram.set4fv("uEyes", director.eyes, MonsterDirector.MAX_EYES)
        gl.drawFullscreen()
    }

    override fun draw(frame: FrameState) {
        waterProgram.use()
        waterProgram.setCommon(frame)
        waterProgram.set1f("uFlowTime", flowTime)
        waterProgram.set1f("uCaustics", caustics.value)
        waterProgram.set1f("uMonsterGlow", glow.value)
        waterProgram.set1f("uRippleEncoded", if (ripples.encoded) 1f else 0f)
        waterProgram.texture("uRipple", 0, ripples.texture)
        waterProgram.texture("uShapes", 1, shapes?.texture ?: 0)
        gl.drawFullscreen()
    }
}
