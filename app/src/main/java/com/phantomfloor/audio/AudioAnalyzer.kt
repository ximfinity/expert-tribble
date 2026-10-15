package com.phantomfloor.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/** Snapshot of the room's sound, normalized to roughly 0..1 by an adaptive gain. */
data class AudioLevels(
    val level: Float = 0f,
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val beatCount: Int = 0,
    val lastBeatNanos: Long = 0L,
)

/**
 * Microphone -> 1024-point FFT (50% overlap, Hann window) -> bass / mid / treble bands and
 * onset ("beat") detection via spectral flux against an adaptive threshold. Levels are
 * relative to the room's ambient loudness, so thunder or a scream reads as a spike whether
 * the party is quiet or loud.
 */
class AudioAnalyzer(private val context: Context) {

    @Volatile var levels = AudioLevels()
        private set

    private var job: Job? = null

    /** Beats are ignored until this time, so our own loud sound effects don't trigger effects. */
    @Volatile private var suppressBeatsUntil = 0L

    fun suppressBeats(millis: Long = 400) {
        suppressBeatsUntil = System.nanoTime() + millis * 1_000_000L
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            try {
                capture()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.w(TAG, "Audio capture failed", t)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        levels = AudioLevels()
    }

    @SuppressLint("MissingPermission") // Caller checks RECORD_AUDIO before start().
    private suspend fun capture() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, N * 4))
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("AudioRecord failed to initialize")
        }
        // Cancel the app's own sound effects out of the mic signal where the device supports it.
        val echo = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } else null

        val fft = Fft(N)
        val window = FloatArray(N) { (0.5 - 0.5 * cos(2.0 * PI * it / (N - 1))).toFloat() }
        val ring = FloatArray(N)
        val hop = ShortArray(HOP)
        val re = FloatArray(N)
        val im = FloatArray(N)
        val mag = FloatArray(N / 2)
        val prevMag = FloatArray(N / 2)
        val fluxHistory = FloatArray(FLUX_HISTORY)
        var fluxIndex = 0
        val ambient = floatArrayOf(0.01f, 0.001f, 0.001f, 0.001f)
        val raw = FloatArray(4)
        val env = FloatArray(4)
        var beatCount = 0
        var lastBeat = 0L

        record.startRecording()
        try {
            while (currentCoroutineContext().isActive) {
                var read = 0
                while (read < HOP) {
                    val r = record.read(hop, read, HOP - read)
                    if (r <= 0) error("AudioRecord.read returned $r")
                    read += r
                }
                System.arraycopy(ring, HOP, ring, 0, N - HOP)
                for (i in 0 until HOP) ring[N - HOP + i] = hop[i] / 32768f

                var sumSq = 0f
                for (i in 0 until N) {
                    re[i] = ring[i] * window[i]
                    im[i] = 0f
                    sumSq += ring[i] * ring[i]
                }
                val rms = sqrt(sumSq / N)
                fft.transform(re, im)
                for (k in 0 until N / 2) mag[k] = sqrt(re[k] * re[k] + im[k] * im[k]) / (N / 2)

                val bands = floatArrayOf(bandAvg(mag, 1, BASS_END), bandAvg(mag, BASS_END, MID_END), bandAvg(mag, MID_END, TREBLE_END))

                // Spectral flux over bass+mid catches thunder, screams, slams, music hits.
                var flux = 0f
                for (k in 1 until MID_END) flux += max(0f, mag[k] - prevMag[k])
                System.arraycopy(mag, 0, prevMag, 0, mag.size)
                var mean = 0f
                for (f in fluxHistory) mean += f
                mean /= FLUX_HISTORY
                var variance = 0f
                for (f in fluxHistory) variance += (f - mean) * (f - mean)
                val std = sqrt(variance / FLUX_HISTORY)
                fluxHistory[fluxIndex] = flux
                fluxIndex = (fluxIndex + 1) % FLUX_HISTORY
                val now = System.nanoTime()
                if (flux > mean + 1.6f * std && flux > MIN_FLUX && now - lastBeat > BEAT_COOLDOWN_NANOS && now > suppressBeatsUntil) {
                    beatCount++
                    lastBeat = now
                }

                // Ambient-relative loudness: a slow average (~4 s) tracks the room, and only sound
                // above it registers. Steady party noise reads ~0, thunder or a scream reads ~1.
                // Floors keep a silent room from amplifying hiss.
                raw[0] = rms
                for (b in 0 until 3) raw[b + 1] = bands[b]
                for (i in 0 until 4) {
                    ambient[i] = max(ambient[i] + (raw[i] - ambient[i]) * AMBIENT_RATE, FLOORS[i])
                    val excess = ((raw[i] / ambient[i] - 1f) / EXCESS_FOR_FULL).coerceIn(0f, 1f)
                    val a = if (excess > env[i]) ATTACK else RELEASE
                    env[i] += (excess - env[i]) * a
                }
                levels = AudioLevels(env[0], env[1], env[2], env[3], beatCount, lastBeat)
            }
        } finally {
            echo?.release()
            record.stop()
            record.release()
        }
    }

    private fun bandAvg(mag: FloatArray, from: Int, to: Int): Float {
        var s = 0f
        for (k in from until to) s += mag[k]
        return s / (to - from)
    }

    companion object {
        private const val TAG = "AudioAnalyzer"
        private const val SAMPLE_RATE = 44_100
        private const val N = 1024
        private const val HOP = 512                // ~86 analyses per second
        // Bin width = 44100 / 1024 = 43 Hz
        private const val BASS_END = 6             // ~43-250 Hz
        private const val MID_END = 47             // ~250-2000 Hz
        private const val TREBLE_END = 187         // ~2-8 kHz
        private const val FLUX_HISTORY = 43        // ~0.5 s
        private const val MIN_FLUX = 0.002f
        private const val BEAT_COOLDOWN_NANOS = 180_000_000L
        private const val AMBIENT_RATE = 0.003f
        private const val EXCESS_FOR_FULL = 3f     // 4x the ambient level = full scale
        private val FLOORS = floatArrayOf(0.004f, 0.0003f, 0.0002f, 0.0001f)
        private const val ATTACK = 0.6f
        private const val RELEASE = 0.08f
    }
}
