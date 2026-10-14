package com.phantomfloor.ui

import android.app.Presentation
import android.content.Context
import android.graphics.Matrix
import android.os.Bundle
import android.view.Display
import android.view.MotionEvent
import android.view.WindowManager
import com.phantomfloor.engine.EngineSettings
import com.phantomfloor.engine.Keystone
import com.phantomfloor.engine.PhantomRenderer
import com.phantomfloor.engine.PhantomSurfaceView
import com.phantomfloor.engine.TouchPeople

/**
 * Full-screen output on a second display (wireless display / Miracast / HDMI). The phone keeps
 * the controls; its touchpad events are forwarded here, scaled to this display.
 */
class ProjectorPresentation(
    context: Context,
    display: Display,
    private val renderer: PhantomRenderer,
    private val keystone: Keystone,
    private val settings: EngineSettings,
    private val touches: TouchPeople,
) : Presentation(context, display) {

    private lateinit var view: PhantomSurfaceView
    private val scale = Matrix()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = PhantomSurfaceView(context)
        view.start(renderer, keystone, settings, touches)
        setContentView(view)
    }

    fun onResume() { if (::view.isInitialized) view.onResume() }

    fun onPause() { if (::view.isInitialized) view.onPause() }

    /** Replays a touch from a phone-side view of size [srcW] x [srcH] onto the projector output. */
    fun forwardTouch(event: MotionEvent, srcW: Int, srcH: Int) {
        if (!::view.isInitialized || srcW == 0 || srcH == 0 || view.width == 0) return
        val copy = MotionEvent.obtain(event)
        scale.setScale(view.width.toFloat() / srcW, view.height.toFloat() / srcH)
        copy.transform(scale)
        view.dispatchTouchEvent(copy)
        copy.recycle()
    }
}
