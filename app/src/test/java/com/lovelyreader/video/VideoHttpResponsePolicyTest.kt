package com.lovelyreader.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoHttpResponsePolicyTest {
    @Test
    fun `html access denied response is treated as a network interception`() {
        assertTrue(shouldRetryVideoHttpResponse(200, "<html><title>Access Denied</title></html>"))
        assertTrue(shouldRetryVideoHttpResponse(403, ""))
        assertTrue(shouldRetryVideoHttpResponse(451, "Request blocked by network policy"))
    }

    @Test
    fun `normal catalogue html and structured errors are not classified as html block pages`() {
        assertFalse(shouldRetryVideoHttpResponse(200, "<html><form><input name=wd></form></html>"))
        assertFalse(shouldRetryVideoHttpResponse(403, "{\"message\":\"rate limited\"}"))
    }

    @Test
    fun `video response inspector rejects every non success response`() {
        var failed = false
        try {
            inspectVideoHttpResponse(503, "<html>temporarily unavailable</html>")
        } catch (_: VideoHttpResponseException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `video diagnostics retain only the origin`() {
        val safe = redactVideoUrl("https://cdn.example/video/master.m3u8?token=secret&expires=123")
        assertEquals("https://cdn.example", safe)

        val signedPath = redactVideoUrl("https://cdn.example/play/a8f3?sig=x")
        assertEquals("https://cdn.example", signedPath)

        val encodedPathToken = redactVideoUrl("https://cdn.example/v/short%2Fsecret-token.m3u8#fragment")
        assertEquals("https://cdn.example", encodedPathToken)
    }
}
