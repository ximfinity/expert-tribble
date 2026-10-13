package com.phantomfloor.vision

import com.phantomfloor.engine.PersonPoint
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.hypot

/**
 * Turns noisy per-frame detections (canvas UV) into stable tracks with ids and velocities.
 * Greedy nearest-neighbour association, exponential smoothing, and short-lived track memory so
 * a missed detection or two doesn't make the fog snap shut.
 *
 * [update] runs on the vision coroutine; [snapshot] is lock-free for the GL thread.
 */
class PersonTracker(private val maxTracks: Int = 8) {

    private class Track(val id: Int, var x: Float, var y: Float) {
        var vx = 0f
        var vy = 0f
        var lastSeen = 0L
        var hits = 0
    }

    private val tracks = ArrayList<Track>()
    private val published = AtomicReference<List<PersonPoint>>(emptyList())
    private var nextId = 1

    /** Tracks seen within [STALE_NANOS]. Safe to call from any thread. */
    fun snapshot(nowNanos: Long): List<PersonPoint> {
        val list = published.get()
        if (list.isEmpty()) return list
        return list.filter { nowNanos - it.timestampNanos < STALE_NANOS }
    }

    @Synchronized
    fun clear() {
        tracks.clear()
        published.set(emptyList())
    }

    @Synchronized
    fun update(detections: List<FloatArray>, nowNanos: Long) {
        val unmatched = detections.toMutableList()
        val claimed = HashSet<Track>()

        // Associate, closest pairs first.
        val pairs = ArrayList<Triple<Track, FloatArray, Float>>()
        for (t in tracks) for (d in detections) {
            val dist = hypot(d[0] - t.x, d[1] - t.y)
            if (dist < GATE) pairs.add(Triple(t, d, dist))
        }
        pairs.sortBy { it.third }
        for ((t, d, _) in pairs) {
            if (t in claimed || d !in unmatched) continue
            claimed.add(t)
            unmatched.remove(d)
            val dt = ((nowNanos - t.lastSeen) / 1e9f).coerceIn(1e-3f, 0.5f)
            val nx = t.x + (d[0] - t.x) * POS_ALPHA
            val ny = t.y + (d[1] - t.y) * POS_ALPHA
            t.vx += ((nx - t.x) / dt - t.vx) * VEL_ALPHA
            t.vy += ((ny - t.y) / dt - t.vy) * VEL_ALPHA
            t.x = nx
            t.y = ny
            t.lastSeen = nowNanos
            t.hits++
        }

        for (d in unmatched) {
            if (tracks.size >= maxTracks) break
            tracks.add(Track(nextId++, d[0], d[1]).apply { lastSeen = nowNanos; hits = 1 })
        }

        tracks.removeAll { nowNanos - it.lastSeen > DROP_NANOS }
        published.set(
            tracks.filter { it.hits >= MIN_HITS }.map {
                PersonPoint(it.id, it.x, it.y, it.vx, it.vy, it.lastSeen)
            },
        )
    }

    companion object {
        private const val GATE = 0.15f
        private const val POS_ALPHA = 0.6f
        private const val VEL_ALPHA = 0.35f
        private const val MIN_HITS = 2
        private const val STALE_NANOS = 400_000_000L
        private const val DROP_NANOS = 600_000_000L
    }
}
