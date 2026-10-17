package com.lovelyreader.ui.video.ranking

import com.lovelyreader.video.ranking.RankingCoverage
import com.lovelyreader.video.ranking.RankingEntry
import com.lovelyreader.video.ranking.RankingMediaType
import com.lovelyreader.video.ranking.SourceRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingQualityTest {
    private fun entry(
        title: String = "示例片",
        summary: String? = "这是一个中文简介。",
        poster: String? = "asset://ranking/posters/example.img",
        countries: List<String> = listOf("CN"),
        ratings: List<SourceRating> = listOf(SourceRating("imdb", 8.2, 10.0, 1200))
    ) = RankingEntry(
        canonicalId = "imdb:example",
        rank = 1,
        displayTitle = title,
        originalTitle = "Example",
        aliases = emptyList(),
        mediaType = RankingMediaType.SERIES,
        releaseYear = 2025,
        countries = countries,
        genres = listOf("剧情"),
        posterUrl = poster,
        summary = summary,
        aggregateScore = 8.2,
        sourceRatings = ratings,
        sourceRanks = emptyList(),
        coverage = RankingCoverage(1, listOf("imdb"), complete = true),
        searchTitle = "示例片",
        rankingScore = 8.1
    )

    @Test
    fun `quality exposes missing poster and placeholder summary instead of pretending card is complete`() {
        val quality = rankingMetadataQuality(
            entry(summary = "公开资料概览：《示例片》；当前快照未收录剧情正文。", poster = null)
        )

        assertFalse(quality.isComplete)
        assertEquals(listOf("简介待补充", "海报待补充"), quality.missingLabels)
        assertEquals("资料待补：简介、海报", quality.displayLabel)
    }

    @Test
    fun `quality accepts verified chinese title summary poster region and rating`() {
        val quality = rankingMetadataQuality(entry())

        assertTrue(quality.isComplete)
        assertTrue(quality.missingLabels.isEmpty())
        assertEquals("资料完整", quality.displayLabel)
    }

    @Test
    fun `page coverage counts visible metadata fields without claiming full catalogue coverage`() {
        val coverage = rankingPageMetadataCoverage(
            listOf(
                entry(),
                entry(title = "English Only", summary = null, poster = null, countries = emptyList(), ratings = emptyList())
                    .copy(searchTitle = "English Only")
                    .copy(aggregateScore = null)
            )
        )

        assertEquals(2, coverage.total)
        assertEquals(1, coverage.chineseTitleCount)
        assertEquals(1, coverage.summaryCount)
        assertEquals(1, coverage.posterCount)
        assertEquals(1, coverage.regionCount)
        assertEquals(1, coverage.ratingCount)
        assertEquals("中文名 1/2 · 简介 1/2 · 海报 1/2", coverage.shortLabel)
    }

    @Test
    fun `card semantics names rank score title and missing fields`() {
        val description = rankingCardContentDescription(
            entry(summary = "公开资料概览：《示例片》；当前快照未收录剧情正文。", poster = null),
            preferredTitle = "示例片",
            aggregateScoreLabel = "综合参考分"
        )

        assertTrue(description.contains("第 1 名"))
        assertTrue(description.contains("示例片"))
        assertTrue(description.contains("排名分 8.1"))
        assertTrue(description.contains("简介待补充"))
        assertTrue(description.contains("海报待补充"))
    }

    @Test
    fun `visual contract preserves poster ratio and touch targets`() {
        assertEquals(84, rankingVisualContract.posterWidthDp)
        assertEquals(112, rankingVisualContract.posterHeightDp)
        assertEquals(48, rankingVisualContract.minimumTouchTargetDp)
        assertEquals(3, rankingVisualContract.titleMaxLines)
        assertEquals(4, rankingVisualContract.summaryMaxLines)
    }
}
