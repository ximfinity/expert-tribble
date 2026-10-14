package com.phantomfloor.gl

import android.opengl.GLES30

/** A linked GLSL program with a cached uniform-location table. */
class GlProgram(vertexSource: String, fragmentSource: String, private val name: String) {

    val id: Int
    private val uniforms = HashMap<String, Int>()

    init {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vs)
        GLES30.glAttachShader(id, fs)
        GLES30.glLinkProgram(id)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(id)
            GLES30.glDeleteProgram(id)
            throw IllegalStateException("Link failed for $name: $log")
        }
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            val kind = if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"
            throw IllegalStateException("Compile failed for $name ($kind): $log")
        }
        return shader
    }

    fun use() = GLES30.glUseProgram(id)

    fun location(uniform: String): Int =
        uniforms.getOrPut(uniform) { GLES30.glGetUniformLocation(id, uniform) }

    fun set1f(uniform: String, v: Float) {
        val l = location(uniform); if (l >= 0) GLES30.glUniform1f(l, v)
    }

    fun set2f(uniform: String, x: Float, y: Float) {
        val l = location(uniform); if (l >= 0) GLES30.glUniform2f(l, x, y)
    }

    fun set3f(uniform: String, x: Float, y: Float, z: Float) {
        val l = location(uniform); if (l >= 0) GLES30.glUniform3f(l, x, y, z)
    }

    fun set4f(uniform: String, x: Float, y: Float, z: Float, w: Float) {
        val l = location(uniform); if (l >= 0) GLES30.glUniform4f(l, x, y, z, w)
    }

    fun set1i(uniform: String, v: Int) {
        val l = location(uniform); if (l >= 0) GLES30.glUniform1i(l, v)
    }

    fun set4fv(uniform: String, values: FloatArray, count: Int) {
        val l = location(uniform); if (l >= 0 && count > 0) GLES30.glUniform4fv(l, count, values, 0)
    }

    /** [columnMajor] is a 3x3 matrix laid out column by column, as GLSL expects. */
    fun setMat3(uniform: String, columnMajor: FloatArray) {
        val l = location(uniform); if (l >= 0) GLES30.glUniformMatrix3fv(l, 1, false, columnMajor, 0)
    }

    /** Binds [texture] to [unit] and points the sampler uniform at it. */
    fun texture(uniform: String, unit: Int, texture: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        set1i(uniform, unit)
    }

    fun release() = GLES30.glDeleteProgram(id)
}
