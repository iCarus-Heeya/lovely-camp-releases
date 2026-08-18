package com.lovelyreader.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VideoRequestDiagnosticsTest {
    @Test
    fun `diagnostics redacts query values and retains newest bounded events`() {
        val diagnostics = VideoRequestDiagnostics(capacity = 2)

        diagnostics.record("GET", "https://example.com/search?wd=private-title", "搜索页面")
        diagnostics.record("POST", "https://example.com/search?wd=another-title", "找到搜索表单")
        diagnostics.record("GET", "https://example.com/detail/1?token=secret", "HTTP 请求失败: SocketTimeoutException")

        val events = diagnostics.snapshot()

        assertEquals(2, events.size)
        assertEquals("POST https://example.com/search - 找到搜索表单", events.first().displayText)
        assertFalse(events.joinToString().contains("private-title"))
        assertFalse(events.joinToString().contains("secret"))
    }
}
