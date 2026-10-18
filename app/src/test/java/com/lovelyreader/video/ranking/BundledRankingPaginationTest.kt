package com.lovelyreader.video.ranking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledRankingPaginationTest {
    private val fingerprint = "0123456789abcdef"

    @Test
    fun `bundled cursor round trips and keeps page positions opaque`() {
        assertEquals("offset:0123456789abcdef:0", bundledRankingCursor(0, fingerprint))
        assertEquals("offset:0123456789abcdef:50", bundledRankingCursor(50, fingerprint))
        assertEquals(0, bundledRankingOffset(null, fingerprint))
        assertEquals(50, bundledRankingOffset("offset:0123456789abcdef:50", fingerprint))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid bundled cursor is rejected instead of restarting at first page`() {
        bundledRankingOffset("page-2", fingerprint)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cursor from a different catalogue revision is rejected`() {
        bundledRankingOffset("offset:fedcba9876543210:50", fingerprint)
    }

    @Test
    fun `source board score reads only the requested source rating`() {
        val item = JSONObject(
            """{"aggregateScore":8.4,"sourceRatings":[{"sourceId":"imdb","value":8.0},{"sourceId":"tvmaze","value":9.1}]}"""
        )
        assertEquals(8.4, rankingBoardScore(item, "aggregate")!!, 0.0)
        assertEquals(8.0, rankingBoardScore(item, "source-imdb")!!, 0.0)
        assertEquals(9.1, rankingBoardScore(item, "source-tvmaze")!!, 0.0)
        assertNull(rankingBoardScore(JSONObject("{\"sourceRatings\":[]}"), "source-tvmaze"))
    }

    @Test
    fun `rank sorting gives vote confidence more weight than tiny perfect scores`() {
        val tinyPerfect = JSONObject(
            """{"aggregateScore":10.0,"sourceRatings":[{"sourceId":"imdb","value":10.0,"voteCount":251}]}"""
        )
        val broadlyRated = JSONObject(
            """{"aggregateScore":9.2,"sourceRatings":[{"sourceId":"imdb","value":9.2,"voteCount":10000}]}"""
        )
        assertEquals(10.0, rankingBoardScore(tinyPerfect, "aggregate")!!, 0.0)
        assertTrue(rankingBoardSortScore(broadlyRated, "aggregate")!! > rankingBoardSortScore(tinyPerfect, "aggregate")!!)
    }

    @Test
    fun `all years request does not silently become current year`() {
        assertNull(rankingTargetYear(period = "year", requestedYear = null, now = 0L))
        assertEquals(2025, rankingTargetYear(period = "year", requestedYear = 2025, now = 0L))
    }

    @Test
    fun `bundled poster asset names can recover cached art for rows missing a URL`() {
        assertEquals(
            "ranking/posters/imdb_tt0111161.img",
            bundledPosterAssetPath("imdb:tt0111161")
        )
        assertNull(bundledPosterAssetPath(""))
    }

    @Test
    fun `preferred genres are grouped ahead of other genres`() {
        assertEquals(1, rankingPreferredGenrePriority(listOf("剧情", "恐怖")))
        assertEquals(1, rankingPreferredGenrePriority(listOf("喜剧")))
        assertEquals(0, rankingPreferredGenrePriority(listOf("剧情", "爱情")))
        assertEquals(0, rankingPreferredGenrePriority(emptyList()))
    }
}
