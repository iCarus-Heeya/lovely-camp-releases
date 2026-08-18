package com.lovelyreader.source

import com.lovelyreader.domain.SizeBand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregatedNovelCatalogTest {

    @Test
    fun randomBrowse_filtersByCategory() {
        val catalog = AggregatedNovelCatalog
        catalog.categories.forEach { category ->
            val results = catalog.randomBrowse(
                category = category,
                finishedOnly = false,
                sizeBand = SizeBand("all", 0, 999_999)
            )
            assertTrue("分类 $category 应在精选书单中有结果", results.isNotEmpty())
            results.forEach {
                assertTrue(
                    "结果《${it.title}》的分类应为 $category，简介：${it.summary}",
                    it.summary.contains(category)
                )
            }
        }
    }

    @Test
    fun randomBrowse_filtersBySizeBand() {
        val catalog = AggregatedNovelCatalog
        catalog.sizeBands.forEach { band ->
            val categoryWithMatch = catalog.categories.firstOrNull { cat ->
                catalog.randomBrowse(cat, false, band).isNotEmpty()
            }
            assertTrue("大小 ${band.label} 应至少有一个分类有匹配书籍", categoryWithMatch != null)
            val results = catalog.randomBrowse(categoryWithMatch!!, false, band)
            assertTrue("大小 ${band.label} 应至少有一条结果", results.isNotEmpty())
            results.forEach { result ->
                val sizeKb = extractSizeKb(result.summary)
                assertTrue(
                    "《${result.title}》大小 $sizeKb KB 不在 ${band.label} 区间 [${band.minKb}, ${band.maxKb}]",
                    band.contains(sizeKb)
                )
            }
        }
    }

    @Test
    fun randomBrowse_sizeBandStrictness() {
        val catalog = AggregatedNovelCatalog
        val category = "古言宫斗"
        val smallBand = catalog.sizeBands.first()
        val largeBand = catalog.sizeBands.last()

        val smallResults = catalog.randomBrowse(category, false, smallBand)
        val largeResults = catalog.randomBrowse(category, false, largeBand)

        assertTrue("${smallBand.label} 应有结果", smallResults.isNotEmpty())
        assertTrue("${largeBand.label} 应有结果", largeResults.isNotEmpty())
        assertFalse(
            "同一分类下不同大小区间不应完全相同",
            smallResults.map { it.title }.toSet() == largeResults.map { it.title }.toSet()
        )
    }

    @Test
    fun randomBrowse_finishedOnly_matchesAllFinishedCatalog() {
        val catalog = AggregatedNovelCatalog
        val category = "都市职场"
        val results = catalog.randomBrowse(category, finishedOnly = true, SizeBand("all", 0, 999_999))
        assertTrue("完结筛选应有结果", results.isNotEmpty())
        results.forEach {
            assertTrue("《${it.title}》简介应包含已完结", it.summary.contains("已完结"))
        }
    }

    @Test
    fun randomBrowse_skipsSeenTitles() {
        val catalog = AggregatedNovelCatalog
        val category = "古言宫斗"
        val all = catalog.randomBrowse(category, false, SizeBand("all", 0, 999_999))
        assertTrue("测试分类应有数据", all.size >= 2)
        val firstTitle = all.first().title
        val second = catalog.randomBrowse(
            category,
            false,
            SizeBand("all", 0, 999_999),
            seenTitles = setOf(firstTitle)
        )
        assertTrue("排除已见书名后仍应返回结果", second.isNotEmpty())
        assertTrue("排除的书不应再出现", second.none { it.title == firstTitle })
    }

    @Test
    fun randomBrowse_defaultMatchesFirstCategoryAndSizeBand() {
        val catalog = AggregatedNovelCatalog
        val category = catalog.categories.first()
        val sizeBand = catalog.sizeBands.first()
        val results = catalog.randomBrowse(category, finishedOnly = true, sizeBand = sizeBand)
        assertTrue("默认分类+默认大小应有结果", results.isNotEmpty())
        results.forEach {
            assertTrue("《${it.title}》应在分类 $category", it.summary.contains(category))
            assertTrue("《${it.title}》大小应在 ${sizeBand.label}", sizeBand.contains(extractSizeKb(it.summary)))
        }
    }

    private fun extractSizeKb(summary: String): Int {
        val match = Regex("([0-9]+)KB", RegexOption.IGNORE_CASE).find(summary)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
