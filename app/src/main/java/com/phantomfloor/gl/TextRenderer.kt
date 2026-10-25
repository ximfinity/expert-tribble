package com.phantomfloor.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import kotlin.math.ceil

/**
 * Draws text on the floor: each string is rasterised once with Android's Canvas into a texture
 * (cached), then drawn as a quad in world units. GL thread only.
 */
class TextRenderer {

    private class Entry(val texture: Int, val aspect: Float)

    private lateinit var gl: GlResources
    private lateinit var program: GlProgram
    // Created on first use, so games can be built (and play-tested on the JVM) without a Canvas.
    private val paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 96f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
            setShadowLayer(10f, 0f, 0f, Color.BLACK)
        }
    }
    private val cache = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean {
            val evict = size > 128
            if (evict) GLES30.glDeleteTextures(1, intArrayOf(eldest.value.texture), 0)
            return evict
        }
    }

    /** Call on every new GL context; old textures died with the old context. */
    fun onSurfaceCreated(gl: GlResources) {
        this.gl = gl
        program = gl.shaders.program("text.vert", "text.frag")
        cache.clear()
    }

    private fun entry(text: String): Entry = cache.getOrPut(text) {
        val pad = 16
        val w = ceil(paint.measureText(text)).toInt() + pad * 2
        val h = 128
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, pad.toFloat(), 98f, paint)
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        Entry(ids[0], w.toFloat() / h)
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
}
