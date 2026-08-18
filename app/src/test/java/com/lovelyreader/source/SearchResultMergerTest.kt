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
}
