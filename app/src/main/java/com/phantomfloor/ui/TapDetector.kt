package com.phantomfloor.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot

/**
 * Detects a quick single-finger tap: one finger down and up within [MAX_TAP_MS] without moving.
 * Drags and multi-finger touches are ignored, so they can still act as people on the floor.
 */
class TapDetector(context: Context, private val onTap: () -> Unit) {

    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 1.5f
    private var downTime = 0L
    private var startX = 0f
    private var startY = 0f
    private var valid = false

    fun onTouchEvent(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = e.eventTime
                startX = e.x
                startY = e.y
                valid = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> valid = false
            MotionEvent.ACTION_MOVE -> if (hypot(e.x - startX, e.y - startY) > slop) valid = false
            MotionEvent.ACTION_UP -> {
                if (valid && e.eventTime - downTime < MAX_TAP_MS) onTap()
                valid = false
            }
            MotionEvent.ACTION_CANCEL -> valid = false
        }
    }

    companion object {
        private const val MAX_TAP_MS = 300L
    }
}
