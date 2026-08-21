package com.lovelyreader.source

import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.domain.SizeBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QisuwangSourceTest {
    private val source = QisuwangSource()

    private fun parseHomepage(html: String): CategoryBrowseResult = source.parseHomepagePage(html)

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

    @Test
    fun rejectsCategoriesWithoutAnExactSourcePath() {
        assertEquals("https://m.9qishu.com/yanqing/", source.categoryPageUrl("言情", 1))
        assertEquals("https://m.9qishu.com/dushi/index_2.html", source.categoryPageUrl("现代都市", 2))
        assertEquals(null, source.categoryPageUrl("现代言情", 1))
        assertEquals(null, source.categoryPageUrl("灵异", 1))
        assertEquals(null, source.categoryPageUrl("不存在的分类", 1))
    }

    @Test
    fun allCategoryUsesOneRequestPerBatchAndRotatesColumnsBeforePages() {
        assertEquals("https://m.9qishu.com/yanqing/", source.categoryPageUrl("全部", 1))
        assertEquals("https://m.9qishu.com/dushi/", source.categoryPageUrl("全部", 2))
        assertEquals("https://m.9qishu.com/tongren/", source.categoryPageUrl("全部", 7))
        assertEquals(null, source.categoryPageUrl("全部", 8))
        assertTrue(source.allCategoryHasMore(6))
        assertFalse(source.allCategoryHasMore(7))
    }

    @Test
    fun categoryParserRequiresRealImgTextListAndScopedPages() {
        val valid = """
            <a href="/yanqing/txt999.html" class="pic">容器外书</a>
            <div class="module"><ul class="imgtextlist">
              <li><a href="/yanqing/txt1.html" class="pic"><img src="/a.jpg" /></a>
              <p class="title"><a href="/yanqing/txt1.html">《容器内书》</a></p>
              <p class="author">作者：<a href="/writer/a/">甲</a></p></li>
            </ul><div class="pages"><a href='/yanqing/index_2.html'>下一页</a></div></div>
            <a href='/yanqing/index_99.html'>容器外分页</a>
        """.trimIndent()
        val verification = "<html><form id='challenge-form'>安全验证</form></html>"
        val malformed = "<ul class='imgtextlist'><li>结构已变化</li></ul><div class='pages'>页次：1/1</div>"
        val emptyLastPage = "<ul class='imgtextlist'> </ul><div class='pages'>页次：2/2</div>"

        val parsed = source.parseCategoryListPage(valid, 1)
        assertTrue(parsed is CategoryBrowseResult.Success)
        parsed as CategoryBrowseResult.Success
        assertEquals(listOf("容器内书"), parsed.items.map { it.title })
        assertTrue(parsed.hasMore)
        assertTrue(source.parseCategoryListPage(verification, 1) is CategoryBrowseResult.Failure)
        assertTrue(source.parseCategoryListPage(malformed, 1) is CategoryBrowseResult.Failure)
        val empty = source.parseCategoryListPage(emptyLastPage, 2)
        assertTrue(empty is CategoryBrowseResult.Success)
        empty as CategoryBrowseResult.Success
        assertTrue(empty.items.isEmpty())
        assertFalse(empty.hasMore)
    }

    @Test
    fun categoryParserMarksPartiallyMalformedRealList() {
        val html = """
            <div class="module"><ul class="imgtextlist">
              <li><a href="/yanqing/txt1.html" class="pic"><img src="/a.jpg" /></a>
              <p class="title"><a href="/yanqing/txt1.html">《有效书》</a></p>
              <p class="author">作者：<a>甲</a></p></li>
              <li>损坏条目</li>
            </ul><div class="pages">页次：1/1</div></div>
        """.trimIndent()

        val parsed = source.parseCategoryListPage(html, 1) as CategoryBrowseResult.Success

        assertEquals(listOf("有效书"), parsed.items.map { it.title })
        assertTrue(parsed.partialFailure)
    }

    @Test
    fun discoveryClientUsesRealShortSocketTimeouts() {
        val timeouts = source.discoveryTimeoutConfiguration()
        assertTrue(timeouts.connectMillis <= 6_000)
        assertTrue(timeouts.readMillis <= 8_000)
    }

    @Test
    fun homepageParserRejectsVerificationPageInsteadOfReturningEmptySuccess() {
        val result = parseHomepage("<html><form id='challenge-form'>安全验证</form></html>")

        assertTrue(result is CategoryBrowseResult.Failure)
    }

    @Test
    fun homepageParserRejectsMissingListContainerInsteadOfReturningEmptySuccess() {
        val result = parseHomepage("<main><a href='/yanqing/txt1.html'>《不是首页列表》</a></main>")

        assertTrue(result is CategoryBrowseResult.Failure)
    }

    @Test
    fun homepageParserRejectsChangedListItemStructure() {
        val result = parseHomepage(
            "<ul class='imgtextlist'><li>结构已变化</li></ul>"
        )

        assertTrue(result is CategoryBrowseResult.Failure)
    }

    @Test
    fun homepageParserAcceptsAnExplicitlyEmptyRealList() {
        val result = parseHomepage("<ul class='imgtextlist'></ul>")

        assertTrue(result is CategoryBrowseResult.Success)
        result as CategoryBrowseResult.Success
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun homepageParserReturnsItemsFromTheVerifiedImageTextList() {
        val result = parseHomepage(
            """
            <ul class='imgtextlist'>
              <li><a href="/yanqing/txt7.html" class="pic"><img src="/cover.jpg" /></a>
                <p class="title"><a href="/yanqing/txt7.html">《首页书》</a></p>
                <p class="author">作者：<a href="/writer/a/">首页作者</a></p>
              </li>
            </ul>
            """.trimIndent()
        )

        assertTrue(result is CategoryBrowseResult.Success)
        result as CategoryBrowseResult.Success
        assertEquals(listOf("首页书"), result.items.map { it.title })
    }

}
