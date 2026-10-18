import unittest
from unittest.mock import patch

from build_catalog import (
    MOVIE_COUNTRY_BY_TITLE,
    REGIONAL_MOVIE_SEEDS,
    REGIONAL_MOVIE_TITLES,
    SHOW_COUNTRY_BY_TITLE,
    choose_localized_title,
    extract_chinese_title,
    chinese_summary_or_fallback,
    genre_label,
    preferred_localized_title,
    IMDB_METADATA_OVERRIDES,
    select_stratified_tv_candidates,
    show_country_codes,
    is_approved_poster_url,
    localization_candidates,
    localize_prominent_items,
    regional_tvmaze_queries,
    ranking_board_ids,
    ranking_bayesian_score,
    ranking_score_for_board,
    metadata_coverage,
    tvmaze_page_numbers,
    wikidata_cache_needs_refresh,
)


class CatalogPresentationTests(unittest.TestCase):
    @patch("build_catalog.wikipedia_search_summary", return_value=("绝命毒师讲述沃尔特·怀特的故事。", None))
    @patch("build_catalog.wikipedia_localized_title", return_value="绝命毒师")
    def test_prominent_rows_promote_verified_chinese_title_before_summary_enrichment(self, localized, summary):
        item = {
            "canonicalId": "imdb:tt0903747",
            "displayTitle": "Breaking Bad",
            "originalTitle": None,
            "aliases": [],
            "posterUrl": None,
            "summary": "公开资料概览：《Breaking Bad》；当前快照未收录剧情正文。",
            "aggregateScore": 9.5,
            "releaseYear": 2008,
            "countries": ["US"],
            "genres": ["剧情"],
            "searchTitle": "Breaking Bad",
        }

        localize_prominent_items([item], limit=1)

        self.assertEqual(item["displayTitle"], "绝命毒师")
        self.assertEqual(item["searchTitle"], "绝命毒师")
        self.assertIn("Breaking Bad", item["aliases"])
        localized.assert_called_once_with("Breaking Bad")

    def test_wikimedia_thumbnail_hosts_are_approved_without_opening_unknown_hosts(self):
        self.assertTrue(is_approved_poster_url("https://upload.wikimedia.org/wikipedia/zh/a/ab/poster.jpg"))
        self.assertTrue(is_approved_poster_url("https://thumb.wikimedia.org/wikipedia/zh/thumb/a/ab/poster.jpg/330px-poster.jpg"))
        self.assertFalse(is_approved_poster_url("https://example.com/poster.jpg"))

    def test_prefers_chinese_title_and_keeps_original_as_alias(self):
        self.assertEqual(
            choose_localized_title("Breaking Bad", ["绝命毒师", "Ruptura Total"]),
            "绝命毒师",
        )
        self.assertEqual(
            choose_localized_title("The Office", ["The Office", "The Office (US)"]),
            "The Office",
        )
        self.assertEqual(preferred_localized_title("Breaking Bad"), "绝命毒师")
        self.assertEqual(preferred_localized_title("The Untamed"), "陈情令")
        self.assertEqual(preferred_localized_title("Gisaengchung"), "寄生虫")

    def test_extracts_chinese_side_of_bilingual_title(self):
        self.assertEqual(extract_chinese_title("Breaking Bad / 绝命毒师"), "绝命毒师")
        self.assertEqual(extract_chinese_title("绝命毒师 (Breaking Bad)"), "绝命毒师")
        self.assertEqual(extract_chinese_title("JOJO的奇妙冒险：钢球重跑"), "JOJO的奇妙冒险：钢球重跑")
        self.assertIsNone(extract_chinese_title("Bisaahee"))

    def test_rejects_japanese_script_as_a_chinese_title_candidate(self):
        self.assertIsNone(extract_chinese_title("俺と世界の絶望"))
        self.assertIsNone(extract_chinese_title("照明店の客人たち"))
        self.assertEqual(extract_chinese_title("照明店的客人们"), "照明店的客人们")

    def test_curated_korean_series_use_simplified_chinese_names(self):
        self.assertEqual(
            preferred_localized_title("Seuwiteuhom", ["俺と世界の絶望", "Sweet Home"]),
            "甜蜜家园",
        )
        self.assertEqual(
            preferred_localized_title("Jomyeonggage", ["照明店の客人たち", "Light Shop"]),
            "照明店的客人们",
        )

    def test_votes_only_change_confidence_and_keep_ranking_score_bounded(self):
        high_vote_seven_point_two = ranking_bayesian_score(7.2, 43_571)
        lower_vote_seven_point_six = ranking_bayesian_score(7.6, 7_563)
        self.assertAlmostEqual(high_vote_seven_point_two, 7.2, places=2)
        self.assertGreater(lower_vote_seven_point_six, high_vote_seven_point_two)
        self.assertLess(ranking_bayesian_score(10.0, 100), 8.0)
        self.assertLessEqual(ranking_bayesian_score(10.0, 100), 10.0)

    def test_regional_movie_seed_metadata_is_localized_and_country_scoped(self):
        self.assertTrue(REGIONAL_MOVIE_SEEDS["CN"])
        self.assertTrue(REGIONAL_MOVIE_SEEDS["KR"])
        self.assertTrue(REGIONAL_MOVIE_SEEDS["TH"])
        for title in REGIONAL_MOVIE_TITLES.values():
            self.assertRegex(title, r"^[\u3400-\u9fff0-9：·]+$")
        for title in ("The Shawshank Redemption", "Gisaengchung"):
            self.assertTrue(MOVIE_COUNTRY_BY_TITLE[title])

    def test_country_fallback_covers_known_shows_without_network_country(self):
        self.assertEqual(show_country_codes({"name": "Moving"}), ["KR"])
        self.assertEqual(show_country_codes({"name": "I Told Sunset About You"}), ["TH"])
        self.assertEqual(show_country_codes({"name": "The Crown"}), ["GB"])
        self.assertEqual(show_country_codes({"name": "unknown show"}), [])
        self.assertEqual(SHOW_COUNTRY_BY_TITLE["The Rap of China"], ["CN"])

    def test_maps_every_common_tvmaze_genre_to_chinese(self):
        for raw in (
            "Anime",
            "Biography",
            "Children",
            "Documentary",
            "Game Show",
            "Legal",
            "Medical",
            "Nature",
            "News",
            "Reality",
            "Sci-Fi",
            "Sport",
            "Supernatural",
            "Talk Show",
            "Short",
        ):
            self.assertRegex(genre_label(raw), r"^[\u4e00-\u9fff]+$")

    def test_does_not_leak_english_summary_to_chinese_ui(self):
        self.assertEqual(
            chinese_summary_or_fallback("Breaking Bad follows Walter White.", "绝命毒师"),
            "公开资料概览：《绝命毒师》；当前快照未收录剧情正文。",
        )
        self.assertEqual(
            chinese_summary_or_fallback("绝命毒师讲述沃尔特·怀特的故事。", "绝命毒师"),
            "绝命毒师讲述沃尔特·怀特的故事。",
        )
        self.assertEqual(
            chinese_summary_or_fallback(
                "Breaking Bad follows Walter White.",
                "绝命毒师",
                year=2008,
                countries=["US"],
                genres=["剧情", "犯罪"],
            ),
            "公开资料概览：《绝命毒师》是2008年美国剧情、犯罪作品；当前快照未收录剧情正文。",
        )

    def test_recent_co_production_without_official_chinese_aka_is_marked_as_translation(self):
        self.assertEqual(IMDB_METADATA_OVERRIDES["tt34909040"]["title"], "约会游戏（暂译）")

    def test_dead_friend_forever_has_a_reviewed_poster_override(self):
        self.assertEqual(IMDB_METADATA_OVERRIDES["tt30581662"]["title"], "永远的逝友")
        self.assertEqual(
            IMDB_METADATA_OVERRIDES["tt30581662"]["poster"],
            "https://static.tvmaze.com/uploads/images/original_untouched/484/1210725.jpg",
        )


