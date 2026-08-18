package com.lovelyreader.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSearchFormPageCacheTest {
    @Test
    fun `recent form page can be reused but expires promptly`() {
        val cache = VideoSearchFormPageCache(ttlMillis = 90_000)
        val url = "https://www.88ystv.com/"
        val page = "<form action='/search/'><input name='wd'></form>"

        cache.store(url, page, nowMillis = 1_000)

        assertEquals(page, cache.takeFresh(url, nowMillis = 2_000))
        assertNull(cache.takeFresh(url, nowMillis = 91_001))
    }

    @Test
    fun `cache never stores a page without a search form`() {
        val cache = VideoSearchFormPageCache(ttlMillis = 90_000)

        cache.store("https://www.88ystv.com/", "<html>announcement</html>", nowMillis = 1_000)

        assertNull(cache.takeFresh("https://www.88ystv.com/", nowMillis = 1_001))
    }
}
