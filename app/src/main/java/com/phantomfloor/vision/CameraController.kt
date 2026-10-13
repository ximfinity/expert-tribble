package com.phantomfloor.vision

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** CameraX analysis-only pipeline: no preview surface, frames go straight to the analyzer. */
class CameraController(private val context: Context, private val owner: LifecycleOwner) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "phantom-camera").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private var provider: ProcessCameraProvider? = null

    suspend fun start(analyzer: ImageAnalysis.Analyzer) {
        val p = provider ?: awaitProvider().also { provider = it }
        val selector = when {
            p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> error("No camera available")
        }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        analysis.setAnalyzer(executor, analyzer)
        p.unbindAll()
        p.bindToLifecycle(owner, selector, analysis)
    }

    fun stop() {
        provider?.unbindAll()
    }

    fun shutdown() {
        stop()
        executor.shutdown()
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cont.resume(future.get())
            } catch (t: Throwable) {
                cont.resumeWithException(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
