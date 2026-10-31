package com.phantomfloor.games

import com.phantomfloor.audio.Sfx
import com.phantomfloor.scenes.SceneParam
import kotlin.math.ceil
import kotlin.math.floor

/**
 * THE FLOOR IS LAVA: tiles covering the whole floor keep melting into lava (they flash first).
 * Everyone has 3 lives; standing on lava costs one. The team scores a point for every second
 * someone is still in, and the round ends when everybody is out. It speeds up as it goes:
 * warnings get shorter and more of the floor melts.
 */
class FloorIsLavaGame : GameScene("game_lava", "The Floor is Lava") {

    private val speed = SceneParam("speed", "How fast it melts", 0.5f, 2f, 1f)
    private val livesParam = SceneParam("lives", "Lives each", 1f, 5f, 3f, step = 1f)
    override val params = listOf(speed, livesParam)
    override val howToPlay = "Tiles flash, then turn to lava.\nEveryone has 3 lives. Stay off the lava!"
    override val accent = floatArrayOf(1f, 0.5f, 0.15f)
    override val roundSeconds = 90f

    private val rows = 6
    private val maxCols = 16
    private val lava = FloatArray(maxCols * rows)    // shown molten amount
    private val target = BooleanArray(maxCols * rows) // will be / is lava
    private val warn = FloatArray(maxCols * rows)    // seconds of warning left
    private val seed = FloatArray(maxCols * rows) { it * 0.37f }
    private var changeTimer = 0f

    private class Player(var lives: Int, var onLava: Float = 0f, var safeFor: Float = 0f, var x: Float = 0f, var y: Float = 0f)
    private val players = HashMap<Int, Player>()
    private var safeClock = 0f

    // Tiles fill the floor edge to edge, so there is nowhere permanently safe to camp.
    private val cell = 1f / rows
    private val cols get() = ceil(aspect / cell).toInt().coerceAtMost(maxCols)
    private fun tx(c: Int) = aspect / 2f + (c - (cols - 1) / 2f) * cell
    private fun ty(r: Int) = (r + 0.5f) * cell
    private fun tileAt(x: Float, y: Float): Int {
        val c = floor((x - tx(0)) / cell + 0.5f).toInt()
        val r = floor(y / cell).toInt()
        return if (c in 0 until cols && r in 0 until rows) r * cols + c else -1
    }

    private val alive get() = players.values.count { it.lives > 0 }

    override fun scoreText() = "Survived ${score}s  ·  ${alive} in"

    override fun startRound() {
        lava.fill(0f); target.fill(false); warn.fill(0f)
        players.clear()
        changeTimer = 2f
        safeClock = 0f
    }

    override fun updatePlaying(dt: Float) {
        val n = cols * rows
        val progress = (phaseTime / 60f).coerceIn(0f, 1f)
        changeTimer -= dt * speed.value
        if (changeTimer <= 0f) {
            changeTimer = 3.5f - 1.5f * progress
            // The share of lava grows from ~15% to ~90% as the round goes on.
            val goal = 0.15f + 0.75f * progress
            val lavaNow = (0 until n).count { target[it] }
            repeat(((goal * n).toInt() - lavaNow).coerceIn(1, 8)) {
                val safe = (0 until n).filter { !target[it] && warn[it] <= 0f }
                if (safe.size > 3) warn[safe[rng.nextInt(safe.size)]] = 1.5f - 1f * progress
            }
            // A little lava cools back down so the safe path keeps moving (less often later).
            val hot = (0 until n).filter { target[it] }
            if (hot.isNotEmpty() && rng.nextFloat() < 0.6f - 0.5f * progress) target[hot[rng.nextInt(hot.size)]] = false
        }
        for (i in 0 until n) {
            if (warn[i] > 0f) {
                warn[i] -= dt
                if (warn[i] <= 0f) { target[i] = true; frame.sound.play(Sfx.SIZZLE, 0.3f, pan(tx(i % cols) / aspect)) }
            }
            lava[i] += ((if (target[i]) 1f else 0f) - lava[i]) * (dt * 3f).coerceAtMost(1f)
        }

        for (p in frame.people) {
            val pl = players.getOrPut(p.id) { Player(livesParam.int) }
            pl.x = p.x * aspect; pl.y = p.y
            if (pl.lives <= 0) continue
            pl.safeFor = (pl.safeFor - dt).coerceAtLeast(0f)
            val tile = tileAt(pl.x, pl.y)
            // Off the tiles (the very edge of the floor) counts as lava too.
            val hot = tile < 0 || lava[tile] > 0.6f
            pl.onLava = if (hot && pl.safeFor <= 0f) pl.onLava + dt else 0f
            if (pl.onLava > BURN_TIME) {
                pl.lives--
                pl.onLava = 0f
                pl.safeFor = 1.5f // a moment to get off the lava
                frame.sound.play(Sfx.SIZZLE, 1f, pan(p.x))
                frame.sound.play(Sfx.BUZZ, 0.6f, pan(p.x))
                floater(if (pl.lives > 0) "−1 ♥" else "OUT!", pl.x, pl.y + 0.08f, 1f, 0.45f, 0.2f)
            }
        }
        if (players.isNotEmpty() && alive == 0) { endRound("ALL BURNED!"); return }
        if (alive > 0) {
            safeClock += dt
            if (safeClock >= 1f) { safeClock -= 1f; score++ }
        }
    }

    override fun drawPlaying() {
        val half = cell * 0.48f
        for (r in 0 until rows) for (c in 0 until cols) {
            val i = r * cols + c
            shape(SHAPE_LAVA, tx(c), ty(r), half, 1f, 1f, 1f, 1f, p1 = lava[i], p2 = if (warn[i] > 0f) 1f else 0f, p3 = seed[i])
        }
        if (phase != Phase.PLAYING) return
        // Each player wears their remaining lives as small hearts-coloured dots; burning players glow.
        for (p in frame.people) {
            val pl = players[p.id] ?: continue
            val x = p.x * aspect
            if (pl.lives <= 0) { shape(SHAPE_X, x, p.y, 0.04f, 0.6f, 0.6f, 0.6f, 0.8f); continue }
            if (pl.onLava > 0.05f || pl.safeFor > 0f) shape(SHAPE_GLOW, x, p.y, 0.1f, 1f, 0.4f, 0.1f, 0.9f)
            for (k in 0 until pl.lives) shape(SHAPE_DISC, x + (k - (pl.lives - 1) / 2f) * 0.028f, p.y + 0.075f, 0.011f, 1f, 0.3f, 0.35f)
        }
    }

    private companion object {
        /** Seconds on lava before it costs a life: a quick hop across is fine, standing isn't. */
        const val BURN_TIME = 0.3f
    }
}
