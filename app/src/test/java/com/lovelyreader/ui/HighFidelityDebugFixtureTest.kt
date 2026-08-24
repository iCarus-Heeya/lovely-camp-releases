package com.lovelyreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighFidelityDebugFixtureTest {
    @Test
    fun fixtureIsAvailableInTheReleaseAcceptanceArtifactToo() {
        assertTrue(highFidelityDebugFixtureEnabled(isDebuggable = true))
        assertTrue(highFidelityDebugFixtureEnabled(isDebuggable = false))
    }

    @Test
    fun fixturePagesExposeEveryBookAndDramaAcceptanceState() {
        assertEquals(
            listOf("小书架", "找书", "书籍详情", "阅读", "追剧", "追剧详情", "播放器", "下载列表", "小纸条 / 设置"),
            highFidelityDebugFixturePageLabels()
        )
    }

    @Test
    fun fixtureBookCardsUseStableDistinctCoverPlaceholders() {
        val covers = highFidelityFixtureBookCoverUrls()

        assertEquals(
            listOf(
                "fixture://book-jiulong.png",
                "fixture://book-night.png",
                "fixture://book-spring.png",
                "fixture://book-mirror.png"
            ),
            covers
        )
        assertEquals(covers.size, covers.toSet().size)
    }
}
