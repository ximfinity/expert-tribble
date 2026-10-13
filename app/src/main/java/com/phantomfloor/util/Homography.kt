package com.phantomfloor.util

import kotlin.math.abs

/**
 * 3x3 projective transforms stored row-major in a FloatArray(9).
 *
 * Used twice: for the projector keystone (unit canvas square -> screen quad) and for camera
 * calibration (camera image points -> canvas points).
 */
object Homography {

    /**
     * Maps the unit square corners (0,0), (1,0), (1,1), (0,1) onto quad points
     * `q = [x0,y0, x1,y1, x2,y2, x3,y3]` (Heckbert's closed form).
     */
    fun squareToQuad(q: FloatArray): FloatArray {
        val x0 = q[0].toDouble(); val y0 = q[1].toDouble()
        val x1 = q[2].toDouble(); val y1 = q[3].toDouble()
        val x2 = q[4].toDouble(); val y2 = q[5].toDouble()
        val x3 = q[6].toDouble(); val y3 = q[7].toDouble()
        val dx3 = x0 - x1 + x2 - x3
        val dy3 = y0 - y1 + y2 - y3
        val a: Double; val b: Double; val d: Double; val e: Double; val g: Double; val h: Double
        if (abs(dx3) < 1e-12 && abs(dy3) < 1e-12) {
            a = x1 - x0; b = x3 - x0; d = y1 - y0; e = y3 - y0; g = 0.0; h = 0.0
        } else {
            val dx1 = x1 - x2; val dx2 = x3 - x2
            val dy1 = y1 - y2; val dy2 = y3 - y2
            val den = dx1 * dy2 - dx2 * dy1
            g = (dx3 * dy2 - dx2 * dy3) / den
            h = (dx1 * dy3 - dx3 * dy1) / den
            a = x1 - x0 + g * x1
            b = x3 - x0 + h * x3
            d = y1 - y0 + g * y1
            e = y3 - y0 + h * y3
        }
        return floatArrayOf(
            a.toFloat(), b.toFloat(), x0.toFloat(),
            d.toFloat(), e.toFloat(), y0.toFloat(),
            g.toFloat(), h.toFloat(), 1f,
        )
    }

    /** Homography that maps quad [src] onto quad [dst] (corresponding corner order). */
    fun quadToQuad(src: FloatArray, dst: FloatArray): FloatArray? {
        val inv = invert(squareToQuad(src)) ?: return null
        return multiply(squareToQuad(dst), inv)
    }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(9)
        for (row in 0 until 3) for (col in 0 until 3) {
            var s = 0.0
            for (k in 0 until 3) s += a[row * 3 + k].toDouble() * b[k * 3 + col]
            r[row * 3 + col] = s.toFloat()
        }
        return r
    }

    fun invert(m: FloatArray): FloatArray? {
        val a = m[0].toDouble(); val b = m[1].toDouble(); val c = m[2].toDouble()
        val d = m[3].toDouble(); val e = m[4].toDouble(); val f = m[5].toDouble()
        val g = m[6].toDouble(); val h = m[7].toDouble(); val i = m[8].toDouble()
        val A = e * i - f * h
        val B = -(d * i - f * g)
        val C = d * h - e * g
        val det = a * A + b * B + c * C
        if (abs(det) < 1e-12) return null
        val s = 1.0 / det
        return floatArrayOf(
            (A * s).toFloat(), (-(b * i - c * h) * s).toFloat(), ((b * f - c * e) * s).toFloat(),
            (B * s).toFloat(), ((a * i - c * g) * s).toFloat(), (-(a * f - c * d) * s).toFloat(),
            (C * s).toFloat(), (-(a * h - b * g) * s).toFloat(), ((a * e - b * d) * s).toFloat(),
        )
    }

    /** Applies [m] to (x, y); writes the result to out[0..1]. Returns false if w is ~0. */
    fun map(m: FloatArray, x: Float, y: Float, out: FloatArray): Boolean {
        val w = m[6] * x + m[7] * y + m[8]
        if (abs(w) < 1e-9f) return false
        out[0] = (m[0] * x + m[1] * y + m[2]) / w
        out[1] = (m[3] * x + m[4] * y + m[5]) / w
        return true
    }

    fun toColumnMajor(m: FloatArray, out: FloatArray = FloatArray(9)): FloatArray {
        for (row in 0 until 3) for (col in 0 until 3) out[col * 3 + row] = m[row * 3 + col]
        return out
    }

    /** True if the 4-point polygon is strictly convex (either winding). */
    fun isConvex(q: FloatArray, epsilon: Float = 1e-5f): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val ax = q[i * 2]; val ay = q[i * 2 + 1]
            val bx = q[(i + 1) % 4 * 2]; val by = q[(i + 1) % 4 * 2 + 1]
            val cx = q[(i + 2) % 4 * 2]; val cy = q[(i + 2) % 4 * 2 + 1]
            val cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
            if (abs(cross) < epsilon) return false
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }
}