class CatalogCoverageTests(unittest.TestCase):
    def test_metadata_coverage_counts_real_user_visible_fields(self):
        coverage = metadata_coverage([
            {
                "displayTitle": "示例片",
                "summary": "这是中文简介。",
                "posterUrl": "asset://ranking/posters/example.img",
                "countries": ["CN"],
                "aggregateScore": 8.0,
                "sourceRatings": [],
            },
            {
                "displayTitle": "Example Only",
                "summary": "公开资料概览：《Example Only》；当前快照未收录剧情正文。",
                "posterUrl": None,
                "countries": [],
                "aggregateScore": None,
                "sourceRatings": [],
            },
        ])
        self.assertEqual(
            coverage,
            {
                "total": 2,
                "chineseTitle": 1,
                "summary": 1,
                "poster": 1,
                "region": 1,
                "rating": 1,
            },
        )

    def test_tvmaze_index_scope_is_not_a_small_top_page_sample(self):
        pages = list(tvmaze_page_numbers())
        self.assertEqual(pages[0], 0)
        self.assertEqual(pages[-1], 379)
        self.assertEqual(len(pages), 380)

    def test_wikidata_cache_without_optional_poster_is_still_a_cache_hit(self):
        self.assertFalse(wikidata_cache_needs_refresh({"countries": ["CN"], "title": "示例", "description": "电影"}))
        self.assertTrue(wikidata_cache_needs_refresh({}))

    def test_recent_china_queries_are_deduplicated_for_metadata_enrichment(self):
        items = [
            {"displayTitle": "逐玉", "releaseYear": 2026, "countries": ["CN"]},
            {"displayTitle": "逐玉", "releaseYear": 2026, "countries": ["CN"]},
            {"displayTitle": "陈情令", "releaseYear": 2019, "countries": ["CN"]},
            {"displayTitle": "请回答1988", "releaseYear": 2015, "countries": ["KR"]},
        ]
        self.assertEqual(regional_tvmaze_queries(items, current_year=2026), ["逐玉"])

    def test_localization_budget_prioritizes_all_eligible_china_rows(self):
        items = [
            {"canonicalId": "imdb:us", "aggregateScore": 9.9, "countries": ["US"], "displayTitle": "美国片", "posterUrl": None, "summary": "公开资料概览"},
            {"canonicalId": "imdb:cn-old", "aggregateScore": 5.0, "countries": ["CN"], "displayTitle": "中国老片", "posterUrl": None, "summary": "公开资料概览"},
            {"canonicalId": "imdb:cn-new", "aggregateScore": 4.0, "countries": ["CN"], "displayTitle": "中国新片", "posterUrl": None, "summary": "公开资料概览"},
            {"canonicalId": "imdb:kr", "aggregateScore": 9.8, "countries": ["KR"], "displayTitle": "韩国片", "posterUrl": None, "summary": "公开资料概览"},
        ]
        selected = localization_candidates(items, limit=2)
        self.assertEqual([item["canonicalId"] for item in selected], ["imdb:cn-old", "imdb:cn-new"])

    def test_catalog_declares_localized_source_boards(self):
        self.assertEqual(
            ranking_board_ids(),
            ("aggregate", "source-imdb", "source-tvmaze"),
        )

    def test_source_board_score_requires_the_declared_source(self):
        item = {
            "canonicalId": "imdb:show",
            "aggregateScore": 8.4,
            "sourceRatings": [
                {"sourceId": "imdb", "value": 8.0, "voteCount": 1200},
                {"sourceId": "tvmaze", "value": 9.1, "voteCount": None},
            ],
        }
        self.assertEqual(ranking_score_for_board(item, "aggregate"), 8.4)
        self.assertEqual(ranking_score_for_board(item, "source-imdb"), 8.0)
        self.assertEqual(ranking_score_for_board(item, "source-tvmaze"), 9.1)
        self.assertIsNone(ranking_score_for_board({"sourceRatings": []}, "source-tvmaze"))

    def test_stratified_selection_keeps_recent_and_regional_items_before_score_fill(self):
        def candidate(index, region, year, score):
            return score, {
                "show": {
                    "id": index,
                    "name": f"show-{index}",
                    "premiered": f"{year}-01-01",
                    "network": {"country": {"code": region}},
                },
                "rating": score,
                "imdb": None,
                "score": score,
                "media": "SERIES",
            }

        candidates = [
            candidate(1, "US", 2023, 8.0),
            candidate(2, "CN", 2022, 7.0),
            candidate(3, "KR", 2021, 6.9),
            candidate(4, "TH", 2020, 6.8),
            candidate(5, "GB", 2014, 9.9),
            candidate(6, "US", 2008, 9.8),
        ]

        selected = select_stratified_tv_candidates(candidates, "SERIES", limit=6)
        ids = {item["show"]["id"] for item in selected}
        self.assertTrue({1, 2, 3, 4}.issubset(ids))
        self.assertLessEqual(len(selected), 6)


if __name__ == "__main__":
    unittest.main()
