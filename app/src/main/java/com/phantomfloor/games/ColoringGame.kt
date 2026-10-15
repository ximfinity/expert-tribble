package com.phantomfloor.games

import com.phantomfloor.audio.Sfx
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.scenes.SceneParam
import com.phantomfloor.scenes.StampField

/**
 * COLORING: walk to paint the floor. Step on a colour swatch along the bottom to pick your
 * colour (everyone has their own brush), the eraser to rub out, and stand on CLEAR to start over.
 * Optional outline pictures to colour in.
 */
class ColoringGame : GameScene("game_coloring", "Coloring") {

    private val brush = SceneParam("brush", "Brush size", 0.02f, 0.1f, 0.045f)
    private val template = SceneParam("picture", "Picture: None·Pumpkin·Star·Heart·House·Fish", 0f, 5f, 1f, step = 1f)
    override val params = listOf(brush, template)
    override val howToPlay = ""
    override val freePlay = true
    override val roundSeconds: Float? = null
    override val accent = floatArrayOf(0.9f, 0.6f, 1f)

    private val paint = StampField("paint_field.frag", simHeight = 360)
    private lateinit var display: GlProgram
    private val brushColor = HashMap<Int, Int>() // person id -> palette index (ERASER = -1)
    private var clearHold = 0f
    private var lastTemplate = -1

    private fun swatchX(i: Int) = aspect / 2f + (i - (SWATCHES.size + 1) / 2f) * 0.115f
    private val swatchY = 0.075f
    private val eraserIndex get() = SWATCHES.size

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        paint.onSurfaceCreated(gl)
        display = gl.shaders.program("fullscreen.vert", "paint.frag")
    }

    override fun onResize(width: Int, height: Int) {
        super.onResize(width, height)
        paint.onResize(width, height)
    }

    override fun startRound() = Unit

    override fun updatePlaying(dt: Float) {
        if (template.int != lastTemplate) {
            if (lastTemplate >= 0) paint.reset()
            lastTemplate = template.int
        }
        // Palette: stepping on a swatch picks that colour for that person.
        for (i in 0..eraserIndex) {
            val p = stepped(i, swatchX(i), swatchY, 0.05f) ?: continue
            brushColor[p.id] = if (i == eraserIndex) ERASER else i
            frame.sound.play(Sfx.POP, 0.7f, pan(p.x))
        }
        // CLEAR: stand on it for a moment.
        val clearX = aspect - 0.12f
        clearHold = if (occupants(clearX, swatchY, 0.06f).isNotEmpty()) clearHold + dt else 0f
        if (clearHold > 1.2f) {
            paint.reset()
            clearHold = 0f
            frame.sound.play(Sfx.SPLASH, 0.6f)
        }
        // Everyone on the canvas paints with their brush.
        for (p in frame.people) {
            if (p.y < 0.15f) continue
            val idx = brushColor.getOrPut(p.id) { (p.id and 0x7fffffff) % SWATCHES.size }
            if (idx == ERASER) {
                paint.stamp(p.x, p.y, brush.value * 1.5f, 0.6f, 0f, 0f, 0f, -1f)
            } else {
                val c = SWATCHES[idx]
                paint.stamp(p.x, p.y, brush.value, 0.9f, c[0], c[1], c[2], 1f)
            }
        }
        if (brushColor.size > 64) brushColor.clear()
        paint.step { }
    }

    override fun drawUnderlay() {
        if (phase != Phase.PLAYING) return
        android.opengl.GLES30.glEnable(android.opengl.GLES30.GL_BLEND)
        android.opengl.GLES30.glBlendFunc(android.opengl.GLES30.GL_ONE, android.opengl.GLES30.GL_ONE_MINUS_SRC_ALPHA)
        display.use()
        display.set2f("uResolution", width.toFloat(), height.toFloat())
        display.set1f("uTemplate", template.value)
        display.texture("uPaint", 0, paint.texture)
        gl.drawFullscreen()
        android.opengl.GLES30.glDisable(android.opengl.GLES30.GL_BLEND)
    }

    override fun drawPlaying() {
        for (i in SWATCHES.indices) {
            val c = SWATCHES[i]
            shape(SHAPE_DISC, swatchX(i), swatchY, 0.045f, c[0], c[1], c[2])
        }
        shape(SHAPE_RING, swatchX(eraserIndex), swatchY, 0.045f, 0.9f, 0.9f, 0.9f, p1 = 0.25f)
        label("eraser", swatchX(eraserIndex), swatchY - 0.065f, 0.022f, 0.8f, 0.8f, 0.8f)
        val clearX = aspect - 0.12f
        shape(SHAPE_PAD, clearX, swatchY, 0.05f, 0.35f, 0.35f, 0.4f, p1 = 0.3f)
        if (clearHold > 0f) shape(SHAPE_PROGRESS, clearX, swatchY, 0.06f, 1f, 0.4f, 0.4f, p1 = clearHold / 1.2f)
        label("CLEAR", clearX, swatchY, 0.025f)
        // Show each painter's current colour around their feet.
        for (p in frame.people) {
            val idx = brushColor[p.id] ?: continue
            if (idx == ERASER) shape(SHAPE_RING, p.x * aspect, p.y, brush.value * 1.6f, 1f, 1f, 1f, 0.8f, p1 = 0.15f)
            else SWATCHES[idx].let { shape(SHAPE_RING, p.x * aspect, p.y, brush.value * 1.4f, it[0], it[1], it[2], 0.9f, p1 = 0.15f) }
        }
    }

    companion object {
        private const val ERASER = -1
        private val SWATCHES = arrayOf(
            floatArrayOf(1f, 0.2f, 0.2f), floatArrayOf(1f, 0.55f, 0.1f), floatArrayOf(1f, 0.9f, 0.2f),
            floatArrayOf(0.3f, 0.9f, 0.3f), floatArrayOf(0.25f, 0.55f, 1f), floatArrayOf(0.6f, 0.3f, 1f),
            floatArrayOf(1f, 0.45f, 0.75f), floatArrayOf(1f, 1f, 1f),
        )
    }
}
