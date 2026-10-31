package com.phantomfloor.engine

import com.phantomfloor.util.Homography
import com.phantomfloor.util.locked
import kotlin.concurrent.Volatile

/**
 * Digital keystone: the 4 screen-space (NDC) positions where the canvas corners land.
 * Corner order: 0 bottom-left, 1 bottom-right, 2 top-right, 3 top-left (counter-clockwise).
 *
 * Written from the UI thread (drag), read from the GL thread (render). All access is
 * locked; [version] lets the renderer cheaply detect changes.
 */
class Keystone {

    private val lock = Any()
    private val corners = DEFAULT.copyOf()
    private var inverse: FloatArray? = null

    @Volatile
    var version = 0
        private set

    fun corners(): FloatArray = locked(lock) { corners.copyOf() }

    /** Moves one corner. Rejected (returns false) if it would make the quad non-convex. */
    fun setCorner(index: Int, x: Float, y: Float): Boolean = locked(lock) {
        val candidate = corners.copyOf()
        candidate[index * 2] = x.coerceIn(-1f, 1f)
        candidate[index * 2 + 1] = y.coerceIn(-1f, 1f)
        if (!Homography.isConvex(candidate)) return@locked false
        candidate.copyInto(corners)
        inverse = null
        version++
        true
    }

    fun setAll(values: FloatArray) = locked(lock) {
        if (values.size == 8 && Homography.isConvex(values)) {
            values.copyInto(corners)
            inverse = null
            version++
        }
    }

    fun reset() = setAll(DEFAULT)

    /** Unit square -> NDC quad homography, column-major for `uniform mat3`. */
    fun matrixColumnMajor(out: FloatArray): FloatArray = locked(lock) {
        Homography.toColumnMajor(Homography.squareToQuad(corners), out)
    }

    /** Maps an NDC point on screen back to canvas UV. Returns false if outside the canvas. */
    fun screenToCanvas(ndcX: Float, ndcY: Float, out: FloatArray): Boolean {
        val inv = locked(lock) {
            inverse ?: Homography.invert(Homography.squareToQuad(corners)).also { inverse = it }
        } ?: return false
        if (!Homography.map(inv, ndcX, ndcY, out)) return false
        return out[0] in 0f..1f && out[1] in 0f..1f
    }

    companion object {
        val DEFAULT = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f)
    }
}
