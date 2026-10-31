package com.phantomfloor.gl

/**
 * Loads GLSL by file name through [read] (Android assets, or files fetched by the browser) and
 * expands `#include "file.glsl"` directives so the noise library and shared uniforms are written
 * once.
 */
class ShaderSource(private val read: (String) -> String) {

    private val cache = HashMap<String, String>()

    fun load(path: String): String = cache.getOrPut(path) { resolve(path, LinkedHashSet()) }

    fun program(vertex: String, fragment: String): GlProgram =
        GlProgram(load(vertex), load(fragment), "$vertex + $fragment")

    private fun resolve(path: String, stack: MutableSet<String>): String {
        require(stack.add(path)) { "Circular #include: ${stack.joinToString(" -> ")} -> $path" }
        val text = read(path)
        val out = StringBuilder(text.length + 256)
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#include")) {
                out.append(resolve(trimmed.substringAfter('"').substringBefore('"'), stack))
            } else {
                out.append(line)
            }
            out.append('\n')
        }
        stack.remove(path)
        return out.toString()
    }
}
