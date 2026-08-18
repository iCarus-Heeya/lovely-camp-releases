package com.lovelyreader.source

import com.lovelyreader.domain.BookDetail
import com.lovelyreader.domain.Chapter
import com.lovelyreader.domain.ChapterContent
import com.lovelyreader.domain.DownloadOption
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.domain.SourceHealth

class IjjxsSource(
    private val http: HttpTextClient = HttpTextClient()
) : NovelSource {
    override val sourceId: String = "ijjxs"
    override val displayName: String = "久久小说下载网"
    override val baseUrl: String = "https://m.ijjxs.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.SEARCH,
        SourceCapability.OPEN_ORIGINAL
    )
    private val safety = SourceSafety(baseUrl = baseUrl)

    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val html = http.postForm(
            url = "$baseUrl/e/search/index.php",
            fields = mapOf(
                "show" to "writer,title",
                "keyboard" to query,
                "Submit22" to "搜索"
            ),
            charsetName = "UTF-8"
        )
        return parseSearchResults(html)
    }

    override suspend fun getBookDetail(bookUrl: String): BookDetail? = null

    override suspend fun getChapterList(bookUrl: String): List<Chapter> = emptyList()

    override suspend fun getChapterContent(chapterUrl: String): ChapterContent? = null

    override suspend fun getDownloadOptions(bookUrl: String): List<DownloadOption> = emptyList()

    override suspend fun healthCheck(): SourceHealth {
        return SourceHealth(sourceId, available = true, message = "可搜索 TXT 资源")
    }

    fun parseSearchResults(html: String): List<SearchResult> {
        val cards = HtmlTools.allMatches(
            html,
            "<a[^>]+href=\"([^\"]+)\"[^>]*>\\s*<strong>([\\s\\S]*?)</strong>\\s*</a>([\\s\\S]{0,700})"
        )
        return cards.mapNotNull { match ->
            val href = match.groupValues[1]
            val title = HtmlTools.stripTags(match.groupValues[2]).takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val tail = match.groupValues[3]
            val author = HtmlTools.firstMatch(tail, "&nbsp;\\s*&nbsp;\\s*/([^<]+)<br")
                ?.let(HtmlTools::stripTags)
                .orEmpty()
            val size = HtmlTools.firstMatch(tail, "<span>\\s*(大小:[^<]+)</span>")
                ?.let(HtmlTools::stripTags)
                .orEmpty()
            val intro = HtmlTools.firstMatch(tail, "<span[^>]*class=\"[^\"]*intro[^\"]*\"[^>]*>([\\s\\S]*?)</span>")
                ?.let(HtmlTools::stripTags)
                .orEmpty()
            val summary = listOf(size, intro).filter { it.isNotBlank() }.joinToString(" · ")

            SearchResult(
                sourceId = sourceId,
                title = title,
                author = author,
                bookUrl = HtmlTools.absoluteUrl(baseUrl, href),
                summary = summary,
                capabilities = capabilities
            )
        }.filter { safety.isAllowed(it.bookUrl) }
    }
}
