package com.lovelyreader.source

import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultMergerTest {
    @Test
    fun prefersTxtImportResultForSameBookWhenMerging() {
        val readOnly = SearchResult(
            sourceId = "ixdzs",
            title = "香蜜沉沉烬如霜",
            author = "电线",
            bookUrl = "https://ixdzs8.com/read/1/",
            capabilities = setOf(SourceCapability.READ_CHAPTER)
        )
        val txt = SearchResult(
            sourceId = "qinkan",
            title = "香蜜沉沉烬如霜",
            author = "电线",
            bookUrl = "https://www.qinkan.net/book/1/",
            capabilities = setOf(SourceCapability.TXT_IMPORT)
        )

        val merged = SearchResultMerger.merge(listOf(readOnly, txt))

        assertEquals("qinkan", merged.single().sourceId)
        assertTrue(SourceCapability.TXT_IMPORT in merged.single().capabilities)
    }

    @Test
    fun usesOneNormalizedTitleKeyAcrossFormattingAndMissingAuthor() {
        val first = SearchResult("a", "《 香蜜沉沉烬如霜 》", "电线", "https://a.example/book")
        val second = SearchResult("b", "香蜜沉沉烬如霜", "", "https://b.example/book")

        assertEquals(1, SearchResultMerger.merge(listOf(first, second)).size)
        assertEquals(normalizedTitleKey(first.title), normalizedTitleKey(second.title))
    }

    @Test
    fun keepsSameTitleWhenBothAuthorsAreDifferentAndNonEmpty() {
        val first = SearchResult("a", "潮汐", "甲", "https://a.example/book")
        val second = SearchResult("b", "《潮汐》", "乙", "https://b.example/book")

        assertEquals(2, SearchResultMerger.merge(listOf(first, second)).size)
    }
}
