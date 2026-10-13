package com.phantomfloor.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot

/** Detects a quick N-finger tap (all fingers down and up without moving). */
class MultiFingerTapDetector(
    context: Context,
    private val fingers: Int = 3,
    private val onTap: () -> Unit,
) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2f
    private var downTime = 0L
    private var maxPointers = 0
    private var moved = false
    private val startX = HashMap<Int, Float>()
    private val startY = HashMap<Int, Float>()

    fun onTouchEvent(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = e.eventTime
                maxPointers = 1
                moved = false
                startX.clear(); startY.clear()
                record(e, 0)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                record(e, e.actionIndex)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val sx = startX[id] ?: continue
                    val sy = startY[id] ?: continue
                    if (hypot(e.getX(i) - sx, e.getY(i) - sy) > slop) moved = true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (maxPointers == fingers && !moved && e.eventTime - downTime < MAX_TAP_MS) onTap()
                maxPointers = 0
            }
            MotionEvent.ACTION_CANCEL -> maxPointers = 0
        }
    }

    private fun record(e: MotionEvent, index: Int) {
        val id = e.getPointerId(index)
        startX[id] = e.getX(index)
        startY[id] = e.getY(index)
    }

    companion object {
        private const val MAX_TAP_MS = 500L
    }
}
