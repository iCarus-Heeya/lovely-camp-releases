package com.lovelyreader.source

import com.lovelyreader.domain.SourceCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QinkanSourceTest {
    private val source = QinkanSource()

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
}
