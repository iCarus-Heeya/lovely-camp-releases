package com.lovelyreader.ui.video

import org.junit.Assert.assertFalse
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
}
