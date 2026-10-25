package com.phantomfloor.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.tanh
import org.junit.Test

/**
 * Writes every effect to app/build/sfx/<name>.wav so sounds can be auditioned without a phone.
 * Uses the same soft clip as the live engine.
 */
class SfxWavExport {
    @Test
    fun exportAll() {
        val out = File("build/sfx").apply { mkdirs() }
        for (sfx in Sfx.values()) {
            val (left, _) = renderOffline(sfx, 3f)
            var end = left.size
            while (end > 1 && abs(left[end - 1]) < 1e-4f) end--
            val pcm = ByteBuffer.allocate(44 + end * 2).order(ByteOrder.LITTLE_ENDIAN)
            pcm.put("RIFF".toByteArray()).putInt(36 + end * 2).put("WAVE".toByteArray())
            pcm.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
            pcm.put("data".toByteArray()).putInt(end * 2)
            for (i in 0 until end) pcm.putShort((tanh(left[i] * 0.9f) * 32000f).toInt().toShort())
            File(out, "${sfx.name.lowercase()}.wav").writeBytes(pcm.array())
        }
    }
}
