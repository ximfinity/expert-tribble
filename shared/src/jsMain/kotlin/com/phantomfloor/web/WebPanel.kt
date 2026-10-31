package com.phantomfloor.web

import com.phantomfloor.engine.EngineSettings
import com.phantomfloor.scenes.Scene
import com.phantomfloor.scenes.Season
import com.phantomfloor.util.fmt
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.math.roundToInt

/**
 * The browser control panel, styled like the Android one: coloured category tabs, the chosen
 * category's animations in a card tinted with its colour, sliders built from each scene's
 * params, then projection, interaction and sound settings. A menu button (and Space) opens it;
 * the button fades away when the mouse is still, so nothing glows on the projection.
 */
class WebPanel(
    private val scenes: List<Scene>,
    private val settings: EngineSettings,
    private val storage: WebStorage,
    private val app: WebApp,
) {
    private val root = document.getElementById("panel") as HTMLElement
    private val menuButton = document.getElementById("menu") as HTMLElement
    private lateinit var tabs: HTMLElement
    private lateinit var card: HTMLElement
    private lateinit var cardTitle: HTMLElement
    private lateinit var sceneList: HTMLElement
    private lateinit var params: HTMLElement
    private lateinit var status: HTMLElement
    private lateinit var keystoneButton: HTMLButtonElement
    private var current = 0
    private var shownSeason = Season.HALLOWEEN
    private var idleTimer = 0

    val isOpen get() = root.classList.contains("open")

    fun build(selected: Int) {
        current = selected
        root.innerHTML = ""
        val header = el("div", "panel-header")
        header.append(el("h1", text = "PhantomFloor"))
        header.append(button("✕", "icon") { hide() })
        root.append(header)
        status = el("div", "status")
        root.append(status)

        root.append(el("div", "section", "Category"))
        tabs = el("div", "tabs")
        root.append(tabs)
        card = el("div", "card")
        cardTitle = el("div", "card-title")
        sceneList = el("div", "scenes")
        params = el("div", "params")
        card.append(cardTitle, sceneList, params)
        root.append(card)
        buildTabs(scenes[selected].season)

        root.append(el("div", "section", "Projection"))
        val row = el("div", "row")
        row.append(button("⛶ Fullscreen (F)", "tonal") { app.toggleFullscreen() })
        keystoneButton = button("Edit keystone (K)", "tonal") { app.setKeystoneEdit(!settings.keystoneEdit); syncToggles() }
        row.append(keystoneButton)
        row.append(button("Reset", "outline") { app.resetKeystone() })
        root.append(row)
        root.append(slider("Brightness", 0.1f, 1.5f, 0f, settings.brightness) { settings.brightness = it; save() })
        root.append(slider("Render quality", 0.5f, 1f, 0f, settings.quality) { settings.quality = it; save() })
        root.append(slider("Edge feather", 0f, 0.15f, 0f, settings.edgeFeather) { settings.edgeFeather = it; save() })

        root.append(el("div", "section", "Interaction"))
        root.append(toggle("Mouse acts as a person (no click needed)", app.mouseIsPerson) { app.mouseIsPerson = it; storage.mouseIsPerson = it })
        root.append(toggle("Touch and click act as people", settings.touchAsPeople) { settings.touchAsPeople = it; save() })
        root.append(toggle("Show tracked points", settings.showTracking) { settings.showTracking = it; save() })

        root.append(el("div", "section", "Sound"))
        root.append(toggle("Sound effects", settings.soundEffects) { settings.soundEffects = it; app.applySound(); save() })
        root.append(slider("Effects volume", 0f, 1f, 0f, settings.soundVolume) { settings.soundVolume = it; app.applySound(); save() })

        root.append(el("div", "section", "About"))
        val about = el("div", "about")
        about.innerHTML = """
            Every frame and every sound is generated live. Point a projector at the floor, go
            fullscreen, and walk on it, or use your mouse or fingers as feet.<br><br>
            <b>Keys:</b> Space menu · F fullscreen · K keystone · ←/→ change animation<br><br>
            Camera tracking is in the <a href="https://github.com/ximfinity/expert-tribble/releases/latest" target="_blank" rel="noopener">Android app</a>.
            <a href="https://ximfinity.itch.io/phantom-floor-interactive-projector-floor-app" target="_blank" rel="noopener">♥ Support PhantomFloor on itch.io</a>
        """.trimIndent()
        root.append(about)

        menuButton.addEventListener("click", { toggle() })
        poke()
    }

    private fun buildTabs(selected: Season) {
        tabs.innerHTML = ""
        for (season in scenes.map { it.season }.distinct()) {
            val tab = button("${season.icon} ${season.label}", "tab") { buildTabs(season) }
            val color = css(season.color)
            if (season == selected) {
                tab.classList.add("active")
                tab.style.background = color
            }
            tab.style.setProperty("--accent", color)
            tabs.append(tab)
        }
        showSeason(selected)
    }

    private fun showSeason(season: Season) {
        shownSeason = season
        val color = css(season.color)
        card.style.setProperty("--accent", color)
        cardTitle.textContent = "Animation · ${season.label}"
        sceneList.innerHTML = ""
        scenes.forEachIndexed { i, scene ->
            if (scene.season != season) return@forEachIndexed
            val b = button(scene.title, "scene") { select(i) }
            if (i == current) b.classList.add("active")
            sceneList.append(b)
        }
        buildParams()
    }

    private fun select(i: Int) {
        current = i
        app.selectScene(i)
        if (scenes[i].season != shownSeason) buildTabs(scenes[i].season) else showSeason(shownSeason)
    }

    /** Next/previous animation across every category (arrow keys). */
    fun step(delta: Int) = select((current + delta + scenes.size) % scenes.size)

    private fun buildParams() {
        params.innerHTML = ""
        val scene = scenes[current]
        if (scene.season != shownSeason) return
        for (p in scene.params) {
            params.append(slider(p.label, p.min, p.max, p.step, p.value) { p.value = it; storage.saveParams(scene) })
        }
    }

    fun syncToggles() {
        keystoneButton.textContent = if (settings.keystoneEdit) "✓ Done editing (K)" else "Edit keystone (K)"
    }

    fun updateStatus(fps: Float, error: String?) {
        if (!isOpen) return
        status.textContent = "${fmt(fps, 0)} fps" + (error?.let { " · ⚠ $it" } ?: "")
    }

    fun toggle() = if (isOpen) hide() else show()

    fun show() {
        root.classList.add("open")
        poke()
    }

    fun hide() {
        root.classList.remove("open")
    }

    /** Shows the menu button for a few seconds after any activity. */
    fun poke() {
        menuButton.classList.add("visible")
        window.clearTimeout(idleTimer)
        idleTimer = window.setTimeout({ menuButton.classList.remove("visible") }, 3000)
    }

    private fun save() = storage.saveSettings(settings)

    // --- DOM helpers ----------------------------------------------------------------------------

    private fun el(tag: String, cls: String? = null, text: String? = null): HTMLElement {
        val e = document.createElement(tag) as HTMLElement
        if (cls != null) e.className = cls
        if (text != null) e.textContent = text
        return e
    }

    private fun button(text: String, cls: String, onClick: () -> Unit): HTMLButtonElement {
        val b = document.createElement("button") as HTMLButtonElement
        b.className = cls
        b.textContent = text
        b.addEventListener("click", { onClick() })
        return b
    }

    private fun slider(label: String, min: Float, max: Float, step: Float, initial: Float, onChange: (Float) -> Unit): HTMLElement {
        val wrap = el("label", "slider")
        val text = el("span")
        val input = document.createElement("input") as HTMLInputElement
        input.type = "range"
        input.min = min.toString()
        input.max = max.toString()
        input.step = if (step > 0f) step.toString() else ((max - min) / 200f).toString()
        input.value = initial.toString()
        fun format(v: Float) = if (step >= 1f) "$label: ${v.roundToInt()}" else "$label: ${fmt(v, 2)}"
        text.textContent = format(initial)
        input.addEventListener("input", {
            val v = input.value.toFloat()
            text.textContent = format(v)
            onChange(v)
        })
        wrap.append(text, input)
        return wrap
    }

    private fun toggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit): HTMLElement {
        val wrap = el("label", "toggle")
        val input = document.createElement("input") as HTMLInputElement
        input.type = "checkbox"
        input.checked = initial
        input.addEventListener("change", { onChange(input.checked) })
        wrap.append(el("span", text = label), input)
        return wrap
    }

    private fun css(argb: Int): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')
}
