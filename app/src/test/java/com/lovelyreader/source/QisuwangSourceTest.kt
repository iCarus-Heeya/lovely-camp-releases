package com.lovelyreader.source

import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.domain.SizeBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QisuwangSourceTest {
    private val source = QisuwangSource()

    @Test
    fun parsesListPageIntoDownloadCapableSearchResults() {
        val html = """
            <li>
              <a href="/yanqing/txt59683.html" class="pic"><img src="https://pic.9qishu.com/a.jpg" alt="亡夫兄长竟是她曾经白月光小说" /></a>
              <p class="title"><a href="/yanqing/txt59683.html" title="亡夫兄长竟是她曾经白月光TXT下载" class="orange">《亡夫兄长竟是她曾经白月光》</a></p>
              <p class="author">作者：<a href="/writer/不落言笙/">不落言笙</a></p>
              <p class="author">大小：968 KB</p>
              <p class="intro">简介：她曾钟情一人。</p>
            </li>
        """.trimIndent()

        val result = source.parseListPage(html).single()

        assertEquals("亡夫兄长竟是她曾经白月光", result.title)
        assertEquals("不落言笙", result.author)
        assertEquals("https://m.9qishu.com/yanqing/txt59683.html", result.bookUrl)
        assertTrue(SourceCapability.TXT_IMPORT in result.capabilities)
        assertTrue(SourceCapability.READ_CHAPTER in result.capabilities)
    }

    @Test
    fun parsedListResultsCarrySizeForBrowseFiltering() {
        val html = """
            <li>
              <a href="/yanqing/txt59683.html" class="pic"><img src="https://pic.9qishu.com/a.jpg" /></a>
              <p class="title"><a href="/yanqing/txt59683.html">《亡夫兄长竟是她曾经白月光》</a></p>
              <p class="author">作者：<a href="/writer/不落言笙/">不落言笙</a></p>
              <p class="author">大小：968 KB</p>
              <p class="intro">简介：她曾钟情一人。</p>
            </li>
        """.trimIndent()

        val result = source.parseListPage(html).single()

        assertTrue(result.summary.contains("968 KB"))
        assertTrue(SizeBand("0.5-1M", 512, 1024).contains(968))
    }

    @Test
    fun parsesBookDetailAndDownloadOptions() {
        val detail = source.parseBookDetail(
            "https://m.9qishu.com/wangyou/2017/08/txt7001.html",
            """
                <meta property="og:novel:book_name" content="全职高手"/>
                <meta property="og:novel:author" content="蝴蝶蓝"/>
                <meta property="og:description" content="网游荣耀高手重返巅峰。"/>
                <meta property="og:novel:category" content="网游竞技"/>
                <meta property="og:novel:status" content="完结"/>
                <p class="gray">大小：<span class="num">10 MB</span></p>
                <a href="/down/txt6c7001qishu.html" class="bdbtn greenBtn">进入小说下载地址</a>
            """.trimIndent()
        )
        val options = source.parseDownloadOptions(
            """
                <a href=" https://down.qishu99.cc/d/file/down/2017/08/00/全职高手.txt  "> 下载地址1</a>
                <a href=" https://example.com/全职高手.txt  "> 下载地址2</a>
            """.trimIndent()
        )

        assertEquals("全职高手", detail.book.title)
        assertEquals("可下载TXT并在书架阅读", detail.offlineLabel)
        assertEquals(2, options.size)
        assertTrue(options.first().allowed)
        assertFalse(options.last().allowed)
    }
}
