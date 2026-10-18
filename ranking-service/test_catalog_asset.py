import json
import hashlib
import unittest
from pathlib import Path


CATALOG = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "ranking" / "catalog.json"
CATALOG_INDEX = CATALOG.parent / "catalog-index.json"
ITEM_ROWS = CATALOG.parent / "items.ndjson"


class FullCatalogAssetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        cls.items = cls.catalog["items"]

    def test_asset_is_not_a_small_top_n_snapshot(self):
        self.assertGreaterEqual(len(self.items), 1000)
        self.assertEqual(self.catalog["debugFixture"], False)
        self.assertEqual(self.catalog["testDataOnly"], False)

    def test_china_has_real_rows_for_recent_years(self):
        expected_minimum = {2024: 112, 2025: 102, 2026: 31}
        for year, minimum in expected_minimum.items():
            rows = [item for item in self.items if "CN" in item.get("countries", []) and item.get("releaseYear") == year]
            self.assertGreaterEqual(
                len(rows), minimum,
                f"CN {year} must retain the verified full-scope row count; a zero/short sample is a regression",
            )

    def test_recent_china_rows_have_chinese_display_names(self):
        for year in (2024, 2025, 2026):
            rows = [item for item in self.items if "CN" in item.get("countries", []) and item.get("releaseYear") == year]
            self.assertTrue(rows)
            self.assertTrue(
                all(any("\u3400" <= char <= "\u9fff" for char in item.get("displayTitle", "")) for item in rows),
                f"CN {year} must not leak an English-only title into the user-facing filter",
            )

    def test_recent_china_rows_are_not_release_market_only_placeholders(self):
        for year in (2024, 2025, 2026):
            rows = [item for item in self.items if "CN" in item.get("countries", []) and item.get("releaseYear") == year]
            self.assertTrue(
                any(item.get("countrySource") in {"wikidata-p495", "wikidata-description", "reviewed-recent-title-map", "tvmaze-network"} for item in rows),
                f"CN {year} must have a structured or reviewed production-country identity",
            )

    def test_china_titles_are_chinese_first(self):
        rows = [item for item in self.items if "CN" in item.get("countries", [])]
        self.assertGreaterEqual(len(rows), 100)
        cjk = sum(any("\u3400" <= char <= "\u9fff" for char in item["displayTitle"]) for item in rows)
        self.assertGreaterEqual(cjk / len(rows), 0.95)

    def test_known_global_titles_use_the_verified_chinese_display_name(self):
        by_id = {item["canonicalId"]: item for item in self.items}
        self.assertEqual(by_id["imdb:tt0903747"]["displayTitle"], "绝命毒师")
        self.assertEqual(by_id["imdb:tt0898266"]["displayTitle"], "生活大爆炸")
        self.assertEqual(by_id["imdb:tt6468322"]["displayTitle"], "紙房子")

    def test_korean_series_do_not_expose_japanese_aliases_as_primary_titles(self):
        by_id = {item["canonicalId"]: item for item in self.items}
        self.assertEqual(by_id["imdb:tt11612120"]["displayTitle"], "甜蜜家园")
        self.assertEqual(by_id["imdb:tt31014115"]["displayTitle"], "照明店的客人们")

    def test_dead_friend_forever_uses_the_reviewed_poster_asset(self):
        by_id = {item["canonicalId"]: item for item in self.items}
        self.assertEqual(by_id["imdb:tt30581662"]["displayTitle"], "永远的逝友")
        self.assertEqual(
            by_id["imdb:tt30581662"]["posterUrl"],
            "asset://ranking/posters/imdb_tt30581662.img",
        )

    def test_mixed_script_display_rows_do_not_hide_a_pure_chinese_alias(self):
        def has_han(value):
            return any("\u3400" <= char <= "\u9fff" for char in str(value or ""))

        def has_non_chinese_script(value):
            return any(
                "\u3040" <= char <= "\u30ff"
                or "\u1100" <= char <= "\u11ff"
                or "\u3130" <= char <= "\u318f"
                or "\uac00" <= char <= "\ud7af"
                for char in str(value or "")
            )

        leaks = []
        for item in self.items:
            display = item.get("displayTitle")
            aliases = [*(item.get("aliases") or []), item.get("originalTitle")]
            has_pure_alias = any(has_han(alias) and not has_non_chinese_script(alias) for alias in aliases)
            if has_pure_alias and has_non_chinese_script(display):
                leaks.append(item.get("canonicalId"))
        self.assertEqual(leaks, [])

    def test_english_display_rows_do_not_hide_a_chinese_alias(self):
        def has_cjk(value):
            return any("\u3400" <= char <= "\u9fff" for char in str(value or ""))

        leaks = [
            item for item in self.items
            if not has_cjk(item.get("displayTitle"))
            and (has_cjk(item.get("originalTitle")) or any(has_cjk(alias) for alias in item.get("aliases", [])))
        ]
        self.assertEqual(leaks, [], "a verified Chinese alias must win over an English display title")

    def test_catalog_declares_region_semantics_and_chinese_genres(self):
        provenance = self.catalog.get("provenance", {})
        self.assertIn("regionFieldPolicy", provenance)
        self.assertIn("制作国家", provenance["regionFieldPolicy"])
        self.assertIn("Wikidata", provenance["regionFieldPolicy"])
        self.assertTrue(self.catalog["capabilities"]["genres"])
        self.assertFalse(
            any(any("A" <= char <= "Z" or "a" <= char <= "z" for char in option["label"])
                for option in self.catalog["capabilities"]["genres"]),
            "genre labels must be Chinese-first",
        )

    def test_country_filter_does_not_use_every_release_market_as_production_country(self):
        # Shogun was released in China but its structured production countries
        # are Canada/United States.  This guards the regression where every
        # IMDb AKA market was copied into countries and made China a global
        # release-market filter.
        shogun = next((item for item in self.items if item.get("canonicalId") == "imdb:tt2788316"), None)
        self.assertIsNotNone(shogun)
        self.assertNotIn("CN", shogun.get("countries", []))
        self.assertIn("CN", shogun.get("releaseMarkets", []))

    def test_streaming_runtime_assets_cover_the_same_full_scope(self):
        index = json.loads(CATALOG_INDEX.read_text(encoding="utf-8"))
        self.assertEqual(index["itemCount"], len(self.items))
        self.assertEqual(index["itemAsset"], "ranking/items.ndjson")
        digest = hashlib.sha256()
        count = 0
        with ITEM_ROWS.open(encoding="utf-8") as rows:
            for line in rows:
                self.assertTrue(line.strip())
                json.loads(line)
                digest.update(line.encode("utf-8"))
                count += 1
        self.assertEqual(count, len(self.items))
        self.assertEqual(digest.hexdigest()[:16], index["catalogFingerprint"])

    def test_catalog_index_declares_conservative_metadata_quality_counts(self):
        index = json.loads(CATALOG_INDEX.read_text(encoding="utf-8"))
        coverage = index.get("metadataCoverage")
        self.assertEqual(coverage["total"], len(self.items))
        self.assertGreater(coverage["chineseTitle"], 70_000)
        self.assertGreater(coverage["summary"], 10_000)
        self.assertGreater(coverage["poster"], 12_000)
        self.assertGreater(coverage["region"], 129_000)
        self.assertGreaterEqual(coverage["rating"], len(self.items) - 1)


if __name__ == "__main__":
    unittest.main()
