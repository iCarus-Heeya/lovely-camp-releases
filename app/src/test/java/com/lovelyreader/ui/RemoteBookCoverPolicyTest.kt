package com.lovelyreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteBookCoverPolicyTest {
    @Test
    fun `accepts only public HTTPS book cover addresses`() {
        assertTrue(isSafeBookCoverUrl("https://cdn.example.com/cover.jpg"))
        assertFalse(isSafeBookCoverUrl("http://cdn.example.com/cover.jpg"))
        assertFalse(isSafeBookCoverUrl("https://127.0.0.1/cover.jpg"))
        assertFalse(isSafeBookCoverUrl("https://[::1]/cover.jpg"))
        assertFalse(isSafeBookCoverUrl("file:///sdcard/cover.jpg"))
    }
}
