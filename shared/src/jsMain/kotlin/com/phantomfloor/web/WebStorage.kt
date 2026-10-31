package com.phantomfloor.web

import com.phantomfloor.engine.EngineSettings
import com.phantomfloor.scenes.Scene
import kotlinx.browser.localStorage

/**
 * Remembers the scene, its sliders, settings and keystone between visits (localStorage). Every
 * access is guarded: private windows and blocked storage just mean nothing is remembered.
 */
class WebStorage {

    private fun get(key: String): String? = try { localStorage.getItem("phantomfloor.$key") } catch (_: Throwable) { null }
    private fun set(key: String, value: String) { try { localStorage.setItem("phantomfloor.$key", value) } catch (_: Throwable) {} }

    var sceneId: String?
        get() = get("scene")
        set(v) { if (v != null) set("scene", v) }

    var mouseIsPerson: Boolean
        get() = get("mouse") != "0"
        set(v) = set("mouse", if (v) "1" else "0")

    fun loadSettings(s: EngineSettings) {
        val m = readMap("settings")
        m["brightness"]?.let { s.brightness = it }
        m["quality"]?.let { s.quality = it }
        m["feather"]?.let { s.edgeFeather = it }
        m["showTracking"]?.let { s.showTracking = it > 0.5f }
        m["touchAsPeople"]?.let { s.touchAsPeople = it > 0.5f }
        m["soundEffects"]?.let { s.soundEffects = it > 0.5f }
        m["soundVolume"]?.let { s.soundVolume = it }
    }

    fun saveSettings(s: EngineSettings) = writeMap(
        "settings",
        mapOf(
            "brightness" to s.brightness, "quality" to s.quality, "feather" to s.edgeFeather,
            "showTracking" to if (s.showTracking) 1f else 0f, "touchAsPeople" to if (s.touchAsPeople) 1f else 0f,
            "soundEffects" to if (s.soundEffects) 1f else 0f, "soundVolume" to s.soundVolume,
        ),
    )

    fun loadParams(scenes: List<Scene>) {
        for (scene in scenes) {
            val m = readMap("params.${scene.id}")
            for (p in scene.params) m[p.key]?.let { p.value = it }
        }
    }

    fun saveParams(scene: Scene) = writeMap("params.${scene.id}", scene.params.associate { it.key to it.value })

    fun loadKeystone(): FloatArray? =
        get("keystone")?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 8 }?.toFloatArray()

    fun saveKeystone(c: FloatArray) = set("keystone", c.joinToString(","))

    /** Stored as "key=value;key=value": simple, and never throws on odd input. */
    private fun readMap(key: String): Map<String, Float> =
        get(key)?.split(';')?.mapNotNull { e ->
            val i = e.indexOf('=')
            if (i <= 0) null else e.substring(i + 1).toFloatOrNull()?.let { e.substring(0, i) to it }
        }?.toMap() ?: emptyMap()

    private fun writeMap(key: String, m: Map<String, Float>) = set(key, m.entries.joinToString(";") { "${it.key}=${it.value}" })
}
