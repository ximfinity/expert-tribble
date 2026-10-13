package com.phantomfloor.scenes

import android.opengl.GLES30
import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min

/**
 * THE SWARM: hundreds of procedural spiders driven by [BoidSwarm], drawn with one instanced
 * draw call. Shadow style draws them as dark silhouettes over dim lantern-light on the floor,
 * so on a projector they read as real shadows. Spectral style draws them glowing on black.
 */
class SwarmScene : Scene("swarm", "The Swarm") {

    private val count = SceneParam("count", "Swarm count", 20f, MAX_SPIDERS.toFloat(), 320f, step = 10f)
    private val speed = SceneParam("speed", "Scurry speed", 0.3f, 2.5f, 1f)
    private val spiderSize = SceneParam("size", "Spider size", 0.4f, 2.5f, 1f)
    private val fear = SceneParam("fear", "Fear radius", 0.05f, 0.45f, 0.2f)
    private val cohesion = SceneParam("cohesion", "Clumping", 0f, 2f, 0.6f)
    private val style = SceneParam("style", "Shadow ↔ Spectral", 0f, 1f, 0f, step = 1f)
    private val light = SceneParam("light", "Floor lantern light", 0f, 1f, 0.6f)

    override val params = listOf(count, speed, spiderSize, fear, cohesion, style, light)

    private val swarm = BoidSwarm(MAX_SPIDERS)
    private lateinit var floorProgram: GlProgram
    private lateinit var spiderProgram: GlProgram
    private var vao = 0
    private var instanceVbo = 0
    private val instanceData: FloatBuffer = ByteBuffer
        .allocateDirect(MAX_SPIDERS * FLOATS_PER_INSTANCE * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        floorProgram = gl.shaders.program("fullscreen.vert", "swarm_floor.frag")
        spiderProgram = gl.shaders.program("spider.vert", "spider.frag")

        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0)
        instanceVbo = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, MAX_SPIDERS * FLOATS_PER_INSTANCE * 4, null, GLES30.GL_DYNAMIC_DRAW)
        val stride = FLOATS_PER_INSTANCE * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glVertexAttribDivisor(0, 1)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 16)
        GLES30.glVertexAttribDivisor(1, 1)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    override fun onResize(width: Int, height: Int) {
        super.onResize(width, height)
        swarm.setWorld(width.toFloat() / height)
    }

    override fun update(frame: FrameState) {
        swarm.setWorld(frame.aspect)
        swarm.setCount(count.int)
        swarm.maxSpeed = 0.09f * speed.value
        swarm.baseSize = BASE_SIZE * spiderSize.value
        swarm.separationDist = 0.032f * spiderSize.value
        swarm.perception = 0.075f * spiderSize.value.coerceAtLeast(1f)
        swarm.fleeRadius = fear.value
        swarm.wCohesion = cohesion.value
        if (frame.beatTriggered && frame.level > 0.3f) swarm.startle(min(1f, 0.4f + frame.level))
        // Sub-step so fast feet can't tunnel through the swarm on a frame hitch.
        val steps = if (frame.dt > 1f / 45f) 2 else 1
        repeat(steps) { swarm.step(frame.dt / steps, frame.people) }
    }

    override fun draw(frame: FrameState) {
        val shadowStyle = style.value < 0.5f
        if (shadowStyle) {
            floorProgram.use()
            floorProgram.setCommon(frame)
            floorProgram.set1f("uLight", light.value)
            gl.drawFullscreen()
        }

        val n = swarm.count
        instanceData.clear()
        for (i in 0 until n) {
            instanceData.put(swarm.x[i]).put(swarm.y[i]).put(swarm.heading[i])
                .put(swarm.baseSize * swarm.size[i])
                .put(swarm.gait[i]).put(swarm.panic[i])
        }
        instanceData.flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, n * FLOATS_PER_INSTANCE * 4, instanceData)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        spiderProgram.use()
        spiderProgram.set1f("uAspect", frame.aspect)
        spiderProgram.set1f("uStyle", if (shadowStyle) 0f else 1f)
        spiderProgram.set2f("uShadowOffset", 0.25f, -0.3f)
        GLES30.glBindVertexArray(vao)
        if (shadowStyle) {
            spiderProgram.set1f("uShadowPass", 1f)
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, n)
        }
        spiderProgram.set1f("uShadowPass", 0f)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, n)
        GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    companion object {
        const val MAX_SPIDERS = 2000
        private const val FLOATS_PER_INSTANCE = 6
        private const val BASE_SIZE = 0.028f
    }
}
