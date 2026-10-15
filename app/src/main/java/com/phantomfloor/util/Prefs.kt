package com.phantomfloor.util

import android.content.Context
import androidx.core.content.edit
import com.phantomfloor.engine.EngineSettings
import com.phantomfloor.scenes.Scene

/** Persists keystone, calibration, scene choice, parameters and toggles across launches. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("phantomfloor", Context.MODE_PRIVATE)

    /** Saved by id, not list position, so adding scenes never changes which one reopens. */
    var sceneId: String?
        get() = sp.getString("sceneId", null)
        set(v) = sp.edit { putString("sceneId", v) }

    var trackingEnabled: Boolean
        get() = sp.getBoolean("tracking", true)
        set(v) = sp.edit { putBoolean("tracking", v) }

    var trackingBodies: Boolean
        get() = sp.getBoolean("trackingBodies", false)
        set(v) = sp.edit { putBoolean("trackingBodies", v) }

    /** Tracking camera: the phone's own, or a network camera at [rtspUrl]. */
    var networkCamera: Boolean
        get() = sp.getBoolean("networkCamera", false)
        set(v) = sp.edit { putBoolean("networkCamera", v) }

    var rtspUrl: String
        get() = sp.getString("rtspUrl", "") ?: ""
        set(v) = sp.edit { putString("rtspUrl", v) }

    var firstRun: Boolean
        get() = sp.getBoolean("firstRun", true)
        set(v) = sp.edit { putBoolean("firstRun", v) }

    fun loadKeystone(): FloatArray? = floats("keystone", 8)
    fun saveKeystone(c: FloatArray) = putFloats("keystone", c)

    /** Camera-to-floor mapping, kept separately for the phone camera and the network camera. */
    fun loadCalibration(network: Boolean): FloatArray? = floats(calibrationKey(network), 9)
    fun saveCalibration(network: Boolean, m: FloatArray?) {
        val key = calibrationKey(network)
        if (m == null) sp.edit { remove(key) } else putFloats(key, m)
    }
    private fun calibrationKey(network: Boolean) = if (network) "calibrationNet" else "calibration"

    fun loadSettings(s: EngineSettings) {
        s.brightness = sp.getFloat("brightness", s.brightness)
        s.quality = sp.getFloat("quality", s.quality)
        s.edgeFeather = sp.getFloat("feather", s.edgeFeather)
        s.showTracking = sp.getBoolean("showTracking", s.showTracking)
        s.touchAsPeople = sp.getBoolean("touchAsPeople", s.touchAsPeople)
        s.audioReactive = sp.getBoolean("audio", s.audioReactive)
        s.audioGain = sp.getFloat("audioGain", s.audioGain)
        s.soundEffects = sp.getBoolean("soundEffects", s.soundEffects)
        s.soundVolume = sp.getFloat("soundVolume", s.soundVolume)
    }

    fun saveSettings(s: EngineSettings) = sp.edit {
        putFloat("brightness", s.brightness)
        putFloat("quality", s.quality)
        putFloat("feather", s.edgeFeather)
        putBoolean("showTracking", s.showTracking)
        putBoolean("touchAsPeople", s.touchAsPeople)
        putBoolean("audio", s.audioReactive)
        putFloat("audioGain", s.audioGain)
        putBoolean("soundEffects", s.soundEffects)
        putFloat("soundVolume", s.soundVolume)
    }

    fun loadParams(scenes: List<Scene>) {
        for (scene in scenes) for (p in scene.params) {
            p.value = sp.getFloat("param.${scene.id}.${p.key}", p.default)
        }
    }

    fun saveParam(scene: Scene, key: String, value: Float) = sp.edit { putFloat("param.${scene.id}.$key", value) }

    private fun floats(key: String, n: Int): FloatArray? {
        val parts = sp.getString(key, null)?.split(',') ?: return null
        if (parts.size != n) return null
        return FloatArray(n) { parts[it].toFloatOrNull() ?: return null }
    }

    private fun putFloats(key: String, v: FloatArray) = sp.edit { putString(key, v.joinToString(",")) }
}
