package com.phantomfloor.scenes

import android.opengl.GLES30
import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.SpriteBatch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * FALL LEAVES: a carpet of maple and oak leaves. Feet kick them up and sweep them along;
 * airborne leaves flutter, spin and drift on gusts before settling again. Each leaf has a
 * height, so it casts a shadow that separates from it as it rises.
 */
class LeavesScene : Scene("leaves", "Fall Leaves", Season.FALL) {

    private val count = SceneParam("count", "Leaves", 50f, MAX_LEAVES.toFloat(), 450f, step = 10f)
    private val leafSize = SceneParam("size", "Leaf size", 0.5f, 2f, 1f)
    private val kick = SceneParam("kick", "Kick strength", 0f, 2f, 1f)
    private val wind = SceneParam("wind", "Gusts", 0f, 1f, 0.4f)
    private val light = SceneParam("light", "Ground light", 0f, 1f, 0.6f)

    override val params = listOf(count, leafSize, kick, wind, light)

    private lateinit var groundProgram: GlProgram
    private lateinit var leafProgram: GlProgram
    private val sprites = SpriteBatch(MAX_LEAVES)
    private val rng = Random(11)

    private val x = FloatArray(MAX_LEAVES)
    private val y = FloatArray(MAX_LEAVES)
    private val vx = FloatArray(MAX_LEAVES)
    private val vy = FloatArray(MAX_LEAVES)
    private val h = FloatArray(MAX_LEAVES)
    private val vh = FloatArray(MAX_LEAVES)
    private val angle = FloatArray(MAX_LEAVES)
    private val spin = FloatArray(MAX_LEAVES)
    private val size = FloatArray(MAX_LEAVES)
    private val kind = FloatArray(MAX_LEAVES)
    private val hue = IntArray(MAX_LEAVES)
    private val seed = FloatArray(MAX_LEAVES)
    private var initialized = false
    private var gustAngle = 0.5f

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        groundProgram = gl.shaders.program("fullscreen.vert", "earth_ground.frag")
        leafProgram = gl.shaders.program("sprite.vert", "leaf.frag")
        sprites.onSurfaceCreated()
    }

    override fun update(frame: FrameState) {
        val dt = frame.dt
        val aspect = frame.aspect
        if (!initialized) {
            for (i in 0 until MAX_LEAVES) {
                x[i] = rng.nextFloat() * aspect
                y[i] = rng.nextFloat()
                angle[i] = rng.nextFloat() * 6.28f
                size[i] = 0.7f + rng.nextFloat() * 0.6f
                kind[i] = if (rng.nextFloat() < 0.6f) 0f else 1f
                hue[i] = rng.nextInt(PALETTE.size)
                seed[i] = rng.nextFloat()
            }
            initialized = true
        }

        gustAngle += sin(frame.time * 0.07f) * 0.2f * dt
        val gust = wind.value * (0.5f + 0.5f * sin(frame.time * 0.31f)) * (0.6f + 0.4f * sin(frame.time * 1.7f))
        val gx = cos(gustAngle) * gust * 0.25f
        val gy = sin(gustAngle) * gust * 0.25f
        val groundDrag = exp(-dt * 6f)
        val airDrag = exp(-dt * 0.9f)
        val n = count.int
        val reach = 0.07f * leafSize.value

        for (i in 0 until n) {
            for (p in frame.people) {
                val dx = x[i] - p.x * aspect
                val dy = y[i] - p.y
                val d = hypot(dx, dy)
                if (d < reach + 0.03f && d > 1e-4f) {
                    val k = (1f - d / (reach + 0.03f)) * kick.value
                    val pvx = p.vx * aspect
                    val pvy = p.vy
                    val speed = hypot(pvx, pvy)
                    vx[i] += (dx / d * (0.3f + speed) + pvx * 1.4f) * k * dt * 8f
                    vy[i] += (dy / d * (0.3f + speed) + pvy * 1.4f) * k * dt * 8f
                    vh[i] += (0.15f + speed * 0.9f) * k * dt * 8f
                    spin[i] += (seed[i] - 0.5f) * 20f * k * dt
                }
            }
            val airborne = h[i] > 0f
            if (airborne) {
                // Flutter: side-to-side rocking as it falls.
                val flutter = sin(frame.time * (4f + seed[i] * 3f) + seed[i] * 50f) * 0.15f
                vx[i] += ((gx - vx[i]) * 1.2f + cos(angle[i]) * flutter) * dt
                vy[i] += ((gy - vy[i]) * 1.2f + sin(angle[i]) * flutter) * dt
                vx[i] *= airDrag; vy[i] *= airDrag
                spin[i] *= exp(-dt * 0.4f)
            } else {
                vx[i] *= groundDrag; vy[i] *= groundDrag
                spin[i] *= exp(-dt * 6f)
                // Strong gusts pick up the odd leaf.
                if (gust > 0.5f && rng.nextFloat() < dt * 0.15f * gust) {
                    vh[i] += 0.25f * gust
                    spin[i] += (rng.nextFloat() - 0.5f) * 6f
                }
            }
            vh[i] -= 1.1f * dt
            h[i] += vh[i] * dt
            if (h[i] <= 0f) { h[i] = 0f; vh[i] = 0f }
            h[i] = h[i].coerceAtMost(0.6f)
            angle[i] += spin[i] * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            if (x[i] < 0.01f) { x[i] = 0.01f; vx[i] = -vx[i] * 0.4f }
            if (x[i] > aspect - 0.01f) { x[i] = aspect - 0.01f; vx[i] = -vx[i] * 0.4f }
            if (y[i] < 0.01f) { y[i] = 0.01f; vy[i] = -vy[i] * 0.4f }
            if (y[i] > 0.99f) { y[i] = 0.99f; vy[i] = -vy[i] * 0.4f }
        }
    }

    override fun draw(frame: FrameState) {
        groundProgram.use()
        groundProgram.setCommon(frame)
        groundProgram.set1f("uLight", light.value)
        gl.drawFullscreen()

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        leafProgram.use()
        leafProgram.set1f("uAspect", frame.aspect)
        leafProgram.set2f("uOffset", 0f, 0f)
        val n = count.int
        val base = 0.022f * leafSize.value

        // Shadows first (offset grows with height), then grounded leaves, then airborne ones on top.
        sprites.begin()
        for (i in 0 until n) {
            sprites.add(x[i] + 0.006f + h[i] * 0.25f, y[i] - 0.008f - h[i] * 0.3f, angle[i], base * size[i], 0f, 0f, 0f, 1f, kind[i], 0f, 1f)
        }
        sprites.draw()
        for (pass in 0..1) {
            sprites.begin()
            for (i in 0 until n) {
                if ((h[i] > 0f) != (pass == 1)) continue
                val c = PALETTE[hue[i]]
                val shade = 0.85f + 0.3f * seed[i]
                sprites.add(
                    x[i], y[i], angle[i], base * size[i] * (1f + h[i] * 1.2f),
                    c[0] * shade, c[1] * shade, c[2] * shade, 1f,
                    kind[i], (seed[i] - 0.5f) * 2f + h[i] * 3f, 0f,
                )
            }
            sprites.draw()
        }
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    companion object {
        const val MAX_LEAVES = 1000
        private val PALETTE = arrayOf(
            floatArrayOf(0.85f, 0.18f, 0.05f), // red
            floatArrayOf(0.95f, 0.45f, 0.08f), // orange
            floatArrayOf(0.95f, 0.72f, 0.15f), // gold
            floatArrayOf(0.55f, 0.3f, 0.1f),   // brown
            floatArrayOf(0.65f, 0.08f, 0.1f),  // crimson
            floatArrayOf(0.7f, 0.6f, 0.15f),   // olive-yellow
        )
    }
}
