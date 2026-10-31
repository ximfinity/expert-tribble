package com.phantomfloor.web

import com.phantomfloor.gl.GLES30
import com.phantomfloor.gl.GlBackend

/**
 * WebGL2 for the shared [GLES30] facade. WebGL hands out objects rather than integer names, so
 * each kind is kept in a table and the facade sees its index (0 = none, as in GL). Kotlin
 * FloatArrays are Float32Arrays in JavaScript, so uploads need no copying.
 */
class WebGlBackend(private val gl: dynamic) : GlBackend {

    private class Table {
        private val objects = ArrayList<dynamic>().apply { add(null) }
        private val free = ArrayList<Int>()
        fun put(o: dynamic): Int {
            if (o == null) return 0
            val slot = if (free.isNotEmpty()) free.removeAt(free.lastIndex) else objects.size.also { objects.add(null) }
            objects[slot] = o
            return slot
        }
        operator fun get(id: Int): dynamic = if (id <= 0 || id >= objects.size) null else objects[id]
        fun idOf(o: dynamic): Int = if (o == null) 0 else objects.indexOfFirst { it === o }.coerceAtLeast(0)
        fun remove(id: Int) {
            if (id <= 0 || id >= objects.size) return
            objects[id] = null
            free.add(id)
        }
    }

    private val shaders = Table()
    private val programs = Table()
    private val buffers = Table()
    private val vaos = Table()
    private val textures = Table()
    private val framebuffers = Table()
    private val locations = Table()
    private val locationCache = HashMap<String, Int>()

    /** Canvas used to rasterise text for the floor. */
    private val textCanvas: dynamic = js("document.createElement('canvas')")
    private val textCtx: dynamic = textCanvas.getContext("2d")

    /** True once the extension that makes half-float targets renderable is enabled. */
    val floatTargets: Boolean = gl.getExtension("EXT_color_buffer_float") != null ||
        gl.getExtension("EXT_color_buffer_half_float") != null

