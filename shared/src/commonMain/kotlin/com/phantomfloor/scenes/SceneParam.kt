package com.phantomfloor.scenes


import kotlin.concurrent.Volatile
/** A user-tweakable scene variable, rendered as a slider in the control panel. */
class SceneParam(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val default: Float,
    /** 0 = continuous. Otherwise the slider snaps to this step. */
    val step: Float = 0f,
) {
    @Volatile
    var value: Float = default
        set(v) { field = v.coerceIn(min, max) }

    val int: Int get() = value.toInt()
}
