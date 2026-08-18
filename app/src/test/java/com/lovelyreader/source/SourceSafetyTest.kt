package com.lovelyreader.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSafetyTest {
    @Test
    fun allowsSameHostHttpsReadPath() {
        val safety = SourceSafety(
            baseUrl = "https://ixdzs8.com",
            disallowedPrefixes = listOf("/down", "/download/")
        )

        assertTrue(safety.isAllowed("https://ixdzs8.com/read/183714/p56.html"))
    }

    @Test
    fun blocksDisallowedDownloadPath() {
        val safety = SourceSafety(
            baseUrl = "https://ixdzs8.com",
            disallowedPrefixes = listOf("/down", "/download/")
        )

        assertFalse(safety.isAllowed("https://ixdzs8.com/download/183714.txt"))
    }

    @Test
    fun blocksDisallowedPathAfterDotSegmentNormalization() {
        val safety = SourceSafety(
            baseUrl = "https://ixdzs8.com",
            disallowedPrefixes = listOf("/down", "/download/")
        )

        assertFalse(safety.isAllowed("https://ixdzs8.com/read/../down/183714.txt"))
    }

    @Test
    fun blocksDisallowedPathAfterPercentDecodingAndCaseNormalization() {
        val safety = SourceSafety(
            baseUrl = "https://ixdzs8.com",
            disallowedPrefixes = listOf("/down", "/download/")
        )

        assertFalse(safety.isAllowed("https://ixdzs8.com/%44OWN/183714.txt"))
    }

    @Test
    fun blocksCrossSiteUrls() {
        val safety = SourceSafety(baseUrl = "https://m.ijjxs.com")

        assertFalse(safety.isAllowed("https://example.com/txt/56501.html"))
    }

    @Test
    fun blocksHttpUrls() {
        val safety = SourceSafety(baseUrl = "https://m.ijjxs.com")

        assertFalse(safety.isAllowed("http://m.ijjxs.com/txt/56501.html"))
    }
}
