package com.phantomfloor.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log

/**
 * Plays the shared [SoundMixer] through an [AudioTrack] on a dedicated audio thread. Scenes call
 * [play] from the GL thread; the active scene's [Ambience] crossfades in smoothly.
 */
class SoundEngine : SoundOutput {

    val mixer = SoundMixer()

    var enabled: Boolean
        get() = mixer.enabled
        set(v) { mixer.enabled = v }
    var volume: Float
        get() = mixer.volume
        set(v) { mixer.volume = v }
    var onLoudSound: (() -> Unit)?
        get() = mixer.onLoudSound
        set(v) { mixer.onLoudSound = v }

    @Volatile private var running = false
    private var thread: Thread? = null

    override fun play(sfx: Sfx, volume: Float, pan: Float) {
        if (running) mixer.play(sfx, volume, pan)
    }

    override fun setAmbience(ambience: Ambience) = mixer.setAmbience(ambience)

    fun start() {
        if (running) return
        running = true
        thread = Thread({ run() }, "phantom-audio").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        mixer.reset()
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuf, BLOCK * 4 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            Log.w(TAG, "AudioTrack unavailable", t)
            running = false
            return
        }

        val left = FloatArray(BLOCK)
        val right = FloatArray(BLOCK)
        val pcm = ShortArray(BLOCK * 2)
        track.play()
        try {
            while (running) {
                mixer.render(left, right, BLOCK)
                for (i in 0 until BLOCK) {
                    pcm[i * 2] = (left[i] * 32000f).toInt().toShort()
                    pcm[i * 2 + 1] = (right[i] * 32000f).toInt().toShort()
                }
                track.write(pcm, 0, pcm.size)
            }
        } finally {
            track.stop()
            track.release()
        }
    }

    companion object {
        private const val TAG = "SoundEngine"
        private const val BLOCK = 512
    }
}