    override fun enable(cap: Int) { gl.enable(cap) }
    override fun disable(cap: Int) { gl.disable(cap) }
    override fun blendFunc(src: Int, dst: Int) { gl.blendFunc(src, dst) }
    override fun blendEquation(mode: Int) { gl.blendEquation(mode) }
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) { gl.clearColor(r, g, b, a) }
    override fun clear(mask: Int) { gl.clear(mask) }
    override fun viewport(x: Int, y: Int, w: Int, h: Int) { gl.viewport(x, y, w, h) }

    override fun getString(name: Int): String? = when (name) {
        // Report the desktop-GL names GlResources looks for when float targets work.
        GLES30.GL_EXTENSIONS -> if (floatTargets) "GL_EXT_color_buffer_float" else ""
        GLES30.GL_VERSION -> gl.getParameter(name) as String?
        else -> gl.getParameter(name) as String?
    }

    override fun getInteger(pname: Int): Int = when (pname) {
        GLES30.GL_FRAMEBUFFER_BINDING -> framebuffers.idOf(gl.getParameter(pname))
        else -> (gl.getParameter(pname) as Number).toInt()
    }

    override fun createShader(type: Int) = shaders.put(gl.createShader(type))
    override fun shaderSource(shader: Int, source: String) { gl.shaderSource(shaders[shader], source) }
    override fun compileShader(shader: Int) { gl.compileShader(shaders[shader]) }
    override fun getShaderParameter(shader: Int, pname: Int): Int = boolOrInt(gl.getShaderParameter(shaders[shader], pname))
    override fun getShaderInfoLog(shader: Int): String = (gl.getShaderInfoLog(shaders[shader]) as String?) ?: ""
    override fun deleteShader(shader: Int) { gl.deleteShader(shaders[shader]); shaders.remove(shader) }
    override fun createProgram() = programs.put(gl.createProgram())
    override fun attachShader(program: Int, shader: Int) { gl.attachShader(programs[program], shaders[shader]) }
    override fun linkProgram(program: Int) { gl.linkProgram(programs[program]) }
    override fun getProgramParameter(program: Int, pname: Int): Int = boolOrInt(gl.getProgramParameter(programs[program], pname))
    override fun getProgramInfoLog(program: Int): String = (gl.getProgramInfoLog(programs[program]) as String?) ?: ""
    override fun deleteProgram(program: Int) { gl.deleteProgram(programs[program]); programs.remove(program) }
    override fun useProgram(program: Int) { gl.useProgram(programs[program]) }

    override fun getUniformLocation(program: Int, name: String): Int {
        val key = "$program/$name"
        return locationCache.getOrPut(key) {
            val loc = gl.getUniformLocation(programs[program], name)
            if (loc == null) -1 else locations.put(loc)
        }
    }

    override fun uniform1f(l: Int, x: Float) { gl.uniform1f(locations[l], x) }
    override fun uniform2f(l: Int, x: Float, y: Float) { gl.uniform2f(locations[l], x, y) }
    override fun uniform3f(l: Int, x: Float, y: Float, z: Float) { gl.uniform3f(locations[l], x, y, z) }
    override fun uniform4f(l: Int, x: Float, y: Float, z: Float, w: Float) { gl.uniform4f(locations[l], x, y, z, w) }
    override fun uniform1i(l: Int, x: Int) { gl.uniform1i(locations[l], x) }
    override fun uniform4fv(l: Int, count: Int, v: FloatArray, offset: Int) {
        gl.uniform4fv(locations[l], v, offset, count * 4)
    }
    override fun uniformMatrix3fv(l: Int, count: Int, transpose: Boolean, v: FloatArray, offset: Int) {
        gl.uniformMatrix3fv(locations[l], transpose, v, offset, count * 9)
    }

    override fun createBuffer() = buffers.put(gl.createBuffer())
    override fun bindBuffer(target: Int, buffer: Int) { gl.bindBuffer(target, buffers[buffer]) }
    override fun bufferData(target: Int, sizeBytes: Int, usage: Int) { gl.bufferData(target, sizeBytes, usage) }
    override fun bufferSubData(target: Int, offsetBytes: Int, data: FloatArray, count: Int) {
        gl.bufferSubData(target, offsetBytes, data, 0, count)
    }
    override fun createVertexArray() = vaos.put(gl.createVertexArray())
    override fun bindVertexArray(vao: Int) { gl.bindVertexArray(vaos[vao]) }
    override fun enableVertexAttribArray(index: Int) { gl.enableVertexAttribArray(index) }
    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) {
        gl.vertexAttribPointer(index, size, type, normalized, stride, offset)
    }
    override fun vertexAttribDivisor(index: Int, divisor: Int) { gl.vertexAttribDivisor(index, divisor) }
    override fun drawArrays(mode: Int, first: Int, count: Int) { gl.drawArrays(mode, first, count) }
    override fun drawArraysInstanced(mode: Int, first: Int, count: Int, instances: Int) {
        gl.drawArraysInstanced(mode, first, count, instances)
    }

    override fun createTexture() = textures.put(gl.createTexture())
    override fun deleteTexture(texture: Int) { gl.deleteTexture(textures[texture]); textures.remove(texture) }
    override fun bindTexture(target: Int, texture: Int) { gl.bindTexture(target, textures[texture]) }
    override fun activeTexture(unit: Int) { gl.activeTexture(unit) }
    override fun texParameteri(target: Int, pname: Int, value: Int) { gl.texParameteri(target, pname, value) }
    override fun texImage2D(target: Int, level: Int, internalFormat: Int, w: Int, h: Int, format: Int, type: Int) {
        gl.texImage2D(target, level, internalFormat, w, h, 0, format, type, null)
    }
    override fun createFramebuffer() = framebuffers.put(gl.createFramebuffer())
    override fun deleteFramebuffer(fbo: Int) { gl.deleteFramebuffer(framebuffers[fbo]); framebuffers.remove(fbo) }
    override fun bindFramebuffer(target: Int, fbo: Int) { gl.bindFramebuffer(target, framebuffers[fbo]) }
    override fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int) {
        gl.framebufferTexture2D(target, attachment, texTarget, textures[texture], level)
    }
    override fun checkFramebufferStatus(target: Int): Int = (gl.checkFramebufferStatus(target) as Number).toInt()

    override fun uploadText(text: String): Float {
        val ctx = textCtx
        val font = "bold 96px system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
        ctx.font = font
        val pad = 16
        val w = maxOf(1, kotlin.math.ceil((ctx.measureText(text).width as Number).toDouble()).toInt() + pad * 2)
        val h = 128
        textCanvas.width = w
        textCanvas.height = h
        ctx.font = font // resizing the canvas resets its state
        ctx.clearRect(0, 0, w, h)
        ctx.fillStyle = "#ffffff"
        ctx.shadowColor = "#000000"
        ctx.shadowBlur = 10
        ctx.fillText(text, pad, 98)
        // Android uploads text bitmaps premultiplied; match it.
        gl.pixelStorei(UNPACK_PREMULTIPLY_ALPHA, true)
        gl.texImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, textCanvas)
        gl.pixelStorei(UNPACK_PREMULTIPLY_ALPHA, false)
        return w.toFloat() / h
    }

    private fun boolOrInt(v: dynamic): Int = when (v) {
        true -> 1
        false, null -> 0
        else -> (v as Number).toInt()
    }

    private companion object {
        const val UNPACK_PREMULTIPLY_ALPHA = 0x9241
    }
}
