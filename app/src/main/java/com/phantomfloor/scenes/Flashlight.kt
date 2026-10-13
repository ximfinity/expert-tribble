package com.phantomfloor.scenes

import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * A hand-held flashlight sweeping the floor. Every few seconds it picks a new spot to search,
 * eases there on a critically damped spring (no snapping, natural slow-in/slow-out), lingers,
 * then moves on. A sum of small, unrelated sines adds hand tremor on top.
 * Positions are world units: x in [0, aspect], y in [0, 1].
 */
class Flashlight(seed: Int = 31) {

    private val rng = Random(seed)
    private var aimX = 0.8f
    private var aimY = 0.5f
    private var vx = 0f
    private var vy = 0f
    private var targetX = 0.8f
    private var targetY = 0.5f
    private var holdTimer = 0f
    private var t = 0f

    var x = 0.8f
        private set
    var y = 0.5f
        private set

    fun update(dt: Float, aspect: Float, speed: Float) {
        t += dt
        holdTimer -= dt * speed
        if (holdTimer <= 0f) {
            // Sometimes a short nervous flick, usually a slower sweep somewhere new.
            val flick = rng.nextFloat() < 0.3f
            val reach = if (flick) 0.18f else 0.6f
            targetX = (aimX + (rng.nextFloat() - 0.5f) * 2f * reach * aspect).coerceIn(0.12f, aspect - 0.12f)
            targetY = (aimY + (rng.nextFloat() - 0.5f) * 2f * reach).coerceIn(0.12f, 0.88f)
            holdTimer = if (flick) 0.6f + rng.nextFloat() * 0.8f else 2f + rng.nextFloat() * 3f
        }

        // Critically damped spring toward the target.
        val omega = 2.2f * speed
        val ax = omega * omega * (targetX - aimX) - 2f * omega * vx
        val ay = omega * omega * (targetY - aimY) - 2f * omega * vy
        vx += ax * dt
        vy += ay * dt
        val damp = exp(-dt * 0.5f)
        vx *= damp
        vy *= damp
        aimX += vx * dt
        aimY += vy * dt

        val tremorX = 0.006f * sin(t * 7.3f) + 0.004f * sin(t * 11.9f + 1.7f) + 0.01f * sin(t * 0.9f + 0.4f)
        val tremorY = 0.006f * sin(t * 8.1f + 2.3f) + 0.004f * sin(t * 13.3f) + 0.01f * sin(t * 1.1f + 2.0f)
        x = aimX + tremorX
        y = aimY + tremorY
    }
}
