package com.phantomfloor

import com.phantomfloor.audio.Fft
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

class FftTest {

    @Test
    fun pureToneLandsInItsBin() {
        val n = 1024
        val bin = 37
        val re = FloatArray(n) { cos(2 * PI * bin * it / n).toFloat() }
        val im = FloatArray(n)
        Fft(n).transform(re, im)
        val mags = FloatArray(n / 2) { hypot(re[it], im[it]) }
        val peak = mags.indices.maxBy { mags[it] }
        assertEquals(bin, peak)
        assertEquals(n / 2f, mags[bin], 1e-2f)
        assertEquals(0f, mags[bin + 5], 1e-2f)
    }

    @Test
    fun dcComponent() {
        val n = 64
        val re = FloatArray(n) { 1f }
        val im = FloatArray(n)
        Fft(n).transform(re, im)
        assertEquals(n.toFloat(), re[0], 1e-4f)
        assertEquals(0f, re[1], 1e-4f)
    }
}
