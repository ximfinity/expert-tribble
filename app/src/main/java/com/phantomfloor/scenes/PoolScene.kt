package com.phantomfloor.scenes

import android.opengl.GLES30
import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.SpriteBatch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * SUMMER POOL: a sunny swimming pool. Wading makes ripples; jumping in (arriving, or a quick
 * big step) throws a splash of droplets that arc out and ripple where they land.
 */
class PoolScene : Scene("pool", "Pool Splash", Season.SUMMER) {

    private val splash = SceneParam("splash", "Splash size", 0.3f, 2f, 1f)
    private val waves = SceneParam("waves", "Ripple strength", 0f, 1.5f, 0.8f)
    private val sun = SceneParam("sun", "Sunshine", 0.3f, 1.3f, 1f)
    private val calm = SceneParam("calm", "Water settles", 0f, 1f, 0.5f)

    override val params = listOf(splash, waves, sun, calm)
    override val resolutionScale = 0.85f

    private lateinit var poolProgram: GlProgram
    private lateinit var sparkProgram: GlProgram
    private val ripples = RippleField()
    private val sprites = SpriteBatch(MAX_DROPS)
    private val rng = Random(3)
    private var flowTime = 0f

    private var n = 0
    private val dx = FloatArray(MAX_DROPS); private val dy = FloatArray(MAX_DROPS)
    private val dvx = FloatArray(MAX_DROPS); private val dvy = FloatArray(MAX_DROPS)
    private val dh = FloatArray(MAX_DROPS); private val dvh = FloatArray(MAX_DROPS)
    private val dsize = FloatArray(MAX_DROPS)

    private class Walker(var lastX: Float, var lastY: Float, var lastStep: Float, var lastSplash: Float)
    private val walkers = HashMap<Int, Walker>()

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        poolProgram = gl.shaders.program("fullscreen.vert", "pool.frag")
        sparkProgram = gl.shaders.program("sprite.vert", "spark.frag")
        ripples.onSurfaceCreated(gl)
        sprites.onSurfaceCreated()
    }

    override fun onResize(width: Int, height: Int) {
        super.onResize(width, height)
        ripples.onResize(width, height)
    }

    private fun splashAt(x: Float, y: Float, aspect: Float, power: Float) {
        ripples.drop(x / aspect, y, 0.04f * power, 0.9f * waves.value)
        val count = (40 * power).toInt()
        repeat(count) {
            if (n >= MAX_DROPS) return
            val a = rng.nextFloat() * TAU
            val v = (0.15f + rng.nextFloat() * 0.35f) * power
            dx[n] = x; dy[n] = y
            dvx[n] = cos(a) * v; dvy[n] = sin(a) * v
            dh[n] = 0.01f; dvh[n] = (0.5f + rng.nextFloat() * 0.6f) * power
            dsize[n] = 0.003f + rng.nextFloat() * 0.004f
            n++
        }
    }

    override fun update(frame: FrameState) {
        val dt = frame.dt
        val aspect = frame.aspect
        flowTime += dt
        val power = splash.value

        for (p in frame.people) {
            val px = p.x * aspect
            val w = walkers[p.id]
            val speed = hypot(p.vx * aspect, p.vy)
            if (w == null) {
                splashAt(px, p.y, aspect, power * 1.3f) // jumped in
                walkers[p.id] = Walker(px, p.y, frame.time, frame.time)
                continue
            }
            if (speed > 0.7f && frame.time - w.lastSplash > 0.6f) {
                splashAt(px, p.y, aspect, power)
                w.lastSplash = frame.time
            }
            if (frame.time - w.lastStep > 0.12f) {
                ripples.drop(p.x, p.y, 0.016f, (0.25f + speed.coerceAtMost(1f) * 0.5f) * waves.value)
                w.lastStep = frame.time
            }
        }
        if (walkers.size > 64) walkers.clear()
        if (frame.beatTriggered && frame.level > 0.5f) splashAt(rng.nextFloat() * aspect, 0.2f + rng.nextFloat() * 0.6f, aspect, power)

        // Droplets fly in arcs; each one that lands makes its own small ripple.
        var w = 0
        for (i in 0 until n) {
            dvh[i] -= 3f * dt
            dh[i] += dvh[i] * dt
            dx[i] += dvx[i] * dt
            dy[i] += dvy[i] * dt
            if (dh[i] <= 0f) {
                if (dx[i] in 0f..aspect && dy[i] in 0f..1f) ripples.drop(dx[i] / aspect, dy[i], 0.008f, 0.15f * waves.value)
                continue
            }
            if (w != i) {
                dx[w] = dx[i]; dy[w] = dy[i]; dvx[w] = dvx[i]; dvy[w] = dvy[i]
                dh[w] = dh[i]; dvh[w] = dvh[i]; dsize[w] = dsize[i]
            }
            w++
        }
        n = w
        ripples.step(damping = 0.975f + 0.02f * (1f - calm.value))
    }

    override fun draw(frame: FrameState) {
        poolProgram.use()
        poolProgram.setCommon(frame)
        poolProgram.set1f("uFlowTime", flowTime)
        poolProgram.set1f("uSun", sun.value)
        poolProgram.set1f("uRippleEncoded", if (ripples.encoded) 1f else 0f)
        poolProgram.set2f("uRippleTexel", 1f / (256f * frame.aspect), 1f / 256f)
        poolProgram.texture("uRipple", 0, ripples.texture)
        gl.drawFullscreen()

        sprites.begin()
        for (i in 0 until n) {
            // Higher droplets look bigger (closer to the projector's viewpoint).
            sprites.add(dx[i], dy[i], 0f, dsize[i] * (1f + dh[i] * 4f), 0.85f, 0.95f, 1f, 0.9f, p0 = 0.7f, p1 = -1f, p2 = 0.15f)
        }
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        sparkProgram.use()
        sparkProgram.set1f("uAspect", frame.aspect)
        sparkProgram.set1f("uTime", frame.time)
        sparkProgram.set2f("uOffset", 0f, 0f)
        sprites.draw()
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    companion object {
        private const val MAX_DROPS = 1500
        private const val TAU = (2 * PI).toFloat()
    }
}
