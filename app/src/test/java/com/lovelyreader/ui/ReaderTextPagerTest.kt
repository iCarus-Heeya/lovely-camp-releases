package com.lovelyreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextPagerTest {
    @Test
    fun splitsLongNovelTextIntoSafeRenderablePages() {
        val longText = (1..4_000).joinToString("\n") { index ->
            "第${index}段，她把这一页慢慢读完，屏幕保持温柔，不再一次性撑出超高文本。"
        }

        val pages = splitReaderTextIntoCharacterBoundedChunks(longText, maxChars = 1_200)

        assertTrue(pages.size > 10)
        assertTrue(pages.all { it.length <= 1_200 })
        assertEquals(longText.take(20), pages.first().take(20))
        assertEquals(longText, pages.joinToString(separator = ""))
    }

    @Test
    fun progressFirstPageNoOffset() {
        assertEquals(0f, readerProgress(pagesSize = 5, index = 0, offset = 0, itemHeight = null), 0.001f)
    }

    @Test
    fun progressLastPageNoOffset() {
        assertEquals(1f, readerProgress(pagesSize = 5, index = 4, offset = 0, itemHeight = null), 0.001f)
    }

    @Test
    fun progressWithOffset() {
        // Page index 1, item height 800, offset 200, 5 pages total -> denominator 4
        // (1 + 200/800) / 4 = 1.25 / 4 = 0.3125
        assertEquals(0.3125f, readerProgress(pagesSize = 5, index = 1, offset = 200, itemHeight = 800), 0.001f)
    }

    @Test
    fun progressWithSinglePage() {
        assertEquals(0f, readerProgress(pagesSize = 1, index = 0, offset = 100, itemHeight = 800), 0.001f)
    }

    @Test
    fun progressOutOfBoundsClamped() {
        assertEquals(1f, readerProgress(pagesSize = 3, index = 5, offset = 0, itemHeight = null), 0.001f)
        assertEquals(0f, readerProgress(pagesSize = 0, index = 0, offset = 0, itemHeight = null), 0.001f)
    }

    @Test
    fun fontSizeChangeMapsSavedProgressToTheNewPageCount() {
        // A font change repaginates the same chapter. The reader must restore
        // the nearest equivalent page instead of jumping back to page zero.
        assertEquals(4, readerPageForProgress(progress = 0.5f, pagesSize = 9))
        assertEquals(0, readerPageForProgress(progress = -1f, pagesSize = 9))
        assertEquals(8, readerPageForProgress(progress = 2f, pagesSize = 9))
        assertEquals(0, readerPageForProgress(progress = 0.5f, pagesSize = 1))
    }
}
