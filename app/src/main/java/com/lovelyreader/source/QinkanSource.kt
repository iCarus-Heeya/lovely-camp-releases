package com.lovelyreader.source

import com.lovelyreader.domain.Book
import com.lovelyreader.domain.BookDetail
import com.lovelyreader.domain.BookStatus
import com.lovelyreader.domain.Chapter
import com.lovelyreader.domain.ChapterContent
import com.lovelyreader.domain.DownloadOption
import com.lovelyreader.domain.RankingPeriod
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SizeBand
import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.domain.SourceHealth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.URI

class QinkanSource(
    private val http: HttpTextClient = HttpTextClient()
) : NovelSource, BrowsableNovelSource {
    override val sourceId: String = "qinkan"
    override val displayName: String = "勤看小说"
    override val baseUrl: String = "https://www.qinkan.net"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.SEARCH,
        SourceCapability.READ_CHAPTER,
        SourceCapability.TXT_IMPORT,
        SourceCapability.EPUB_IMPORT,
        SourceCapability.OPEN_ORIGINAL
    )

    private val safety = SourceSafety(baseUrl = baseUrl)
    private val searchablePaths = listOf("/", "/npyq/", "/xdds/")
    private val downloadHosts = setOf("d3.qinkan.net")

    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val normalized = normalize(query)
        return searchablePaths
            .flatMap { path ->
                runCatching { parseListPage(http.get("$baseUrl$path", safety)) }.getOrDefault(emptyList())
            }
            .filter { result ->
                normalize(result.title).contains(normalized) ||
                    normalize(result.author).contains(normalized) ||
                    normalized.contains(normalize(result.title))
            }
            .distinctBy { it.bookUrl }
    }

    override suspend fun getBookDetail(bookUrl: String): BookDetail? {
        val allowedUrl = safety.requireAllowed(bookUrl)
        return parseBookDetail(allowedUrl, http.get(allowedUrl, safety))
    }

    override suspend fun getChapterList(bookUrl: String): List<Chapter> {
        val allowedUrl = safety.requireAllowed(bookUrl)
        return listOf(Chapter(title = "全文TXT", url = allowedUrl, order = 0))
    }

    override suspend fun getChapterContent(chapterUrl: String): ChapterContent? {
        val allowedUrl = safety.requireAllowed(chapterUrl)
        val html = http.get(allowedUrl, safety)
        val options = parseDownloadOptions(html).filter { it.allowed && it.format == "txt" }
        if (options.isEmpty()) return null
        val title = parseTitle(html).ifBlank { "全文TXT" }
        for (option in options) {
            val text = http.get(option.url, referer = allowedUrl).trim()
                .takeIf(SourceContentGuard::isReadableNovelText)
                ?: continue
            return ChapterContent(
                title = title,
                url = option.url,
                content = text
            )
        }
        return null
    }

    override suspend fun getDownloadOptions(bookUrl: String): List<DownloadOption> {
        val allowedUrl = safety.requireAllowed(bookUrl)
        return parseDownloadOptions(http.get(allowedUrl, safety))
    }

    override suspend fun healthCheck(): SourceHealth {
        return SourceHealth(sourceId, available = true, message = "可解析详情页与多格式下载；搜索接口异常时使用列表页索引")
    }

    override fun isSafeReadUrl(bookUrl: String): Boolean = safety.isAllowed(bookUrl)

    override suspend fun ranking(period: RankingPeriod): List<SearchResult> {
        val path = when (period) {
            RankingPeriod.MONTH -> "/"
            RankingPeriod.YEAR -> "/npyq/"
            RankingPeriod.TOTAL -> "/xdds/"
        }
        return parseListPage(http.get("$baseUrl$path", safety)).take(30)
    }

    override suspend fun randomBrowse(category: String, finishedOnly: Boolean, sizeBand: SizeBand): List<SearchResult> {
        val paths = when (category) {
            "全部" -> listOf("/npyq/", "/xdds/", "/xhqh/", "/khly/")
            "现言甜宠", "青春校园", "古言宫斗", "年代种田", "穿越重生" -> listOf("/npyq/")
            "都市职场" -> listOf("/xdds/")
            "仙侠奇缘" -> listOf("/xhqh/")
            "悬疑推理" -> listOf("/khly/")
            else -> listOf("/npyq/")
        }
        return coroutineScope {
            paths
                .map { path -> async { parseListPage(http.get("$baseUrl$path", safety)) } }
                .awaitAll()
                .flatten()
        }
            .shuffled()
            .take(12)
    }

    fun parseListPage(html: String): List<SearchResult> {
        val links = HtmlTools.allMatches(html, "<a[^>]+href=\"(/book/\\d+\\.html)\"[^>]*>([\\s\\S]*?)</a>")
        return links.mapNotNull { match ->
            val title = HtmlTools.stripTags(match.groupValues[2]).trim('《', '》').takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            SearchResult(
                sourceId = sourceId,
                title = title,
                author = "",
                bookUrl = HtmlTools.absoluteUrl(baseUrl, match.groupValues[1]),
                summary = "勤看小说站内列表结果，可进入详情页下载TXT/EPUB等格式。",
                capabilities = capabilities
            )
        }.filter { safety.isAllowed(it.bookUrl) }.distinctBy { it.bookUrl }
    }

    fun parseBookDetail(url: String, html: String): BookDetail {
        val title = parseTitle(html).ifBlank { "未知书名" }
        val author = HtmlTools.firstMatch(html, "书籍作者[：:]\\s*<a[^>]*>([\\s\\S]*?)</a>")
            ?.let(HtmlTools::stripTags)
            ?: HtmlTools.firstMatch(html, "书籍作者[：:]\\s*([^<]+)")
                ?.let(HtmlTools::stripTags)
            ?: ""
        val size = HtmlTools.firstMatch(html, "文件大小[：:]\\s*([^<]+)</li>")
            ?.let(HtmlTools::stripTags)
        val category = HtmlTools.firstMatch(html, "书籍类型[：:]\\s*([^<]+)</li>")
            ?.let(HtmlTools::stripTags)
        val status = HtmlTools.firstMatch(html, "书籍状态[：:]\\s*([^<]+)</li>")
            ?.let(HtmlTools::stripTags)
        val summary = HtmlTools.firstMatch(html, "<div[^>]*class=\"[^\"]*showInfo[^\"]*\"[^>]*>([\\s\\S]*?)</div>")
            ?.let(HtmlTools::stripTags)
            .orEmpty()

        return BookDetail(
            book = Book(
                id = stableBookId(sourceId, title, author),
                title = title,
                author = author,
                status = if (status?.contains("全本") == true || status?.contains("完结") == true) {
                    BookStatus.FINISHED
                } else {
                    BookStatus.UNKNOWN
                },
                summary = summary,
                coverUrl = HtmlTools.firstMatch(html, "<div[^>]*class=\"[^\"]*detail_pic[^\"]*\"[\\s\\S]*?<img[^>]+src=\"([^\"]+)\"")
                    ?.let { HtmlTools.absoluteUrl(baseUrl, it) },
                sourceIds = listOf(sourceId)
            ),
            sourceUrl = url,
            category = category,
            wordCountOrSize = size,
            offlineLabel = "可下载TXT/EPUB/MOBI/AZW3并在书架阅读"
        )
    }

    fun parseDownloadOptions(html: String): List<DownloadOption> {
        return HtmlTools.allMatches(html, "<a[^>]+class=\"[^\"]*downButton[^\"]*\"[^>]+href='\\s*([^']+?)\\s*'[^>]*>([\\s\\S]*?)</a>")
            .map { match ->
                val url = match.groupValues[1].trim()
                val label = HtmlTools.stripTags(match.groupValues[2]).ifBlank { "下载" }
                val format = when {
                    url.endsWith(".txt", ignoreCase = true) -> "txt"
                    url.endsWith(".epub", ignoreCase = true) -> "epub"
                    url.endsWith(".mobi", ignoreCase = true) -> "mobi"
                    url.endsWith(".azw3", ignoreCase = true) -> "azw3"
                    else -> "file"
                }
                DownloadOption(
                    label = label,
                    url = url,
                    format = format,
                    allowed = isAllowedDownloadUrl(url)
                )
            }
            .distinctBy { it.url }
    }

    private fun parseTitle(html: String): String {
        return HtmlTools.firstMatch(html, "<div[^>]*class=\"[^\"]*detail_right[^\"]*\"[\\s\\S]*?<h1>([\\s\\S]*?)</h1>")
            ?.let(HtmlTools::stripTags)
            ?.substringBefore("txt")
            ?.trim('《', '》', ' ', '+')
            .orEmpty()
    }

    private fun isAllowedDownloadUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        return uri.scheme == "https" &&
            uri.host in downloadHosts &&
            listOf(".txt", ".epub", ".mobi", ".azw3").any { uri.path.endsWith(it, ignoreCase = true) }
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(" ", "")
}
