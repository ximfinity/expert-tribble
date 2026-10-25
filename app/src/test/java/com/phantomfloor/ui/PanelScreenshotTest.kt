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

    private companion object {
        const val ROWS_PER_PART = 9
    }

    @Test fun gameScene() = paparazzi.snapshot(panel("game_bomb"), "panel_game_bomb")

    @Test fun networkCamera() = paparazzi.snapshot(
        panel("swarm") {
            networkCameraChip.isChecked = true
            rtspGroup.visibility = View.VISIBLE
        },
        "panel_network_camera",
    )
}
