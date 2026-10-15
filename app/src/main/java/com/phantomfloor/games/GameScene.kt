package com.phantomfloor.games

import android.opengl.GLES30
import com.phantomfloor.audio.Ambience
import com.phantomfloor.audio.Sfx
import com.phantomfloor.engine.FrameState
import com.phantomfloor.engine.PersonPoint
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.SpriteBatch
import com.phantomfloor.gl.TextRenderer
import com.phantomfloor.scenes.Scene
import com.phantomfloor.scenes.Season
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Base for floor games. Handles the shared flow:
 * ATTRACT (title, how to play, step on START) -> COUNTDOWN (3, 2, 1) -> PLAYING -> OVER (score).
 * Games add shapes and labels in world units (x in [0, aspect], y in [0, 1]); "stepping on" a
 * target is detected when a person (camera track or finger) newly enters it.
 */
abstract class GameScene(id: String, title: String) : Scene(id, title, Season.GAMES) {

    enum class Phase { ATTRACT, COUNTDOWN, PLAYING, OVER }

    /** Instructions shown on the attract screen; use \n for line breaks. */
    protected abstract val howToPlay: String

    /** Round length in seconds, or null for untimed games that end themselves. */
    protected open val roundSeconds: Float? = 60f

    protected open val accent = floatArrayOf(0.5f, 1f, 0.6f)

    /** Free-play games (Coloring) skip the start screen and never end. */
    protected open val freePlay = false

    override val ambience = Ambience.SILENT

    protected var phase = Phase.ATTRACT
        private set
    protected var phaseTime = 0f
        private set
    protected var score = 0
    protected var best = 0
        private set
    protected var aspect = 16f / 9f
        private set
    protected val rng = Random(System.nanoTime())
    protected lateinit var frame: FrameState
        private set

    private var overMessage = ""
    private var newBest = false
    private var lastCount = -1

    // Rendering
    protected val shapes = SpriteBatch(3000)
    private lateinit var shapeProgram: GlProgram
    private lateinit var background: GlProgram
    private val text = TextRenderer()
    private class Label(val s: String, val x: Float, val y: Float, val h: Float, val r: Float, val g: Float, val b: Float, val a: Float)
    private val labels = ArrayList<Label>()

    // Step detection: which (person, target) pairs were inside last frame.
    private var inside = HashSet<Long>()
    private var insideNow = HashSet<Long>()

