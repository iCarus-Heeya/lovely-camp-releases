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
    fun technical_snapshot_versions_are_localized_for_end_users() {
        assertEquals("分层综合参考 v2", rankingMethodologyLabel("weighted-stratified-v2"))
        assertEquals("分层综合参考 v3", rankingMethodologyLabel("weighted-stratified-v3"))
        assertEquals("完整公开评分目录（票数≥100）· IMDb + TVmaze剧集数据", rankingMethodologyLabel("imdb-rated-scope-v4-tvmaze-full-index"))
        assertEquals("完整公开评分目录（票数≥100）· 排名分按评分与票数置信度排序 · IMDb + TVmaze剧集数据", rankingMethodologyLabel("imdb-rated-scope-v5-tvmaze-full-index"))
        assertEquals("完整公开评分目录（票数≥100）· 贝叶斯加权排序（票数仅影响置信度） · IMDb + TVmaze剧集数据", rankingMethodologyLabel("imdb-rated-scope-v6-tvmaze-full-index"))
        assertEquals("完整公开评分目录（票数≥100）· 偏好类型优先 + 贝叶斯加权排序 · IMDb + TVmaze剧集数据", rankingMethodologyLabel("imdb-rated-scope-v7-preferred-genres-tvmaze-full-index"))
        assertEquals("公开评分目录（票数≥100）· 按评分降序", rankingMethodologyLabel("imdb-rated-scope-v3-tvmaze-enriched"))
        assertEquals("中文身份映射", rankingIdentityLabel("imdb-tvmaze-wikipedia-zh-v2"))
        assertEquals("综合参考算法", rankingMethodologyLabel("debug-v1"))
        assertEquals("综合参考分", rankingAggregateScoreLabel("imdb-rated-scope-v4-tvmaze-full-index"))
        assertEquals("综合参考分", rankingBoardScoreLabel("aggregate", "imdb-rated-scope-v4-tvmaze-full-index"))
        assertEquals("IMDb评分", rankingBoardScoreLabel("source-imdb", "imdb-rated-scope-v4-tvmaze-full-index"))
        assertEquals("剧集评分", rankingBoardScoreLabel("source-tvmaze", "imdb-rated-scope-v4-tvmaze-full-index"))
        assertEquals("综合参考", rankingAggregateScoreLabel("debug-v1"))
        assertEquals("身份映射", rankingIdentityLabel("debug-v1"))
        assertEquals("韩国", rankingRegionLabel("KR"))
        assertEquals("剧集评分", rankingSourceLabel("tvmaze"))
        assertEquals("IMDb评分", rankingSourceLabel("imdb"))
    }
}
