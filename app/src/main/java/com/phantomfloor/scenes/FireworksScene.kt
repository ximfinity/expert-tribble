package com.phantomfloor.scenes

import android.opengl.GLES30
import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.RenderTarget
import com.phantomfloor.gl.SpriteBatch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 4TH OF JULY FIREWORKS: each step sets off a burst right under the walker's feet. Peonies,
 * rings, golden willows, crossettes that split again, and crackling glitter, with persistent
 * light trails, a flash at each burst, and drifting smoke. Idle floors put on a slow show.
 */
class FireworksScene : Scene("fireworks", "Fireworks", Season.JULY_4TH) {

    private val burstSize = SceneParam("size", "Burst size", 0.4f, 2f, 1f)
    private val stride = SceneParam("stride", "Steps per burst", 0.05f, 0.4f, 0.14f)
    private val trails = SceneParam("trails", "Trail length", 0f, 1f, 0.6f)
    private val idleShow = SceneParam("idle", "Show when nobody's walking", 0f, 1f, 0.4f)
    private val patriotic = SceneParam("palette", "Red-white-blue only", 0f, 1f, 1f, step = 1f)
    private val smoke = SceneParam("smoke", "Smoke", 0f, 1f, 0.5f)

    override val params = listOf(burstSize, stride, trails, idleShow, patriotic, smoke)

    private lateinit var sparkProgram: GlProgram
    private lateinit var solid: GlProgram
    private lateinit var copy: GlProgram
    private var trailTarget: RenderTarget? = null
    private val sprites = SpriteBatch(MAX_SPARKS + MAX_PUFFS + 16)
    private val rng = Random(1776)

    // Sparks (structure of arrays)
    private var n = 0
    private val sx = FloatArray(MAX_SPARKS); private val sy = FloatArray(MAX_SPARKS)
    private val svx = FloatArray(MAX_SPARKS); private val svy = FloatArray(MAX_SPARKS)
    private val age = FloatArray(MAX_SPARKS); private val life = FloatArray(MAX_SPARKS)
    private val cr = FloatArray(MAX_SPARKS); private val cg = FloatArray(MAX_SPARKS); private val cb = FloatArray(MAX_SPARKS)
    private val drag = FloatArray(MAX_SPARKS); private val ssize = FloatArray(MAX_SPARKS)
    private val glitter = FloatArray(MAX_SPARKS)  // >= 0: twinkle phase
    private val splitAt = FloatArray(MAX_SPARKS)  // crossette split time, or -1

    private class Flash(var x: Float, var y: Float, var age: Float, val r: Float, val g: Float, val b: Float)
    private class Puff(var x: Float, var y: Float, var vx: Float, var vy: Float, var age: Float, val life: Float, val size: Float)
    private val flashes = ArrayList<Flash>()
    private val puffs = ArrayList<Puff>()

