package com.lovelyreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighFidelityLayoutPolicyTest {

    @Test
    fun `experience switch follows page specific concept placement`() {
        assertEquals(HighFidelityChromePlacement.BelowHeader, highFidelityChromePlacement(BookPage.Shelf))
        assertEquals(HighFidelityChromePlacement.HeaderTrailing, highFidelityChromePlacement(BookPage.Search))
        assertEquals(HighFidelityChromePlacement.BelowHeader, highFidelityChromePlacement(BookPage.Detail))
        assertEquals(HighFidelityChromePlacement.Hidden, highFidelityChromePlacement(BookPage.Reader))
    }
    @Test
    fun bookshelfHasOneFindBookEntryAndKeepsTheSharedChrome() {
        val layout = highFidelityBookLayout(BookPage.Shelf)

        assertEquals(1, layout.findBookEntryCount)
        assertTrue(layout.showsContinueReadingSection)
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
        assertEquals(78, layout.readerContentTopInsetDp)
        assertEquals(144, layout.readerContentBottomInsetDp)
    }

    @Test
    fun settingsKeepsUpdateActionsAndVersionHistoryVisuallySeparated() {
        val layout = highFidelitySettingsLayout()

        assertTrue(layout.showsRoseMark)
        assertTrue(layout.showsUpdateCard)
        assertTrue(layout.historyIsSeparateSection)
    }

    @Test
    fun playerUsesConceptDarkChromeAndDedicatedControlSurface() {
        val layout = highFidelityPlayerLayout()

        assertTrue(layout.darkSurface)
        assertTrue(layout.showsProgressAndFullscreenControls)
        assertTrue(layout.showsSourceAndEpisodeSurface)
    }
}
