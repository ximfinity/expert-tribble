package com.phantomfloor.gl


/** Per-context shared GL state handed to every scene. */
class GlResources(val shaders: ShaderSource) {

    /** Empty VAO: full-screen passes generate their vertices from gl_VertexID. */
    private val emptyVao: Int

    /** True if half-float color attachments are renderable (needed for precise ripple simulation). */
    val halfFloatRenderable: Boolean

    init {
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        emptyVao = ids[0]
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        val version = GLES30.glGetString(GLES30.GL_VERSION) ?: ""
        halfFloatRenderable = ext.contains("GL_EXT_color_buffer_half_float") ||
            ext.contains("GL_EXT_color_buffer_float") ||
            version.contains("OpenGL ES 3.2")
    }

    /** Draws one oversized triangle covering the viewport. Pairs with `fullscreen.vert`. */
    fun drawFullscreen() {
        GLES30.glBindVertexArray(emptyVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    /** Draws a 4-vertex triangle strip quad. Pairs with `warp.vert` and `overlay.vert`. */
    fun drawQuad() {
        GLES30.glBindVertexArray(emptyVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }
}
