package com.phantomfloor.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.phantomfloor.databinding.ActivityMainBinding
import com.phantomfloor.engine.EngineSettings
import com.phantomfloor.scenes.Scene
import com.phantomfloor.scenes.SceneCatalog
import com.phantomfloor.scenes.SceneParam
import org.junit.Rule
import org.junit.Test

/**
 * Screenshots of the control panel as users see it (landscape phone), for UI review.
 * Plain test runs write PNGs to app/build/reports/paparazzi/; nothing is compared.
 */
class PanelScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(orientation = ScreenOrientation.LANDSCAPE),
        theme = "Theme.PhantomFloor",
        maxPercentDifference = 100.0,
    )

    private val callbacks = object : ControlPanel.Callbacks {
        override fun onSceneSelected(index: Int) = Unit
        override fun onParamChanged(scene: Scene, param: SceneParam) = Unit
        override fun onSettingsChanged() = Unit
    }

    private fun panel(sceneId: String, setup: ActivityMainBinding.() -> Unit = {}): View = bind(sceneId, setup).root

    private fun bind(sceneId: String, setup: ActivityMainBinding.() -> Unit = {}): ActivityMainBinding {
        val binding = ActivityMainBinding.inflate(LayoutInflater.from(paparazzi.context))
        val scenes = SceneCatalog.create()
        ControlPanel(binding, scenes, EngineSettings(), callbacks).build(scenes.indexOfFirst { it.id == sceneId }.coerceAtLeast(0))
        binding.controlPanel.visibility = View.VISIBLE
        binding.setup()
        return binding
    }

    @Test fun halloweenScene() = paparazzi.snapshot(panel("fog"), "panel_fog")

    /** The whole scrolling panel, one screenful at a time (rows outside each part are hidden). */
    @Test fun fullPanel() {
        val binding = bind("fog")
        val content = (binding.controlPanel as ViewGroup).getChildAt(0) as ViewGroup
        val shown = List(content.childCount) { content.getChildAt(it).visibility }
        var first = 0
        var part = 1
        while (first < content.childCount) {
            for (k in 0 until content.childCount) {
                content.getChildAt(k).visibility = if (k in first until first + ROWS_PER_PART) shown[k] else View.GONE
            }
            paparazzi.snapshot(binding.root, "panel_part$part")
            first += ROWS_PER_PART
            part++
        }
    }

    /** The About & help sheet's content, top and bottom halves. */
    @Test fun aboutPage() {
        val b = com.phantomfloor.databinding.DialogAboutBinding.inflate(LayoutInflater.from(paparazzi.context))
        b.aboutVersion.text = "Version 1.3.0 · build 3"
        b.aboutUpdate.text = "You have the latest version ✓"
        b.root.setBackgroundColor(0xFF1E1E24.toInt())
        paparazzi.snapshot(b.root, "about_top")
        b.root.post { b.root.scrollTo(0, 100_000) }
        (b.root as android.widget.ScrollView).getChildAt(0).let { content ->
            for (k in 0 until 7) (content as ViewGroup).getChildAt(k).visibility = View.GONE
        }
        paparazzi.snapshot(b.root, "about_bottom")
    }

    private companion object {
        const val ROWS_PER_PART = 6
    }

    @Test fun gameScene() = paparazzi.snapshot(panel("game_bomb"), "panel_game_bomb")

    /** Scrolled to the Interaction section (rows above it hidden) so the RTSP field is in view. */
    @Test fun networkCamera() {
        val binding = bind("swarm") {
            networkCameraChip.isChecked = true
            rtspGroup.visibility = View.VISIBLE
        }
        val content = (binding.controlPanel as ViewGroup).getChildAt(0) as ViewGroup
        val first = content.indexOfChild(binding.trackingSwitch) - 1
        for (k in 0 until first) content.getChildAt(k).visibility = View.GONE
        paparazzi.snapshot(binding.root, "panel_network_camera")
    }

    /** A game's own settings (scrolled so the scene chips and sliders are in view). */
    private fun gameParams(sceneId: String, name: String) {
        val binding = bind(sceneId)
        val content = (binding.controlPanel as ViewGroup).getChildAt(0) as ViewGroup
        for (k in 0 until content.indexOfChild(binding.sceneGroup)) content.getChildAt(k).visibility = View.GONE
        paparazzi.snapshot(binding.root, name)
    }

    @Test fun ticTacToeParams() = gameParams("game_ttt", "panel_game_ttt")

    @Test fun coloringParams() = gameParams("game_coloring", "panel_game_coloring")
}
