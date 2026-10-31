package com.phantomfloor.gl

/**
 * Draws text on the floor: each string is rasterised once by the platform (Android Canvas, or a
 * browser 2D canvas) into a texture (cached, least-recently-used first out), then drawn as a quad
 * in world units. GL thread only.
 */
class TextRenderer {

    private class Entry(val texture: Int, val aspect: Float)

    private lateinit var gl: GlResources
    private lateinit var program: GlProgram
    private val cache = LinkedHashMap<String, Entry>()

    /** Call on every new GL context; old textures died with the old context. */
    fun onSurfaceCreated(gl: GlResources) {
        this.gl = gl
        program = gl.shaders.program("text.vert", "text.frag")
        cache.clear()
    }

    private fun entry(text: String): Entry {
        cache.remove(text)?.let { cache[text] = it; return it } // move to most-recent
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val aspect = GLES30.backend.uploadText(text)
        val e = Entry(ids[0], aspect)
        cache[text] = e
        if (cache.size > MAX_CACHED) {
            val oldest = cache.keys.first()
            cache.remove(oldest)?.let { GLES30.glDeleteTextures(1, intArrayOf(it.texture), 0) }
        }
        return e
    }

    /** Draws [text] centred at world ([x], [y]) with cap height [height]. Caller enables blending. */
    fun draw(text: String, x: Float, y: Float, height: Float, aspect: Float, r: Float, g: Float, b: Float, a: Float = 1f) {
        if (text.isEmpty()) return
        val e = entry(text)
        val halfH = height * 0.65f
        program.use()
        program.set2f("uCenter", x, y)
        program.set2f("uHalf", halfH * e.aspect, halfH)
        program.set1f("uAspect", aspect)
        program.set4f("uColor", r, g, b, a)
        program.texture("uTex", 0, e.texture)
        gl.drawQuad()
    }

    private companion object {
        const val MAX_CACHED = 128
    }
}
