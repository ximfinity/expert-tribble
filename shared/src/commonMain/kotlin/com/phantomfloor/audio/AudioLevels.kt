package com.phantomfloor.audio

/** Snapshot of the room's sound, normalized to roughly 0..1 by an adaptive gain. */
data class AudioLevels(
    val level: Float = 0f,
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val beatCount: Int = 0,
    val lastBeatNanos: Long = 0L,
)
