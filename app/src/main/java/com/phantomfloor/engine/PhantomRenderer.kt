package com.phantomfloor.engine

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import com.phantomfloor.audio.AudioAnalyzer
import com.phantomfloor.audio.SoundEngine
import com.phantomfloor.gl.AndroidGlBackend
import com.phantomfloor.gl.GLES30
import com.phantomfloor.scenes.Scene
import com.phantomfloor.vision.FloorCalibrator
import com.phantomfloor.vision.PersonTracker
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Runs the shared [PhantomEngine] frame loop on Android's GL thread, with OpenGL ES as the GL
 * backend, shaders from `assets/shaders/`, camera tracks from [PersonTracker], the microphone
 * analyzer, and the floor calibrator.
 */
class PhantomRenderer(
    private val context: Context,
    keystone: Keystone,
    settings: EngineSettings,
    tracker: PersonTracker,
    touches: TouchPeople,
    audio: AudioAnalyzer,
    calibrator: FloorCalibrator,
    sound: SoundEngine,
) : GLSurfaceView.Renderer {

    private val engine = PhantomEngine(keystone, settings, touches, sound, context.resources.displayMetrics.density).apply {
        cameraPeople = { now -> tracker.snapshot(now) }
        audioLevels = { audio.levels }
        calibration = calibrator
        onError = { id, t -> Log.e(TAG, "Scene $id failed", t) }
    }

    val scenes: List<Scene> get() = engine.scenes
    val fps get() = engine.fps
    val trackedCount get() = engine.trackedCount
    val lastError get() = engine.lastError

    fun requestScene(index: Int) = engine.requestScene(index)

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES30.backend = AndroidGlBackend
        engine.onSurfaceCreated { name -> context.assets.open("shaders/$name").bufferedReader().use { it.readText() } }
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) = engine.onSurfaceChanged(width, height)

    override fun onDrawFrame(unused: GL10?) = engine.drawFrame(System.nanoTime())

    companion object {
        private const val TAG = "PhantomRenderer"
    }
}
