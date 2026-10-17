package com.lovelyreader.ui.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DramaPosterPolicyTest {
    @Test
    fun `poster loader accepts only public HTTPS image addresses`() {
        assertTrue(isSafeDramaPosterUrl("https://www.88ystv.com/upload/one.jpg"))
        assertFalse(isSafeDramaPosterUrl("http://www.88ystv.com/upload/one.jpg"))
        assertFalse(isSafeDramaPosterUrl("file:///sdcard/one.jpg"))
        assertFalse(isSafeDramaPosterUrl("https://127.0.0.1/one.jpg"))
    }

    @Test
    fun `poster redirects stay on public HTTPS origins`() {
        assertEquals(
            "https://cdn.example/poster.jpg",
            resolveDramaPosterRedirect(
                currentUrl = "https://www.88ystv.com/upload/poster.jpg",
                location = "https://cdn.example/poster.jpg"
            )
        )
        assertEquals(
            "https://www.88ystv.com/images/poster.jpg",
            resolveDramaPosterRedirect(
                currentUrl = "https://www.88ystv.com/upload/poster.jpg",
                location = "/images/poster.jpg"
            )
        )
        assertNull(
            resolveDramaPosterRedirect(
                currentUrl = "https://www.88ystv.com/upload/poster.jpg",
                location = "http://cdn.example/poster.jpg"
            )
        )
        assertNull(
            resolveDramaPosterRedirect(
                currentUrl = "https://www.88ystv.com/upload/poster.jpg",
                location = "https://127.0.0.1/poster.jpg"
            )
        )
    }

    @Test
    fun `poster decode plan caps large source dimensions`() {
        assertEquals(4, dramaPosterSampleSize(width = 2_400, height = 3_600, maxWidth = 720, maxHeight = 960))
        assertEquals(1, dramaPosterSampleSize(width = 640, height = 960, maxWidth = 720, maxHeight = 960))
        assertEquals(1, dramaPosterSampleSize(width = 0, height = 0, maxWidth = 720, maxHeight = 960))
    }

    @Test
    fun `successful poster decode is returned after cache update`() {
        val cached = mutableListOf<String>()
        assertEquals("decoded", rememberDecodedDramaPoster("decoded") { cached += it })
        assertEquals(listOf("decoded"), cached)
        assertNull(rememberDecodedDramaPoster<String>(null) { cached += it })
    }
}
