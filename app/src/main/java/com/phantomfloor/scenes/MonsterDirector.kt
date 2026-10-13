package com.phantomfloor.scenes

import com.phantomfloor.engine.PersonPoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Decides when and where the water comes alive. Monsters surface near people (but not on top
 * of them), turn to follow them, and sink back with a splash when someone steps on them.
 * Pure CPU logic; the scene uploads [monsterA]/[monsterB]/[eyes] as uniform arrays.
 */
class MonsterDirector(seed: Int = 99) {

    enum class Type { TENTACLES, HAND, FACE }

    private class Monster {
        var active = false
        var type = Type.TENTACLES
        var x = 0f
        var y = 0f
        var angle = 0f
        var age = 0f
        var emergeDur = 2.5f
        var holdDur = 6f
        var sinkDur = 2f
        var seed = 0f
        var size = 0.25f
        var bubbleTimer = 0f

        val total get() = emergeDur + holdDur + sinkDur

        fun emerge(): Float = when {
            age < emergeDur -> smooth(age / emergeDur)
            age < emergeDur + holdDur -> 1f
            else -> 1f - smooth((age - emergeDur - holdDur) / sinkDur)
        }

        private fun smooth(t: Float): Float {
            val c = t.coerceIn(0f, 1f)
            return c * c * (3f - 2f * c)
        }
    }

    private class Eye {
        var active = false
        var x = 0f
        var y = 0f
        var age = 0f
        var life = 4f
        var size = 0.02f
    }

    private val rng = Random(seed)
    private val monsters = Array(MAX_MONSTERS) { Monster() }
    private val eyeList = Array(MAX_EYES) { Eye() }
    private var spawnTimer = 2f
    private var eyeTimer = 1f

    val monsterA = FloatArray(MAX_MONSTERS * 4)
    val monsterB = FloatArray(MAX_MONSTERS * 4)
    val eyes = FloatArray(MAX_EYES * 4)
    var monsterCount = 0
        private set
    var eyeCount = 0
        private set

    /** Ripple impulse callback: canvas uv x, y, radius (world), strength. */
    var onSplash: ((Float, Float, Float, Float) -> Unit)? = null

    fun update(
        dt: Float,
        aspect: Float,
        people: List<PersonPoint>,
        activity: Float,
        monsterSize: Float,
        beat: Boolean,
        level: Float,
    ) {
        spawnTimer -= dt * activity
        if (beat && level > 0.55f && spawnTimer > 0.8f) spawnTimer = 0f
        if (spawnTimer <= 0f) {
            spawn(aspect, people, monsterSize)
            spawnTimer = 3f + rng.nextFloat() * 5f
        }

        for (m in monsters) {
            if (!m.active) continue
            m.age += dt
            if (m.age >= m.total) {
                m.active = false
                continue
            }
            val nearest = nearestPerson(m.x, m.y, aspect, people)
            if (nearest != null) {
                val px = nearest.x * aspect
                val dx = px - m.x
                val dy = nearest.y - m.y
                // Tentacles and hands reach toward the person; a face shows its top away from them
                // so it reads upright from where they stand.
                val target = if (m.type == Type.FACE) atan2(-dy, -dx) else atan2(dy, dx)
                m.angle += wrap(target - m.angle) * (dt * 1.5f).coerceAtMost(1f)
                // Stepping onto a monster makes it recoil and sink with a big splash.
                val holding = m.age > m.emergeDur && m.age < m.emergeDur + m.holdDur
                if (holding && hypot(dx, dy) < m.size * 0.45f) {
                    m.age = m.emergeDur + m.holdDur
                    m.sinkDur = 0.9f
                    onSplash?.invoke(m.x / aspect, m.y, m.size * 0.5f, 0.9f)
                }
            }
            // Water churns while it rises or falls.
            val e = m.emerge()
            if (e in 0.02f..0.98f) {
                m.bubbleTimer -= dt
                if (m.bubbleTimer <= 0f) {
                    m.bubbleTimer = 0.15f
                    val a = rng.nextFloat() * 2f * PI.toFloat()
                    val r = m.size * 0.4f * rng.nextFloat()
                    onSplash?.invoke((m.x + cos(a) * r) / aspect, m.y + sin(a) * r, 0.015f, 0.25f)
                }
            }
        }

        eyeTimer -= dt * activity
        if (eyeTimer <= 0f) {
            eyeList.firstOrNull { !it.active }?.let { e ->
                e.active = true
                e.age = 0f
                e.life = 3f + rng.nextFloat() * 4f
                e.size = 0.012f + rng.nextFloat() * 0.018f
                e.x = 0.1f + rng.nextFloat() * (aspect - 0.2f)
                e.y = 0.1f + rng.nextFloat() * 0.8f
            }
            eyeTimer = 1.5f + rng.nextFloat() * 3f
        }
        for (e in eyeList) if (e.active) {
            e.age += dt
            if (e.age > e.life) e.active = false
            // Eyes shut and vanish when someone gets close.
            if (people.any { hypot(it.x * aspect - e.x, it.y - e.y) < 0.12f }) e.age = maxOf(e.age, e.life - 0.3f)
        }

        pack(aspect)
    }

