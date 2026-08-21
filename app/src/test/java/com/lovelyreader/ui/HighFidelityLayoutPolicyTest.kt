package com.lovelyreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighFidelityLayoutPolicyTest {
    @Test
    fun bookshelfHasOneFindBookEntryAndKeepsTheSharedChrome() {
        val layout = highFidelityBookLayout(BookPage.Shelf)

        assertEquals(1, layout.findBookEntryCount)
        assertTrue(layout.showsSharedAppChrome)
        assertTrue(layout.showsPaperDecoration)
    }

    @Test
    fun searchUsesOneDiscoveryTabRowAndKeepsTheSharedChrome() {
        val layout = highFidelityBookLayout(BookPage.Search)

        assertEquals(listOf("搜索", "首页精选", "随便看看"), layout.discoveryTabs)
        assertTrue(layout.showsSharedAppChrome)
        assertTrue(layout.showsPaperDecoration)
    }

    @Test
    fun detailUsesScrollableSummaryAndTwoExplicitActions() {
        val layout = highFidelityBookLayout(BookPage.Detail)

        assertTrue(layout.scrollableContent)
        assertEquals(listOf("加入书架", "打开原站"), layout.primaryActions)
    }

    @Test
    fun readerIsFocusedAndHidesSharedChromeAndBottomNavigation() {
        val layout = highFidelityBookLayout(BookPage.Reader)

        assertFalse(layout.showsSharedAppChrome)
        assertFalse(layout.showsBottomNavigation)
        assertTrue(layout.showsPaperDecoration)
    }
}
