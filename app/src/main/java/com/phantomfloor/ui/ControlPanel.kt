package com.phantomfloor.ui

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import com.phantomfloor.databinding.ActivityMainBinding
import com.phantomfloor.engine.EngineSettings
import com.phantomfloor.scenes.Scene
import com.phantomfloor.scenes.SceneParam
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The translucent control overlay. Builds scene chips and parameter sliders from each scene's
 * [SceneParam] list, so adding a parameter to a scene adds a slider automatically.
 * Slider writes go straight into @Volatile fields the GL thread reads every frame.
 */
class ControlPanel(
    private val binding: ActivityMainBinding,
    private val scenes: List<Scene>,
    private val settings: EngineSettings,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onSceneSelected(index: Int)
        fun onParamChanged(scene: Scene, param: SceneParam)
        fun onSettingsChanged()
    }

    private val context = binding.root.context
    private var currentScene = 0

    val isVisible get() = binding.controlPanel.visibility == View.VISIBLE

    fun build(selectedScene: Int) {
        currentScene = selectedScene
        binding.sceneGroup.removeAllViews()
        scenes.forEachIndexed { i, scene ->
            val chip = Chip(context).apply {
                id = View.generateViewId()
                text = scene.title
                isCheckable = true
                isCheckedIconVisible = true
                isChecked = i == selectedScene
                setOnClickListener {
                    if (currentScene != i) {
                        currentScene = i
                        callbacks.onSceneSelected(i)
                        buildParams()
                    }
                }
            }
            binding.sceneGroup.addView(chip)
        }
        buildParams()

        binding.projectionContainer.removeAllViews()
        addSlider(binding.projectionContainer, "Brightness", 0.1f, 1.5f, 0f, settings.brightness) {
            settings.brightness = it; callbacks.onSettingsChanged()
        }
        addSlider(binding.projectionContainer, "Render quality", 0.5f, 1f, 0f, settings.quality) {
            settings.quality = it; callbacks.onSettingsChanged()
        }
        addSlider(binding.projectionContainer, "Edge feather", 0f, 0.15f, 0f, settings.edgeFeather) {
            settings.edgeFeather = it; callbacks.onSettingsChanged()
        }

        binding.audioContainer.removeAllViews()
        addSlider(binding.audioContainer, "Audio sensitivity", 0f, 3f, 0f, settings.audioGain) {
            settings.audioGain = it; callbacks.onSettingsChanged()
        }
    }

    private fun buildParams() {
        val container = binding.paramContainer
        container.removeAllViews()
        val scene = scenes[currentScene]
        for (p in scene.params) {
            addSlider(container, p.label, p.min, p.max, p.step, p.value) {
                p.value = it
                callbacks.onParamChanged(scene, p)
            }
        }
    }

    private fun addSlider(
        container: LinearLayout,
        label: String,
        min: Float,
        max: Float,
        step: Float,
        initial: Float,
        onChange: (Float) -> Unit,
    ) {
        val text = TextView(context).apply {
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 13f
            setPadding(0, 12, 0, 0)
        }
        fun format(v: Float) = if (step >= 1f) "$label: ${v.roundToInt()}" else "$label: ${String.format(Locale.US, "%.2f", v)}"
        val slider = Slider(context).apply {
            valueFrom = min
            valueTo = max
            if (step > 0f) stepSize = step
            value = snap(initial, min, max, step)
            addOnChangeListener { _, v, fromUser ->
                text.text = format(v)
                if (fromUser) onChange(v)
            }
        }
        text.text = format(slider.value)
        container.addView(text)
        container.addView(slider)
    }

    private fun snap(v: Float, min: Float, max: Float, step: Float): Float {
        val c = v.coerceIn(min, max)
        if (step <= 0f) return c
        return (min + ((c - min) / step).roundToInt() * step).coerceIn(min, max)
    }

    fun show() {
        binding.controlPanel.visibility = View.VISIBLE
        binding.controlPanel.alpha = 0f
        binding.controlPanel.animate().alpha(1f).setDuration(180).start()
    }

    fun hide() {
        binding.controlPanel.animate().alpha(0f).setDuration(180).withEndAction {
            binding.controlPanel.visibility = View.GONE
        }.start()
    }
}