    // Confetti
    private class Bit(var x: Float, var y: Float, var vx: Float, var vy: Float, var age: Float, val r: Float, val g: Float, val b: Float, var spin: Float)
    private val confettiBits = ArrayList<Bit>()

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        shapeProgram = gl.shaders.program("sprite.vert", "shape.frag")
        background = gl.shaders.program("fullscreen.vert", "game_bg.frag")
        text.onSurfaceCreated(gl)
        shapes.onSurfaceCreated()
    }

    // --- Hooks for games ------------------------------------------------------------------------

    protected abstract fun startRound()
    protected abstract fun updatePlaying(dt: Float)
    /** Add shapes/labels for the playing field (also shown faded behind the attract screen). */
    protected abstract fun drawPlaying()
    protected open fun scoreText(): String = "Score $score"
    /** Optional extra shapes on the attract screen. */
    protected open fun drawAttractDecor() {}
    /** Full-screen layer drawn over the background, under the shapes (e.g. paint). */
    protected open fun drawUnderlay() {}

    /** Ends the round now with a message ("BOOM!", "X WINS!"). */
    protected fun endRound(message: String) {
        if (phase != Phase.PLAYING) return
        overMessage = message
        newBest = score > best
        if (newBest) best = score
        setPhase(Phase.OVER)
        frame.sound.play(if (newBest) Sfx.FANFARE else Sfx.BEEP, 0.8f)
        if (newBest) confetti(aspect / 2f, 0.5f, 120)
    }

    // --- Helpers --------------------------------------------------------------------------------

    /** The person who stepped onto circle ([x], [y], [radius]) this frame, if any. [target] must be unique per target. */
    protected fun stepped(target: Int, x: Float, y: Float, radius: Float): PersonPoint? {
        var hit: PersonPoint? = null
        for (p in frame.people) {
            if (hypot(p.x * aspect - x, p.y - y) < radius) {
                val key = p.id.toLong() * 100_000L + target
                insideNow.add(key)
                if (key !in inside && hit == null) hit = p
            }
        }
        return hit
    }

    /** People currently standing within the circle. */
    protected fun occupants(x: Float, y: Float, radius: Float): List<PersonPoint> =
        frame.people.filter { hypot(it.x * aspect - x, it.y - y) < radius }

    protected fun shape(type: Int, x: Float, y: Float, size: Float, r: Float, g: Float, b: Float, a: Float = 1f, p1: Float = 0f, p2: Float = 0f, p3: Float = 0f, angle: Float = 0f) =
        shapes.add(x, y, angle, size, r, g, b, a, type.toFloat(), p1, p2, p3)

    protected fun label(s: String, x: Float, y: Float, h: Float, r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f) {
        labels += Label(s, x, y, h, r, g, b, a)
    }

    protected fun confetti(x: Float, y: Float, count: Int) {
        repeat(count) {
            val an = rng.nextFloat() * 6.283f
            val v = 0.15f + rng.nextFloat() * 0.5f
            val c = CONFETTI[rng.nextInt(CONFETTI.size)]
            confettiBits += Bit(x, y, cos(an) * v, sin(an) * v, 0f, c[0], c[1], c[2], rng.nextFloat() * 6f)
        }
    }

    protected val timeLeft: Float get() = (roundSeconds ?: 0f) - phaseTime

    private fun setPhase(p: Phase) {
        phase = p
        phaseTime = 0f
        lastCount = -1
    }

    // --- Scene plumbing -------------------------------------------------------------------------

    final override fun update(frame: FrameState) {
        this.frame = frame
        aspect = frame.aspect
        val dt = frame.dt
        phaseTime += dt
        insideNow = HashSet()

        if (freePlay && phase != Phase.PLAYING) {
            startRound()
            setPhase(Phase.PLAYING)
        }
        when (phase) {
            Phase.ATTRACT -> {
                if (stepped(START_ID, aspect / 2f, START_Y, START_R) != null) {
                    frame.sound.play(Sfx.CLICK)
                    setPhase(Phase.COUNTDOWN)
                }
            }
            Phase.COUNTDOWN -> {
                val n = 3 - phaseTime.toInt()
                if (n != lastCount && n > 0) { frame.sound.play(Sfx.BEEP, 0.7f); lastCount = n }
                if (phaseTime >= 3f) {
                    score = 0
                    startRound()
                    setPhase(Phase.PLAYING)
                    frame.sound.play(Sfx.TONE_4, 0.8f)
                }
            }
            Phase.PLAYING -> {
                updatePlaying(dt)
                val limit = roundSeconds
                if (limit != null && phase == Phase.PLAYING) {
                    val left = (limit - phaseTime).toInt()
                    if (left in 0..4 && left != lastCount) { frame.sound.play(Sfx.TICK, 0.8f); lastCount = left }
                    if (phaseTime >= limit) endRound("TIME!")
                }
            }
            Phase.OVER -> if (phaseTime > 7f) setPhase(Phase.ATTRACT)
        }

        for (c in confettiBits) {
            c.age += dt
            c.vx *= 0.97f; c.vy *= 0.97f
            c.x += c.vx * dt; c.y += c.vy * dt
            c.spin += dt * 8f
        }
        confettiBits.removeAll { it.age > 2.5f }
        inside = insideNow
    }

    final override fun draw(frame: FrameState) {
        background.use()
        background.setCommonUniforms(frame)
        background.set3f("uAccent", accent[0], accent[1], accent[2])
        gl.drawFullscreen()
        drawUnderlay()

        shapes.begin()
        labels.clear()
        val mid = aspect / 2f
        when (phase) {
            Phase.ATTRACT -> {
                drawAttractDecor()
                label(title.uppercase(), mid, 0.8f, 0.11f, accent[0], accent[1], accent[2])
                howToPlay.split('\n').forEachIndexed { i, line -> label(line, mid, 0.64f - i * 0.065f, 0.045f, 0.85f, 0.85f, 0.9f) }
                if (best > 0) label("Best: $best", mid, 0.37f, 0.045f, 1f, 0.85f, 0.3f)
                val pulse = 0.5f + 0.5f * sin(frame.time * 4f)
                shape(SHAPE_RING, mid, START_Y, START_R * (1f + pulse * 0.08f), accent[0], accent[1], accent[2], 1f, p1 = 0.12f)
                shape(SHAPE_GLOW, mid, START_Y, START_R * 1.6f, accent[0], accent[1], accent[2], 0.5f + 0.3f * pulse)
                label("START", mid, START_Y, 0.05f)
                label("step here", mid, START_Y - START_R - 0.04f, 0.03f, 0.75f, 0.75f, 0.8f)
            }
            Phase.COUNTDOWN -> {
                drawPlaying()
                val n = (3 - phaseTime.toInt()).coerceAtLeast(1)
                val k = 1f - (phaseTime % 1f)
                label("$n", mid, 0.5f, 0.25f * (0.8f + 0.4f * k), 1f, 1f, 1f, 0.4f + 0.6f * k)
            }
            Phase.PLAYING -> {
                drawPlaying()
                if (!freePlay) label(scoreText(), 0.3f, 0.95f, 0.045f, 1f, 1f, 1f)
                roundSeconds?.let { label("${timeLeft.toInt().coerceAtLeast(0)}s", aspect - 0.18f, 0.95f, 0.045f, if (timeLeft < 5f) 1f else 0.85f, if (timeLeft < 5f) 0.35f else 0.85f, 0.4f) }
            }
            Phase.OVER -> {
                drawPlaying()
                shapes.add(mid, 0.5f, 0f, 2f, 0f, 0f, 0f, 0.55f, SHAPE_PAD.toFloat(), 0.1f, 0f, 0f)
                label(overMessage, mid, 0.62f, 0.12f, accent[0], accent[1], accent[2])
                label(scoreText(), mid, 0.47f, 0.07f)
                if (newBest) label("NEW BEST!", mid, 0.36f, 0.055f, 1f, 0.85f, 0.25f)
            }
        }
        for (c in confettiBits) {
            val a = (1f - c.age / 2.5f).coerceIn(0f, 1f)
            shape(SHAPE_PAD, c.x, c.y, 0.008f, c.r, c.g, c.b, a, p1 = 0.2f, angle = c.spin)
        }

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        shapeProgram.use()
        shapeProgram.set1f("uAspect", aspect)
        shapeProgram.set1f("uTime", frame.time)
        shapeProgram.set2f("uOffset", 0f, 0f)
        shapes.draw()
        for (l in labels) text.draw(l.s, l.x, l.y, l.h, aspect, l.r, l.g, l.b, l.a)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun GlProgram.setCommonUniforms(frame: FrameState) {
        set2f("uResolution", width.toFloat(), height.toFloat())
        set1f("uTime", frame.time)
        set1f("uBeat", frame.beat)
    }

    companion object {
        const val SHAPE_DISC = 0
        const val SHAPE_RING = 1
        const val SHAPE_PAD = 2
        const val SHAPE_X = 3
        const val SHAPE_PROGRESS = 4
        const val SHAPE_STAR = 5
        const val SHAPE_BOMB = 6
        const val SHAPE_MOLE = 7
        const val SHAPE_BALLOON = 8
        const val SHAPE_SPLAT = 9
        const val SHAPE_SPIDER = 10
        const val SHAPE_GLOW = 11
        const val SHAPE_LAVA = 12

        private const val START_ID = 99_999
        private const val START_Y = 0.18f
        private const val START_R = 0.085f

        private val CONFETTI = arrayOf(
            floatArrayOf(1f, 0.3f, 0.3f), floatArrayOf(1f, 0.85f, 0.2f), floatArrayOf(0.3f, 0.6f, 1f),
            floatArrayOf(0.4f, 1f, 0.5f), floatArrayOf(0.9f, 0.4f, 1f),
        )
    }
}
