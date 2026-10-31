package com.phantomfloor.gl

/**
 * The subset of OpenGL ES 3.0 PhantomFloor uses, with Android's names and signatures so scene
 * code reads the same on every platform. Calls go to [backend]: Android's GLES30 on the phone,
 * WebGL2 in the browser. Object names (shaders, buffers, textures...) are plain Ints everywhere;
 * the WebGL backend maps them to its objects.
 *
 * Differences from android.opengl.GLES30: buffer uploads take a FloatArray instead of a
 * java.nio.Buffer, and [glTexImage2D] can only allocate (data is always null).
 */
@Suppress("FunctionName", "unused")
object GLES30 {
    lateinit var backend: GlBackend

    const val GL_ZERO = 0
    const val GL_ONE = 1
    const val GL_TRIANGLES = 0x0004
    const val GL_TRIANGLE_STRIP = 0x0005
    const val GL_SRC_ALPHA = 0x0302
    const val GL_ONE_MINUS_SRC_ALPHA = 0x0303
    const val GL_DEPTH_TEST = 0x0B71
    const val GL_DITHER = 0x0BD0
    const val GL_BLEND = 0x0BE2
    const val GL_TEXTURE_2D = 0x0DE1
    const val GL_UNSIGNED_BYTE = 0x1401
    const val GL_FLOAT = 0x1406
    const val GL_HALF_FLOAT = 0x140B
    const val GL_RGBA = 0x1908
    const val GL_VERSION = 0x1F02
    const val GL_EXTENSIONS = 0x1F03
    const val GL_NEAREST = 0x2600
    const val GL_LINEAR = 0x2601
    const val GL_TEXTURE_MAG_FILTER = 0x2800
    const val GL_TEXTURE_MIN_FILTER = 0x2801
    const val GL_TEXTURE_WRAP_S = 0x2802
    const val GL_TEXTURE_WRAP_T = 0x2803
    const val GL_COLOR_BUFFER_BIT = 0x4000
    const val GL_FUNC_ADD = 0x8006
    const val GL_FUNC_REVERSE_SUBTRACT = 0x800B
    const val GL_RGBA8 = 0x8058
    const val GL_CLAMP_TO_EDGE = 0x812F
    const val GL_TEXTURE0 = 0x84C0
    const val GL_RGBA16F = 0x881A
    const val GL_ARRAY_BUFFER = 0x8892
    const val GL_DYNAMIC_DRAW = 0x88E8
    const val GL_FRAGMENT_SHADER = 0x8B30
    const val GL_VERTEX_SHADER = 0x8B31
    const val GL_COMPILE_STATUS = 0x8B81
    const val GL_LINK_STATUS = 0x8B82
    const val GL_FRAMEBUFFER_BINDING = 0x8CA6
    const val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
    const val GL_COLOR_ATTACHMENT0 = 0x8CE0
    const val GL_FRAMEBUFFER = 0x8D40

    fun glEnable(cap: Int) = backend.enable(cap)
    fun glDisable(cap: Int) = backend.disable(cap)
    fun glBlendFunc(src: Int, dst: Int) = backend.blendFunc(src, dst)
    fun glBlendEquation(mode: Int) = backend.blendEquation(mode)
    fun glClearColor(r: Float, g: Float, b: Float, a: Float) = backend.clearColor(r, g, b, a)
    fun glClear(mask: Int) = backend.clear(mask)
    fun glViewport(x: Int, y: Int, w: Int, h: Int) = backend.viewport(x, y, w, h)
    fun glGetString(name: Int): String? = backend.getString(name)
    fun glGetIntegerv(pname: Int, out: IntArray, offset: Int) { out[offset] = backend.getInteger(pname) }

