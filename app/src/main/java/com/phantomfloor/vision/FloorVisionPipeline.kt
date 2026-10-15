package com.phantomfloor.vision

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Camera frames -> coroutine -> ML Kit -> floor coordinates -> [PersonTracker].
 *
 * Frames come from CameraX (as an [ImageAnalysis.Analyzer]) or from a network camera via
 * [submit]. The producer only hands frames to a one-slot channel; detection runs in a coroutine
 * on [Dispatchers.Default] and suspends on ML Kit's Task with `await()`, so neither the camera
 * thread nor the GL render thread ever blocks. A frame that arrives while one is still being
 * processed is dropped at once, so the pipeline always works on a recent frame.
 */
class FloorVisionPipeline(
    private val tracker: PersonTracker,
    private val mapping: FloorMapping,
    private val calibrator: FloorCalibrator,
) : ImageAnalysis.Analyzer {

    enum class Mode { FEET, BODIES }

    @Volatile var mode = Mode.FEET

    /** Latest inference time, for the status line. */
    @Volatile var lastInferenceMs = 0L
        private set

    private val poseDetector by lazy {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
        )
    }
    private val objectDetector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .build(),
        )
    }

    @Volatile private var frames: Channel<VisionFrame>? = null
    private var job: Job? = null
    private val luma = FloatArray(FloorCalibrator.GRID_W * FloorCalibrator.GRID_H)
    private val mapped = FloatArray(2)

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        val channel = Channel<VisionFrame>(1)
        frames = channel
        job = scope.launch(Dispatchers.Default) {
            for (frame in channel) {
                try {
                    process(frame)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w(TAG, "Frame failed", t)
                } finally {
                    frame.close()
                }
            }
        }
    }

    fun stop() {
        val channel = frames
        frames = null
        channel?.close()
        job?.cancel()
        job = null
        // Release anything still queued so the producer can continue.
        while (true) channel?.tryReceive()?.getOrNull()?.close() ?: break
        tracker.clear()
    }

    /** CameraX entry point. */
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image ?: return image.close()
        submit(VisionFrame(media, image.imageInfo.rotationDegrees) { image.close() })
    }

    /** Takes ownership of [frame]: it is closed after processing, or now if the pipeline is busy. */
    fun submit(frame: VisionFrame) {
        val channel = frames
        if (channel == null || !channel.trySend(frame).isSuccess) frame.close()
    }

    private suspend fun process(frame: VisionFrame) {
        val rotation = frame.rotation
        if (calibrator.isActive) {
            sampleLuma(frame)
            calibrator.onLumaFrame(luma, FloorCalibrator.GRID_W, FloorCalibrator.GRID_H, rotation, SystemClock.elapsedRealtime())
            return
        }
        val input = InputImage.fromMediaImage(frame.image, rotation)
        val uprightW = if (rotation % 180 == 0) frame.width else frame.height
        val uprightH = if (rotation % 180 == 0) frame.height else frame.width
        val started = SystemClock.elapsedRealtime()

        val camPoints = ArrayList<FloatArray>(8)
        when (mode) {
            Mode.FEET -> {
                val pose = poseDetector.process(input).await()
                footPoint(pose, PoseLandmark.LEFT_HEEL, PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.LEFT_ANKLE)?.let(camPoints::add)
                footPoint(pose, PoseLandmark.RIGHT_HEEL, PoseLandmark.RIGHT_FOOT_INDEX, PoseLandmark.RIGHT_ANKLE)?.let(camPoints::add)
            }
            Mode.BODIES -> {
                // The bottom-centre of each body's box is where it meets the floor.
                for (obj in objectDetector.process(input).await()) {
                    val box = obj.boundingBox
                    camPoints.add(floatArrayOf(box.exactCenterX(), box.bottom.toFloat()))
                }
            }
        }
        lastInferenceMs = SystemClock.elapsedRealtime() - started

        val floorPoints = ArrayList<FloatArray>(camPoints.size)
        for (c in camPoints) {
            if (mapping.map(c[0] / uprightW, c[1] / uprightH, mapped) &&
                mapped[0] in -0.1f..1.1f && mapped[1] in -0.1f..1.1f
            ) {
                floorPoints.add(floatArrayOf(mapped[0], mapped[1]))
            }
        }
        tracker.update(floorPoints, System.nanoTime())
    }

    private fun footPoint(pose: Pose, heel: Int, toe: Int, ankle: Int): FloatArray? {
        val h = pose.getPoseLandmark(heel)
        val t = pose.getPoseLandmark(toe)
        if (h != null && t != null && h.inFrameLikelihood > MIN_LIKELIHOOD && t.inFrameLikelihood > MIN_LIKELIHOOD) {
            return floatArrayOf((h.position.x + t.position.x) * 0.5f, (h.position.y + t.position.y) * 0.5f)
        }
        val a = pose.getPoseLandmark(ankle) ?: return null
        if (a.inFrameLikelihood < MIN_LIKELIHOOD) return null
        return floatArrayOf(a.position.x, a.position.y)
    }

    /** Box-averages the Y plane into a small grid for marker detection. */
    private fun sampleLuma(frame: VisionFrame) {
        val plane = frame.image.planes[0]
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixStride = plane.pixelStride
        val gw = FloorCalibrator.GRID_W
        val gh = FloorCalibrator.GRID_H
        val cellW = frame.width / gw
        val cellH = frame.height / gh
        for (gy in 0 until gh) for (gx in 0 until gw) {
            var sum = 0
            var n = 0
            var y = gy * cellH
            while (y < (gy + 1) * cellH) {
                var x = gx * cellW
                while (x < (gx + 1) * cellW) {
                    sum += buf.get(y * rowStride + x * pixStride).toInt() and 0xFF
                    n++
                    x += 2
                }
                y += 2
            }
            luma[gy * gw + gx] = if (n > 0) sum.toFloat() / n else 0f
        }
    }

    fun close() {
        stop()
        poseDetector.close()
        objectDetector.close()
    }

    companion object {
        private const val TAG = "FloorVision"
        private const val MIN_LIKELIHOOD = 0.5f
    }
}
