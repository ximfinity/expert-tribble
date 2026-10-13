package com.phantomfloor.vision

/** Converts normalized raw-sensor coordinates to the upright frame ML Kit reports in. */
object ImageGeometry {
    /** [rotation] = degrees the raw image must be rotated clockwise to be upright. */
    fun rawToUpright(nx: Float, ny: Float, rotation: Int, out: FloatArray) {
        when (((rotation % 360) + 360) % 360) {
            90 -> { out[0] = 1f - ny; out[1] = nx }
            180 -> { out[0] = 1f - nx; out[1] = 1f - ny }
            270 -> { out[0] = ny; out[1] = 1f - nx }
            else -> { out[0] = nx; out[1] = ny }
        }
    }
}
