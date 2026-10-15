package com.phantomfloor.games

import com.phantomfloor.audio.Sfx
import com.phantomfloor.scenes.SceneParam
import kotlin.math.hypot
import kotlin.math.sin

/** BALLOON POP: balloons drift across the floor; pop them by stepping on them. Gold ones are worth 5. */
class BalloonPopGame : GameScene("game_balloons", "Balloon Pop") {

    private val amount = SceneParam("amount", "Balloons", 0.5f, 2f, 1f)
    override val params = listOf(amount)
    override val howToPlay = "Pop the balloons by stepping on them!\nGold balloons are worth 5 points."
    override val accent = floatArrayOf(1f, 0.5f, 0.7f)

    private class Balloon(var x: Float, var y: Float, val vx: Float, val vy: Float, val size: Float, val color: FloatArray, val gold: Boolean, val phase: Float)

    private val balloons = ArrayList<Balloon>()
    private var spawnTimer = 0f

    override fun scoreText() = "Popped: $score"

    override fun startRound() {
        balloons.clear()
        spawnTimer = 0f
    }

    override fun updatePlaying(dt: Float) {
        spawnTimer -= dt * amount.value * (1f + phaseTime / 40f)
        if (spawnTimer <= 0f && balloons.size < 30) {
            spawnTimer = 0.7f
            val gold = rng.nextFloat() < 0.08f
            val fromLeft = rng.nextBoolean()
            balloons += Balloon(
                x = if (fromLeft) -0.05f else aspect + 0.05f, y = 0.15f + rng.nextFloat() * 0.75f,
                vx = (0.05f + rng.nextFloat() * 0.07f) * if (fromLeft) 1f else -1f, vy = (rng.nextFloat() - 0.5f) * 0.03f,
                size = 0.045f + rng.nextFloat() * 0.02f,
                color = if (gold) floatArrayOf(1f, 0.82f, 0.25f) else COLORS[rng.nextInt(COLORS.size)],
                gold = gold, phase = rng.nextFloat() * 6f,
            )
        }
        val it = balloons.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.x += (b.vx + sin(phaseTime * 1.3f + b.phase) * 0.01f) * dt
            b.y += (b.vy + sin(phaseTime * 0.9f + b.phase * 2f) * 0.015f) * dt
            if (b.x < -0.1f || b.x > aspect + 0.1f) { it.remove(); continue }
            val popper = frame.people.firstOrNull { hypot(it.x * aspect - b.x, it.y - b.y) < b.size * 0.9f }
            if (popper != null) {
                score += if (b.gold) 5 else 1
                frame.sound.play(Sfx.POP, 1f, pan(b.x / aspect))
                if (b.gold) frame.sound.play(Sfx.CHIME, 0.6f, pan(b.x / aspect))
                confetti(b.x, b.y, if (b.gold) 40 else 14)
                it.remove()
            }
        }
    }

    override fun drawPlaying() {
        for (b in balloons) {
            shape(SHAPE_BALLOON, b.x, b.y, b.size, b.color[0], b.color[1], b.color[2], 1f, angle = sin(phaseTime + b.phase) * 0.2f)
            if (b.gold) shape(SHAPE_GLOW, b.x, b.y, b.size * 2.2f, 1f, 0.85f, 0.3f, 0.5f)
        }
    }

    override fun drawAttractDecor() {
        for (i in 0 until 5) {
            val c = COLORS[i % COLORS.size]
            shape(SHAPE_BALLOON, aspect * (0.12f + i * 0.19f), 0.48f + 0.04f * sin(frame.time * 1.5f + i), 0.05f, c[0], c[1], c[2], 0.9f)
        }
    }

    companion object {
        private val COLORS = arrayOf(
            floatArrayOf(1f, 0.25f, 0.3f), floatArrayOf(0.3f, 0.6f, 1f), floatArrayOf(0.35f, 0.9f, 0.4f),
            floatArrayOf(0.8f, 0.4f, 1f), floatArrayOf(1f, 0.55f, 0.15f), floatArrayOf(1f, 0.45f, 0.8f),
        )
    }
}
