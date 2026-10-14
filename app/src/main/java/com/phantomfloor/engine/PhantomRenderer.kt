package com.phantomfloor.engine

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import com.phantomfloor.audio.AudioAnalyzer
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.RenderTarget
import com.phantomfloor.gl.ShaderSource
import com.phantomfloor.scenes.Scene
import com.phantomfloor.scenes.SceneCatalog
import com.phantomfloor.vision.FloorCalibrator
import com.phantomfloor.vision.PersonTracker
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp

/**
 * The frame loop, on the GL thread:
 *  1. Gather inputs (camera tracks + touches, audio levels) into [FrameState].
 *  2. Each live scene updates its simulation, then renders into its own offscreen canvas.
 *     During a scene switch, the outgoing scene keeps running so the dissolve is alive.
 *  3. The canvas is drawn to the screen through the keystone homography (warp.vert), with the
 *     ectoplasmic dissolve, edge feather and brightness applied in warp.frag.
 *  4. Screen-space overlays: keystone handles while editing.
 *
 * Everything outside the warped canvas is cleared to pure #000000, so the projector emits no
 * light there.
 */
class PhantomRenderer(
    private val context: Context,
    private val keystone: Keystone,
    private val settings: EngineSettings,
    private val tracker: PersonTracker,
    private val touches: TouchPeople,
    private val audio: AudioAnalyzer,
    private val calibrator: FloorCalibrator,
) : GLSurfaceView.Renderer {

    val scenes: List<Scene> = SceneCatalog.create()

    @Volatile var fps = 0f
        private set
    @Volatile var trackedCount = 0
        private set
    @Volatile var lastError: String? = null
        private set

    @Volatile private var requestedScene = 0
    private var activeScene = -1
    private var previousScene = -1
    private var transitionStart = 0f

    private lateinit var gl: GlResources
    private lateinit var warp: GlProgram
    private lateinit var overlay: GlProgram
    private val targets = arrayOfNulls<RenderTarget>(scenes.size)
    private val failed = BooleanArray(scenes.size)
    /** Scenes compile shaders lazily, on first use in each GL context, so startup stays fast. */
    private val created = BooleanArray(scenes.size)
    private var screenW = 1
    private var screenH = 1
    private var appliedQuality = -1f

    private val frame = FrameState()
    private var lastNanos = 0L
    private var lastBeatCount = 0
    private var fpsFrames = 0
    private var fpsStart = 0L
    private val homography = FloatArray(9)
    private val corners = FloatArray(8)
    private val density = context.resources.displayMetrics.density

    fun requestScene(index: Int) {
        requestedScene = index.coerceIn(0, scenes.lastIndex)
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_DITHER)
        gl = GlResources(ShaderSource(context.assets))
        warp = gl.shaders.program("warp.vert", "warp.frag")
        overlay = gl.shaders.program("overlay.vert", "overlay.frag")
        // New context: every old handle is gone, so forget (don't delete) the old targets and
        // let each scene rebuild itself the next time it is shown.
        targets.fill(null)
        created.fill(false)
        failed.fill(false)
        appliedQuality = -1f
        lastNanos = 0L
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        screenW = width.coerceAtLeast(1)
        screenH = height.coerceAtLeast(1)
        rebuildTargets()
    }

    /** Drops every canvas; [ensureReady] recreates them at the new size/quality on demand. */
    private fun rebuildTargets() {
        appliedQuality = settings.quality
        for (i in scenes.indices) releaseTarget(i)
    }

    private fun releaseTarget(i: Int) {
        targets[i]?.release()
        targets[i] = null
    }

    /** Compiles the scene (once per context) and allocates its canvas (once per size). */
    private fun ensureReady(i: Int) {
        if (i < 0 || failed[i]) return
        val scene = scenes[i]
        try {
            if (!created[i]) {
                scene.onSurfaceCreated(gl)
                created[i] = true
            }
            if (targets[i] == null) {
                val scale = scene.resolutionScale * appliedQuality
                val w = (screenW * scale).toInt().coerceAtLeast(16)
                val h = (screenH * scale).toInt().coerceAtLeast(16)
                targets[i] = RenderTarget.create(w, h)
                scene.onResize(w, h)
            }
        } catch (t: Throwable) {
            fail(i, t)
        }
    }

    private fun fail(i: Int, t: Throwable) {
        failed[i] = true
        lastError = "${scenes[i].title}: ${t.message}"
        Log.e(TAG, "Scene ${scenes[i].id} failed", t)
    }

    override fun onDrawFrame(unused: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = now
        frame.dt = dt
        frame.time += dt
        frame.nanoTime = now
        frame.aspect = screenW.toFloat() / screenH

        if (settings.quality != appliedQuality) rebuildTargets()

        val wanted = requestedScene
        if (wanted != activeScene) {
            previousScene = activeScene
            activeScene = wanted
            transitionStart = frame.time
        }
        val transition = if (previousScene < 0) 1f
        else ((frame.time - transitionStart) / settings.transitionSeconds).coerceIn(0f, 1f)
        if (transition >= 1f && previousScene >= 0) {
            releaseTarget(previousScene) // only live scenes keep a full-size canvas
            previousScene = -1
        }
        ensureReady(activeScene)
        if (previousScene >= 0) ensureReady(previousScene)

        gatherInputs(now)

        val calibrating = calibrator.isActive
        val current = targets[activeScene]
        if (calibrating) {
            drawCalibrationFrame(current)
        } else {
            renderScene(activeScene)
            if (previousScene >= 0) renderScene(previousScene)
            if (settings.showTracking && current != null) drawTrackingMarkers(current)
        }

        // Composite through the keystone warp onto a pure-black screen.
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenW, screenH)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (current != null) {
            keystone.matrixColumnMajor(homography)
            warp.use()
            warp.setMat3("uHomography", homography)
            val prevTex = if (!calibrating && previousScene >= 0) targets[previousScene]?.texture else null
            warp.texture("uCurrent", 0, current.texture)
            warp.texture("uPrevious", 1, prevTex ?: current.texture)
            warp.set1f("uTransition", if (prevTex == null) 1f else transition)
            warp.set1f("uTime", frame.time)
            warp.set1f("uBrightness", if (calibrating) 1f else settings.brightness)
            warp.set1f("uFeather", if (calibrating) 0f else settings.edgeFeather)
            warp.set1f("uEdit", if (settings.keystoneEdit) 1f else 0f)
            gl.drawQuad()
        }
        if (settings.keystoneEdit) drawKeystoneHandles()

        fpsFrames++
        if (now - fpsStart >= 1_000_000_000L) {
            fps = fpsFrames * 1e9f / (now - fpsStart)
            fpsFrames = 0
            fpsStart = now
        }
    }

    private fun renderScene(index: Int) {
        if (index < 0 || failed[index]) return
        val target = targets[index] ?: return
        val scene = scenes[index]
        try {
            scene.update(frame) // may run its own offscreen passes
            target.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            scene.draw(frame)
        } catch (t: Throwable) {
            fail(index, t)
        }
    }

    private fun gatherInputs(now: Long) {
        val people = ArrayList<PersonPoint>(FrameState.MAX_PEOPLE)
        people.addAll(tracker.snapshot(now))
        trackedCount = people.size
        if (settings.touchAsPeople) people.addAll(touches.snapshot(now))
        frame.setPeople(people)

        if (settings.audioReactive) {
            val a = audio.levels
            val g = settings.audioGain
            frame.level = (a.level * g).coerceAtMost(1.5f)
            frame.bass = (a.bass * g).coerceAtMost(1.5f)
            frame.mid = (a.mid * g).coerceAtMost(1.5f)
            frame.treble = (a.treble * g).coerceAtMost(1.5f)
            frame.beat = if (a.lastBeatNanos == 0L) 0f else exp(-(now - a.lastBeatNanos) / 2.5e8f)
            frame.beatTriggered = a.beatCount != lastBeatCount
            lastBeatCount = a.beatCount
        } else {
            frame.level = 0f; frame.bass = 0f; frame.mid = 0f; frame.treble = 0f
            frame.beat = 0f; frame.beatTriggered = false
        }
    }

    private fun drawCalibrationFrame(target: RenderTarget?) {
        target ?: return
        target.bind()
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val i = calibrator.markerIndex
        if (i < 0) return
        val u = FloorCalibrator.TARGETS[i * 2]
        val v = FloorCalibrator.TARGETS[i * 2 + 1]
        val r = 0.06f
        drawCircle(u * 2f - 1f, v * 2f - 1f, r / frame.aspect * 2f, r * 2f, 1f, 1f, 1f, 1f, 0f)
    }

    private fun drawTrackingMarkers(target: RenderTarget) {
        target.bind()
        val r = 0.035f
        for (p in frame.people) {
            val camera = p.id >= 0
            drawCircle(
                p.x * 2f - 1f, p.y * 2f - 1f, r / frame.aspect * 2f, r * 2f,
                if (camera) 0.3f else 1f, if (camera) 1f else 0.6f, if (camera) 0.5f else 0.2f, 0.9f, 0.18f,
            )
        }
    }

    private fun drawKeystoneHandles() {
        val c = keystone.corners()
        c.copyInto(corners)
        val rx = 28f * density / screenW * 2f
        val ry = 28f * density / screenH * 2f
        for (i in 0 until 4) {
            drawCircle(corners[i * 2], corners[i * 2 + 1], rx, ry, 0.3f, 0.9f, 1f, 0.95f, 0.22f)
            drawCircle(corners[i * 2], corners[i * 2 + 1], rx * 0.25f, ry * 0.25f, 1f, 1f, 1f, 1f, 0f)
        }
    }

    private fun drawCircle(cx: Float, cy: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, a: Float, ring: Float) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        overlay.use()
        overlay.set2f("uCenter", cx, cy)
        overlay.set2f("uRadius", rx, ry)
        overlay.set4f("uColor", r, g, b, a)
        overlay.set1f("uRing", ring)
        gl.drawQuad()
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    companion object {
        private const val TAG = "PhantomRenderer"
    }
}
