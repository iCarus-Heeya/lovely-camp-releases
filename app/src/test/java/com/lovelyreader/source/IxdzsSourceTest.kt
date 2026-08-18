package com.lovelyreader.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IxdzsSourceTest {
    private val source = IxdzsSource()

    @Test
    fun parsesSearchResultsFromIxdzsHtml() {
        val html = """
            <ul class="u-list">
              <li class="burl" data-url="/read/183714/">
                <h3 class="bname"><a href="/read/183714/" title="你是我的荣耀">你是我的荣耀</a></h3>
                <span class="bauthor"><a href="/author/%E9%A1%BE%E6%BC%AB">顾漫</a></span>
                <p class="l-p2">简介一：十年过去。</p>
                <a href="/read/183714/p56.html"><span class="l-chapter">把婚礼补全了</span></a>
              </li>
            </ul>
        """.trimIndent()

        val results = source.parseSearchResults(html)

        assertEquals(1, results.size)
        assertEquals("你是我的荣耀", results[0].title)
        assertEquals("顾漫", results[0].author)
        assertEquals("https://ixdzs8.com/read/183714/", results[0].bookUrl)
        assertTrue(results[0].summary.contains("十年过去"))
    }

    @Test
    fun parsesChapterContentFromIxdzsHtml() {
        val html = """
            <div class="page-d-top">
                <h1 class="page-d-name">第一章 荣耀</h1>
            </div>
            <article class="page-content">
              <h3>第一章 荣耀</h3>
              <section>
                <p>&nbsp;&nbsp;&nbsp;&nbsp;乔晶晶坐在窗边。</p>
                <p>于途抬头看她。</p>
              </section>
            </article>
        """.trimIndent()

        val chapter = source.parseChapterContent("https://ixdzs8.com/read/1/p1.html", html)

        assertEquals("第一章 荣耀", chapter?.title)
        assertTrue(chapter?.content?.contains("乔晶晶坐在窗边。") == true)
        assertTrue(chapter?.content?.contains("于途抬头看她。") == true)
    }

    @Test
    fun rejectsBrowserVerificationPageInsteadOfTreatingWholeHtmlAsChapter() {
        val html = """
            <html>
              <body>
                <h1>正在验证浏览器</h1>
                <p>请稍等，正在进行安全驗證...</p>
              </body>
            </html>
        """.trimIndent()

        val chapter = source.parseChapterContent("https://ixdzs8.com/read/1/p1.html", html)

        assertNull(chapter)
    }

    @Test
    fun filtersUnsafeChapterLinksBeforeReturningChapterList() {
        val html = """
            <div>
              <a href="/read/183714/p1.html">第一章</a>
              <a href="https://example.com/read/183714/p2.html">外站章节</a>
              <a href="/read/../down/p3.html">下载路径伪装章节</a>
            </div>
        """.trimIndent()

        val chapters = source.parseChapterList(html)

        assertEquals(1, chapters.size)
        assertEquals("https://ixdzs8.com/read/183714/p1.html", chapters[0].url)
    }
}
