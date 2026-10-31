package com.phantomfloor.web

import com.phantomfloor.engine.EngineSettings
import com.phantomfloor.engine.Keystone
import com.phantomfloor.engine.PhantomEngine
import com.phantomfloor.engine.TouchPeople
import com.phantomfloor.gl.GLES30
import com.phantomfloor.util.nanoTime
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.Promise
import kotlin.math.hypot

/**
 * PhantomFloor in the browser: the shared engine on WebGL2, the mouse and fingers as feet,
 * Web Audio for sound, an HTML control panel, and settings kept in localStorage.
 */
fun main() {
    if (document.readyState.toString() == "loading") {
        document.addEventListener("DOMContentLoaded", { boot() })
    } else {
        boot()
    }
}

private fun boot() {
    val status = document.getElementById("loading") as HTMLElement?
    loadShaders().then { shaders ->
        status?.style?.display = "none"
        try {
            WebApp(shaders).start()
        } catch (t: Throwable) {
            showError(t.message ?: t.toString())
        }
    }.catch { e -> showError("Couldn't load the shaders: ${e.message}") }
}

/** Fetches every shader listed in shaders/manifest.json (written by the build). */
private fun loadShaders(): Promise<Map<String, String>> =
    window.fetch("shaders/manifest.json").then { it.json() }.then { list ->
        val names = (list.unsafeCast<Array<String>>()).toList()
        Promise.all(names.map { n -> window.fetch("shaders/$n").then { it.text() } }.toTypedArray())
            .then { texts -> names.zip(texts.toList()).toMap() }
    }.unsafeCast<Promise<Map<String, String>>>()

fun showError(message: String) {
    val box = document.getElementById("error") as HTMLElement? ?: return
    box.textContent = message
    box.style.display = "block"
}

class WebApp(private val shaders: Map<String, String>) {

    private val canvas = document.getElementById("floor") as HTMLCanvasElement
    private val storage = WebStorage()
    private val settings = EngineSettings().apply { audioReactive = false }
    private val keystone = Keystone()
    private val touches = TouchPeople()
    private val sound = WebSound()
    private var gl: dynamic = null
    private lateinit var engine: PhantomEngine
    private lateinit var panel: WebPanel
    private var contextLost = false
    /** The mouse counts as a person while it moves over the floor, even without a click. */
    var mouseIsPerson = true

    fun start() {
        val attrs: dynamic = js("({})")
        attrs.antialias = false
        attrs.alpha = false
        attrs.depth = false
        attrs.powerPreference = "high-performance"
        gl = canvas.getContext("webgl2", attrs)
            ?: throw IllegalStateException("This browser doesn't support WebGL2. Try a recent Chrome, Edge, Firefox or Safari.")
        GLES30.backend = WebGlBackend(gl)

        storage.loadSettings(settings)
        storage.loadKeystone()?.let(keystone::setAll)
        mouseIsPerson = storage.mouseIsPerson

        engine = PhantomEngine(keystone, settings, touches, sound, window.devicePixelRatio.toFloat())
        engine.onError = { id, t -> console.error("Scene $id failed", t) }
        storage.loadParams(engine.scenes)
        val sceneIndex = engine.scenes.indexOfFirst { it.id == storage.sceneId }.coerceAtLeast(0)
        engine.requestScene(sceneIndex)
        engine.onSurfaceCreated { name -> shaders[name] ?: throw IllegalStateException("Missing shader $name") }
        resize()

        panel = WebPanel(engine.scenes, settings, storage, this)
        panel.build(sceneIndex)
        applySound()

        window.addEventListener("resize", { resize() })
        canvas.addEventListener("webglcontextlost", { e -> e.preventDefault(); contextLost = true })
        canvas.addEventListener("webglcontextrestored", {
            GLES30.backend = WebGlBackend(gl)
            engine.onSurfaceCreated { name -> shaders[name]!! }
            resize()
            contextLost = false
        })
        bindPointer()
        bindKeys()
        bindStartOverlay()
        window.requestAnimationFrame { frame() }
    }

    private fun frame() {
        if (!contextLost) {
            try {
                engine.drawFrame(nanoTime())
            } catch (t: Throwable) {
                console.error(t)
            }
            panel.updateStatus(engine.fps, engine.lastError)
        }
        window.requestAnimationFrame { frame() }
    }

    /** Backing store follows the window; capped at 2x so 4K laptops don't melt. */
    fun resize() {
        val dpr = minOf(window.devicePixelRatio, 2.0)
        val w = (canvas.clientWidth * dpr).toInt().coerceAtLeast(1)
        val h = (canvas.clientHeight * dpr).toInt().coerceAtLeast(1)
        if (canvas.width != w || canvas.height != h) {
            canvas.width = w
            canvas.height = h
        }
        engine.onSurfaceChanged(w, h)
    }

    fun selectScene(index: Int) {
        engine.requestScene(index)
        storage.sceneId = engine.scenes[index].id
    }

    fun applySound() {
        sound.mixer.enabled = settings.soundEffects
        sound.mixer.volume = settings.soundVolume
    }

    fun resetKeystone() {
        keystone.reset()
        storage.saveKeystone(keystone.corners())
    }

