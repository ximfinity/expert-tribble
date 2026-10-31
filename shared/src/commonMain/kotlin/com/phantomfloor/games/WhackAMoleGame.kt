package com.phantomfloor.games

import com.phantomfloor.audio.Sfx
import com.phantomfloor.scenes.SceneParam
import kotlin.math.cos
import kotlin.math.sin

/** WHACK-A-MOLE: moles pop out of nine holes; step on them before they duck back down. */
class WhackAMoleGame : GameScene("game_moles", "Whack-a-Mole") {

    private val speed = SceneParam("speed", "Mole speed", 0.5f, 2f, 1f)
    override val params = listOf(speed)
    override val howToPlay = "Moles pop out of the holes.\nStep on them before they hide!"
    override val roundSeconds = 45f
    override val accent = floatArrayOf(0.6f, 0.9f, 0.3f)

    private val up = FloatArray(9)       // seconds left above ground
    private val rise = FloatArray(9)     // animation 0..1
    private val riseVel = FloatArray(9)
    private val dust = FloatArray(9)
    private val stunned = FloatArray(9)
    private var popTimer = 0f

    private fun hx(i: Int) = aspect / 2f + (i % 3 - 1) * 0.33f
    private fun hy(i: Int) = 0.5f + (i / 3 - 1) * 0.24f

    override fun scoreText() = "Whacked: $score"

    override fun startRound() {
        up.fill(0f); rise.fill(0f); stunned.fill(0f); riseVel.fill(0f); dust.fill(0f)
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
            // A springy pop-up: moles overshoot a little and settle, rather than sliding.
            riseVel[i] += ((target - rise[i]) * 260f - riseVel[i] * 18f) * dt
            rise[i] = (rise[i] + riseVel[i] * dt).coerceIn(0f, 1.15f)
            dust[i] = (dust[i] - dt * 3f).coerceAtLeast(0f)
            val hit = stepped(i, hx(i), hy(i), 0.11f)
            if (hit != null && up[i] > 0f && rise[i] > 0.5f && stunned[i] <= 0f) {
                score++
                up[i] = 0f
                stunned[i] = 0.6f
                dust[i] = 1f
                floater("+1", hx(i), hy(i) + 0.1f, 1f, 0.9f, 0.4f)
                frame.sound.play(Sfx.BONK, 1f, pan(hit.x))
            }
        }
    }

    override fun drawPlaying() {
        for (i in 0 until 9) {
            // A whacked mole squashes flat for a moment.
            val squash = if (stunned[i] > 0.45f) 0.85f else 1f
            shape(SHAPE_MOLE, hx(i), hy(i), 0.12f * squash, 1f, 1f, 1f, 1f, p1 = rise[i].coerceAtMost(1f), p2 = if (stunned[i] > 0f) 1f else 0f)
            if (dust[i] > 0f) {
                val k = 1f - dust[i]
                for (j in 0 until 6) {
                    val a = j * 1.047f + i
                    shape(SHAPE_DISC, hx(i) + cos(a) * (0.06f + 0.08f * k), hy(i) - 0.02f + sin(a) * (0.03f + 0.04f * k), 0.012f * (1f - k * 0.5f), 0.75f, 0.65f, 0.5f, dust[i] * 0.8f)
                }
            }
        }
    }
}
