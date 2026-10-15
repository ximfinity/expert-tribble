package com.phantomfloor.games

import com.phantomfloor.audio.Sfx
import com.phantomfloor.scenes.SceneParam

/** SIMON SAYS: watch the pads light up, then step on them in the same order. One more each round. */
class SimonGame : GameScene("game_simon", "Simon Says") {

    private val speed = SceneParam("speed", "Speed", 0.5f, 2f, 1f)
    override val params = listOf(speed)
    override val howToPlay = "Watch the pads light up,\nthen step on them in the same order."
    override val roundSeconds: Float? = null
    override val accent = floatArrayOf(1f, 0.85f, 0.3f)

    private enum class Mode { SHOW, INPUT, NEXT }

    private val colors = arrayOf(
        floatArrayOf(0.25f, 1f, 0.35f), floatArrayOf(1f, 0.25f, 0.25f),
        floatArrayOf(1f, 0.85f, 0.2f), floatArrayOf(0.3f, 0.5f, 1f),
    )
    private val tones = arrayOf(Sfx.TONE_1, Sfx.TONE_2, Sfx.TONE_3, Sfx.TONE_4)
    private val seq = ArrayList<Int>()
    private var mode = Mode.SHOW
    private var modeTime = 0f
    private var entered = 0
    private val lit = FloatArray(4)

    private fun px(i: Int) = aspect / 2f + if (i % 2 == 0) -0.2f else 0.2f
    private fun py(i: Int) = if (i < 2) 0.7f else 0.36f

    override fun scoreText() = "Length $score"

    override fun startRound() {
        seq.clear()
        seq += rng.nextInt(4)
        begin(Mode.SHOW)
    }

    private fun begin(m: Mode) { mode = m; modeTime = 0f; entered = 0 }

    override fun updatePlaying(dt: Float) {
        modeTime += dt
        // Track pad presence every frame, so standing on a pad while the sequence plays
        // doesn't count as a step the moment input begins.
        val hits = Array(4) { stepped(it, px(it), py(it), 0.15f) }
        for (i in 0 until 4) lit[i] = (lit[i] - dt * 2.5f).coerceAtLeast(0f)
        val step = 0.75f / speed.value / (1f + seq.size * 0.04f)
        when (mode) {
            Mode.SHOW -> {
                val t = modeTime - 0.7f
                if (t >= 0f) {
                    val idx = (t / step).toInt()
                    if (idx in seq.indices && t - idx * step < frame.dt + 1e-4f) {
                        lit[seq[idx]] = 1f
                        frame.sound.play(tones[seq[idx]], 0.8f)
                    }
                    if (idx >= seq.size) begin(Mode.INPUT)
                }
            }
            Mode.INPUT -> {
                if (modeTime > 7f) { endRound("TOO SLOW!"); return }
                for (i in 0 until 4) {
                    val p = hits[i] ?: continue
                    lit[i] = 1f
                    modeTime = 0f
                    if (i != seq[entered]) {
                        frame.sound.play(Sfx.BUZZ, 1f, pan(p.x))
                        endRound("WRONG!")
                        return
                    }
                    frame.sound.play(tones[i], 0.8f, pan(p.x))
                    entered++
                    if (entered == seq.size) {
                        score = seq.size
                        begin(Mode.NEXT)
                        return
                    }
                }
            }
            Mode.NEXT -> if (modeTime > 1f) { seq += rng.nextInt(4); begin(Mode.SHOW) }
        }
    }

    override fun drawPlaying() {
        for (i in 0 until 4) {
            val c = colors[i]
            val k = 0.3f + 0.7f * lit[i]
            shape(SHAPE_PAD, px(i), py(i), 0.15f, c[0] * k, c[1] * k, c[2] * k, 1f, p1 = 0.35f, p2 = lit[i])
            if (lit[i] > 0f) shape(SHAPE_GLOW, px(i), py(i), 0.32f, c[0], c[1], c[2], lit[i] * 0.8f)
        }
        val msg = when (mode) {
            Mode.SHOW -> "Watch..."
            Mode.INPUT -> "Your turn: ${entered + 1} of ${seq.size}"
            Mode.NEXT -> "Nice!"
        }
        label(msg, aspect / 2f, 0.12f, 0.05f, 1f, 0.95f, 0.7f)
    }
}
