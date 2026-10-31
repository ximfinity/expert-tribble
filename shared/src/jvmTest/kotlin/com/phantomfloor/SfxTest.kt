package com.phantomfloor

import com.phantomfloor.audio.Sfx
import com.phantomfloor.audio.renderOffline
import org.junit.Assert.assertTrue
import org.junit.Test

class SfxTest {
    @Test
    fun everyEffectIsAudibleFiniteAndEnds() {
        for (sfx in Sfx.entries) {
            val (left, finished) = renderOffline(sfx, seconds = 3f)
            var peak = 0f
            var energy = 0.0
            for (s in left) {
                assertTrue("$sfx produced a non-finite sample", s.isFinite())
                peak = maxOf(peak, kotlin.math.abs(s))
                energy += s * s
            }
            assertTrue("$sfx is silent", energy > 1e-3)
            assertTrue("$sfx peaks at $peak (too loud)", peak < 2.5f)
            assertTrue("$sfx never finishes", finished)
        }
    }
}
