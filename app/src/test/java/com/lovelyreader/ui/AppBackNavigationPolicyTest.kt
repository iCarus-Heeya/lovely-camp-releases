package com.lovelyreader.ui

import com.lovelyreader.domain.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackNavigationPolicyTest {
    @Test
    fun `system Back returns book detail to its preserved search surface`() {
        val detail = Screen.Detail(SearchResult("source", "title", "author", "https://example.com/book"))

        assertEquals(Screen.Search, readerBackDestination(detail))
    }

    @Test
    fun `system Back returns search and settings to bookshelf instead of exiting`() {
        assertEquals(Screen.Shelf, readerBackDestination(Screen.Search))
        assertEquals(Screen.Shelf, readerBackDestination(Screen.Settings))
    }

    @Test
    fun `system Back exits only from the bookshelf root`() {
        assertNull(readerBackDestination(Screen.Shelf))
    }

    @Test
    fun `system Back at bookshelf is consumed so the activity does not finish unexpectedly`() {
        assertTrue(shouldConsumeRootSystemBack(Screen.Shelf))
        assertFalse(shouldConsumeRootSystemBack(Screen.Search))
    }
}
