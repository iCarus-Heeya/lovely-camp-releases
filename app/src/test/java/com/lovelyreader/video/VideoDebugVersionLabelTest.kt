package com.lovelyreader.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDebugVersionLabelTest {
    @Test
    fun `debug label includes name and version code`() {
        assertEquals("调试包 v0.7.9 (62)", videoDebugVersionLabel("0.7.9", 62))
    }

    @Test
    fun `debug diagnostics expose the candidate build id separately from app version`() {
        assertEquals("诊断构建 tlsdiag-20260829", videoDebugBuildIdLabel())
    }
}
