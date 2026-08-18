package com.lovelyreader.ui

import com.lovelyreader.domain.SearchResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseNavigationPolicyTest {
    @Test
    fun `search and detail routes keep the same browse surface composed`() {
        val detail = Screen.Detail(SearchResult("source", "title", "author", "https://example.com/book"))

        assertTrue(shouldComposeBrowseSurface(Screen.Search))
        assertTrue(shouldComposeBrowseSurface(detail))
        assertFalse(shouldComposeBrowseSurface(Screen.Shelf))
    }
}
