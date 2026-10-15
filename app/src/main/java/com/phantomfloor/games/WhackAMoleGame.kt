package com.phantomfloor.games

import com.phantomfloor.audio.Sfx
import com.phantomfloor.scenes.SceneParam

/** WHACK-A-MOLE: moles pop out of nine holes; step on them before they duck back down. */
class WhackAMoleGame : GameScene("game_moles", "Whack-a-Mole") {

    private val speed = SceneParam("speed", "Mole speed", 0.5f, 2f, 1f)
    override val params = listOf(speed)
    override val howToPlay = "Moles pop out of the holes.\nStep on them before they hide!"
    override val roundSeconds = 45f
    override val accent = floatArrayOf(0.6f, 0.9f, 0.3f)

    private val up = FloatArray(9)       // seconds left above ground
    private val rise = FloatArray(9)     // animation 0..1
    private val stunned = FloatArray(9)
    private var popTimer = 0f

    private fun hx(i: Int) = aspect / 2f + (i % 3 - 1) * 0.33f
    private fun hy(i: Int) = 0.5f + (i / 3 - 1) * 0.24f

    override fun scoreText() = "Whacked: $score"

    override fun startRound() {
        up.fill(0f); rise.fill(0f); stunned.fill(0f)
        popTimer = 0.5f
    }

    override fun updatePlaying(dt: Float) {
        val pace = speed.value * (1f + phaseTime / 45f)
        popTimer -= dt
        if (popTimer <= 0f) {
            popTimer = (0.9f / pace).coerceAtLeast(0.3f)
            val free = (0 until 9).filter { up[it] <= 0f && stunned[it] <= 0f }
            if (free.isNotEmpty()) up[free[rng.nextInt(free.size)]] = (1.5f / pace).coerceAtLeast(0.6f)
        }
        for (i in 0 until 9) {
            if (up[i] > 0f) up[i] -= dt
            if (stunned[i] > 0f) stunned[i] -= dt
            val target = if (up[i] > 0f || stunned[i] > 0f) 1f else 0f
            rise[i] += (target - rise[i]) * (dt * 12f).coerceAtMost(1f)
            val hit = stepped(i, hx(i), hy(i), 0.11f)
            if (hit != null && up[i] > 0f && rise[i] > 0.5f && stunned[i] <= 0f) {
                score++
                up[i] = 0f
                stunned[i] = 0.6f
                frame.sound.play(Sfx.BONK, 1f, pan(hit.x))
            }
        }
    }

    override fun drawPlaying() {
        for (i in 0 until 9) shape(SHAPE_MOLE, hx(i), hy(i), 0.12f, 1f, 1f, 1f, 1f, p1 = rise[i], p2 = if (stunned[i] > 0f) 1f else 0f)
    }
}
