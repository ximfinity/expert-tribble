package com.phantomfloor.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class RtspUrlTest {
    @Test
    fun redactHidesPassword() {
        assertEquals("rtsp://admin:•••@192.168.1.50/live", RtspCameraSource.redact("rtsp://admin:hunter2@192.168.1.50/live"))
    }

    @Test
    fun redactLeavesUrlsWithoutPasswordAlone() {
        assertEquals("rtsp://192.168.1.9:8554/front", RtspCameraSource.redact("rtsp://192.168.1.9:8554/front"))
        assertEquals("rtsp://user@cam/live", RtspCameraSource.redact("rtsp://user@cam/live"))
    }
}
