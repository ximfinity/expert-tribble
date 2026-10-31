package com.phantomfloor.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import android.opengl.GLES30 as ES

/** OpenGL ES 3.0 for the shared [GLES30] facade. GL thread only. */
object AndroidGlBackend : GlBackend {

    private val one = IntArray(1)
    private var staging: FloatBuffer = floats(4096)

    private fun floats(n: Int): FloatBuffer =
        ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun gen(block: (IntArray) -> Unit): Int { block(one); return one[0] }

    override fun enable(cap: Int) = ES.glEnable(cap)
    override fun disable(cap: Int) = ES.glDisable(cap)
    override fun blendFunc(src: Int, dst: Int) = ES.glBlendFunc(src, dst)
    override fun blendEquation(mode: Int) = ES.glBlendEquation(mode)
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = ES.glClearColor(r, g, b, a)
    override fun clear(mask: Int) = ES.glClear(mask)
    override fun viewport(x: Int, y: Int, w: Int, h: Int) = ES.glViewport(x, y, w, h)
    override fun getString(name: Int): String? = ES.glGetString(name)
    override fun getInteger(pname: Int): Int { ES.glGetIntegerv(pname, one, 0); return one[0] }

    override fun createShader(type: Int) = ES.glCreateShader(type)
    override fun shaderSource(shader: Int, source: String) = ES.glShaderSource(shader, source)
    override fun compileShader(shader: Int) = ES.glCompileShader(shader)
    override fun getShaderParameter(shader: Int, pname: Int): Int { ES.glGetShaderiv(shader, pname, one, 0); return one[0] }
    override fun getShaderInfoLog(shader: Int): String = ES.glGetShaderInfoLog(shader)
    override fun deleteShader(shader: Int) = ES.glDeleteShader(shader)
    override fun createProgram() = ES.glCreateProgram()
    override fun attachShader(program: Int, shader: Int) = ES.glAttachShader(program, shader)
    override fun linkProgram(program: Int) = ES.glLinkProgram(program)
    override fun getProgramParameter(program: Int, pname: Int): Int { ES.glGetProgramiv(program, pname, one, 0); return one[0] }
    override fun getProgramInfoLog(program: Int): String = ES.glGetProgramInfoLog(program)
    override fun deleteProgram(program: Int) = ES.glDeleteProgram(program)
    override fun useProgram(program: Int) = ES.glUseProgram(program)
    override fun getUniformLocation(program: Int, name: String) = ES.glGetUniformLocation(program, name)
    override fun uniform1f(l: Int, x: Float) = ES.glUniform1f(l, x)
    override fun uniform2f(l: Int, x: Float, y: Float) = ES.glUniform2f(l, x, y)
    override fun uniform3f(l: Int, x: Float, y: Float, z: Float) = ES.glUniform3f(l, x, y, z)
    override fun uniform4f(l: Int, x: Float, y: Float, z: Float, w: Float) = ES.glUniform4f(l, x, y, z, w)
    override fun uniform1i(l: Int, x: Int) = ES.glUniform1i(l, x)
    override fun uniform4fv(l: Int, count: Int, v: FloatArray, offset: Int) = ES.glUniform4fv(l, count, v, offset)
    override fun uniformMatrix3fv(l: Int, count: Int, transpose: Boolean, v: FloatArray, offset: Int) =
        ES.glUniformMatrix3fv(l, count, transpose, v, offset)

    override fun createBuffer() = gen { ES.glGenBuffers(1, it, 0) }
    override fun bindBuffer(target: Int, buffer: Int) = ES.glBindBuffer(target, buffer)
    override fun bufferData(target: Int, sizeBytes: Int, usage: Int) = ES.glBufferData(target, sizeBytes, null, usage)
    override fun bufferSubData(target: Int, offsetBytes: Int, data: FloatArray, count: Int) {
        if (staging.capacity() < count) staging = floats(count)
        staging.clear()
        staging.put(data, 0, count)
        staging.flip()
        ES.glBufferSubData(target, offsetBytes, count * 4, staging)
    }
    override fun createVertexArray() = gen { ES.glGenVertexArrays(1, it, 0) }
    override fun bindVertexArray(vao: Int) = ES.glBindVertexArray(vao)
    override fun enableVertexAttribArray(index: Int) = ES.glEnableVertexAttribArray(index)
    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) =
        ES.glVertexAttribPointer(index, size, type, normalized, stride, offset)
    override fun vertexAttribDivisor(index: Int, divisor: Int) = ES.glVertexAttribDivisor(index, divisor)
    override fun drawArrays(mode: Int, first: Int, count: Int) = ES.glDrawArrays(mode, first, count)
    override fun drawArraysInstanced(mode: Int, first: Int, count: Int, instances: Int) =
        ES.glDrawArraysInstanced(mode, first, count, instances)

    override fun createTexture() = gen { ES.glGenTextures(1, it, 0) }
    override fun deleteTexture(texture: Int) { one[0] = texture; ES.glDeleteTextures(1, one, 0) }
    override fun bindTexture(target: Int, texture: Int) = ES.glBindTexture(target, texture)
    override fun activeTexture(unit: Int) = ES.glActiveTexture(unit)
    override fun texParameteri(target: Int, pname: Int, value: Int) = ES.glTexParameteri(target, pname, value)
    override fun texImage2D(target: Int, level: Int, internalFormat: Int, w: Int, h: Int, format: Int, type: Int) =
        ES.glTexImage2D(target, level, internalFormat, w, h, 0, format, type, null)
    override fun createFramebuffer() = gen { ES.glGenFramebuffers(1, it, 0) }
    override fun deleteFramebuffer(fbo: Int) { one[0] = fbo; ES.glDeleteFramebuffers(1, one, 0) }
    override fun bindFramebuffer(target: Int, fbo: Int) = ES.glBindFramebuffer(target, fbo)
    override fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int) =
        ES.glFramebufferTexture2D(target, attachment, texTarget, texture, level)
    override fun checkFramebufferStatus(target: Int) = ES.glCheckFramebufferStatus(target)

    private val paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 96f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
            setShadowLayer(10f, 0f, 0f, Color.BLACK)
        }
    }

    override fun uploadText(text: String): Float {
        val pad = 16
        val w = (ceil(paint.measureText(text)).toInt() + pad * 2).coerceAtLeast(1)
        val h = 128
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, pad.toFloat(), 98f, paint)
        GLUtils.texImage2D(ES.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        return w.toFloat() / h
    }
}
