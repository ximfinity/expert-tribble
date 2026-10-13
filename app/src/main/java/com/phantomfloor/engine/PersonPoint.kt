package com.phantomfloor.engine

/**
 * A tracked foot/body position on the floor, in canvas UV (0..1, origin bottom-left).
 * Velocity is in UV units per second.
 */
data class PersonPoint(
    val id: Int,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val timestampNanos: Long,
)
