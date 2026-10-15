package com.phantomfloor.vision

import android.content.Context
import android.graphics.ImageFormat
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import java.util.concurrent.atomic.AtomicInteger

/**
 * A network camera (Wyze with RTSP firmware, docker-wyze-bridge, Reolink, Tapo, Amcrest, any
 * IP camera with an RTSP URL) used as the tracking camera instead of the phone's.
 *
 * Mounting a separate camera above the floor gives a steadier, wider view than a phone, and an
 * IR night-vision camera still sees people in the dark room a projection needs.
 *
 * Media3 ExoPlayer pulls the stream (RTP over TCP, small buffers for low latency) and its
 * hardware decoder renders straight into an [ImageReader] in YUV_420_888, the same format
 * CameraX delivers, so [FloorVisionPipeline] and ML Kit treat both sources identically. Each
 * decoded frame is offered to [onFrame]; the pipeline drops it at once if it's still busy.
 *
 * Call [start] and [stop] on the main thread. The player reconnects on its own after errors.
 */
@OptIn(UnstableApi::class)
class RtspCameraSource(
    private val context: Context,
    private val onFrame: (VisionFrame) -> Unit,
) {
    enum class State { OFF, CONNECTING, STREAMING, RETRYING }

    @Volatile var state = State.OFF
        private set

    /** Last error, shown in the status line until the stream recovers. */
    @Volatile var lastError: String? = null
        private set

    @Volatile var videoWidth = 0
        private set
    @Volatile var videoHeight = 0
        private set

    /** Frames per second arriving from the camera, averaged over about a second. */
    @Volatile var fps = 0f
        private set

    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var imageHandler: Handler? = null
    private var player: ExoPlayer? = null
    private var reader: Reader? = null
    private var url: String? = null
    private var fpsCount = 0
    private var fpsStart = 0L

    private val retry = Runnable { url?.let(::connect) }

    fun start(url: String) {
        stop()
        this.url = url
        thread = HandlerThread("phantom-rtsp").also { it.start() }
        imageHandler = Handler(thread!!.looper)
        connect(url)
    }

    fun stop() {
        main.removeCallbacks(retry)
        url = null
        releasePlayer()
        reader?.release()
        reader = null
        thread?.quitSafely()
        thread = null
        imageHandler = null
        state = State.OFF
        fps = 0f
        lastError = null
    }

    private fun connect(url: String) {
        releasePlayer()
        state = State.CONNECTING
        val loadControl = DefaultLoadControl.Builder()
            // Play as soon as anything arrives and keep at most half a second queued: tracking
            // wants the newest frame, not smooth playback.
            .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, 0, 0)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val p = ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setLoadControl(loadControl)
            .build()
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        p.volume = 0f
        p.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) ensureReader(size.width, size.height)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> { state = State.STREAMING; lastError = null }
                    Player.STATE_ENDED -> scheduleRetry("stream ended")
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "RTSP error", error)
                scheduleRetry(describe(error))
            }
        })
        player = p
        // Decoders need a surface before the first frame; 1280x720 until the stream says otherwise.
        ensureReader(reader?.width ?: 1280, reader?.height ?: 720)
        p.setMediaSource(
            RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(TIMEOUT_MS)
                .createMediaSource(MediaItem.fromUri(Uri.parse(url))),
        )
        p.playWhenReady = true
        p.prepare()
    }

    private fun scheduleRetry(reason: String) {
        if (url == null) return
        lastError = reason
        state = State.RETRYING
        releasePlayer()
        main.removeCallbacks(retry)
        main.postDelayed(retry, RETRY_MS)
    }

    private fun releasePlayer() {
        player?.let {
            it.clearVideoSurface()
            it.release()
        }
        player = null
    }

    private fun ensureReader(w: Int, h: Int) {
        val current = reader
        if (current != null && current.width == w && current.height == h) {
            player?.setVideoSurface(current.imageReader.surface)
            return
        }
        val handler = imageHandler ?: return
        val next = Reader(w, h, handler)
        reader = next
        player?.setVideoSurface(next.imageReader.surface)
        current?.release()
        videoWidth = w
        videoHeight = h
    }

    private fun countFrame() {
        val now = SystemClock.elapsedRealtime()
        if (fpsStart == 0L) fpsStart = now
        fpsCount++
        if (now - fpsStart >= 1000) {
            fps = fpsCount * 1000f / (now - fpsStart)
            fpsCount = 0
            fpsStart = now
        }
    }

    /**
     * An [ImageReader] whose images may still be in ML Kit when the stream stops or changes size.
     * Closing a reader invalidates its images' buffers, so [release] waits until every frame
     * handed out has been closed.
     */
    private inner class Reader(val width: Int, val height: Int, handler: Handler) {
        val imageReader: ImageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, MAX_IMAGES)
        private val outstanding = AtomicInteger(0)
        @Volatile private var released = false

        init {
            imageReader.setOnImageAvailableListener({ r ->
                if (released) return@setOnImageAvailableListener
                // Always drain the queue, or the decoder stalls waiting for a free buffer.
                val image = try {
                    r.acquireLatestImage()
                } catch (e: IllegalStateException) {
                    null // every image is still in use downstream; skip this one
                } ?: return@setOnImageAvailableListener
                countFrame()
                outstanding.incrementAndGet()
                onFrame(VisionFrame(image, 0) {
                    image.close()
                    if (outstanding.decrementAndGet() == 0 && released) closeReader()
                })
            }, handler)
        }

        fun release() {
            released = true
            if (outstanding.get() == 0) closeReader()
        }

        private val closed = AtomicInteger(0)
        private fun closeReader() {
            if (closed.compareAndSet(0, 1)) imageReader.close()
        }
    }

    companion object {
        private const val TAG = "RtspCamera"
        private const val MAX_IMAGES = 4
        private const val MIN_BUFFER_MS = 100
        private const val MAX_BUFFER_MS = 500
        private const val TIMEOUT_MS = 8000L
        private const val RETRY_MS = 3000L

        fun describe(error: PlaybackException): String {
            val cause = generateSequence(error as Throwable) { it.cause }.last()
            return when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "can't reach the camera"
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "camera refused the stream (check user/password and path)"
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "this phone can't decode the camera's video (try H.264)"
                else -> cause.message ?: error.errorCodeName
            }
        }

        /** The URL with any password hidden, for display. */
        fun redact(url: String): String = url.replace(Regex("(?<=://)([^:/@]+):[^@/]*@"), "$1:•••@")

        /** Rough check before connecting, so typos get a clear message. */
        fun isValidUrl(url: String): Boolean {
            val u = Uri.parse(url.trim())
            return (u.scheme == "rtsp" || u.scheme == "rtsps") && !u.host.isNullOrEmpty()
        }
    }
}