    // Shaders and programs
    fun glCreateShader(type: Int): Int = backend.createShader(type)
    fun glShaderSource(shader: Int, source: String) = backend.shaderSource(shader, source)
    fun glCompileShader(shader: Int) = backend.compileShader(shader)
    fun glGetShaderiv(shader: Int, pname: Int, out: IntArray, offset: Int) { out[offset] = backend.getShaderParameter(shader, pname) }
    fun glGetShaderInfoLog(shader: Int): String = backend.getShaderInfoLog(shader)
    fun glDeleteShader(shader: Int) = backend.deleteShader(shader)
    fun glCreateProgram(): Int = backend.createProgram()
    fun glAttachShader(program: Int, shader: Int) = backend.attachShader(program, shader)
    fun glLinkProgram(program: Int) = backend.linkProgram(program)
    fun glGetProgramiv(program: Int, pname: Int, out: IntArray, offset: Int) { out[offset] = backend.getProgramParameter(program, pname) }
    fun glGetProgramInfoLog(program: Int): String = backend.getProgramInfoLog(program)
    fun glDeleteProgram(program: Int) = backend.deleteProgram(program)
    fun glUseProgram(program: Int) = backend.useProgram(program)
    fun glGetUniformLocation(program: Int, name: String): Int = backend.getUniformLocation(program, name)
    fun glUniform1f(l: Int, x: Float) = backend.uniform1f(l, x)
    fun glUniform2f(l: Int, x: Float, y: Float) = backend.uniform2f(l, x, y)
    fun glUniform3f(l: Int, x: Float, y: Float, z: Float) = backend.uniform3f(l, x, y, z)
    fun glUniform4f(l: Int, x: Float, y: Float, z: Float, w: Float) = backend.uniform4f(l, x, y, z, w)
    fun glUniform1i(l: Int, x: Int) = backend.uniform1i(l, x)
    fun glUniform4fv(l: Int, count: Int, v: FloatArray, offset: Int) = backend.uniform4fv(l, count, v, offset)
    fun glUniformMatrix3fv(l: Int, count: Int, transpose: Boolean, v: FloatArray, offset: Int) =
        backend.uniformMatrix3fv(l, count, transpose, v, offset)

    // Buffers and vertex arrays
    fun glGenBuffers(n: Int, out: IntArray, offset: Int) { for (i in 0 until n) out[offset + i] = backend.createBuffer() }
    fun glBindBuffer(target: Int, buffer: Int) = backend.bindBuffer(target, buffer)
    /** Allocates [sizeBytes] of uninitialised storage. */
    fun glBufferData(target: Int, sizeBytes: Int, usage: Int) = backend.bufferData(target, sizeBytes, usage)
    /** Uploads the first [count] floats of [data] at byte [offsetBytes]. */
    fun glBufferSubData(target: Int, offsetBytes: Int, data: FloatArray, count: Int) = backend.bufferSubData(target, offsetBytes, data, count)
    fun glGenVertexArrays(n: Int, out: IntArray, offset: Int) { for (i in 0 until n) out[offset + i] = backend.createVertexArray() }
    fun glBindVertexArray(vao: Int) = backend.bindVertexArray(vao)
    fun glEnableVertexAttribArray(index: Int) = backend.enableVertexAttribArray(index)
    fun glVertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) =
        backend.vertexAttribPointer(index, size, type, normalized, stride, offset)
    fun glVertexAttribDivisor(index: Int, divisor: Int) = backend.vertexAttribDivisor(index, divisor)
    fun glDrawArrays(mode: Int, first: Int, count: Int) = backend.drawArrays(mode, first, count)
    fun glDrawArraysInstanced(mode: Int, first: Int, count: Int, instances: Int) = backend.drawArraysInstanced(mode, first, count, instances)

    // Textures and framebuffers
    fun glGenTextures(n: Int, out: IntArray, offset: Int) { for (i in 0 until n) out[offset + i] = backend.createTexture() }
    fun glDeleteTextures(n: Int, ids: IntArray, offset: Int) { for (i in 0 until n) backend.deleteTexture(ids[offset + i]) }
    fun glBindTexture(target: Int, texture: Int) = backend.bindTexture(target, texture)
    fun glActiveTexture(unit: Int) = backend.activeTexture(unit)
    fun glTexParameteri(target: Int, pname: Int, value: Int) = backend.texParameteri(target, pname, value)
    @Suppress("UNUSED_PARAMETER")
    fun glTexImage2D(target: Int, level: Int, internalFormat: Int, w: Int, h: Int, border: Int, format: Int, type: Int, data: Nothing?) =
        backend.texImage2D(target, level, internalFormat, w, h, format, type)
    fun glGenFramebuffers(n: Int, out: IntArray, offset: Int) { for (i in 0 until n) out[offset + i] = backend.createFramebuffer() }
    fun glDeleteFramebuffers(n: Int, ids: IntArray, offset: Int) { for (i in 0 until n) backend.deleteFramebuffer(ids[offset + i]) }
    fun glBindFramebuffer(target: Int, fbo: Int) = backend.bindFramebuffer(target, fbo)
    fun glFramebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int) =
        backend.framebufferTexture2D(target, attachment, texTarget, texture, level)
    fun glCheckFramebufferStatus(target: Int): Int = backend.checkFramebufferStatus(target)
}

