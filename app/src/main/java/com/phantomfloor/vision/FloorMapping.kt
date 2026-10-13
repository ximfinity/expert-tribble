package com.phantomfloor.vision

import com.phantomfloor.util.Homography

/**
 * Maps upright, normalized camera coordinates (0..1, origin top-left, as ML Kit reports after
 * rotation) to canvas UV (0..1, origin bottom-left). Set by [FloorCalibrator].
 */
class FloorMapping {

    @Volatile
    private var matrix: FloatArray? = null

    val isCalibrated get() = matrix != null

    fun set(m: FloatArray?) {
        matrix = m?.copyOf()
    }

    fun get(): FloatArray? = matrix?.copyOf()

    fun map(camX: Float, camY: Float, out: FloatArray): Boolean {
        val m = matrix
        if (m == null) {
            // Uncalibrated guess: camera sees roughly what the projector covers.
            out[0] = camX
            out[1] = 1f - camY
            return true
        }
        return Homography.map(m, camX, camY, out)
    }
}
