package com.lovelyreader.ui.video.ranking

import com.lovelyreader.ui.MainTab
import com.lovelyreader.ui.video.DramaPage
import com.lovelyreader.ui.video.dramaBackDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingNavigationPolicyTest {
    @Test
    fun `rankings system back returns to drama home`() {
        assertEquals(DramaPage.Home, dramaBackDestination(DramaPage.Rankings))
        assertEquals(DramaPage.Rankings, dramaBackDestination(DramaPage.Detail, DramaPage.Rankings))
        assertEquals(DramaPage.Detail, dramaBackDestination(DramaPage.Player))
        assertNull(dramaBackDestination(DramaPage.Home))
    }

    @Test
    fun `ranking does not add a global bottom bar item`() {
        assertFalse(MainTab.values().any { it.label == "排行榜" })
        assertTrue(MainTab.values().all { it.label != "追剧" })
    }

    @Test
    fun `search handoff contains only canonical metadata and a display query`() {
        val intent = RankingSearchIntent(
            canonicalId = "tmdb:123",
            displayTitle = "春日来信",
            aliases = listOf("Letters of Spring")
        )

        assertEquals("春日来信", intent.displayTitle)
        assertEquals(listOf("Letters of Spring"), intent.aliases)
        // The policy is intentionally data-only: the intent has no source URL
        // or playback address that could be passed into DramaViewModel.openTitle.
        assertFalse(intent.canonicalId.startsWith("http"))
    }
}
