package com.phantomfloor.vision

import android.media.Image

/**
 * One camera frame handed to [FloorVisionPipeline], whatever produced it: the phone camera
 * (CameraX) or a network camera stream ([RtspCameraSource]). [image] must be YUV_420_888.
 * [rotation] = degrees clockwise the image must be turned to be upright. The pipeline calls
 * [close] exactly once when it's done, which returns the buffer to its producer.
 */
class VisionFrame(val image: Image, val rotation: Int, private val release: () -> Unit) {
    val width get() = image.width
    val height get() = image.height
    fun close() = release()
}
