package com.phantomfloor.scenes

import com.phantomfloor.engine.PersonPoint
import kotlin.math.hypot

/**
 * Turns tracked positions into individual footprints: one print per stride, alternating left
 * and right, offset to either side of the walking line and pointing the way the person walks.
 * Someone who arrives and stands still gets a pair of prints side by side.
 */
class FootstepTracker(var stride: Float = 0.085f, var gauge: Float = 0.022f) {

    /** Position in canvas UV; direction as a unit vector in world units; side +1 right, -1 left. */
    data class Footprint(val personId: Int, val x: Float, val y: Float, val dirX: Float, val dirY: Float, val side: Float, val speed: Float)

    private class Walker(var lastX: Float, var lastY: Float, var dirX: Float, var dirY: Float, var side: Float, var seen: Float)

    private val walkers = HashMap<Int, Walker>()

    fun update(people: List<PersonPoint>, aspect: Float, time: Float, out: MutableList<Footprint>) {
        for (p in people) {
            val px = p.x * aspect
            val py = p.y
            val speed = hypot(p.vx * aspect, p.vy)
            val w = walkers[p.id]
            if (w == null) {
                var dx = p.vx * aspect
                var dy = p.vy
                val l = hypot(dx, dy)
                if (l < 1e-3f) { dx = 0f; dy = 1f } else { dx /= l; dy /= l }
                walkers[p.id] = Walker(px, py, dx, dy, 1f, time)
                out += print(p.id, px, py, dx, dy, -1f, aspect, speed)
                out += print(p.id, px, py, dx, dy, 1f, aspect, speed)
                continue
            }
            w.seen = time
            val dx = px - w.lastX
            val dy = py - w.lastY
            val dist = hypot(dx, dy)
            if (dist >= stride) {
                // Blend toward the new heading so prints don't jitter with tracking noise.
                var nx = w.dirX * 0.3f + dx / dist * 0.7f
                var ny = w.dirY * 0.3f + dy / dist * 0.7f
                val nl = hypot(nx, ny).coerceAtLeast(1e-4f)
                nx /= nl; ny /= nl
                w.dirX = nx; w.dirY = ny
                w.side = -w.side
                w.lastX = px; w.lastY = py
                out += print(p.id, px, py, nx, ny, w.side, aspect, speed)
            }
        }
        walkers.entries.removeAll { time - it.value.seen > 2f }
    }

    private fun print(id: Int, px: Float, py: Float, dx: Float, dy: Float, side: Float, aspect: Float, speed: Float): Footprint {
        // The right foot lands to the right of the walking line: right of (dx, dy) is (dy, -dx).
        val ox = px + dy * gauge * side
        val oy = py - dx * gauge * side
        return Footprint(id, ox / aspect, oy, dx, dy, side, speed)
    }
}
