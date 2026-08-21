package com.lovelyreader.source

import com.lovelyreader.domain.SourceCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QinkanSourceTest {
    private val source = QinkanSource()

    private fun parseHomepage(html: String): CategoryBrowseResult = source.parseHomepagePage(html)

    @Test
    fun parsesListLinksAsSearchResults() {
        val html = """
            <li class="orgNum">
              <font>1</font>
              <u><em>唐家三少</em><a href="/book/12938.html">《斗罗大陆》</a></u>
            </li>
        """.trimIndent()

        val result = source.parseListPage(html).single()

        assertEquals("斗罗大陆", result.title)
        assertEquals("https://www.qinkan.net/book/12938.html", result.bookUrl)
        assertTrue(SourceCapability.TXT_IMPORT in result.capabilities)
        assertTrue(SourceCapability.READ_CHAPTER in result.capabilities)
    }

    @Test
    fun parsesDetailAndAllowsOnlyKnownDownloadHost() {
        val detail = source.parseBookDetail(
            "https://www.qinkan.net/book/12938.html",
            """
                <div class="detail_pic"><img src="/d/pic/98/book.jpg"/></div>
                <div class="detail_right">
                  <h1>《斗罗大陆》txt+epub+mobi全本</h1>
                  <ul>
                    <li class="small">文件大小：5.40 MB</li>
                    <li class="small">书籍类型：异世大陆</li>
                    <li class="small">书籍状态：全本</li>
                    <li class="small">书籍作者：<a href="/writer/唐家三少.html">唐家三少</a></li>
                  </ul>
                </div>
                <div class="showInfo"><p>一个魂师世界的故事。</p></div>
            """.trimIndent()
        )
        val options = source.parseDownloadOptions(
            """
                <a class="downButton" href='https://d3.qinkan.net/d/txt/98/book_qinkan.net.txt'>Txt格式下载</a>
                <a class="downButton" href='https://d3.qinkan.net/d/epub/98/book_qinkan.net.epub'>Epub格式下载</a>
                <a class="downButton" href='https://example.com/book.txt'>伪造TXT下载</a>
            """.trimIndent()
        )

        assertEquals("斗罗大陆", detail.book.title)
        assertEquals("唐家三少", detail.book.author)
        assertEquals("可下载TXT/EPUB/MOBI/AZW3并在书架阅读", detail.offlineLabel)
        assertEquals(3, options.size)
        assertTrue(options[0].allowed)
        assertTrue(options[1].allowed)
        assertFalse(options[2].allowed)
    }

    @Test
    fun mapsOnlyExactDiscoveryCategoriesToRealPages() {
        assertEquals("https://www.qinkan.net/npyq/xd/", source.categoryPageUrl("现代言情", 1))
        assertEquals("https://www.qinkan.net/npyq/yq/index_3.html", source.categoryPageUrl("古代言情", 3))
        assertEquals("https://www.qinkan.net/npyq/cy/", source.categoryPageUrl("穿越架空", 1))
        assertEquals("https://www.qinkan.net/khly/ly/", source.categoryPageUrl("灵异神怪", 1))
        assertEquals(null, source.categoryPageUrl("现言甜宠", 1))
        assertEquals(null, source.categoryPageUrl("不存在的分类", 1))
    }

    @Test
    fun categoryParserReadsOnlyTheRealListBoxAndDetectsNextPage() {
        val html = """
            <aside class="global-rank"><a href="/book/100.html">《全站榜外部书》</a></aside>
            <div class="list">
              <div class="listBox">
                <div class="listTab"><h1>现代言情</h1></div>
                <ul>
                  <li>
                    <div class="s">作者：一舞轻狂<br />大小：2.11百万字<br>更新：2018-04-11</div>
                    <a href="/book/39383.html"><img src="https://www.qinkan.net/d/pic/book.jpg">《重生商女》</a>
                    <div class="u">真实分类简介</div>
                  </li>
                </ul>
                <div class="tspage"><a href='/npyq/xd/index_2.html'>下一页</a></div>
              </div>
            </div>
            <footer><a href="/book/200.html">《页脚外部书》</a></footer>
        """.trimIndent()

        val result = source.parseCategoryListPage(html, page = 1)

        assertTrue(result is CategoryBrowseResult.Success)
        result as CategoryBrowseResult.Success
        assertEquals(listOf("重生商女"), result.items.map { it.title })
        assertEquals("一舞轻狂", result.items.single().author)
        assertTrue(result.hasMore)
    }

    @Test
    fun allCategoryUsesOneRequestPerBatchAndRotatesColumnsBeforePages() {
        assertEquals("https://www.qinkan.net/npyq/", source.categoryPageUrl("全部", 1))
        assertEquals("https://www.qinkan.net/xdds/", source.categoryPageUrl("全部", 2))
        assertEquals("https://www.qinkan.net/mwtr/", source.categoryPageUrl("全部", 8))
        assertEquals(null, source.categoryPageUrl("全部", 9))
        assertTrue(source.allCategoryHasMore(7))
        assertFalse(source.allCategoryHasMore(8))
    }

    @Test
    fun categoryParserScopesPaginationAndRejectsMalformedItems() {
        val malformed = """
            <div class="listBox"><ul><li><a href="/book/1.html">没有书名结构</a></li></ul>
            <div class="tspage">页次：1/1</div></div>
            <a href="/npyq/xd/index_2.html">容器外下一页</a>
        """.trimIndent()
        val emptyLastPage = """
            <div class="listBox"><ul>  </ul><div class="tspage">页次：2/2</div></div>
            <a href="/npyq/xd/index_3.html">容器外伪下一页</a>
        """.trimIndent()

        assertTrue(source.parseCategoryListPage(malformed, 1) is CategoryBrowseResult.Failure)
        val empty = source.parseCategoryListPage(emptyLastPage, 2)
        assertTrue(empty is CategoryBrowseResult.Success)
        empty as CategoryBrowseResult.Success
        assertTrue(empty.items.isEmpty())
        assertFalse(empty.hasMore)
    }

    @Test
    fun categoryParserDoesNotEscapeAClosedListBoxIntoAnUnrelatedList() {
        val html = """
            <div class="listBox"><div class="notice">栏目维护中</div></div>
            <ul>
              <li><div class="s">作者：外部作者</div><a href="/book/9.html">《外部书》</a><div class="u">外部简介</div></li>
            </ul>
            <div class="tspage"><a href="/npyq/index_2.html">下一页</a></div>
        """.trimIndent()

        assertTrue(source.parseCategoryListPage(html, 1) is CategoryBrowseResult.Failure)
    }

    @Test
    fun categoryParserKeepsSameTitleDifferentAuthorsAndMarksPartialParse() {
        val html = """
            <div class="listBox"><ul>
              <li><div class="s">作者：甲</div><a href="/book/1.html">《同名书》</a><div class="u">甲版</div></li>
              <li><div class="s">作者：乙</div><a href="/book/2.html">《同名书》</a><div class="u">乙版</div></li>
              <li><a href="/broken.html">损坏条目</a></li>
            </ul><div class="tspage">页次：1/1</div></div>
        """.trimIndent()

        val parsed = source.parseCategoryListPage(html, 1) as CategoryBrowseResult.Success

        assertEquals(listOf("甲", "乙"), parsed.items.map { it.author })
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
        val result = parseHomepage("<main><a href='/book/1.html'>《不是首页列表》</a></main>")

        assertTrue(result is CategoryBrowseResult.Failure)
    }

    @Test
    fun homepageParserRejectsChangedListItemStructure() {
        val result = parseHomepage(
            "<div class='listBox'><ul><li><a href='/book/1.html'>结构已变化</a></li></ul></div>"
        )

        assertTrue(result is CategoryBrowseResult.Failure)
    }

    @Test
    fun homepageParserAcceptsAnExplicitlyEmptyRealList() {
        val result = parseHomepage("<div class='listBox'><ul></ul></div>")

        assertTrue(result is CategoryBrowseResult.Success)
        result as CategoryBrowseResult.Success
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun homepageParserReturnsItemsFromTheVerifiedListBox() {
        val result = parseHomepage(
            """
            <div class='listBox'><div class='listTab'>首页精选</div><ul>
              <li><div class='s'>作者：首页作者</div>
                <a href='/book/7.html'><img src='/cover.jpg'>《首页书》</a>
                <div class='u'>首页简介</div>
              </li>
            </ul></div>
            """.trimIndent()
        )

        assertTrue(result is CategoryBrowseResult.Success)
        result as CategoryBrowseResult.Success
        assertEquals(listOf("首页书"), result.items.map { it.title })
    }
}
