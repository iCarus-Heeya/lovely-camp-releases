package com.lovelyreader.source

import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceReadEligibilityTest {
    private val sources = listOf(IxdzsSource(), IjjxsSource())

    @Test
    fun allowsOnlyMatchingHttpsSourceThatDeclaresReadChapter() {
        val result = SearchResult(
            sourceId = "ixdzs",
            title = "你是我的荣耀",
            author = "顾漫",
            bookUrl = "https://ixdzs8.com/read/183714/",
            capabilities = setOf(SourceCapability.READ_CHAPTER, SourceCapability.OPEN_ORIGINAL)
        )

        assertTrue(SourceReadEligibility.canReadInApp(sources, result))
    }

    @Test
    fun rejectsResultThatClaimsReadChapterForOpenOriginalOnlySource() {
        val result = SearchResult(
            sourceId = "ijjxs",
            title = "你是我的荣耀",
            author = "顾漫",
            bookUrl = "https://m.ijjxs.com/txt/1.html",
            capabilities = setOf(SourceCapability.READ_CHAPTER, SourceCapability.OPEN_ORIGINAL)
        )

        assertFalse(SourceReadEligibility.canReadInApp(sources, result))
    }

    @Test
    fun rejectsLookalikeHostEvenWhenResultClaimsReadChapter() {
        val result = SearchResult(
            sourceId = "ixdzs",
            title = "你是我的荣耀",
            author = "顾漫",
            bookUrl = "https://ixdzs8.com.evil.example/read/183714/",
            capabilities = setOf(SourceCapability.READ_CHAPTER, SourceCapability.OPEN_ORIGINAL)
        )

        assertFalse(SourceReadEligibility.canReadInApp(sources, result))
    }

    @Test
    fun rejectsHttpUrlEvenWhenHostMatches() {
        val result = SearchResult(
            sourceId = "ixdzs",
            title = "你是我的荣耀",
            author = "顾漫",
            bookUrl = "http://ixdzs8.com/read/183714/",
            capabilities = setOf(SourceCapability.READ_CHAPTER, SourceCapability.OPEN_ORIGINAL)
        )

        assertFalse(SourceReadEligibility.canReadInApp(sources, result))
    }

    @Test
    fun rejectsDisallowedSameHostReadUrl() {
        val result = SearchResult(
            sourceId = "ixdzs",
            title = "你是我的荣耀",
            author = "顾漫",
            bookUrl = "https://ixdzs8.com/download/183714.txt",
            capabilities = setOf(SourceCapability.READ_CHAPTER, SourceCapability.OPEN_ORIGINAL)
        )

        assertFalse(SourceReadEligibility.canReadInApp(sources, result))
    }
}
