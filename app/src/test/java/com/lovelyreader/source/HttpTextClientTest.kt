package com.lovelyreader.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class HttpTextClientTest {
    @Test
    fun decodesGb18030TextWhenNoCharsetHeaderIsPresent() {
        val bytes = "香蜜沉沉烬如霜\n第一章 她醒了。".toByteArray(Charset.forName("GB18030"))

        val text = HttpTextClient.decodeText(null, bytes)

        assertTrue(text.contains("香蜜沉沉烬如霜"))
        assertFalse(text.contains("�"))
    }

    @Test
    fun fallsBackWhenDeclaredCharsetProducesReplacementCharacters() {
        val bytes = "香蜜沉沉烬如霜".toByteArray(Charset.forName("GB18030"))

        val text = HttpTextClient.decodeText("text/plain; charset=UTF-8", bytes)

        assertTrue(text.contains("香蜜沉沉烬如霜"))
        assertFalse(text.contains("�"))
    }

    @Test
    fun keepsUtf8ChineseSearchHtmlWhenNoCharsetHeaderIsPresent() {
        val html = """
            <html><body>
              <h3 class="bname"><a href="/d/123">十日终焉</a></h3>
              <span class="bauthor"><a>杀虫队队员</a></span>
              <p class="l-p2">2024年度榜单，作者简介，最新章节，已完结。</p>
            </body></html>
        """.trimIndent()
        val bytes = html.toByteArray(Charsets.UTF_8)

        val text = HttpTextClient.decodeText(null, bytes)

        assertTrue(text.contains("十日终焉"))
        assertTrue(text.contains("作者简介"))
        assertFalse(text.contains("锟"))
        assertFalse(text.contains("�"))
        assertFalse(text.contains("€"))
    }

    @Test
    fun honorsDeclaredUtf8SearchHtmlInsteadOfOverScoringGbkMojibake() {
        val html = """
            <html><body>
              <ul>
                <li class="burl">
                  <h3 class="bname"><a href="/d/123">十日终焉</a></h3>
                  <span class="bauthor"><a>杀虫队队员</a></span>
                  <p class="l-p2">24年番茄年度巅峰榜TOP1，2024年度中国网络文学影响力榜，出版销量超200万册。</p>
                </li>
              </ul>
            </body></html>
        """.trimIndent()
        val bytes = html.toByteArray(Charsets.UTF_8)

        val text = HttpTextClient.decodeText("text/html; charset=UTF-8", bytes)

        assertTrue(text.contains("十日终焉"))
        assertTrue(text.contains("年度巅峰榜"))
        assertFalse(text.contains("锦"))
        assertFalse(text.contains("锟"))
        assertFalse(text.contains("�"))
    }

    @Test
    fun fallsBackFromDamagedDeclaredUtf8ToGb18030() {
        val bytes = "第一章 她醒了。可以离线阅读的正文。".toByteArray(Charset.forName("GB18030"))

        val text = HttpTextClient.decodeText("text/plain; charset=UTF-8", bytes)

        assertTrue(text.contains("第一章"))
        assertTrue(text.contains("离线阅读"))
        assertFalse(text.contains("�"))
        assertFalse(text.contains("锟"))
    }
}
