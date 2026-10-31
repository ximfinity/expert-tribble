package com.phantomfloor.scenes

import com.phantomfloor.engine.PersonPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Boids (Reynolds 1987) tuned to feel like spiders: separation, alignment and cohesion, plus
 * wander, stop-and-go pauses, soft walls, and a predator term that makes them flee from feet.
 * Fear is contagious: a startled spider spreads panic to its neighbours, so a single step
 * sends a scatter wave through the swarm.
 *
 * World units: x in [0, worldW], y in [0, 1]. Data is structure-of-arrays for cache-friendly
 * neighbour scans. Neighbour lookup uses a uniform grid (linked list per cell), so a step is
 * O(N * k) rather than O(N²). 2000 agents run well under 2 ms on a modern phone CPU.
 */
class BoidSwarm(val capacity: Int, seed: Int = 1337) {

    var count = 0
        private set

    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)
    val heading = FloatArray(capacity)
    val gait = FloatArray(capacity)
    val panic = FloatArray(capacity)
    val size = FloatArray(capacity)
    private val wander = FloatArray(capacity)
    private val pause = FloatArray(capacity)

    var worldW = 16f / 9f
        private set
    private val worldH = 1f
    private val rng = Random(seed)

    // Tunables (set from scene params every frame).
    var perception = 0.075f
    var separationDist = 0.032f
    var maxSpeed = 0.09f
    var fleeRadius = 0.2f
    var wAlign = 0.6f
    var wCohesion = 0.5f
    var wSeparation = 1.4f
    var wFlee = 3f
    var wWander = 0.35f
    var baseSize = 0.028f

    // Light to avoid (world units). Strength 0 disables it.
    var lightX = 0f
    var lightY = 0f
    var lightRadius = 0.2f
    var wLight = 0f

    // Spatial grid
    private var gridW = 1
    private var gridH = 1
    private var cell = perception
    private var head = IntArray(1)
    private val next = IntArray(capacity)

    fun setWorld(width: Float) {
        if (width <= 0f || width == worldW) return
        val k = width / worldW
        for (i in 0 until count) x[i] *= k
        worldW = width
    }

    fun setCount(n: Int) {
        val target = n.coerceIn(0, capacity)
        while (count < target) spawn(count++)
        count = target
    }

    /** New spiders scurry in from a random edge. */
    private fun spawn(i: Int) {
        when (rng.nextInt(4)) {
            0 -> { x[i] = 0.01f; y[i] = rng.nextFloat() }
            1 -> { x[i] = worldW - 0.01f; y[i] = rng.nextFloat() }
            2 -> { x[i] = rng.nextFloat() * worldW; y[i] = 0.01f }
            else -> { x[i] = rng.nextFloat() * worldW; y[i] = worldH - 0.01f }
        }
        val toCenter = atan2(0.5f - y[i], worldW * 0.5f - x[i]) + (rng.nextFloat() - 0.5f)
        heading[i] = toCenter
        wander[i] = toCenter
        vx[i] = cos(toCenter) * maxSpeed
        vy[i] = sin(toCenter) * maxSpeed
        gait[i] = rng.nextFloat() * 6.28f
        panic[i] = 0f
        pause[i] = 0f
        size[i] = 0.75f + rng.nextFloat() * 0.5f
    }

    /** Global scare (thunder, scream): everyone bolts. */
    fun startle(amount: Float) {
        for (i in 0 until count) panic[i] = max(panic[i], amount)
    }

    private fun rebuildGrid() {
        cell = max(perception, 0.02f)
        val gw = (worldW / cell).toInt() + 1
        val gh = (worldH / cell).toInt() + 1
        if (gw * gh != head.size) head = IntArray(gw * gh)
        gridW = gw
        gridH = gh
        head.fill(-1)
        for (i in 0 until count) {
            val c = cellOf(x[i], y[i])
            next[i] = head[c]
            head[c] = i
        }
    }

    private fun cellOf(px: Float, py: Float): Int {
        val cx = (px / cell).toInt().coerceIn(0, gridW - 1)
        val cy = (py / cell).toInt().coerceIn(0, gridH - 1)
        return cy * gridW + cx
    }

    /** [people] positions are canvas UV; converted to world here. */
    fun step(dt: Float, people: List<PersonPoint>) {
        if (count == 0) return
        rebuildGrid()
        val r2 = perception * perception
        val sep2 = separationDist * separationDist
        val margin = 0.06f
        val decay = exp(-dt * 0.8f)

        for (i in 0 until count) {
            val xi = x[i]
            val yi = y[i]
            var n = 0
            var alignX = 0f; var alignY = 0f
            var cohX = 0f; var cohY = 0f
            var sepX = 0f; var sepY = 0f
            var panicSum = 0f

            val cx = (xi / cell).toInt().coerceIn(0, gridW - 1)
            val cy = (yi / cell).toInt().coerceIn(0, gridH - 1)
            for (gy in max(cy - 1, 0)..min(cy + 1, gridH - 1)) {
                for (gx in max(cx - 1, 0)..min(cx + 1, gridW - 1)) {
                    var j = head[gy * gridW + gx]
                    while (j != -1 && n < MAX_NEIGHBOURS) {
                        if (j != i) {
                            val dx = x[j] - xi
                            val dy = y[j] - yi
                            val d2 = dx * dx + dy * dy
                            if (d2 < r2) {
                                n++
                                alignX += vx[j]; alignY += vy[j]
                                cohX += dx; cohY += dy
                                panicSum += panic[j]
                                if (d2 < sep2 && d2 > 1e-10f) {
                                    sepX -= dx / d2; sepY -= dy / d2
                                }
                            }
                        }
                        j = next[j]
                    }
                }
            }

            var fx = 0f
            var fy = 0f
            if (n > 0) {
                val inv = 1f / n
                fx += (alignX * inv - vx[i]) * wAlign * 2f
                fy += (alignY * inv - vy[i]) * wAlign * 2f
                fx += cohX * inv * wCohesion * 2f
                fy += cohY * inv * wCohesion * 2f
                fx += sepX * wSeparation * 0.004f
                fy += sepY * wSeparation * 0.004f
                val contagion = panicSum * inv * 0.85f
                if (contagion > panic[i]) panic[i] += (contagion - panic[i]) * min(1f, dt * 6f)
            }

            // Wander: a slowly drifting preferred direction.
            wander[i] += (rng.nextFloat() - 0.5f) * 6f * dt
            fx += cos(wander[i]) * wWander * 0.3f
            fy += sin(wander[i]) * wWander * 0.3f

            // Flee from feet. Fast-moving feet (stomps, kicks) are scarier.
            for (p in people) {
                val px = p.x * worldW
                val dx = xi - px
                val dy = yi - p.y
                val d = sqrt(dx * dx + dy * dy)
                if (d < fleeRadius) {
                    val k = 1f - d / fleeRadius
                    val threat = 1f + min(hypot(p.vx * worldW, p.vy) * 4f, 2f)
                    val s = k * k * wFlee * threat * 1.5f / max(d, 0.005f)
                    fx += dx * s
                    fy += dy * s
                    panic[i] = max(panic[i], min(1f, k * threat * 0.8f))
                    pause[i] = 0f
                }
            }

            // Spiders scurry out of the flashlight beam (milder than fleeing a foot).
            if (wLight > 0f) {
                val dx = xi - lightX
                val dy = yi - lightY
                val d = sqrt(dx * dx + dy * dy)
                if (d < lightRadius) {
                    val k = 1f - d / lightRadius
                    val s = k * wLight * 1.2f / max(d, 0.01f)
                    fx += dx * s
                    fy += dy * s
                    panic[i] = max(panic[i], k * 0.35f * wLight)
                    pause[i] = 0f
                }
            }

            // Soft walls
            if (xi < margin) fx += (margin - xi) * 25f
            if (xi > worldW - margin) fx -= (xi - (worldW - margin)) * 25f
            if (yi < margin) fy += (margin - yi) * 25f
            if (yi > worldH - margin) fy -= (yi - (worldH - margin)) * 25f

            // Stop-and-go, the way real spiders move (calm ones only).
            if (panic[i] > 0.15f) {
                pause[i] = 0f
            } else if (pause[i] > 0f) {
                pause[i] -= dt
            } else if (rng.nextFloat() < dt * 0.35f) {
                pause[i] = 0.3f + rng.nextFloat() * 1.4f
            }

            var nvx = vx[i] + fx * dt
            var nvy = vy[i] + fy * dt
            val limit = maxSpeed * (1f + panic[i] * 2.8f)
            var sp = sqrt(nvx * nvx + nvy * nvy)
            if (pause[i] > 0f) {
                val damp = 1f - min(1f, dt * 8f)
                nvx *= damp; nvy *= damp; sp *= damp
            } else if (sp > limit) {
                nvx *= limit / sp; nvy *= limit / sp; sp = limit
            }
            vx[i] = nvx
            vy[i] = nvy
            x[i] = (xi + nvx * dt).coerceIn(0.002f, worldW - 0.002f)
            y[i] = (yi + nvy * dt).coerceIn(0.002f, worldH - 0.002f)

            // Body turns toward travel direction at a limited rate.
            if (sp > 0.003f) {
                var diff = atan2(nvy, nvx) - heading[i]
                while (diff > PI) diff -= TWO_PI
                while (diff < -PI) diff += TWO_PI
                val turn = 9f * dt
                heading[i] += diff.coerceIn(-turn, turn)
            }
            gait[i] += sp / (baseSize * size[i]) * dt * 4f
            if (gait[i] > 1000f) gait[i] %= TWO_PI  // keep float precision for the leg sine
            panic[i] *= decay
        }
    }

    companion object {
        private const val MAX_NEIGHBOURS = 24
        private const val PI = kotlin.math.PI.toFloat()
        private const val TWO_PI = (kotlin.math.PI * 2).toFloat()
    }
}
