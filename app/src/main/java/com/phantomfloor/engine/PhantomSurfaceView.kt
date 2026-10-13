package com.phantomfloor.engine

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.hypot

/**
 * GLES 3.0 surface. Touch handling depends on mode:
 *  - Keystone edit: each finger grabs the nearest corner handle and drags it (multi-touch).
 *  - Normal: fingers become "people" on the floor, mapped through the inverse keystone.
 */
class PhantomSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    private lateinit var keystone: Keystone
    private lateinit var settings: EngineSettings
    private lateinit var touches: TouchPeople

    /** Called on the UI thread when the user lets go of a keystone corner. */
    var onKeystoneEdited: (() -> Unit)? = null

    private val dragCorner = HashMap<Int, Int>()
    private val dragOffset = HashMap<Int, FloatArray>()
    private val tmp = FloatArray(2)
    private val grabRadiusPx = 96f * resources.displayMetrics.density

    fun start(renderer: Renderer, keystone: Keystone, settings: EngineSettings, touches: TouchPeople) {
        this.keystone = keystone
        this.settings = settings
        this.touches = touches
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        keepScreenOn = true
    }

    private fun ndcX(px: Float) = px / width * 2f - 1f
    private fun ndcY(py: Float) = 1f - py / height * 2f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!::settings.isInitialized) return false
        if (settings.keystoneEdit) {
            touches.clear()
            handleKeystone(event)
        } else {
            handlePeople(event)
        }
        return true
    }

    private fun handleKeystone(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                val x = e.getX(idx)
                val y = e.getY(idx)
                val c = keystone.corners()
                var best = -1
                var bestDist = grabRadiusPx
                for (i in 0 until 4) {
                    if (dragCorner.containsValue(i)) continue
                    val sx = (c[i * 2] + 1f) * 0.5f * width
                    val sy = (1f - c[i * 2 + 1]) * 0.5f * height
                    val d = hypot(sx - x, sy - y)
                    if (d < bestDist) { bestDist = d; best = i }
                }
                if (best >= 0) {
                    val id = e.getPointerId(idx)
                    dragCorner[id] = best
                    dragOffset[id] = floatArrayOf(c[best * 2] - ndcX(x), c[best * 2 + 1] - ndcY(y))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val corner = dragCorner[id] ?: continue
                    val off = dragOffset[id] ?: continue
                    keystone.setCorner(corner, ndcX(e.getX(i)) + off[0], ndcY(e.getY(i)) + off[1])
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                dragCorner.remove(id)
                dragOffset.remove(id)
                onKeystoneEdited?.invoke()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragCorner.clear()
                dragOffset.clear()
                onKeystoneEdited?.invoke()
            }
        }
    }

    private fun handlePeople(e: MotionEvent) {
        if (!settings.touchAsPeople) {
            touches.clear()
            return
        }
        val now = System.nanoTime()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    if (keystone.screenToCanvas(ndcX(e.getX(i)), ndcY(e.getY(i)), tmp)) {
                        touches.update(id, tmp[0], tmp[1], now)
                    } else {
                        touches.remove(id)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> touches.remove(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touches.clear()
        }
    }
}
