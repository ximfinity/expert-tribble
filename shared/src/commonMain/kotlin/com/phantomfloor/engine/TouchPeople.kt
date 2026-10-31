package com.phantomfloor.engine

import com.phantomfloor.util.locked

/**
 * Fingers on the screen act as people on the floor (mapped through the inverse keystone).
 * Handy for testing scenes without the camera, or as a "puppeteer" control during a party.
 */
class TouchPeople {

    private val points = HashMap<Int, PersonPoint>()

    fun update(pointerId: Int, x: Float, y: Float, nowNanos: Long) {
        val id = -1 - pointerId // negative ids never collide with camera tracks
        val prev = locked(points) { points[id] }
        var vx = 0f
        var vy = 0f
        if (prev != null) {
            val dt = ((nowNanos - prev.timestampNanos) / 1e9f).coerceIn(1e-3f, 0.2f)
            vx = prev.vx + ((x - prev.x) / dt - prev.vx) * 0.4f
            vy = prev.vy + ((y - prev.y) / dt - prev.vy) * 0.4f
        }
        locked(points) { points[id] = PersonPoint(id, x, y, vx, vy, nowNanos) }
    }

    fun remove(pointerId: Int) {
        locked(points) { points.remove(-1 - pointerId) }
    }

    fun clear() = locked(points) { points.clear() }

    /** A finger that stopped moving produces no events, so its velocity is zeroed here. */
    fun snapshot(nowNanos: Long): List<PersonPoint> = locked(points) { points.values.toList() }.map {
        if (nowNanos - it.timestampNanos > 100_000_000L) it.copy(vx = 0f, vy = 0f) else it
    }
}
