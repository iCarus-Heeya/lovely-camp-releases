package com.lovelyreader.source

import com.lovelyreader.domain.Book
import com.lovelyreader.domain.BookDetail
import com.lovelyreader.domain.Chapter
import com.lovelyreader.domain.ChapterContent
import com.lovelyreader.domain.DownloadOption
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.domain.SourceHealth

class IxdzsSource(
    private val http: HttpTextClient = HttpTextClient(minimumIntervalMillis = 300)
) : NovelSource {
    override val sourceId: String = "ixdzs"
    override val displayName: String = "爱下电子书"
    override val baseUrl: String = "https://ixdzs8.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.SEARCH,
        SourceCapability.READ_CHAPTER,
        SourceCapability.OPEN_ORIGINAL
    )
    private val safety = SourceSafety(
        baseUrl = baseUrl,
        disallowedPrefixes = listOf("/down", "/download/")
    )

    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return parseSearchResults(http.get("$baseUrl/bsearch?q=$encoded"))
    }

    override suspend fun getBookDetail(bookUrl: String): BookDetail? {
        val allowedUrl = safety.requireAllowed(bookUrl)
        return parseBookDetail(allowedUrl, http.get(allowedUrl, safety))
    }

    override suspend fun getChapterList(bookUrl: String): List<Chapter> {
        val allowedUrl = safety.requireAllowed(bookUrl)
        val html = http.get(allowedUrl, safety)
        val apiChapters = parseFullChapterListFromApi(html)
        if (apiChapters.isNotEmpty()) return apiChapters
        return parseChapterList(html)
    }

    private suspend fun parseFullChapterListFromApi(bookHtml: String): List<Chapter> {
        val bid = HtmlTools.firstMatch(bookHtml, "<input[^>]+id=\"bid\"[^>]+value=\"(\\d+)\"")
            ?: HtmlTools.firstMatch(bookHtml, "bid\\s*[:=]\\s*['\"]?(\\d+)")
            ?: return emptyList()
        val json = runCatching {
            http.postForm("$baseUrl/novel/clist/", mapOf("bid" to bid))
        }.getOrNull() ?: return emptyList()
        return parseApiChapterList(bid, json)
    }

    fun parseApiChapterList(bid: String, json: String): List<Chapter> {
        return runCatching {
            val root = org.json.JSONObject(json)
            if (root.optInt("rs") != 200) return emptyList()
            val data = root.optJSONArray("data") ?: return emptyList()
            (0 until data.length()).mapNotNull { i ->
                val item = data.optJSONObject(i) ?: return@mapNotNull null
                val ordernum = item.optString("ordernum", "").trim()
                val title = item.optString("title", "").trim()
                if (ordernum.isEmpty() || title.isEmpty()) return@mapNotNull null
                Chapter(
                    title = title,
                    url = HtmlTools.absoluteUrl(baseUrl, "/read/$bid/p$ordernum.html"),
                    order = i
                )
            }.filter { safety.isAllowed(it.url) }
        }.getOrDefault(emptyList())
    }

    fun parseChapterList(html: String): List<Chapter> {
        return HtmlTools.allMatches(html, "<a[^>]+href=\"([^\"]*/p\\d+\\.html)\"[^>]*>([\\s\\S]*?)</a>")
            .mapIndexed { index, match ->
                Chapter(
                    title = HtmlTools.stripTags(match.groupValues[2]),
                    url = HtmlTools.absoluteUrl(baseUrl, match.groupValues[1]),
                    order = index
                )
            }
            .distinctBy { it.url }
            .filter { safety.isAllowed(it.url) }
    }

    override suspend fun getChapterContent(chapterUrl: String): ChapterContent? {
        val allowedUrl = safety.requireAllowed(chapterUrl)
        return parseChapterContent(allowedUrl, http.get(allowedUrl, safety))
    }

    override suspend fun getDownloadOptions(bookUrl: String): List<DownloadOption> = emptyList()

    override suspend fun healthCheck(): SourceHealth {
        return SourceHealth(sourceId, available = true, message = "可搜索，默认打开原站")
    }

    override fun isSafeReadUrl(bookUrl: String): Boolean = safety.isAllowed(bookUrl)

    fun parseSearchResults(html: String): List<SearchResult> {
        val items = HtmlTools.allMatches(html, "<li[^>]*class=\"[^\"]*burl[^\"]*\"[\\s\\S]*?</li>")
        return items.mapNotNull { match ->
            val item = match.value
            val href = HtmlTools.firstMatch(item, "<h3[^>]*class=\"[^\"]*bname[^\"]*\"[\\s\\S]*?<a[^>]+href=\"([^\"]+)\"")
                ?: HtmlTools.firstMatch(item, "<a[^>]+href=\"([^\"]+)\"")
                ?: return@mapNotNull null
            val title = HtmlTools.firstMatch(item, "<h3[^>]*class=\"[^\"]*bname[^\"]*\"[\\s\\S]*?<a[^>]*>([\\s\\S]*?)</a>")
                ?.let(HtmlTools::stripTags)
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val author = HtmlTools.firstMatch(item, "<span[^>]*class=\"[^\"]*bauthor[^\"]*\"[\\s\\S]*?<a[^>]*>([\\s\\S]*?)</a>")
                ?.let(HtmlTools::stripTags)
                .orEmpty()
            val summary = HtmlTools.firstMatch(item, "<p[^>]*class=\"[^\"]*l-p2[^\"]*\"[^>]*>([\\s\\S]*?)</p>")
                ?.let(HtmlTools::stripTags)
                .orEmpty()
            val latest = HtmlTools.firstMatch(item, "<span[^>]*class=\"[^\"]*l-chapter[^\"]*\"[^>]*>([\\s\\S]*?)</span>")
                ?.let(HtmlTools::stripTags)

            SearchResult(
                sourceId = sourceId,
                title = title,
                author = author,
                bookUrl = HtmlTools.absoluteUrl(baseUrl, href),
                summary = summary,
                latestChapter = latest,
                capabilities = capabilities
            )
        }.filter { safety.isAllowed(it.bookUrl) }
    }

    fun parseBookDetail(url: String, html: String): BookDetail {
        val title = HtmlTools.firstMatch(html, "<h1[^>]*>([\\s\\S]*?)</h1>")
            ?.let(HtmlTools::stripTags)
            ?: "未知书名"
        val author = HtmlTools.firstMatch(html, "作者[：:]\\s*</?[^>]*>\\s*([\\s\\S]*?)<")
            ?.let(HtmlTools::stripTags)
            ?: ""
        val summary = HtmlTools.firstMatch(html, "<p[^>]*class=\"[^\"]*intro[^\"]*\"[^>]*>([\\s\\S]*?)</p>")
            ?.let(HtmlTools::stripTags)
            .orEmpty()

        return BookDetail(
            book = Book(
                id = stableBookId(sourceId, title, author),
                title = title,
                author = author,
                summary = summary,
                sourceIds = listOf(sourceId)
            ),
            sourceUrl = url,
            offlineLabel = "仅打开原站"
        )
    }

    fun parseChapterContent(url: String, html: String): ChapterContent? {
        val title = HtmlTools.firstMatch(html, "<h1[^>]*class=\"[^\"]*page-d-name[^\"]*\"[^>]*>([\\s\\S]*?)</h1>")
            ?: HtmlTools.firstMatch(html, "<h1[^>]*>([\\s\\S]*?)</h1>")
            ?.let(HtmlTools::stripTags)
            ?: "未命名章节"
        val article = HtmlTools.firstMatch(html, "<article[^>]*class=\"[^\"]*page-content[^\"]*\"[^>]*>([\\s\\S]*?)</article>")
            ?: HtmlTools.firstMatch(html, "<article[^>]*id=\"content\"[^>]*>([\\s\\S]*?)</article>")
            ?: HtmlTools.firstMatch(html, "<div[^>]*id=\"content\"[^>]*>([\\s\\S]*?)</div>")
            ?: return null
        val content = HtmlTools.stripTags(
            article
                .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        ).takeIf(SourceContentGuard::isReadableNovelText) ?: return null

        return ChapterContent(title = title, url = url, content = content)
    }
}

fun stableBookId(sourceId: String, title: String, author: String): String {
    return "$sourceId-${title.trim()}-${author.trim()}".lowercase().hashCode().toUInt().toString(16)
}
