package com.phantomfloor.scenes

import com.phantomfloor.gl.GLES30
import com.phantomfloor.audio.Ambience
import com.phantomfloor.audio.Sfx
import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.SpriteBatch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * SUMMER FIREFLIES: lightning bugs drift over a moonlit lawn, then gather around people and
 * follow them in lazy orbits. Their blinking slowly falls into step (a Kuramoto model, the
 * same maths that describes real synchronous fireflies), so the swarm ends up pulsing together.
 */
class FirefliesScene : Scene("fireflies", "Fireflies", Season.SUMMER) {

    private val count = SceneParam("count", "Fireflies", 10f, MAX_FLIES.toFloat(), 120f, step = 10f)
    private val follow = SceneParam("follow", "Follow people", 0f, 1f, 0.8f)
    private val sync = SceneParam("sync", "Blink in sync", 0f, 1f, 0.5f)
    private val glow = SceneParam("glow", "Glow", 0.3f, 2f, 1f)
    private val moon = SceneParam("moon", "Moonlight on grass", 0f, 1f, 0.6f)

    override val params = listOf(count, follow, sync, glow, moon)

    override val ambience = Ambience(crickets = 1f, wind = 0.1f)
    private var lastMeanPhase = 0f

    private lateinit var lawn: GlProgram
    private lateinit var sparkProgram: GlProgram
    private val sprites = SpriteBatch(MAX_FLIES * 2)
    private val rng = Random(8)

    private val x = FloatArray(MAX_FLIES); private val y = FloatArray(MAX_FLIES)
    private val vx = FloatArray(MAX_FLIES); private val vy = FloatArray(MAX_FLIES)
    private val heading = FloatArray(MAX_FLIES)
    private val phase = FloatArray(MAX_FLIES); private val omega = FloatArray(MAX_FLIES)
    private val orbitR = FloatArray(MAX_FLIES); private val orbitA = FloatArray(MAX_FLIES); private val orbitW = FloatArray(MAX_FLIES)
    private val altitude = FloatArray(MAX_FLIES)
    private var initialized = false

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        lawn = gl.shaders.program("fullscreen.vert", "night_lawn.frag")
        sparkProgram = gl.shaders.program("sprite.vert", "spark.frag")
        sprites.onSurfaceCreated()
    }

    override fun update(frame: FrameState) {
        val dt = frame.dt
        val aspect = frame.aspect
        if (!initialized) {
            for (i in 0 until MAX_FLIES) {
                x[i] = rng.nextFloat() * aspect
                y[i] = rng.nextFloat()
                heading[i] = rng.nextFloat() * TAU
                phase[i] = rng.nextFloat() * TAU
                omega[i] = TAU / (1.8f + rng.nextFloat() * 1.4f)
                orbitR[i] = 0.05f + rng.nextFloat() * 0.13f
                orbitA[i] = rng.nextFloat() * TAU
                orbitW[i] = (0.4f + rng.nextFloat() * 0.8f) * if (rng.nextBoolean()) 1f else -1f
                altitude[i] = rng.nextFloat()
            }
            initialized = true
        }
        val n = count.int

        // Mean-field Kuramoto coupling: each fly nudges its phase toward the swarm's average.
        var cs = 0f
        var sn = 0f
        for (i in 0 until n) { cs += cos(phase[i]); sn += sin(phase[i]) }
        val order = hypot(cs, sn) / max(n, 1)
        val meanPhase = atan2(sn, cs)
        val k = sync.value * 1.6f
        // Once the swarm has synchronised, each collective flash rings softly.
        if (order > 0.8f && lastMeanPhase < 0f && meanPhase >= 0f) frame.sound.play(Sfx.CHIME, 0.15f * order)
        lastMeanPhase = meanPhase

        val people = frame.people
        for (i in 0 until n) {
            phase[i] += (omega[i] + k * order * sin(meanPhase - phase[i])) * dt
            if (phase[i] > TAU) phase[i] -= TAU

            // Lazy wandering
            heading[i] += (sin(frame.time * 0.7f + i * 1.3f) * 1.5f) * dt
            var tx = cos(heading[i]) * 0.04f
            var ty = sin(heading[i]) * 0.04f

            if (people.isNotEmpty() && follow.value > 0f) {
                // Each fly adopts one person and circles them.
                val p = people[i % people.size]
                orbitA[i] += orbitW[i] * dt
                val gx = p.x * aspect + cos(orbitA[i]) * orbitR[i]
                val gy = p.y + sin(orbitA[i]) * orbitR[i]
                val f = follow.value
                tx = tx * (1f - f) + (gx - x[i]) * 1.8f * f
                ty = ty * (1f - f) + (gy - y[i]) * 1.8f * f
            }
            val blend = 1f - exp(-dt * 2.5f)
            vx[i] += (tx - vx[i]) * blend
            vy[i] += (ty - vy[i]) * blend
            x[i] = (x[i] + vx[i] * dt).coerceIn(0.01f, aspect - 0.01f)
            y[i] = (y[i] + vy[i] * dt).coerceIn(0.01f, 0.99f)
        }
    }

    override fun draw(frame: FrameState) {
        lawn.use()
        lawn.setCommon(frame)
        lawn.set1f("uMoon", moon.value)
        gl.drawFullscreen()

        val n = count.int
        sprites.begin()
        for (i in 0 until n) {
            // A quick bright flash near phase 0, then a faint ember until the next one.
            val c = cos(phase[i])
            val flash = max(c, 0f).pow(14f)
            val ember = 0.06f
            val intensity = (flash + ember) * glow.value
            val size = 0.006f + 0.004f * altitude[i]
            // Light spilling onto the grass beneath, then the bug itself.
            sprites.add(x[i], y[i] - 0.004f, 0f, size * 9f, 0.6f, 1f, 0.25f, flash * 0.18f * glow.value, p0 = 0f, p1 = -1f, p2 = 0f)
            sprites.add(x[i], y[i], 0f, size * (1f + flash), 0.8f, 1f, 0.35f, intensity, p0 = 0.5f, p1 = -1f, p2 = 0.5f)
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
        const val MAX_FLIES = 400
        private const val TAU = (2 * PI).toFloat()
    }
}
