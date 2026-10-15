package com.phantomfloor.games

import com.phantomfloor.audio.Sfx
import com.phantomfloor.scenes.SceneParam

/**
 * THE FLOOR IS LAVA: tiles keep melting into lava (they flash first). Keep everyone on the safe
 * tiles: every second with nobody burning scores a point.
 */
class FloorIsLavaGame : GameScene("game_lava", "The Floor is Lava") {

    private val speed = SceneParam("speed", "How fast it melts", 0.5f, 2f, 1f)
    override val params = listOf(speed)
    override val howToPlay = "Tiles flash, then turn to lava.\nKeep off the lava as long as you can!"
    override val accent = floatArrayOf(1f, 0.5f, 0.15f)

    private val cols = 9
    private val rows = 5
    private val lava = FloatArray(cols * rows)    // shown molten amount
    private val target = BooleanArray(cols * rows) // will be / is lava
    private val warn = FloatArray(cols * rows)    // seconds of warning left
    private val seed = FloatArray(cols * rows) { it * 0.37f }
    private var changeTimer = 0f
    private var burns = 0
    private val onLava = HashMap<Int, Float>()
    private var safeClock = 0f

    private val cell get() = minOf((aspect - 0.1f) / cols, 0.82f / rows)
    private fun tx(c: Int) = aspect / 2f + (c - (cols - 1) / 2f) * cell
    private fun ty(r: Int) = 0.47f + (r - (rows - 1) / 2f) * cell

    override fun scoreText() = "Safe $score  ·  Burns $burns"

    override fun startRound() {
        lava.fill(0f); target.fill(false); warn.fill(0f)
        burns = 0
        onLava.clear()
        changeTimer = 2f
        safeClock = 0f
    }

    override fun updatePlaying(dt: Float) {
        val n = cols * rows
        changeTimer -= dt * speed.value
        if (changeTimer <= 0f) {
            changeTimer = 4f
            // The share of lava grows from ~15% to ~70% across the round.
            val goal = (0.15f + 0.55f * (phaseTime / 60f)).coerceAtMost(0.7f)
            val lavaNow = target.count { it }
            repeat(((goal * n).toInt() - lavaNow).coerceIn(1, 6)) {
                val safe = (0 until n).filter { !target[it] && warn[it] <= 0f }
                if (safe.size > 4) warn[safe[rng.nextInt(safe.size)]] = 1.5f
            }
            // A little lava cools back down so the safe path keeps moving.
            val hot = (0 until n).filter { target[it] }
            if (hot.isNotEmpty() && rng.nextFloat() < 0.6f) target[hot[rng.nextInt(hot.size)]] = false
        }
        for (i in 0 until n) {
            if (warn[i] > 0f) {
                warn[i] -= dt
                if (warn[i] <= 0f) { target[i] = true; frame.sound.play(Sfx.SIZZLE, 0.3f, pan(tx(i % cols) / aspect)) }
            }
            lava[i] += ((if (target[i]) 1f else 0f) - lava[i]) * (dt * 3f).coerceAtMost(1f)
        }
        // Burning: standing on molten lava for more than a moment.
        var anyoneBurning = false
        for (p in frame.people) {
            val c = ((p.x * aspect - tx(0)) / cell + 0.5f).toInt()
            val r = ((p.y - ty(0)) / cell + 0.5f).toInt()
            val hot = c in 0 until cols && r in 0 until rows && lava[r * cols + c] > 0.6f
            if (hot) {
                val t = (onLava[p.id] ?: 0f) + dt
                onLava[p.id] = t
                anyoneBurning = true
                if (t > 0.4f && t - dt <= 0.4f) {
                    burns++
                    frame.sound.play(Sfx.SIZZLE, 1f, pan(p.x))
                    frame.sound.play(Sfx.BUZZ, 0.5f, pan(p.x))
                }
            } else {
                onLava.remove(p.id)
            }
        }
        if (frame.people.isNotEmpty() && !anyoneBurning) {
            safeClock += dt
            if (safeClock >= 1f) { safeClock -= 1f; score++ }
        }
    }

    override fun drawPlaying() {
        val half = cell * 0.47f
        for (r in 0 until rows) for (c in 0 until cols) {
            val i = r * cols + c
            shape(SHAPE_LAVA, tx(c), ty(r), half, 1f, 1f, 1f, 1f, p1 = lava[i], p2 = if (warn[i] > 0f) 1f else 0f, p3 = seed[i])
        }
        for (p in frame.people) if ((onLava[p.id] ?: 0f) > 0.2f) shape(SHAPE_GLOW, p.x * aspect, p.y, 0.1f, 1f, 0.4f, 0.1f, 0.9f)
    }
}
