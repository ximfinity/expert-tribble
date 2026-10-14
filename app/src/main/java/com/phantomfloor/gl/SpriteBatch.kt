package com.phantomfloor.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Instanced quads for particle scenes (snow, leaves, sparks, fireflies, spray). Each sprite is
 * 12 floats: position/angle/half-size, RGBA color, and 4 shape parameters for the fragment
 * shader. Pairs with `sprite.vert`; the scene supplies the fragment shader.
 */
class SpriteBatch(val capacity: Int) {

    private val data: FloatBuffer = ByteBuffer.allocateDirect(capacity * FLOATS * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private var vao = 0
    private var vbo = 0

    var count = 0
        private set

    /** Call on every new GL context. */
    fun onSurfaceCreated() {
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, capacity * FLOATS * 4, null, GLES30.GL_DYNAMIC_DRAW)
        val stride = FLOATS * 4
        for (attr in 0 until 3) {
            GLES30.glEnableVertexAttribArray(attr)
            GLES30.glVertexAttribPointer(attr, 4, GLES30.GL_FLOAT, false, stride, attr * 16)
            GLES30.glVertexAttribDivisor(attr, 1)
        }
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun begin() {
        data.clear()
        count = 0
    }

    /** Position and [size] (half-extent) in world units: x in [0, aspect], y in [0, 1]. */
    fun add(
        x: Float, y: Float, angle: Float, size: Float,
        r: Float, g: Float, b: Float, a: Float,
        p0: Float = 0f, p1: Float = 0f, p2: Float = 0f, p3: Float = 0f,
    ) {
        if (count >= capacity) return
        data.put(x).put(y).put(angle).put(size)
            .put(r).put(g).put(b).put(a)
            .put(p0).put(p1).put(p2).put(p3)
        count++
    }

    /** Uploads and draws everything added since [begin]. The caller binds the program and blend state. */
    fun draw() {
        if (count == 0) return
        data.flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, count * FLOATS * 4, data)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, count)
        GLES30.glBindVertexArray(0)
    }

    companion object {
        const val FLOATS = 12
    }
}
