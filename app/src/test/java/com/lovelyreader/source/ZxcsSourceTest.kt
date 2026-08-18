package com.lovelyreader.source

import com.lovelyreader.domain.RankingPeriod
import com.lovelyreader.domain.SourceCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZxcsSourceTest {
    private val source = ZxcsSource()

    @Test
    fun parsesRankTilesAsDownloadCapableResults() {
        val html = """
            <mio-tile>
              <a href="/book/1064.html">
                <span class="link">《凡人修仙传》（校对版全本+番外）作者：忘语</span>
                <p class="tile-description">一个普通的山村穷小子开始修仙。</p>
                <span class="downloads">下载量：103263</span>
                <time datetime="2023-05-18 16:06:03">2023-05-18 16:06:03</time>
              </a>
            </mio-tile>
        """.trimIndent()

        val result = source.parseListPage(html).single()

        assertEquals("凡人修仙传", result.title)
        assertEquals("忘语", result.author)
        assertEquals("https://zxcs.zip/book/1064.html", result.bookUrl)
        assertTrue(result.summary.contains("下载量：103263"))
        assertTrue(SourceCapability.TXT_IMPORT in result.capabilities)
        assertTrue(SourceCapability.READ_CHAPTER in result.capabilities)
    }

    @Test
    fun parsesDetailAndAllowsOnlyZxcsTxtDownloads() {
        val html = """
            <title>《我的右眼是神级计算机》（校对版）作者：王自律txt下载_知轩藏书</title>
            <meta name="description" content="我是你眼中的超级人工智能。" />
            <p>内容大小：3.2 MB</p>
            <a href="https://download.zxcs.zip/《我的右眼是神级计算机》（校对版全本）作者：王自律.txt" download id="downloadtxt">下载TXT文件</a>
            <a href="https://example.com/bad.txt" download>伪造下载</a>
        """.trimIndent()

        val detail = source.parseBookDetail("https://zxcs.zip/book/10461.html", html)
        val options = source.parseDownloadOptions(html)

        assertEquals("我的右眼是神级计算机", detail.book.title)
        assertEquals("王自律", detail.book.author)
        assertEquals("可下载TXT并在书架阅读", detail.offlineLabel)
        assertEquals(2, options.size)
        assertTrue(options.first().allowed)
        assertFalse(options.last().allowed)
    }

    @Test
    fun mapsRankingPeriodsToAvailablePages() {
        assertEquals("https://zxcs.zip/rank/topdownload", source.rankingUrl(RankingPeriod.MONTH))
        assertEquals("https://zxcs.zip/rank/postdate", source.rankingUrl(RankingPeriod.YEAR))
        assertEquals("https://zxcs.zip/rank/topdownload", source.rankingUrl(RankingPeriod.TOTAL))
    }
}
