package com.phantomfloor.games

import com.phantomfloor.audio.Sfx
import com.phantomfloor.scenes.SceneParam
import java.util.Locale

/**
 * DEFUSE THE BOMB: the bomb flashes a code on its four coloured wires. Step on the wires in the
 * same order before the timer runs out. Each defused bomb is a level; codes get longer.
 */
class DefuseBombGame : GameScene("game_bomb", "Defuse the Bomb") {

    private val startLevel = SceneParam("level", "Starting level", 1f, 5f, 1f, step = 1f)
    override val params = listOf(startLevel)
    override val howToPlay = "Watch the bomb flash its code.\nStep on the wires in that order before it blows!"
    override val roundSeconds: Float? = null
    override val accent = floatArrayOf(1f, 0.45f, 0.2f)

    private enum class Mode { SHOW, INPUT, DEFUSED, EXPLODING }

    private val colors = arrayOf(
        floatArrayOf(1f, 0.2f, 0.2f), floatArrayOf(0.25f, 0.5f, 1f),
        floatArrayOf(1f, 0.85f, 0.2f), floatArrayOf(0.3f, 1f, 0.4f),
    )
    private val tones = arrayOf(Sfx.TONE_1, Sfx.TONE_2, Sfx.TONE_3, Sfx.TONE_4)
    private var mode = Mode.SHOW
    private var modeTime = 0f
    private var level = 1
    private val code = ArrayList<Int>()
    private var entered = 0
    private var timer = 0f
    private var timerMax = 1f
    private var lastTick = 0f
    private val flash = FloatArray(4)

    private fun px(i: Int) = aspect / 2f + if (i % 2 == 0) -0.45f else 0.45f
    private fun py(i: Int) = if (i < 2) 0.72f else 0.3f
    private val cx get() = aspect / 2f

    override fun scoreText() = "Level $level"

    override fun startRound() {
        level = startLevel.int
        newCode()
    }

    private fun newCode() {
        code.clear()
        repeat(level + 2) { code += rng.nextInt(4) }
        entered = 0
        mode = Mode.SHOW
        modeTime = 0f
    }

    override fun updatePlaying(dt: Float) {
        modeTime += dt
        // Track pad presence every frame (see SimonGame) so only fresh steps count during input.
        val hits = Array(4) { stepped(it, px(it), py(it), 0.12f) }
        for (i in 0 until 4) flash[i] = (flash[i] - dt * 3f).coerceAtLeast(0f)
        when (mode) {
            Mode.SHOW -> {
                val step = (0.7f - level * 0.04f).coerceAtLeast(0.4f)
                val t = modeTime - 0.6f // short pause before the first flash
                if (t < 0f) return
                val idx = (t / step).toInt()
                val within = t - idx * step
                if (idx in code.indices && within < dt + 1e-4f) {
                    flash[code[idx]] = 1f
                    frame.sound.play(tones[code[idx]], 0.8f)
                }
                if (idx >= code.size) {
                    mode = Mode.INPUT
                    modeTime = 0f
                    timerMax = (5f + code.size * 1.6f - level * 0.4f).coerceAtLeast(4f)
                    timer = timerMax
                    lastTick = timer
                }
            }
            Mode.INPUT -> {
                timer -= dt
                // Ticking speeds up as time runs out.
                val interval = 0.15f + 0.85f * (timer / timerMax)
                if (lastTick - timer >= interval) { frame.sound.play(Sfx.TICK, 0.7f); lastTick = timer }
                for (i in 0 until 4) {
                    val p = hits[i] ?: continue
                    flash[i] = 1f
                    if (i == code[entered]) {
                        frame.sound.play(tones[i], 0.8f, pan(p.x))
                        entered++
                        if (entered == code.size) {
                            score = level
                            mode = Mode.DEFUSED
                            modeTime = 0f
                            frame.sound.play(Sfx.FANFARE, 0.9f)
                            confetti(cx, 0.52f, 80)
                        }
                    } else {
                        frame.sound.play(Sfx.BUZZ, 0.9f, pan(p.x))
                        timer -= 3f
                    }
                }
                if (timer <= 0f && mode == Mode.INPUT) {
                    mode = Mode.EXPLODING
                    modeTime = 0f
                    frame.sound.play(Sfx.EXPLOSION, 1f)
                }
            }
            Mode.DEFUSED -> if (modeTime > 2f) { level++; newCode() }
            Mode.EXPLODING -> if (modeTime > 1.5f) endRound("BOOM!")
        }
    }

    override fun drawPlaying() {
        for (i in 0 until 4) {
            val c = colors[i]
            val k = 0.45f + 0.55f * flash[i]
            shape(SHAPE_PAD, px(i), py(i), 0.11f, c[0] * k, c[1] * k, c[2] * k, 1f, p1 = 0.3f, p2 = flash[i])
            if (flash[i] > 0f) shape(SHAPE_GLOW, px(i), py(i), 0.25f, c[0], c[1], c[2], flash[i])
        }
        val urgency = if (mode == Mode.INPUT) 1f - timer / timerMax else 0f
        if (mode == Mode.EXPLODING) {
            val k = 1f - modeTime / 1.5f
            shape(SHAPE_GLOW, cx, 0.52f, 0.9f * (1f - k * 0.5f), 1f, 0.55f, 0.1f, k)
            shape(SHAPE_SPLAT, cx, 0.52f, 0.3f, 0.25f, 0.2f, 0.18f, 0.9f, p1 = 0.3f)
        } else {
            shape(SHAPE_BOMB, cx, 0.52f, 0.14f, 1f, 1f, 1f, 1f, p1 = urgency)
        }
        when (mode) {
            Mode.SHOW -> label("Watch the code...", cx, 0.12f, 0.05f, 1f, 0.9f, 0.6f)
            Mode.INPUT -> {
                label(String.format(Locale.US, "%.1f", timer.coerceAtLeast(0f)), cx, 0.52f, 0.06f, 1f, 0.3f + 0.7f * (1f - urgency), 0.3f)
                label("Step ${entered + 1} of ${code.size}", cx, 0.12f, 0.045f)
            }
            Mode.DEFUSED -> label("DEFUSED!", cx, 0.12f, 0.07f, 0.4f, 1f, 0.5f)
            Mode.EXPLODING -> Unit
        }
    }

    override fun drawAttractDecor() {
        shape(SHAPE_BOMB, aspect * 0.15f, 0.45f, 0.1f, 1f, 1f, 1f, 1f, p1 = 0.3f)
        shape(SHAPE_BOMB, aspect * 0.85f, 0.45f, 0.1f, 1f, 1f, 1f, 1f, p1 = 0.6f)
    }
}
