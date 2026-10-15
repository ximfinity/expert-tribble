package com.phantomfloor.engine

import com.phantomfloor.audio.SoundSink

/** Everything a scene needs for one frame. Owned and mutated only by the GL thread. */
class FrameState {
    var time = 0f
    var dt = 0f
    var nanoTime = 0L

    /** Canvas aspect ratio (width / height). Scenes work in "world" units: x in [0, aspect], y in [0, 1]. */
    var aspect = 16f / 9f

    val people = ArrayList<PersonPoint>(MAX_PEOPLE)

    /** Packed `vec4 uPeople[MAX_PEOPLE]`: (x, y, vx, vy) in UV. */
    val peopleUniform = FloatArray(MAX_PEOPLE * 4)
    val peopleCount get() = people.size

    // Audio (already gain-adjusted, 0..~1)
    var level = 0f
    var bass = 0f
    var mid = 0f
    var treble = 0f

    /** 1.0 right on a detected beat / loud onset, decays to 0. */
    var beat = 0f

    /** True only on the frame a new beat was detected. */
    var beatTriggered = false

    /** Sound effects for this frame's events. */
    var sound: SoundSink = SoundSink.None

    fun setPeople(points: List<PersonPoint>) {
        people.clear()
        for (p in points) {
            if (people.size >= MAX_PEOPLE) break
            people.add(p)
        }
        for (i in 0 until MAX_PEOPLE) {
            val p = people.getOrNull(i)
            peopleUniform[i * 4] = p?.x ?: -10f
            peopleUniform[i * 4 + 1] = p?.y ?: -10f
            peopleUniform[i * 4 + 2] = p?.vx ?: 0f
            peopleUniform[i * 4 + 3] = p?.vy ?: 0f
        }
    }

    companion object {
        const val MAX_PEOPLE = 8
    }
}