    fun setKeystoneEdit(on: Boolean) {
        settings.keystoneEdit = on
        touches.clear()
        document.body?.classList?.toggle("editing", on)
        if (!on) storage.saveKeystone(keystone.corners())
    }

    fun toggleFullscreen() {
        val d: dynamic = document
        if (d.fullscreenElement != null) d.exitFullscreen() else (document.documentElement.asDynamic()).requestFullscreen()
    }

    // --- Input ----------------------------------------------------------------------------------

    private val dragCorner = HashMap<Int, Int>()
    private val dragOffset = HashMap<Int, FloatArray>()
    private val tmp = FloatArray(2)

    private fun ndc(e: dynamic): Pair<Float, Float> {
        val r = canvas.getBoundingClientRect()
        val x = ((e.clientX as Double) - r.left) / r.width * 2.0 - 1.0
        val y = 1.0 - ((e.clientY as Double) - r.top) / r.height * 2.0
        return x.toFloat() to y.toFloat()
    }

    private fun bindPointer() {
        canvas.addEventListener("pointerdown", { ev ->
            val e: dynamic = ev
            panel.poke()
            if (settings.keystoneEdit) {
                grabCorner(e)
            } else {
                canvas.asDynamic().setPointerCapture(e.pointerId)
                movePerson(e)
            }
        })
        canvas.addEventListener("pointermove", { ev ->
            val e: dynamic = ev
            panel.poke()
            val id = (e.pointerId as Number).toInt()
            if (settings.keystoneEdit) {
                val corner = dragCorner[id] ?: return@addEventListener
                val off = dragOffset[id] ?: return@addEventListener
                val (x, y) = ndc(e)
                keystone.setCorner(corner, x + off[0], y + off[1])
                return@addEventListener
            }
            val pressed = (e.buttons as Number).toInt() != 0
            val mouse = e.pointerType == "mouse"
            if (pressed || (mouse && mouseIsPerson)) movePerson(e)
        })
        val release = { ev: dynamic ->
            val id = (ev.pointerId as Number).toInt()
            if (dragCorner.remove(id) != null) {
                dragOffset.remove(id)
                storage.saveKeystone(keystone.corners())
            }
            // A mouse keeps walking after the button is released; fingers lift off the floor.
            if (ev.pointerType != "mouse" || !mouseIsPerson) touches.remove(id)
        }
        canvas.addEventListener("pointerup", { release(it) })
        canvas.addEventListener("pointercancel", { release(it) })
        canvas.addEventListener("pointerleave", { ev -> touches.remove((ev.asDynamic().pointerId as Number).toInt()) })
    }

    private fun movePerson(e: dynamic) {
        if (!settings.touchAsPeople) return
        val id = (e.pointerId as Number).toInt()
        val (x, y) = ndc(e)
        if (keystone.screenToCanvas(x, y, tmp)) touches.update(id, tmp[0], tmp[1], nanoTime()) else touches.remove(id)
    }

    private fun grabCorner(e: dynamic) {
        val r = canvas.getBoundingClientRect()
        val px = (e.clientX as Double) - r.left
        val py = (e.clientY as Double) - r.top
        val c = keystone.corners()
        var best = -1
        var bestDist = 70.0
        for (i in 0 until 4) {
            if (dragCorner.containsValue(i)) continue
            val sx = (c[i * 2] + 1.0) * 0.5 * r.width
            val sy = (1.0 - c[i * 2 + 1]) * 0.5 * r.height
            val d = hypot(sx - px, sy - py)
            if (d < bestDist) { bestDist = d; best = i }
        }
        if (best < 0) return
        val id = (e.pointerId as Number).toInt()
        canvas.asDynamic().setPointerCapture(e.pointerId)
        val (x, y) = ndc(e)
        dragCorner[id] = best
        dragOffset[id] = floatArrayOf(c[best * 2] - x, c[best * 2 + 1] - y)
    }

    private fun bindKeys() {
        document.addEventListener("keydown", { ev ->
            val e = ev as KeyboardEvent
            if (e.target.asDynamic().tagName == "INPUT") return@addEventListener
            when (e.key) {
                " ", "m", "M" -> { panel.toggle(); e.preventDefault() }
                "f", "F" -> toggleFullscreen()
                "k", "K" -> setKeystoneEdit(!settings.keystoneEdit).also { panel.syncToggles() }
                "Escape" -> if (settings.keystoneEdit) { setKeystoneEdit(false); panel.syncToggles() } else panel.hide()
                "ArrowRight" -> panel.step(1)
                "ArrowLeft" -> panel.step(-1)
            }
        })
    }

    /** Browsers start audio (and allow fullscreen and wake lock) only after a click. */
    private fun bindStartOverlay() {
        val overlay = document.getElementById("start") as HTMLElement? ?: return
        overlay.addEventListener("click", {
            overlay.style.display = "none"
            sound.start()
            requestWakeLock()
            panel.poke()
        })
    }

    private fun requestWakeLock() {
        val nav: dynamic = window.navigator
        if (nav.wakeLock == null) return
        nav.wakeLock.request("screen").catch { _: dynamic -> }
        document.addEventListener("visibilitychange", {
            if (document.asDynamic().visibilityState == "visible") nav.wakeLock.request("screen").catch { _: dynamic -> }
        })
    }
}
