package com.lovelyreader.ui.video.ranking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.lovelyreader.video.ranking.rankingBayesianScore

class RankingPresentationLabelTest {
    @Test
    fun ranking_prefers_chinese_alias_for_display_and_search() {
        assertEquals(
            "绝命毒师",
            rankingPreferredDisplayTitle("Breaking Bad", "Breaking Bad", listOf("绝命毒师"))
        )
        assertEquals(
            "Breaking Bad",
            rankingPreferredDisplayTitle("Breaking Bad", "Breaking Bad", emptyList())
        )
        assertEquals(
            "绝命毒师",
            rankingPreferredDisplayTitle("Breaking Bad / 绝命毒师", "Breaking Bad", emptyList())
        )
        assertEquals(
            "绝命毒师",
            rankingPreferredDisplayTitle("绝命毒师 (Breaking Bad)", "Breaking Bad", emptyList())
        )
    }

    @Test
    fun ranking_normalizes_traditional_titles_to_simplified_chinese() {
        assertEquals(
            "星际穿越",
            rankingPreferredDisplayTitle("星際穿越", "Interstellar", listOf("星际穿越"))
        )
        assertEquals(
            "帝国反击战",
            rankingPreferredDisplayTitle("帝國反擊戰", "The Empire Strikes Back", emptyList())
        )
        assertEquals(
            "颁奖典礼",
            rankingPreferredDisplayTitle("頒獎典禮", "Award Ceremony", emptyList())
        )
    }

    @Test
    fun ranking_keeps_a_verified_display_title_over_a_japanese_alias() {
        assertEquals(
            "照明商店",
            rankingPreferredDisplayTitle(
                "照明商店",
                "Jomyeonggage",
                listOf("照明店の客人たち", "Light Shop")
            )
        )
    }

    @Test
    fun ranking_score_uses_votes_as_confidence_not_an_unbounded_multiplier() {
        val highVoteSevenPointTwo = rankingBayesianScore(7.2, 43_571)
        val lowerVoteSevenPointSix = rankingBayesianScore(7.6, 7_563)

        assertEquals(7.2, highVoteSevenPointTwo, 0.02)
        assertTrue(lowerVoteSevenPointSix > highVoteSevenPointTwo)
        assertTrue(rankingBayesianScore(10.0, 100) < 8.0)
        assertTrue(rankingBayesianScore(10.0, 100) <= 10.0)
    }

    @Test
    fun ranking_score_tag_uses_the_actual_sort_score() {
        assertEquals("28.3", rankingScoreTag(28.345))
    }

    @Test
    fun ranking_genres_prefer_simplified_chinese_labels() {
        assertEquals("恐怖", rankingGenreLabel("Horror"))
        assertEquals("科幻", rankingGenreLabel("Science-Fiction"))
        assertEquals("悬疑", rankingGenreLabel("悬疑"))
    }

    @Test
    fun ranking_opens_with_region_and_year_filters_discoverable() {
        assertEquals(true, rankingFiltersExpandedByDefault)
    }

    @Test
    fun ranking_card_uses_plain_score_label_for_end_users() {
        assertEquals("评分", rankingBoardScoreLabel("aggregate"))
        assertEquals("IMDb评分", rankingBoardScoreLabel("source-imdb"))
        assertEquals("剧集评分", rankingBoardScoreLabel("source-tvmaze"))
        assertEquals("韩国", rankingRegionLabel("KR"))
        assertEquals("剧集评分", rankingSourceLabel("tvmaze"))
        assertEquals("IMDb评分", rankingSourceLabel("imdb"))
    }

    @Test
    fun ranking_summary_removes_internal_snapshot_copy() {
        assertEquals(
            "《绝命毒师》是2008年美国剧情、犯罪作品",
            rankingDisplaySummary("公开资料概览：《绝命毒师》是2008年美国剧情、犯罪作品；当前快照未收录剧情正文。")
        )
        assertEquals(
            "真实简介仍保留",
            rankingDisplaySummary("真实简介仍保留")
        )
        assertEquals(null, rankingDisplaySummary("公开资料概览：《空条目》；当前快照未收录剧情正文。"))
    }
}