    private val lastBurst = HashMap<Int, FloatArray>() // person id -> x, y, time
    private var idleTimer = 1f

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        sparkProgram = gl.shaders.program("sprite.vert", "spark.frag")
        solid = gl.shaders.program("fullscreen.vert", "solid.frag")
        copy = gl.shaders.program("fullscreen.vert", "copy.frag")
        sprites.onSurfaceCreated()
        trailTarget = null
    }

    override fun onResize(width: Int, height: Int) {
        super.onResize(width, height)
        trailTarget?.release()
        trailTarget = RenderTarget.create(width, height)
    }

    private fun color(): FloatArray {
        val red = floatArrayOf(1f, 0.15f, 0.1f)
        val white = floatArrayOf(1f, 0.95f, 0.9f)
        val blue = floatArrayOf(0.25f, 0.4f, 1f)
        val extra = arrayOf(floatArrayOf(1f, 0.75f, 0.2f), floatArrayOf(0.3f, 1f, 0.35f), floatArrayOf(0.8f, 0.3f, 1f))
        return if (patriotic.value > 0.5f || rng.nextFloat() < 0.4f) arrayOf(red, white, blue)[rng.nextInt(3)]
        else extra[rng.nextInt(extra.size)]
    }

    private fun addSpark(x: Float, y: Float, vx: Float, vy: Float, life: Float, c: FloatArray, drag: Float, size: Float, glitter: Float = -1f, split: Float = -1f) {
        if (n >= MAX_SPARKS) return
        sx[n] = x; sy[n] = y; svx[n] = vx; svy[n] = vy
        age[n] = 0f; this.life[n] = life
        cr[n] = c[0]; cg[n] = c[1]; cb[n] = c[2]
        this.drag[n] = drag; ssize[n] = size
        this.glitter[n] = glitter; splitAt[n] = split
        n++
    }

    private fun burst(x: Float, y: Float, power: Float) {
        val s = burstSize.value * power
        val c = color()
        val c2 = color()
        val count = (90 * s).toInt().coerceIn(30, 220)
        when (rng.nextInt(5)) {
            0 -> repeat(count) { // peony
                val a = rng.nextFloat() * TAU
                val v = (0.25f + 0.2f * sqrt(rng.nextFloat())) * s
                addSpark(x, y, cos(a) * v, sin(a) * v, 1.4f + rng.nextFloat() * 0.6f, if (it % 3 == 0) c2 else c, 2.2f, 0.009f)
            }
            1 -> repeat(count) { i -> // ring
                val a = i * TAU / count
                val v = 0.4f * s
                addSpark(x, y, cos(a) * v, sin(a) * v, 1.6f, c, 2f, 0.01f)
            }
            2 -> repeat(count) { // golden willow: slow, long-lived, glittering
                val a = rng.nextFloat() * TAU
                val v = (0.15f + 0.15f * rng.nextFloat()) * s
                addSpark(x, y, cos(a) * v, sin(a) * v, 2.8f + rng.nextFloat(), floatArrayOf(1f, 0.7f, 0.25f), 1.2f, 0.008f, glitter = rng.nextFloat())
            }
            3 -> repeat(count / 6) { // crossette: few stars that split into four
                val a = rng.nextFloat() * TAU
                val v = 0.3f * s
                addSpark(x, y, cos(a) * v, sin(a) * v, 0.6f, c, 1.5f, 0.013f, split = 0.55f)
            }
            else -> repeat(count) { // two-colour chrysanthemum with crackle
                val a = rng.nextFloat() * TAU
                val v = (0.2f + 0.25f * rng.nextFloat()) * s
                addSpark(x, y, cos(a) * v, sin(a) * v, 1.5f + rng.nextFloat() * 0.8f, if (rng.nextBoolean()) c else c2, 2f, 0.009f, glitter = if (rng.nextFloat() < 0.4f) rng.nextFloat() else -1f)
            }
        }
        flashes.add(Flash(x, y, 0f, c[0], c[1], c[2]))
        if (smoke.value > 0f) repeat(4) {
            val a = rng.nextFloat() * TAU
            puffs.add(Puff(x, y, cos(a) * 0.03f, sin(a) * 0.03f, 0f, 4f + rng.nextFloat() * 3f, 0.06f + 0.06f * rng.nextFloat()))
        }
        while (puffs.size > MAX_PUFFS) puffs.removeAt(0)
    }

    override fun update(frame: FrameState) {
        val dt = frame.dt
        val aspect = frame.aspect

        // Bursts from walking: one per [stride] of distance, and one the moment someone appears.
        for (p in frame.people) {
            val px = p.x * aspect
            val last = lastBurst[p.id]
            if (last == null) {
                burst(px, p.y, 1f)
                lastBurst[p.id] = floatArrayOf(px, p.y, frame.time)
            } else if (hypot(px - last[0], p.y - last[1]) > stride.value && frame.time - last[2] > 0.25f) {
                burst(px, p.y, 1f)
                last[0] = px; last[1] = p.y; last[2] = frame.time
            }
        }
        if (lastBurst.size > 64) lastBurst.clear()
        if (frame.beatTriggered && frame.level > 0.4f) burst(rng.nextFloat() * aspect, 0.15f + rng.nextFloat() * 0.7f, 1.2f)
        if (frame.people.isEmpty() && idleShow.value > 0f) {
            idleTimer -= dt * idleShow.value
            if (idleTimer <= 0f) {
                burst(0.15f + rng.nextFloat() * (aspect - 0.3f), 0.15f + rng.nextFloat() * 0.7f, 0.8f + rng.nextFloat() * 0.6f)
                idleTimer = 0.8f + rng.nextFloat() * 2f
            }
        }

        // Integrate sparks; compact the array in place as they burn out.
        var w = 0
        val splits = ArrayList<FloatArray>()
        for (i in 0 until n) {
            age[i] += dt
            val k = exp(-drag[i] * dt)
            svx[i] *= k; svy[i] *= k
            sx[i] += svx[i] * dt; sy[i] += svy[i] * dt
            if (splitAt[i] in 0f..age[i]) {
                splits.add(floatArrayOf(sx[i], sy[i], cr[i], cg[i], cb[i]))
                continue
            }
            if (age[i] >= life[i]) continue
            if (w != i) {
                sx[w] = sx[i]; sy[w] = sy[i]; svx[w] = svx[i]; svy[w] = svy[i]
                age[w] = age[i]; life[w] = life[i]; cr[w] = cr[i]; cg[w] = cg[i]; cb[w] = cb[i]
                drag[w] = drag[i]; ssize[w] = ssize[i]; glitter[w] = glitter[i]; splitAt[w] = splitAt[i]
            }
            w++
        }
        n = w
        for (s in splits) repeat(4) { j ->
            val a = j * TAU / 4 + rng.nextFloat() * 0.3f
            addSpark(s[0], s[1], cos(a) * 0.22f, sin(a) * 0.22f, 0.9f, floatArrayOf(s[2], s[3], s[4]), 2.5f, 0.009f)
        }
        flashes.forEach { it.age += dt }
        flashes.removeAll { it.age > 0.25f }
        for (p in puffs) {
            p.age += dt; p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= exp(-dt * 0.5f); p.vy *= exp(-dt * 0.5f)
        }
        puffs.removeAll { it.age > it.life }
    }

    override fun draw(frame: FrameState) {
        val canvasFbo = currentFramebuffer()
        val trail = trailTarget ?: return
        trail.bind()

        // Fade the trail buffer: multiply, then subtract a hair so 8-bit values reach black.
        GLES30.glEnable(GLES30.GL_BLEND)
        solid.use()
        val keep = exp(-frame.dt * (18f - 15f * trails.value))
        GLES30.glBlendFunc(GLES30.GL_ZERO, GLES30.GL_SRC_ALPHA)
        solid.set4f("uColor", 0f, 0f, 0f, keep)
        gl.drawFullscreen()
        GLES30.glBlendEquation(GLES30.GL_FUNC_REVERSE_SUBTRACT)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        solid.set4f("uColor", 1.5f / 255f, 1.5f / 255f, 1.5f / 255f, 0f)
        gl.drawFullscreen()
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)

        // Sparks add light into the trail buffer; they cool from white-hot to their colour, then dim.
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        sparkProgram.use()
        sparkProgram.set1f("uAspect", frame.aspect)
        sparkProgram.set1f("uTime", frame.time)
        sparkProgram.set2f("uOffset", 0f, 0f)
        sprites.begin()
        for (i in 0 until n) {
            val t = age[i] / life[i]
            val hot = exp(-age[i] * 8f)
            val fade = (1f - t) * (1f - t)
            sprites.add(
                sx[i], sy[i], 0f, ssize[i] * (1f + hot),
                cr[i] + (1f - cr[i]) * hot, cg[i] + (1f - cg[i]) * hot, cb[i] + (1f - cb[i]) * hot, fade,
                p0 = 0.6f, p1 = glitter[i], p2 = 0.25f,
            )
        }
        sprites.draw()

        // Composite onto the scene canvas, then add flashes and smoke that shouldn't leave trails.
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, canvasFbo)
        GLES30.glViewport(0, 0, width, height)
        copy.use()
        copy.texture("uSource", 0, trail.texture)
        gl.drawFullscreen()

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        sparkProgram.use()
        sprites.begin()
        for (f in flashes) {
            val k = 1f - f.age / 0.25f
            sprites.add(f.x, f.y, 0f, 0.12f * burstSize.value, f.r * 0.5f + 0.5f, f.g * 0.5f + 0.5f, f.b * 0.5f + 0.5f, k * k, p0 = 0f, p1 = -1f, p2 = 0.5f)
        }
        for (p in puffs) {
            val t = p.age / p.life
            val a = smoke.value * 0.05f * (1f - t) * (t / 0.1f).coerceAtMost(1f)
            sprites.add(p.x, p.y, 0f, p.size * (1f + t * 2f), 0.6f, 0.6f, 0.65f, a, p0 = 0f, p1 = -1f, p2 = 1f)
        }
        sprites.draw()
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun currentFramebuffer(): Int {
        val v = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, v, 0)
        return v[0]
    }

    companion object {
        private const val MAX_SPARKS = 5000
        private const val MAX_PUFFS = 60
        private const val TAU = (2 * PI).toFloat()
    }
}
