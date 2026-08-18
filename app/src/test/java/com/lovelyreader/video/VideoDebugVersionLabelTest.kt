package com.lovelyreader.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDebugVersionLabelTest {
    @Test
    fun `debug label includes name and version code`() {
        assertEquals("调试包 v0.7.9 (62)", videoDebugVersionLabel("0.7.9", 62))
    }
}
