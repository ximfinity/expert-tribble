package com.phantomfloor.scenes

import com.phantomfloor.audio.Sfx
import com.phantomfloor.audio.SoundSink
import com.phantomfloor.engine.FrameState
import com.phantomfloor.engine.PersonPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Headless checks of the Swarm's new options and the Shadow Puppets' behaviour (no GL needed). */
class HalloweenOptionsTest {

    private class Run(val scene: Scene) {
        val frame = FrameState()
        val sounds = ArrayList<Sfx>()
        init { frame.sound = object : SoundSink { override fun play(sfx: Sfx, volume: Float, pan: Float) { sounds += sfx } } }
        fun set(key: String, v: Float) { scene.params.first { it.key == key }.value = v }
        fun run(seconds: Float, who: List<Pair<Float, Float>>) {
            var t = 0f
            while (t < seconds) {
                t += 1f / 60f
                frame.time += 1f / 60f; frame.dt = 1f / 60f
                frame.setPeople(who.mapIndexed { i, (x, y) -> PersonPoint(i + 1, x, y, 0f, 0f, 0L) })
                scene.update(frame)
            }
        }
    }

    @Test
    fun giantSpiderDropsInFrontOfSomeoneOnSchedule() {
        val r = Run(SwarmScene())
        r.set("giant", 1f)
        r.set("giantEvery", 10f)
        r.run(20f, emptyList())
        assertEquals("no giant without anyone on the floor", 0, r.sounds.count { it == Sfx.STINGER })
        r.run(14f, listOf(0.5f to 0.5f))
        assertEquals("one giant spider after the interval", 1, r.sounds.count { it == Sfx.STINGER })
    }

    @Test
    fun spidersSpinWebsThatTearWhenWalkedThrough() {
        val r = Run(SwarmScene())
        r.set("webs", 1f)
        r.set("count", 200f)
        r.run(25f, emptyList())
        // A walker sweeping the whole floor tears whatever webs were built.
        var x = 0.02f
        var y = 0.05f
        while (y < 0.95f) {
            r.run(0.05f, listOf(x to y))
            x += 0.04f
            if (x > 0.98f) { x = 0.02f; y += 0.06f }
        }
        assertTrue("webs were torn", r.sounds.count { it == Sfx.RUSTLE } > 0)
    }

    @Test
    fun puppetsComeToSomeoneAndCallOut() {
        val r = Run(ShadowPuppetsScene())
        r.set("follow", 1f)
        r.run(5f, emptyList())
        val voices = setOf(Sfx.CACKLE, Sfx.MOAN, Sfx.GROAN, Sfx.WHISPER)
        assertEquals("quiet with nobody there", 0, r.sounds.count { it in voices })
        r.run(20f, listOf(0.5f to 0.4f))
        assertTrue("puppets noticed the person", r.sounds.count { it in voices } > 0)
    }
}
