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

class QisuwangSource(
    private val http: HttpTextClient = HttpTextClient()
) : NovelSource, BrowsableNovelSource {
    override val sourceId: String = "qisuwang"
    override val displayName: String = "奇书网"
    override val baseUrl: String = "https://m.9qishu.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.SEARCH,
        SourceCapability.READ_CHAPTER,
        SourceCapability.TXT_IMPORT,
        SourceCapability.OPEN_ORIGINAL
    )

    private val safety = SourceSafety(baseUrl = baseUrl)
    private val listPaths = listOf("/yanqing/", "/hot/", "/recommendall/", "/new/")
    private val downloadHosts = setOf("down.qishu99.cc", "txt.qishu77.com")

    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val normalized = normalize(query)
        return listPaths
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
        val detailHtml = http.get(allowedUrl, safety)
        val downloadPage = parseDownloadPageUrl(detailHtml) ?: return emptyList()
        return listOf(Chapter(title = "全文TXT", url = downloadPage, order = 0))
    }

    override suspend fun getChapterContent(chapterUrl: String): ChapterContent? {
        val downloadPage = safety.requireAllowed(chapterUrl)
        val html = http.get(downloadPage, safety)
        val options = parseDownloadOptions(html).filter { it.allowed }
        if (options.isEmpty()) return null
        val title = HtmlTools.firstMatch(html, "<h1[^>]*class=\"[^\"]*title[^\"]*\"[^>]*>([\\s\\S]*?)</h1>")
            ?.let(HtmlTools::stripTags)
            ?: HtmlTools.firstMatch(html, "<meta[^>]+property=\"og:title\"[^>]+content=\"([^\"]+)\"")
            ?: "全文TXT"
        for (option in options) {
            val text = http.get(option.url, referer = downloadPage).trim()
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
        val detailHtml = http.get(allowedUrl, safety)
        val downloadPage = parseDownloadPageUrl(detailHtml) ?: return emptyList()
        return parseDownloadOptions(http.get(downloadPage, safety))
    }

    override suspend fun healthCheck(): SourceHealth {
        return SourceHealth(sourceId, available = true, message = "可解析分类、排行、详情与TXT下载")
    }

    override fun isSafeReadUrl(bookUrl: String): Boolean = safety.isAllowed(bookUrl)

    override suspend fun ranking(period: RankingPeriod): List<SearchResult> {
        val path = when (period) {
            RankingPeriod.MONTH -> "/hot/"
            RankingPeriod.YEAR -> "/recommendall/"
            RankingPeriod.TOTAL -> "/all/"
        }
        return parseListPage(http.get("$baseUrl$path", safety)).take(30)
    }

    override suspend fun randomBrowse(category: String, finishedOnly: Boolean, sizeBand: SizeBand): List<SearchResult> {
        val paths = when (category) {
            "全部" -> listOf("/yanqing/", "/dushi/", "/xuanhuan/", "/kehuan/")
            "现言甜宠", "青春校园", "古言宫斗", "年代种田", "穿越重生" -> listOf("/yanqing/")
            "都市职场" -> listOf("/dushi/")
            "仙侠奇缘" -> listOf("/xuanhuan/")
            "悬疑推理" -> listOf("/kehuan/")
            else -> listOf("/yanqing/")
        }
        return coroutineScope {
            paths
                .map { path -> async { parseListPage(http.get("$baseUrl$path", safety)) } }
                .awaitAll()
                .flatten()
        }
            .filter { result -> sizeBand.contains(result.summary.sizeKbOrDefault(sizeBand.minKb)) }
            .shuffled()
            .take(12)
    }

    fun parseListPage(html: String): List<SearchResult> {
        return HtmlTools.allMatches(html, "<li>\\s*<a[^>]+href=\"([^\"]+)\"[\\s\\S]*?</li>")
            .mapNotNull { match ->
                parseListItem(match.value)
            }
            .filter { safety.isAllowed(it.bookUrl) }
    }

    fun parseBookDetail(url: String, html: String): BookDetail {
        val title = HtmlTools.firstMatch(html, "<meta[^>]+property=\"og:novel:book_name\"[^>]+content=\"([^\"]+)\"")
            ?: HtmlTools.firstMatch(html, "<h1[^>]*class=\"[^\"]*title[^\"]*\"[^>]*>([\\s\\S]*?)</h1>")
                ?.let(HtmlTools::stripTags)
            ?: "未知书名"
        val author = HtmlTools.firstMatch(html, "<meta[^>]+property=\"og:novel:author\"[^>]+content=\"([^\"]+)\"")
            ?: HtmlTools.firstMatch(html, "作者：\\s*([^<]+)")
                ?.let(HtmlTools::stripTags)
            ?: ""
        val summary = HtmlTools.firstMatch(html, "<meta[^>]+property=\"og:description\"[^>]+content=\"([^\"]+)\"")
            ?: HtmlTools.firstMatch(html, "<div[^>]*class=\"[^\"]*con[^\"]*\"[^>]*>([\\s\\S]*?)</div>")
                ?.let(HtmlTools::stripTags)
            ?: ""
        val category = HtmlTools.firstMatch(html, "<meta[^>]+property=\"og:novel:category\"[^>]+content=\"([^\"]+)\"")
        val status = HtmlTools.firstMatch(html, "<meta[^>]+property=\"og:novel:status\"[^>]+content=\"([^\"]+)\"")
        val size = HtmlTools.firstMatch(html, "大小：\\s*<span[^>]*class=\"[^\"]*num[^\"]*\"[^>]*>([^<]+)</span>")
            ?.let(HtmlTools::stripTags)
        return BookDetail(
            book = Book(
                id = stableBookId(sourceId, title, author),
                title = title,
                author = author,
                status = if (status == "完结") BookStatus.FINISHED else BookStatus.UNKNOWN,
                summary = summary,
                coverUrl = HtmlTools.firstMatch(html, "<meta[^>]+property=\"og:image\"[^>]+content=\"([^\"]+)\""),
                sourceIds = listOf(sourceId)
            ),
            sourceUrl = url,
            category = category,
            wordCountOrSize = size,
            offlineLabel = "可下载TXT并在书架阅读"
        )
    }

    fun parseDownloadOptions(html: String): List<DownloadOption> {
        return HtmlTools.allMatches(html, "<a[^>]+href=\"\\s*([^\"]+?\\.txt)\\s*\"[^>]*>([\\s\\S]*?)</a>")
            .mapIndexed { index, match ->
                val url = match.groupValues[1].trim()
                DownloadOption(
                    label = HtmlTools.stripTags(match.groupValues[2]).ifBlank { "TXT下载${index + 1}" },
                    url = url,
                    format = "txt",
                    allowed = isAllowedDownloadUrl(url)
                )
            }
            .distinctBy { it.url }
    }

    private fun parseListItem(item: String): SearchResult? {
        val href = HtmlTools.firstMatch(item, "<a[^>]+href=\"([^\"]+)\"[^>]*class=\"[^\"]*pic[^\"]*\"")
            ?: HtmlTools.firstMatch(item, "<p[^>]*class=\"[^\"]*title[^\"]*\"[\\s\\S]*?<a[^>]+href=\"([^\"]+)\"")
            ?: return null
        val title = HtmlTools.firstMatch(item, "<p[^>]*class=\"[^\"]*title[^\"]*\"[\\s\\S]*?<a[^>]*>([\\s\\S]*?)</a>")
            ?.let(HtmlTools::stripTags)
            ?.trim('《', '》')
            ?: return null
        val author = HtmlTools.firstMatch(item, "作者：\\s*(?:<a[^>]*>)?([^<]+)")
            ?.let(HtmlTools::stripTags)
            ?: HtmlTools.firstMatch(item, "<p[^>]*class=\"[^\"]*author[^\"]*\"[^>]*>([^<]+) 著</p>")
                ?.let(HtmlTools::stripTags)
                ?.removeSuffix(" 著")
            ?: ""
        val size = HtmlTools.firstMatch(item, "大小：\\s*([^<]+)</p>")
            ?.let(HtmlTools::stripTags)
            .orEmpty()
        val summary = HtmlTools.firstMatch(item, "<p[^>]*class=\"[^\"]*intro[^\"]*\"[^>]*>([\\s\\S]*?)</p>")
            ?.let(HtmlTools::stripTags)
            .orEmpty()
        return SearchResult(
            sourceId = sourceId,
            title = title,
            author = author,
            bookUrl = HtmlTools.absoluteUrl(baseUrl, href),
            summary = listOf(size, summary).filter { it.isNotBlank() }.joinToString(" · "),
            coverUrl = HtmlTools.firstMatch(item, "<img[^>]+src=\"([^\"]+)\"")?.let { HtmlTools.absoluteUrl(baseUrl, it) },
            capabilities = capabilities
        )
    }

    private fun parseDownloadPageUrl(html: String): String? {
        return HtmlTools.firstMatch(html, "<a[^>]+href=\"([^\"]*/down/[^\"]+)\"[^>]*>")
            ?.let { HtmlTools.absoluteUrl(baseUrl, it) }
            ?.takeIf { safety.isAllowed(it) }
    }

    private fun isAllowedDownloadUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        return uri.scheme == "https" &&
            uri.host in downloadHosts &&
            uri.path.endsWith(".txt", ignoreCase = true)
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(" ", "")

    private fun String.sizeKbOrDefault(defaultKb: Int): Int {
        val match = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(KB|MB)", RegexOption.IGNORE_CASE).find(this) ?: return defaultKb
        val value = match.groupValues[1].toDoubleOrNull() ?: return defaultKb
        return if (match.groupValues[2].equals("MB", ignoreCase = true)) {
            (value * 1024).toInt()
        } else {
            value.toInt()
        }
    }
}
