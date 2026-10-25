package com.phantomfloor.scary

import com.phantomfloor.audio.Sfx
import com.phantomfloor.audio.SoundSink
import com.phantomfloor.engine.FrameState
import com.phantomfloor.engine.PersonPoint
import com.phantomfloor.scenes.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the Scary scenes with scripted visitors and checks when they scare (by the sounds they make). */
class ScaryScenesTest {

    private class Run(val scene: Scene) {
        val frame = FrameState()
        val events = ArrayList<Pair<Float, Sfx>>()
        var t = 0f
        init {
            frame.sound = object : SoundSink { override fun play(sfx: Sfx, volume: Float, pan: Float) { events += t to sfx } }
        }
        /** Runs [seconds] with [who] on the floor: id to (x, y) in canvas UV, or nobody. */
        fun run(seconds: Float, who: Map<Int, Pair<Float, Float>>, vx: Float = 0f) {
            val end = t + seconds
            while (t < end) {
                t += 1f / 60f
                frame.time = t; frame.dt = 1f / 60f
                frame.setPeople(who.map { (id, p) -> PersonPoint(id, p.first, p.second, vx, 0f, 0L) })
                scene.update(frame)
            }
        }
        fun count(sfx: Sfx, from: Float = 0f) = events.count { it.second == sfx && it.first >= from }
    }

    private val loudOnes = setOf(Sfx.SCREAM, Sfx.STINGER, Sfx.SNAP, Sfx.RUMBLE, Sfx.GROAN, Sfx.HEARTBEAT, Sfx.WHISPER)

    @Test
    fun jumpScareWaitsForSomeoneAndScaresEachPersonOnce() {
        val r = Run(JumpScareScene())
        r.run(10f, emptyMap())
        assertEquals("silent while the floor is empty", 0, r.events.count { it.second in loudOnes })

        r.run(30f, mapOf(1 to (0.3f to 0.4f)))
        assertEquals("one scream for the first visitor", 1, r.count(Sfx.SCREAM))
        val scream = r.events.first { it.second == Sfx.SCREAM }.first
        assertTrue("scare comes after a pause, not instantly (at ${scream - 10f}s)", scream - 10f in 0.3f..3f)
        assertTrue("heartbeat builds suspense first", r.events.any { it.second == Sfx.HEARTBEAT && it.first < scream })

        // A second person arriving gets their own scare (after the rest period).
        r.run(30f, mapOf(1 to (0.3f to 0.4f), 2 to (0.7f to 0.6f)))
        assertEquals(2, r.count(Sfx.SCREAM))

        // Everyone leaves; after the floor is empty for a while, a returning visitor is fair game.
        r.run(5f, emptyMap())
        r.run(25f, mapOf(1 to (0.5f to 0.5f)))
        assertEquals(3, r.count(Sfx.SCREAM))
    }

    @Test
    fun eyesGatherAroundSomeoneStandingStillThenSnap() {
        val r = Run(EyesInTheDarkScene())
        r.run(8f, emptyMap())
        assertEquals(0, r.events.count { it.second in loudOnes })
        r.run(12f, mapOf(1 to (0.5f to 0.5f)))
        assertTrue("whispers once eyes are watching", r.count(Sfx.WHISPER) > 0)
        assertEquals("the maw snaps once", 1, r.count(Sfx.SNAP))
        // Someone who keeps moving is never bitten.
        val moving = Run(EyesInTheDarkScene())
        var x = 0.1f
        repeat(20 * 4) {
            x = if (x > 0.9f) 0.1f else x + 0.05f
            moving.run(0.25f, mapOf(1 to (x to 0.5f)), vx = 0.2f)
        }
        assertEquals(0, moving.count(Sfx.SNAP))
    }

    @Test
    fun handsBurstUpWhereSomeoneStopsAndOnlyThere() {
        val r = Run(GraspingHandsScene())
        r.run(5f, emptyMap())
        assertEquals(0, r.events.count { it.second in loudOnes })
        r.run(4f, mapOf(1 to (0.5f to 0.5f)))
        assertEquals("the floor rumbles open once", 1, r.count(Sfx.RUMBLE))
        assertEquals("hands burst up once", 1, r.count(Sfx.SNAP))
        // Standing in the same spot doesn't open more cracks; stopping somewhere new does.
        r.run(6f, mapOf(1 to (0.5f to 0.5f)))
        assertEquals(1, r.count(Sfx.RUMBLE))
        r.run(3f, mapOf(1 to (0.85f to 0.3f)))
        assertEquals(2, r.count(Sfx.RUMBLE))
    }
}
