package com.phantomfloor.engine

/** Global knobs shared between the UI thread (writer) and the GL thread (reader). */
class EngineSettings {
    @Volatile var brightness = 1f
    /** Multiplier on every scene's render resolution (0.5..1). */
    @Volatile var quality = 1f
    /** Soft fade at the canvas border so the projection edge disappears. */
    @Volatile var edgeFeather = 0.03f
    @Volatile var keystoneEdit = false
    @Volatile var showTracking = false
    @Volatile var touchAsPeople = true
    @Volatile var audioReactive = true
    @Volatile var audioGain = 1f
    @Volatile var transitionSeconds = 2.2f
}
