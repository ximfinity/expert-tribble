package com.phantomfloor.games

import com.phantomfloor.audio.Sfx
import com.phantomfloor.scenes.SceneParam
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** STOMP THE SPIDER: spiders scuttle in from the edges; stomp as many as you can. */
class StompSpiderGame : GameScene("game_stomp", "Stomp the Spider") {

    private val speed = SceneParam("speed", "Spider speed", 0.5f, 2f, 1f)
    override val params = listOf(speed)
    override val howToPlay = "Spiders are invading!\nStomp as many as you can in 60 seconds."
    override val accent = floatArrayOf(0.6f, 1f, 0.4f)

    private class Spider(var x: Float, var y: Float, var heading: Float, var gait: Float)
    private class Splat(val x: Float, val y: Float, val seed: Float, var age: Float)

    private val spiders = ArrayList<Spider>()
    private val splats = ArrayList<Splat>()
    private var spawnTimer = 0f

    override fun scoreText() = "Squashed: $score"

    override fun startRound() {
        spiders.clear()
        splats.clear()
        spawnTimer = 0f
    }

    override fun updatePlaying(dt: Float) {
        val maxAlive = 5 + (phaseTime / 7f).toInt()
        spawnTimer -= dt
        if (spawnTimer <= 0f && spiders.size < maxAlive) {
            spawnTimer = 0.5f
            val edge = rng.nextInt(4)
            val x = when (edge) { 0 -> 0.02f; 1 -> aspect - 0.02f; else -> rng.nextFloat() * aspect }
            val y = when (edge) { 2 -> 0.02f; 3 -> 0.98f; else -> rng.nextFloat() }
            spiders += Spider(x, y, atan2(0.5f - y, aspect / 2f - x), rng.nextFloat() * 6f)
            frame.sound.play(Sfx.SKITTER, 0.4f, pan(x / aspect))
        }
        val base = 0.1f * speed.value * (1f + phaseTime / 60f)
        val it = spiders.iterator()
        while (it.hasNext()) {
            val s = it.next()
            var v = base
            s.heading += (rng.nextFloat() - 0.5f) * 4f * dt
            for (p in frame.people) {
                val dx = s.x - p.x * aspect
                val dy = s.y - p.y
                val d = hypot(dx, dy)
                if (d < 0.05f) { // stomped
                    score++
                    splats += Splat(s.x, s.y, rng.nextFloat(), 0f)
                    floater("+1", s.x, s.y + 0.06f, 0.6f, 1f, 0.4f)
                    frame.sound.play(Sfx.SQUISH, 0.9f, pan(s.x / aspect))
                    it.remove()
                    v = -1f
                    break
                }
                if (d < 0.22f) { // dodge away, but not faster than a stomp
                    s.heading = atan2(dy, dx) + (rng.nextFloat() - 0.5f) * 0.8f
                    v = base * 1.8f
                }
            }
            if (v < 0f) continue
            s.x += cos(s.heading) * v * dt
            s.y += sin(s.heading) * v * dt
            if (s.x < 0.03f || s.x > aspect - 0.03f) s.heading = kotlin.math.PI.toFloat() - s.heading
            if (s.y < 0.05f || s.y > 0.9f) s.heading = -s.heading
            s.x = s.x.coerceIn(0.03f, aspect - 0.03f)
            s.y = s.y.coerceIn(0.05f, 0.9f)
            s.gait += v / 0.035f * dt * 4f
        }
        splats.forEach { it.age += dt }
        splats.removeAll { it.age > 8f }
    }

    override fun drawPlaying() {
        // Splats squash in with a little overshoot.
        for (s in splats) shape(SHAPE_SPLAT, s.x, s.y, 0.05f * (0.6f + 0.4f * minOf(1f, s.age / 0.12f) + 0.3f * sin(minOf(1f, s.age / 0.2f) * 3.1416f)), 0.35f, 0.9f, 0.2f, (1f - s.age / 8f).coerceIn(0f, 1f) * 0.85f, p1 = s.seed)
        for (s in spiders) shape(SHAPE_SPIDER, s.x, s.y, 0.04f, 0.78f, 0.72f, 0.9f, 1f, p1 = s.gait, angle = s.heading)
    }

    override fun drawAttractDecor() {
        shape(SHAPE_SPIDER, aspect * 0.2f, 0.5f, 0.06f, 0.78f, 0.72f, 0.9f, 1f, p1 = frame.time * 6f, angle = 0.4f)
        shape(SHAPE_SPIDER, aspect * 0.8f, 0.45f, 0.06f, 0.78f, 0.72f, 0.9f, 1f, p1 = frame.time * 6f + 1f, angle = 2.8f)
    }
}
