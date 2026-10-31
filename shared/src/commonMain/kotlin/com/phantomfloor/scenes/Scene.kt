package com.phantomfloor.scenes

import com.phantomfloor.audio.Ambience
import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources

/**
 * A procedural illusion. Lifecycle (all on the GL thread):
 * [onSurfaceCreated] -> [onResize] -> repeated ([update], [draw]).
 *
 * [draw] renders into an already-bound, already-cleared canvas render target.
 */
abstract class Scene(val id: String, val title: String, val season: Season = Season.HALLOWEEN) {

    abstract val params: List<SceneParam>

    /** Background sound while this scene is showing. */
    open val ambience: Ambience = Ambience.SILENT

    /** Fraction of screen resolution this scene renders at (smooth scenes can go lower). */
    open val resolutionScale: Float = 1f

    protected lateinit var gl: GlResources
    protected var width = 1
    protected var height = 1

    /** Called on every new GL context. Old GL handles are already invalid; just recreate. */
    open fun onSurfaceCreated(gl: GlResources) {
        this.gl = gl
    }

    open fun onResize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    open fun update(frame: FrameState) {}

    abstract fun draw(frame: FrameState)

    private val cooldowns = HashMap<Long, Float>()

    /** Rate limiter for event sounds: true at most once per [interval] seconds for each [key]. */
    protected fun ready(key: Long, now: Float, interval: Float): Boolean {
        val last = cooldowns[key]
        if (last != null && now - last < interval) return false
        if (cooldowns.size > 256) cooldowns.clear()
        cooldowns[key] = now
        return true
    }

    /** Stereo pan for a canvas x position. */
    protected fun pan(uvX: Float) = (uvX * 2f - 1f).coerceIn(-1f, 1f)

    /** Uniforms most shaders share: resolution, time, people, audio. */
    protected fun GlProgram.setCommon(frame: FrameState) {
        set2f("uResolution", width.toFloat(), height.toFloat())
        set1f("uTime", frame.time)
        set4fv("uPeople", frame.peopleUniform, FrameState.MAX_PEOPLE)
        set1i("uPeopleCount", frame.peopleCount)
        set1f("uLevel", frame.level)
        set1f("uBass", frame.bass)
        set1f("uMid", frame.mid)
        set1f("uTreble", frame.treble)
        set1f("uBeat", frame.beat)
    }
}
