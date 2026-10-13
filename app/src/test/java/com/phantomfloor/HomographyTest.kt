package com.phantomfloor

import com.phantomfloor.util.Homography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomographyTest {

    private val out = FloatArray(2)
    private val unit = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

    @Test
    fun squareToQuadMapsCornersExactly() {
        val quad = floatArrayOf(-0.9f, -0.7f, 0.8f, -1f, 0.6f, 0.95f, -0.75f, 0.7f)
        val h = Homography.squareToQuad(quad)
        for (i in 0 until 4) {
            assertTrue(Homography.map(h, unit[i * 2], unit[i * 2 + 1], out))
            assertEquals(quad[i * 2], out[0], 1e-5f)
            assertEquals(quad[i * 2 + 1], out[1], 1e-5f)
        }
    }

    @Test
    fun identityForUnitSquare() {
        val h = Homography.squareToQuad(unit)
        Homography.map(h, 0.3f, 0.8f, out)
        assertEquals(0.3f, out[0], 1e-6f)
        assertEquals(0.8f, out[1], 1e-6f)
    }

    @Test
    fun inverseRoundTrips() {
        val h = Homography.squareToQuad(floatArrayOf(-1f, -0.8f, 0.9f, -1f, 1f, 1f, -0.85f, 0.9f))
        val inv = Homography.invert(h)
        assertNotNull(inv)
        Homography.map(h, 0.25f, 0.6f, out)
        Homography.map(inv!!, out[0], out[1], out)
        assertEquals(0.25f, out[0], 1e-5f)
        assertEquals(0.6f, out[1], 1e-5f)
    }

    @Test
    fun quadToQuadMatchesCalibrationPoints() {
        // Camera sees the markers rotated, mirrored (y down) and in perspective.
        val camera = floatArrayOf(0.31f, 0.72f, 0.70f, 0.69f, 0.66f, 0.28f, 0.27f, 0.33f)
        val canvas = floatArrayOf(0.2f, 0.2f, 0.8f, 0.2f, 0.8f, 0.8f, 0.2f, 0.8f)
        val m = Homography.quadToQuad(camera, canvas)!!
        for (i in 0 until 4) {
            Homography.map(m, camera[i * 2], camera[i * 2 + 1], out)
            assertEquals(canvas[i * 2], out[0], 1e-4f)
            assertEquals(canvas[i * 2 + 1], out[1], 1e-4f)
        }
    }

    @Test
    fun convexity() {
        assertTrue(Homography.isConvex(unit))
        assertTrue(Homography.isConvex(floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f))) // clockwise
        assertFalse(Homography.isConvex(floatArrayOf(0f, 0f, 1f, 0f, 0.2f, 0.2f, 0f, 1f))) // dented
        assertFalse(Homography.isConvex(floatArrayOf(0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f))) // bow-tie
    }
}
