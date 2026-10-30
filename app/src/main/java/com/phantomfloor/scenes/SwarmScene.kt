package com.phantomfloor.scenes

import android.opengl.GLES30
import com.phantomfloor.audio.Ambience
import com.phantomfloor.audio.Sfx
import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.SpriteBatch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * THE SWARM: hundreds of procedural spiders driven by [BoidSwarm], drawn with one instanced
 * draw call. Shadow style draws them as dark silhouettes over dim lantern-light on the floor,
 * so on a projector they read as real shadows. Spectral style draws them glowing on black.
 *
 * Options: glowing eyes, radioactive spiders (green markings and a glow on the floor), a
 * realistic look (hairy 3-segment legs, patterned bodies lit by the flashlight), a mix of
 * sizes, 3D shadows cast away from the flashlight, spiders that stop to spin webs (which
 * tear when someone walks through), and a giant spider that drops in front of someone.
 */
class SwarmScene : Scene("swarm", "The Swarm") {

    private val count = SceneParam("count", "Swarm count", 20f, MAX_SPIDERS.toFloat(), 320f, step = 10f)
    private val speed = SceneParam("speed", "Scurry speed", 0.3f, 2.5f, 1f)
    private val spiderSize = SceneParam("size", "Spider size", 0.4f, 2.5f, 1f)
    private val sizeMix = SceneParam("sizeMix", "Mix of sizes (tiny to huge)", 0f, 1f, 0.35f)
    private val fear = SceneParam("fear", "Fear radius", 0.05f, 0.45f, 0.2f)
    private val cohesion = SceneParam("cohesion", "Clumping", 0f, 2f, 0.6f)
    private val style = SceneParam("style", "Shadow ↔ Spectral", 0f, 1f, 0f, step = 1f)
    private val realism = SceneParam("realism", "Realistic spiders", 0f, 1f, 0f, step = 1f)
    private val eyeGlow = SceneParam("eyeGlow", "Glowing eyes", 0f, 1f, 0.4f)
    private val radioactive = SceneParam("radioactive", "Radioactive spiders", 0f, 1f, 0f, step = 1f)
    private val shadow3d = SceneParam("shadow3d", "3D shadows from the flashlight", 0f, 1f, 1f, step = 1f)
    private val webs = SceneParam("webs", "Web building", 0f, 1f, 0.5f)
    private val giant = SceneParam("giant", "Giant jump-scare spider", 0f, 1f, 0f, step = 1f)
    private val giantEvery = SceneParam("giantEvery", "Giant spider every (s)", 10f, 120f, 35f)
    private val light = SceneParam("light", "Floor lantern light", 0f, 1f, 0.6f)
    private val flashPower = SceneParam("flash", "Flashlight brightness", 0f, 1.5f, 0.9f)
    private val flashSize = SceneParam("flashSize", "Flashlight size", 0.08f, 0.45f, 0.2f)
    private val flashSpeed = SceneParam("flashSpeed", "Flashlight sweep speed", 0.2f, 2.5f, 1f)
    private val lightFear = SceneParam("lightFear", "Spiders flee the light", 0f, 1f, 0.7f)

    override val params = listOf(
        count, speed, spiderSize, sizeMix, fear, cohesion, style, realism, eyeGlow, radioactive,
        shadow3d, webs, giant, giantEvery, light, flashPower, flashSize, flashSpeed, lightFear,
    )

    private val flashlight = Flashlight()

    override val ambience = Ambience(wind = 0.2f, drone = 0.4f, droneHz = 41f, skitter = 0.5f)

