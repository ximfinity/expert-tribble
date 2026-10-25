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
    /** Seconds since the scene started; unlike [phaseTime] it never resets. */
    private var clock = 0f
    private var pendingFanfare = -1f

    // START: someone has to stand on it for a moment, and people already standing there when the
    // title screen appears (left over from the last round) don't count until they step off.
    private var startHold = 0f
    private val startBlocked = HashSet<Int>()

    // Juice: floating "+1"s and a bounce when the score changes.
    private class Floater(val text: String, val x: Float, val y: Float, val r: Float, val g: Float, val b: Float, var age: Float = 0f)
    private val floaters = ArrayList<Floater>()
    private var shownScore = 0
    private var scoreBounce = 0f

    // Rendering
    protected val shapes = SpriteBatch(3000)
    private lateinit var shapeProgram: GlProgram
    private lateinit var background: GlProgram
    private val text = TextRenderer()
    private class Label(val s: String, val x: Float, val y: Float, val h: Float, val r: Float, val g: Float, val b: Float, val a: Float)
    private val labels = ArrayList<Label>()

    // Step detection: when each (person, target) pair was last seen inside. A step counts only
    // after the pair has been apart for [STEP_GAP], so a tracking frame the camera drops while
    // someone stands still isn't mistaken for a new step.
    private val lastSeen = HashMap<Long, Float>()

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
        // The game's own sound (a buzz, an explosion) plays now; the celebration follows it.
        if (newBest) pendingFanfare = 0.6f else frame.sound.play(Sfx.BEEP, 0.8f)
    }

    // --- Helpers --------------------------------------------------------------------------------

    /** The person who stepped onto circle ([x], [y], [radius]) this frame, if any. [target] must be unique per target. */
    protected fun stepped(target: Int, x: Float, y: Float, radius: Float): PersonPoint? {
        var hit: PersonPoint? = null
        for (p in frame.people) {
            if (hypot(p.x * aspect - x, p.y - y) < radius) {
                val key = p.id.toLong() * 100_000L + target
                val last = lastSeen.put(key, clock)
                if ((last == null || clock - last > STEP_GAP) && hit == null) hit = p
            }
        }
        return hit
    }

    /** Floating text that rises and fades, e.g. "+1" where something was hit. */
    protected fun floater(text: String, x: Float, y: Float, r: Float = 1f, g: Float = 1f, b: Float = 1f) {
        floaters += Floater(text, x, y, r, g, b)
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
        if (p == Phase.ATTRACT) {
            startHold = 0f
            startBlocked.clear()
            if (::frame.isInitialized) for (o in occupants(aspect / 2f, START_Y, START_R)) startBlocked += o.id
        }
    }

    // --- Scene plumbing -------------------------------------------------------------------------

    final override fun update(frame: FrameState) {
        this.frame = frame
        aspect = frame.aspect
        val dt = frame.dt
        phaseTime += dt
        clock += dt

        if (freePlay && phase != Phase.PLAYING) {
            startRound()
            setPhase(Phase.PLAYING)
        }
        when (phase) {
            Phase.ATTRACT -> {
                val onStart = occupants(aspect / 2f, START_Y, START_R)
                startBlocked.retainAll(onStart.map { it.id }.toSet())
                val starter = onStart.any { it.id !in startBlocked }
                val before = startHold
                startHold = if (starter) startHold + dt else (startHold - dt * 2f).coerceAtLeast(0f)
                if (starter && before == 0f) frame.sound.play(Sfx.CLICK, 0.5f)
                if (startHold >= START_HOLD) {
                    frame.sound.play(Sfx.CLICK)
                    // The new round is set up now, so the countdown shows the fresh field.
                    score = 0
                    shownScore = 0
                    startRound()
                    setPhase(Phase.COUNTDOWN)
                }
            }
            Phase.COUNTDOWN -> {
                val n = 3 - phaseTime.toInt()
                if (n != lastCount && n > 0) { frame.sound.play(Sfx.BEEP, 0.7f); lastCount = n }
                if (phaseTime >= 3f) {
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

        if (pendingFanfare >= 0f) {
            pendingFanfare -= dt
            if (pendingFanfare < 0f) {
                frame.sound.play(Sfx.FANFARE, 0.8f)
                confetti(aspect / 2f, 0.5f, 120)
            }
        }
        if (score != shownScore) {
            if (score > shownScore && phase == Phase.PLAYING) scoreBounce = 1f
            shownScore = score
        }
        scoreBounce = (scoreBounce - dt * 4f).coerceAtLeast(0f)
        for (f in floaters) f.age += dt
        floaters.removeAll { it.age > FLOAT_LIFE }
        lastSeen.values.removeAll { clock - it > 1f }

        for (c in confettiBits) {
            c.age += dt
            c.vx *= 0.97f; c.vy *= 0.97f
            c.x += c.vx * dt; c.y += c.vy * dt
            c.spin += dt * 8f
        }
        confettiBits.removeAll { it.age > 2.5f }
    }

    final override fun draw(frame: FrameState) {
        background.use()
        background.setCommonUniforms(frame)
        background.set3f("uAccent", accent[0], accent[1], accent[2])
        gl.drawFullscreen()
        drawUnderlay()
        compose(frame)

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

    /** Builds this frame's shapes and labels. CPU only, so play-test bots can "see" the floor. */
    private fun compose(frame: FrameState) {
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
                val hold = (startHold / START_HOLD).coerceIn(0f, 1f)
                shape(SHAPE_RING, mid, START_Y, START_R * (1f + pulse * 0.08f), accent[0], accent[1], accent[2], 1f, p1 = 0.12f)
                shape(SHAPE_GLOW, mid, START_Y, START_R * (1.6f + hold * 0.6f), accent[0], accent[1], accent[2], 0.5f + 0.3f * pulse + 0.3f * hold)
                if (hold > 0f) shape(SHAPE_PROGRESS, mid, START_Y, START_R * 1.25f, 1f, 1f, 1f, 1f, p1 = hold)
                label("START", mid, START_Y, 0.045f)
                label(if (hold > 0f) "hold still…" else "stand here", mid, START_Y - START_R - 0.05f, 0.04f, 0.8f, 0.8f, 0.85f)
            }
            Phase.COUNTDOWN -> {
                drawPlaying()
                // Dim the new field so the number stands out; it brightens as play begins.
                val dim = 0.6f * (1f - smooth((phaseTime - 2.6f) / 0.4f))
                shapes.add(mid, 0.5f, 0f, 2f, 0f, 0f, 0f, dim, SHAPE_PAD.toFloat(), 0.1f, 0f, 0f)
                val n = (3 - phaseTime.toInt()).coerceAtLeast(1)
                val f = phaseTime % 1f
                // Each number punches in large, settles with an ease-out, then fades.
                val scale = 1f + 0.6f * (1f - easeOut(f / 0.35f))
                val alpha = 1f - smooth((f - 0.7f) / 0.3f)
                shape(SHAPE_RING, mid, 0.5f, 0.12f + 0.25f * easeOut(f), accent[0], accent[1], accent[2], (1f - f) * 0.8f, p1 = 0.05f)
                label("$n", mid, 0.5f, 0.22f * scale, 1f, 1f, 1f, alpha)
            }
            Phase.PLAYING -> {
                drawPlaying()
                if (phaseTime < GO_TIME && !freePlay) {
                    val f = phaseTime / GO_TIME
                    label("GO!", mid, 0.5f, 0.2f * (0.8f + 0.5f * easeOut(f)), accent[0], accent[1], accent[2], 1f - smooth((f - 0.5f) / 0.5f))
                }
                if (!freePlay) label(scoreText(), 0.3f, 0.95f, 0.045f * (1f + 0.3f * scoreBounce * scoreBounce), 1f, 1f, 1f)
                roundSeconds?.let { label("${timeLeft.toInt().coerceAtLeast(0)}s", aspect - 0.18f, 0.95f, 0.045f, if (timeLeft < 5f) 1f else 0.85f, if (timeLeft < 5f) 0.35f else 0.85f, 0.4f) }
            }
            Phase.OVER -> {
                drawPlaying()
                // The results card fades in while the message drops into place and the score counts up.
                val k = easeOut(phaseTime / 0.5f)
                shapes.add(mid, 0.5f, 0f, 2f, 0f, 0f, 0f, 0.6f * k, SHAPE_PAD.toFloat(), 0.1f, 0f, 0f)
                label(overMessage, mid, 0.62f + 0.08f * (1f - k), 0.12f * (0.7f + 0.3f * k), accent[0], accent[1], accent[2], k)
                val shown = score * easeOut((phaseTime - 0.4f) / 1f)
                withScore(shown.toInt()) { label(scoreText(), mid, 0.47f, 0.07f, 1f, 1f, 1f, k) }
                if (newBest && phaseTime > 0.6f) {
                    val b = easeOut((phaseTime - 0.6f) / 0.4f)
                    label("NEW BEST!", mid, 0.36f, 0.055f * (1.4f - 0.4f * b), 1f, 0.85f, 0.25f, b)
                }
            }
        }
        for (f in floaters) {
            val t = f.age / FLOAT_LIFE
            val pop = 1f + 0.5f * (1f - easeOut(t / 0.25f))
            label(f.text, f.x, f.y + 0.1f * easeOut(t), 0.05f * pop, f.r, f.g, f.b, 1f - smooth((t - 0.5f) / 0.5f))
        }
        for (c in confettiBits) {
            val a = (1f - c.age / 2.5f).coerceIn(0f, 1f)
            shape(SHAPE_PAD, c.x, c.y, 0.008f, c.r, c.g, c.b, a, p1 = 0.2f, angle = c.spin)
        }
    }

    /** Runs [block] with [score] temporarily set to [value], so [scoreText] can show a count-up. */
    private inline fun withScore(value: Int, block: () -> Unit) {
        val real = score
        score = value
        block()
        score = real
    }

    private fun easeOut(x: Float): Float { val t = x.coerceIn(0f, 1f); return 1f - (1f - t) * (1f - t) * (1f - t) }
    private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

    // --- Headless play-testing (see app/src/test/.../playtest) ----------------------------------

    /** One shape on the floor: type (SHAPE_*), centre and half-size in world units, alpha, params. */
    internal class SeenShape(val type: Int, val x: Float, val y: Float, val size: Float, val alpha: Float, val p1: Float, val p2: Float, val p3: Float)
    internal class SeenText(val text: String, val x: Float, val y: Float, val height: Float)
    internal class Snapshot(val shapes: List<SeenShape>, val texts: List<SeenText>)

    /** What a player would see this frame, without touching GL. */
    internal fun snapshot(frame: FrameState): Snapshot {
        compose(frame)
        val seen = ArrayList<SeenShape>(shapes.count)
        val f = FloatArray(SpriteBatch.FLOATS)
        for (i in 0 until shapes.count) {
            shapes.read(i, f)
            seen += SeenShape(f[8].toInt(), f[0], f[1], f[3], f[7], f[9], f[10], f[11])
        }
        return Snapshot(seen, labels.map { SeenText(it.s, it.x, it.y, it.h) })
    }

    internal val playPhase get() = phase
    internal val playOverMessage get() = overMessage
    internal val playScore get() = score
    internal val playBest get() = best

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

        private const val START_Y = 0.2f
        private const val START_R = 0.09f
        /** Seconds someone must stand on START: long enough that walking across it doesn't count. */
        private const val START_HOLD = 0.7f
        private const val STEP_GAP = 0.25f
        private const val FLOAT_LIFE = 0.9f
        private const val GO_TIME = 0.8f

        private val CONFETTI = arrayOf(
            floatArrayOf(1f, 0.3f, 0.3f), floatArrayOf(1f, 0.85f, 0.2f), floatArrayOf(0.3f, 0.6f, 1f),
            floatArrayOf(0.4f, 1f, 0.5f), floatArrayOf(0.9f, 0.4f, 1f),
        )
    }
}
