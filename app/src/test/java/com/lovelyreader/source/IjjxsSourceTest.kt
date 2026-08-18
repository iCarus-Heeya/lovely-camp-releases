package com.lovelyreader.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IjjxsSourceTest {
    private val source = IjjxsSource()

    @Test
    fun parsesTxtSearchResultsFromMobileHtml() {
        val html = """
            <div class="booklist_a">
              <div class="list_a">
                <div class="img"><a href="/txt/56501.html"><img src="//image.example/cover.jpg" alt="七零年代之省城媳妇"/></a></div>
                <div class="main"><a href="/txt/56501.html"><strong>七零年代之省城媳妇</strong></a>&nbsp; &nbsp;/末笙<br/>
                  <span>大小:635.8KB</span><br/>
                  <span class="intro">虞茵穿书了，成了狗血年代文里的女配。</span>
                </div>
              </div>
            </div>
        """.trimIndent()

        val results = source.parseSearchResults(html)

        assertEquals(1, results.size)
        assertEquals("七零年代之省城媳妇", results[0].title)
        assertEquals("末笙", results[0].author)
        assertEquals("https://m.ijjxs.com/txt/56501.html", results[0].bookUrl)
        assertTrue(results[0].summary.contains("635.8KB"))
    }
}