    private val swarm = BoidSwarm(MAX_SPIDERS)
    private val rng = Random(77)
    private lateinit var floorProgram: GlProgram
    private lateinit var spiderProgram: GlProgram
    private lateinit var webProgram: GlProgram
    private val webBatch = SpriteBatch(MAX_WEBS)
    private var vao = 0
    private var instanceVbo = 0
    private val instanceData: FloatBuffer = ByteBuffer
        .allocateDirect((MAX_SPIDERS + 1) * FLOATS_PER_INSTANCE * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    // --- Webs ------------------------------------------------------------------------------------
    private class Web(val x: Float, val y: Float, val radius: Float, val seed: Float, var builder: Int) {
        var progress = 0f
        var age = 0f
        var tear = 0f
        var tearing = false
    }
    private val webList = ArrayList<Web>()
    private var webTimer = 2f

    // --- Giant jump-scare spider -----------------------------------------------------------------
    private var giantTimer = 0f
    private var giantT = -1f
    private var gx = 0f
    private var gy = 0f
    private var gHeading = 0f
    private var gGait = 0f
    private var gTargetX = 0f
    private var gTargetY = 0f
    private var gFleeX = 0f
    private var gFleeY = 0f

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        floorProgram = gl.shaders.program("fullscreen.vert", "swarm_floor.frag")
        spiderProgram = gl.shaders.program("spider.vert", "spider.frag")
        webProgram = gl.shaders.program("sprite.vert", "web.frag")
        webBatch.onSurfaceCreated()

        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0)
        instanceVbo = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, (MAX_SPIDERS + 1) * FLOATS_PER_INSTANCE * 4, null, GLES30.GL_DYNAMIC_DRAW)
        val stride = FLOATS_PER_INSTANCE * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glVertexAttribDivisor(0, 1)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, 16)
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
        applySizeMix()
        flashlight.update(frame.dt, frame.aspect, flashSpeed.value)
        swarm.lightX = flashlight.x
        swarm.lightY = flashlight.y
        swarm.lightRadius = flashSize.value
        swarm.wLight = if (flashPower.value > 0.05f) lightFear.value else 0f
        if (frame.beatTriggered && frame.level > 0.3f) {
            swarm.startle(min(1f, 0.4f + frame.level))
            frame.sound.play(Sfx.SKITTER, 1f)
        }
        // Spiders scrabbling away from each moving foot.
        for (p in frame.people) {
            val v = hypot(p.vx * frame.aspect, p.vy)
            if (v > 0.08f && ready(p.id.toLong(), frame.time, 0.35f)) frame.sound.play(Sfx.SKITTER, 0.5f + min(v, 1f) * 0.5f, pan(p.x))
        }
        // Sub-step so fast feet can't tunnel through the swarm on a frame hitch.
        val steps = if (frame.dt > 1f / 45f) 2 else 1
        repeat(steps) { swarm.step(frame.dt / steps, frame.people) }
        updateWebs(frame)
        updateGiant(frame)
    }

    /** Most spiders stay small, some medium, a few huge; the slider blends from uniform to that. */
    private fun applySizeMix() {
        val m = sizeMix.value
        for (i in 0 until swarm.count) {
            val h1 = hash(i * 3 + 1)
            val h2 = hash(i * 3 + 2)
            val h3 = hash(i * 3 + 3)
            val base = 0.75f + h1 * 0.5f
            val mix = when {
                h2 < 0.55f -> 0.5f + 0.2f * h3
                h2 < 0.9f -> 1.0f + 0.4f * h3
                else -> 2.0f + 1.4f * h3
            }
            swarm.size[i] = base * (1f + (mix - 1f) * m)
        }
    }

    private fun hash(i: Int): Float {
        var x = i * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0xFFFFFF) / 16777216f
    }

    // --- Webs ----------------------------------------------------------------------------------

    private fun updateWebs(frame: FrameState) {
        val dt = frame.dt
        val a = frame.aspect
        val maxWebs = (webs.value * MAX_WEBS).toInt()
        webTimer -= dt
        if (webTimer <= 0f && webList.count { !it.tearing } < maxWebs && swarm.count > 0) {
            webTimer = 2.5f + rng.nextFloat() * 3f
            trySpawnWeb(frame)
        }
        for (w in webList) {
            w.age += dt
            // Anyone walking through tears it; the builder bolts.
            if (!w.tearing && frame.people.any { hypot(it.x * a - w.x, it.y - w.y) < w.radius * 1.1f }) {
                w.tearing = true
                if (w.builder >= 0) swarm.panic[w.builder] = 1f
                w.builder = -1
                frame.sound.play(Sfx.RUSTLE, 0.5f, pan(w.x / a))
            }
            if (w.tearing) w.tear = (w.tear + dt * 1.6f).coerceAtMost(1f)
            val b = w.builder
            if (b >= 0 && (b >= swarm.count || swarm.panic[b] > 0.35f)) w.builder = -1
            if (w.builder >= 0) {
                if (w.progress < 1f) w.progress = (w.progress + dt / BUILD_TIME).coerceAtMost(1f)
                pinBuilder(w, dt)
            } else if (w.progress < 0.3f) {
                w.tearing = true // abandoned before it had any real structure
            }
        }
        webList.removeAll { it.tear >= 1f || it.age > WEB_LIFE || (webs.value <= 0f && it.age > 0f && it.builder < 0) }
    }

    private fun trySpawnWeb(frame: FrameState) {
        val a = frame.aspect
        repeat(12) {
            val i = rng.nextInt(swarm.count)
            if (swarm.panic[i] > 0.05f || webList.any { it.builder == i }) return@repeat
            val x = swarm.x[i]
            val y = swarm.y[i]
            val r = (0.06f + rng.nextFloat() * 0.05f) * spiderSize.value.coerceIn(0.6f, 1.6f)
            if (x < r || x > a - r || y < r || y > 1f - r) return@repeat
            if (frame.people.any { hypot(it.x * a - x, it.y - y) < 0.3f }) return@repeat
            if (webList.any { hypot(it.x - x, it.y - y) < (it.radius + r) * 1.2f }) return@repeat
            webList += Web(x, y, r, rng.nextFloat() * 100f, i)
            return
        }
    }

    /** The builder walks the spiral it is spinning, then waits in the hub. */
    private fun pinBuilder(w: Web, dt: Float) {
        val i = w.builder
        val p = w.progress
        val (rr, theta) = if (p < 0.3f) {
            // Laying spokes: out and back along each one.
            val k = (p / 0.3f) * 14f
            val out = 1f - kotlin.math.abs((k % 1f) * 2f - 1f)
            w.radius * 0.9f * out to (k.toInt() * 0.45f + w.seed)
        } else if (p < 1f) {
            val q = (p - 0.3f) / 0.7f
            w.radius * (0.12f + 0.8f * q) to (q * 40f + w.seed)
        } else {
            0f to w.seed
        }
        val nx = w.x + cos(theta) * rr
        val ny = w.y + sin(theta) * rr
        if (p < 1f) {
            swarm.heading[i] = atan2(ny - swarm.y[i], nx - swarm.x[i])
            swarm.gait[i] += dt * 9f
        }
        swarm.x[i] = nx
        swarm.y[i] = ny
        swarm.vx[i] = 0f
        swarm.vy[i] = 0f
    }

    // --- Giant spider --------------------------------------------------------------------------

    private fun updateGiant(frame: FrameState) {
        val dt = frame.dt
        val a = frame.aspect
        if (giantT < 0f) {
            if (giant.value < 0.5f || frame.people.isEmpty()) return
            giantTimer += dt
            if (giantTimer < giantEvery.value) return
            giantTimer = 0f
            val victim = frame.people.random(rng)
            val ang = rng.nextFloat() * 6.283f
            gTargetX = victim.x * a
            gTargetY = victim.y
            gx = (gTargetX + cos(ang) * 0.2f).coerceIn(0.15f, a - 0.15f)
            gy = (gTargetY + sin(ang) * 0.2f).coerceIn(0.15f, 0.85f)
            gHeading = atan2(gTargetY - gy, gTargetX - gx)
            giantT = 0f
            frame.sound.play(Sfx.HISS, 0.6f, pan(gx / a))
            return
        }
        val before = giantT
        giantT += dt
        if (before < DROP_TIME && giantT >= DROP_TIME) {
            // Lands: everything else scatters.
            frame.sound.play(Sfx.STINGER, 1f, pan(gx / a))
            frame.sound.play(Sfx.SNAP, 0.8f, pan(gx / a))
            frame.sound.play(Sfx.SKITTER, 1f, pan(gx / a))
            swarm.startle(1f)
        }
        when {
            giantT < DROP_TIME -> gGait += dt * 2f
            giantT < REAR_END -> {
                // Rears up facing its victim, legs flailing, and lunges once.
                frame.people.minByOrNull { hypot(it.x * a - gx, it.y - gy) }?.let { gTargetX = it.x * a; gTargetY = it.y }
                gHeading = atan2(gTargetY - gy, gTargetX - gx)
                gGait += dt * 22f
                if (giantT in 1.0f..1.25f) { gx += cos(gHeading) * 0.25f * dt; gy += sin(gHeading) * 0.25f * dt }
                if (before < 1.0f && giantT >= 1.0f) frame.sound.play(Sfx.HISS, 1f, pan(gx / a))
                // Pick an escape route off the nearest edge.
                val edges = listOf(-0.4f to gy, a + 0.4f to gy, gx to -0.4f, gx to 1.4f)
                val e = edges.minBy { hypot(it.first - gx, it.second - gy) }
                gFleeX = e.first; gFleeY = e.second
            }
            else -> {
                val dx = gFleeX - gx
                val dy = gFleeY - gy
                val d = hypot(dx, dy)
                if (d < 0.05f || giantT > 7f) { giantT = -1f; return }
                gHeading = atan2(dy, dx)
                gx += dx / d * 0.9f * dt
                gy += dy / d * 0.9f * dt
                gGait += dt * 30f
            }
        }
    }

    // --- Drawing -------------------------------------------------------------------------------

    override fun draw(frame: FrameState) {
        val shadowStyle = style.value < 0.5f
        // Floor: lantern ambience (shadow style only) plus the flashlight beam in both styles.
        floorProgram.use()
        floorProgram.setCommon(frame)
        floorProgram.set1f("uLight", if (shadowStyle) light.value else 0f)
        floorProgram.set2f("uFlash", flashlight.x, flashlight.y)
        floorProgram.set1f("uFlashRadius", flashSize.value)
        floorProgram.set1f("uFlashPower", flashPower.value)
        gl.drawFullscreen()

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        drawWebs(frame, shadowStyle)

        val n = swarm.count
        instanceData.clear()
        for (i in 0 until n) {
            instanceData.put(swarm.x[i]).put(swarm.y[i]).put(swarm.heading[i])
                .put(swarm.baseSize * swarm.size[i])
                .put(swarm.gait[i]).put(swarm.panic[i]).put(0.02f).put(hash(i) * 10f)
        }
        var total = n
        if (giantT >= 0f) {
            // Falling from above: big and high, then landing with a little bounce.
            val k = (giantT / DROP_TIME).coerceIn(0f, 1f)
            val height = (1f - k * k)
            val bounce = if (giantT > DROP_TIME) 1f + 0.08f * sin((giantT - DROP_TIME) * 18f) * kotlin.math.exp(-(giantT - DROP_TIME) * 5f) else 1f
            val scale = GIANT_SIZE * (1f + height * 0.6f) * bounce
            instanceData.put(gx).put(gy).put(gHeading).put(scale).put(gGait).put(1f).put(height).put(3.3f)
            total++
        }
        instanceData.flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, total * FLOATS_PER_INSTANCE * 4, instanceData)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        spiderProgram.use()
        spiderProgram.set1f("uAspect", frame.aspect)
        spiderProgram.set1f("uTime", frame.time)
        spiderProgram.set1f("uStyle", if (shadowStyle) 0f else 1f)
        spiderProgram.set2f("uShadowOffset", 0.25f, -0.3f)
        spiderProgram.set1f("uShadow3D", shadow3d.value)
        spiderProgram.set2f("uLightPos", flashlight.x, flashlight.y)
        spiderProgram.set1f("uLightRadius", flashSize.value)
        spiderProgram.set1f("uLightPower", flashPower.value)
        spiderProgram.set1f("uLantern", if (shadowStyle) light.value else 0f)
        spiderProgram.set1f("uEyeGlow", eyeGlow.value)
        spiderProgram.set1f("uRadioactive", radioactive.value)
        spiderProgram.set1f("uRealism", realism.value)
        spiderProgram.set1f("uGlowPass", 0f)
        spiderProgram.set1f("uQuadScale", 1f)
        GLES30.glBindVertexArray(vao)
        if (shadowStyle) {
            spiderProgram.set1f("uShadowPass", 1f)
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, total)
        }
        spiderProgram.set1f("uShadowPass", 0f)
        if (radioactive.value > 0.5f) {
            spiderProgram.set1f("uGlowPass", 1f)
            spiderProgram.set1f("uQuadScale", 2.6f)
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, total)
            spiderProgram.set1f("uGlowPass", 0f)
            spiderProgram.set1f("uQuadScale", 1f)
        }
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, total)
        GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawWebs(frame: FrameState, shadowStyle: Boolean) {
        if (webList.isEmpty()) return
        webBatch.begin()
        for (w in webList) {
            // Threads catch the lantern faintly and shine in the torch beam.
            val torch = (1f - hypot(w.x - flashlight.x, w.y - flashlight.y) / flashSize.value).coerceIn(0f, 1f) * flashPower.value
            val lit = if (shadowStyle) 0.35f + light.value * 0.5f + torch * 1.4f else 0.8f + torch
            val fade = ((WEB_LIFE - w.age) / 5f).coerceIn(0f, 1f)
            val (r, g, b) = if (shadowStyle) Triple(0.85f, 0.85f, 0.9f) else Triple(0.6f, 1f, 0.8f)
            webBatch.add(w.x, w.y, w.seed, w.radius, r * lit, g * lit, b * lit, fade, w.progress, w.seed, w.tear, 0f)
        }
        webProgram.use()
        webProgram.set1f("uAspect", frame.aspect)
        webProgram.set2f("uOffset", 0f, 0f)
        webBatch.draw()
    }

    companion object {
        const val MAX_SPIDERS = 2000
        private const val FLOATS_PER_INSTANCE = 8
        private const val BASE_SIZE = 0.028f
        private const val MAX_WEBS = 6
        private const val BUILD_TIME = 9f
        private const val WEB_LIFE = 90f
        private const val GIANT_SIZE = 0.2f
        private const val DROP_TIME = 0.55f
        private const val REAR_END = 2.3f
    }
}