/** A platform's GL implementation. Object names are Ints; 0 means "none". */
interface GlBackend {
    fun enable(cap: Int)
    fun disable(cap: Int)
    fun blendFunc(src: Int, dst: Int)
    fun blendEquation(mode: Int)
    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    fun clear(mask: Int)
    fun viewport(x: Int, y: Int, w: Int, h: Int)
    fun getString(name: Int): String?
    fun getInteger(pname: Int): Int

    fun createShader(type: Int): Int
    fun shaderSource(shader: Int, source: String)
    fun compileShader(shader: Int)
    fun getShaderParameter(shader: Int, pname: Int): Int
    fun getShaderInfoLog(shader: Int): String
    fun deleteShader(shader: Int)
    fun createProgram(): Int
    fun attachShader(program: Int, shader: Int)
    fun linkProgram(program: Int)
    fun getProgramParameter(program: Int, pname: Int): Int
    fun getProgramInfoLog(program: Int): String
    fun deleteProgram(program: Int)
    fun useProgram(program: Int)
    /** -1 if the uniform doesn't exist (or was optimised away). */
    fun getUniformLocation(program: Int, name: String): Int
    fun uniform1f(l: Int, x: Float)
    fun uniform2f(l: Int, x: Float, y: Float)
    fun uniform3f(l: Int, x: Float, y: Float, z: Float)
    fun uniform4f(l: Int, x: Float, y: Float, z: Float, w: Float)
    fun uniform1i(l: Int, x: Int)
    fun uniform4fv(l: Int, count: Int, v: FloatArray, offset: Int)
    fun uniformMatrix3fv(l: Int, count: Int, transpose: Boolean, v: FloatArray, offset: Int)

    fun createBuffer(): Int
    fun bindBuffer(target: Int, buffer: Int)
    fun bufferData(target: Int, sizeBytes: Int, usage: Int)
    fun bufferSubData(target: Int, offsetBytes: Int, data: FloatArray, count: Int)
    fun createVertexArray(): Int
    fun bindVertexArray(vao: Int)
    fun enableVertexAttribArray(index: Int)
    fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int)
    fun vertexAttribDivisor(index: Int, divisor: Int)
    fun drawArrays(mode: Int, first: Int, count: Int)
    fun drawArraysInstanced(mode: Int, first: Int, count: Int, instances: Int)

    fun createTexture(): Int
    fun deleteTexture(texture: Int)
    fun bindTexture(target: Int, texture: Int)
    fun activeTexture(unit: Int)
    fun texParameteri(target: Int, pname: Int, value: Int)
    fun texImage2D(target: Int, level: Int, internalFormat: Int, w: Int, h: Int, format: Int, type: Int)
    fun createFramebuffer(): Int
    fun deleteFramebuffer(fbo: Int)
    fun bindFramebuffer(target: Int, fbo: Int)
    fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int)
    fun checkFramebufferStatus(target: Int): Int

    /**
     * Rasterises [text] (bold, white, with a soft black shadow, 96 px glyphs in a 128 px tall
     * image) into the currently bound TEXTURE_2D, premultiplied. Returns the image's width / height.
     */
    fun uploadText(text: String): Float
}
