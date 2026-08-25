package com.lovelyreader.ui.navigation

import com.lovelyreader.ui.Screen
import com.lovelyreader.domain.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteStateTest {
    @Test
    fun `back from detail returns to search and from search returns to shelf`() {
        val result = SearchResult("qinkan", "书", "作者", "https://example.com/book")
        assertEquals(Screen.Search, nextReaderRoute(Screen.Detail(result)))
        assertEquals(Screen.Shelf, nextReaderRoute(Screen.Search))
        assertEquals(Screen.Shelf, nextReaderRoute(Screen.Reader("b1")))
    }
}