    private fun spawn(aspect: Float, people: List<PersonPoint>, sizeScale: Float) {
        val m = monsters.firstOrNull { !it.active } ?: return
        m.active = true
        m.age = 0f
        m.type = Type.entries[rng.nextInt(Type.entries.size)]
        m.seed = rng.nextFloat() * 100f
        m.size = (0.2f + rng.nextFloat() * 0.1f) * sizeScale
        m.emergeDur = 2f + rng.nextFloat()
        m.holdDur = 5f + rng.nextFloat() * 5f
        m.sinkDur = 2f
        m.bubbleTimer = 0f
        if (people.isNotEmpty()) {
            val p = people[rng.nextInt(people.size)]
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val d = 0.2f + rng.nextFloat() * 0.15f
            m.x = p.x * aspect + cos(a) * d
            m.y = p.y + sin(a) * d
        } else {
            m.x = rng.nextFloat() * aspect
            m.y = rng.nextFloat()
        }
        m.x = m.x.coerceIn(0.15f, aspect - 0.15f)
        m.y = m.y.coerceIn(0.15f, 0.85f)
        m.angle = rng.nextFloat() * 2f * PI.toFloat()
        onSplash?.invoke(m.x / aspect, m.y, m.size * 0.4f, 0.6f)
    }

    private fun pack(aspect: Float) {
        monsterCount = 0
        for (m in monsters) if (m.active) {
            val o = monsterCount * 4
            monsterA[o] = m.x; monsterA[o + 1] = m.y; monsterA[o + 2] = m.angle; monsterA[o + 3] = m.emerge()
            monsterB[o] = m.type.ordinal.toFloat(); monsterB[o + 1] = m.seed; monsterB[o + 2] = m.age; monsterB[o + 3] = m.size
            monsterCount++
        }
        eyeCount = 0
        for (e in eyeList) if (e.active) {
            val o = eyeCount * 4
            val blink = if ((e.age * 1.7f) % 3f < 0.15f) 0.1f else 1f
            val open = minOf(e.age / 0.8f, (e.life - e.age) / 0.5f, 1f).coerceAtLeast(0f) * blink
            eyes[o] = e.x; eyes[o + 1] = e.y; eyes[o + 2] = open; eyes[o + 3] = e.size
            eyeCount++
        }
    }

    private fun nearestPerson(x: Float, y: Float, aspect: Float, people: List<PersonPoint>): PersonPoint? =
        people.minByOrNull { hypot(it.x * aspect - x, it.y - y) }

    private fun wrap(a: Float): Float {
        var v = a
        while (v > PI) v -= (2 * PI).toFloat()
        while (v < -PI) v += (2 * PI).toFloat()
        return v
    }

    companion object {
        const val MAX_MONSTERS = 4
        const val MAX_EYES = 6
    }
}
