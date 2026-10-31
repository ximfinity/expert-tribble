package com.phantomfloor.vision

import com.phantomfloor.engine.CalibrationView
import com.phantomfloor.util.Homography

/**
 * Automatic camera-to-floor calibration. The projector shows a dark frame (baseline), then one
 * bright marker at each of 4 known canvas positions. The camera finds each marker as the
 * centroid of pixels that got brighter than the baseline. Four correspondences give the
 * camera -> canvas homography, which already includes the keystone and any camera tilt.
 *
 * The analyzer thread drives the state machine ([onLumaFrame]); the GL thread only reads
 * [markerIndex] to know which marker to draw.
 */
class FloorCalibrator(
    private val mapping: FloorMapping,
    private val onFinished: (success: Boolean, message: String) -> Unit,
) : CalibrationView {
    enum class Phase { IDLE, BASELINE, MARKER }

    @Volatile var phase = Phase.IDLE
        private set

    /** Marker the projector should show now, or -1 for none (black frame). */
    @Volatile override var markerIndex = -1
        private set

    override val isActive get() = phase != Phase.IDLE

    /**
     * How long the camera takes to show a change on the floor beyond a phone camera's own delay.
     * Network streams lag by up to a second or two, and sampling too early would credit the
     * previous marker's position to the next one.
     */
    @Volatile var streamLatencyMs = 0L

    private var baseline: FloatArray? = null
    private var baselineFrames = 0
    private var phaseStartMs = -1L
    private val found = FloatArray(8)
    private var accX = 0f
    private var accY = 0f
    private var accN = 0
    private val tmp = FloatArray(2)

    fun start() {
        baseline = null
        baselineFrames = 0
        phaseStartMs = -1L
        markerIndex = -1
        phase = Phase.BASELINE
    }

    fun cancel() {
        phase = Phase.IDLE
        markerIndex = -1
    }

    /** [grid] is a [w]x[h] luma image (0..255) sampled from the raw camera buffer. */
    @Synchronized
    fun onLumaFrame(grid: FloatArray, w: Int, h: Int, rotation: Int, nowMs: Long) {
        if (phase == Phase.IDLE) return
        if (phaseStartMs < 0) phaseStartMs = nowMs
        val elapsed = nowMs - phaseStartMs

        when (phase) {
            Phase.BASELINE -> {
                if (elapsed < SETTLE_MS + streamLatencyMs) return
                val b = baseline ?: FloatArray(w * h).also { baseline = it }
                for (i in b.indices) b[i] += grid[i]
                if (++baselineFrames >= BASELINE_FRAMES) {
                    for (i in b.indices) b[i] /= baselineFrames
                    nextMarker(0, nowMs)
                }
            }
            Phase.MARKER -> {
                if (elapsed < SETTLE_MS + streamLatencyMs) return
                if (elapsed > TIMEOUT_MS + streamLatencyMs) return fail("marker ${markerIndex + 1} not seen; is the camera aimed at the projection?")
                val b = baseline ?: return fail("no baseline")
                var maxDiff = 0f
                for (i in grid.indices) maxDiff = maxOf(maxDiff, grid[i] - b[i])
                if (maxDiff < MIN_CONTRAST) return
                val threshold = maxOf(MIN_CONTRAST * 0.6f, maxDiff * 0.5f)
                var sx = 0f; var sy = 0f; var sw = 0f
                for (yy in 0 until h) for (xx in 0 until w) {
                    val d = grid[yy * w + xx] - b[yy * w + xx]
                    if (d > threshold) {
                        val wgt = d - threshold
                        sx += (xx + 0.5f) * wgt; sy += (yy + 0.5f) * wgt; sw += wgt
                    }
                }
                if (sw <= 0f) return
                ImageGeometry.rawToUpright(sx / sw / w, sy / sw / h, rotation, tmp)
                accX += tmp[0]; accY += tmp[1]; accN++
                if (accN >= MARKER_FRAMES) {
                    found[markerIndex * 2] = accX / accN
                    found[markerIndex * 2 + 1] = accY / accN
                    if (markerIndex == 3) finish() else nextMarker(markerIndex + 1, nowMs)
                }
            }
            Phase.IDLE -> Unit
        }
    }

    private fun nextMarker(i: Int, nowMs: Long) {
        accX = 0f; accY = 0f; accN = 0
        phaseStartMs = nowMs
        markerIndex = i
        phase = Phase.MARKER
    }

    private fun finish() {
        phase = Phase.IDLE
        markerIndex = -1
        if (!Homography.isConvex(found, 1e-4f)) return fail("marker positions look wrong (camera moved or reflections?)")
        val m = Homography.quadToQuad(found, TARGETS) ?: return fail("degenerate geometry")
        mapping.set(m)
        onFinished(true, "ok")
    }

    private fun fail(reason: String) {
        phase = Phase.IDLE
        markerIndex = -1
        onFinished(false, reason)
    }

    companion object {
        /** Marker positions in canvas UV; order matches a counter-clockwise quad. */
        val TARGETS = CalibrationView.TARGETS
        const val GRID_W = 96
        const val GRID_H = 72
        private const val SETTLE_MS = 900L
        private const val TIMEOUT_MS = 5000L
        private const val BASELINE_FRAMES = 8
        private const val MARKER_FRAMES = 5
        private const val MIN_CONTRAST = 28f
    }
}
