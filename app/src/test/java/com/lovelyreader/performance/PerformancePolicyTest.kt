package com.lovelyreader.performance

import org.junit.Assert.assertEquals
import org.junit.Test

class PerformancePolicyTest {
    @Test
    fun `large reader content uses bounded cache and reports stress band`() {
        assertEquals(3, PerformancePolicy.readerPageCacheLimit)
        assertEquals(PerformanceBand.LARGE, PerformancePolicy.bandFor(bookCount = 1000, chapterCount = 2000))
    }
}
