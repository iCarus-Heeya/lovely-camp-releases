package com.lovelyreader.source

import com.lovelyreader.domain.Book
import com.lovelyreader.domain.BookDetail
import com.lovelyreader.domain.BookStatus
import com.lovelyreader.domain.Chapter
import com.lovelyreader.domain.ChapterContent
import com.lovelyreader.domain.DownloadOption
import com.lovelyreader.domain.RankingPeriod
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.domain.SourceHealth
import java.net.URI

class ZxcsSource(
    private val http: HttpTextClient = HttpTextClient(readTimeoutMillis = 180_000),
    private val discoveryHttp: HttpTextClient = HttpTextClient(
        connectTimeoutMillis = 6_000,
        readTimeoutMillis = 8_000
    )
) : NovelSource, BrowsableNovelSource {
    override val sourceId: String = "zxcs"
    override val displayName: String = "知轩藏书"
    override val baseUrl: String = "https://zxcs.zip"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.SEARCH,
        SourceCapability.READ_CHAPTER,
        SourceCapability.TXT_IMPORT,
        SourceCapability.OPEN_ORIGINAL
    )

    private val safety = SourceSafety(baseUrl = baseUrl)
    private val searchableUrls = listOf(
        "$baseUrl/",
        "$baseUrl/rank/topdownload",
        "$baseUrl/rank/postdate",
        "$baseUrl?page=2"
    )
    private val downloadHosts = setOf("download.zxcs.zip")

    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val normalized = normalize(query)
        return searchableUrls
            .flatMap { url -> runCatching { parseListPage(http.get(url, safety)) }.getOrDefault(emptyList()) }
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

    override suspend fun getChapterContent(chapterUrl: String): ChapterContent? =
        getChapterContentWithProgress(chapterUrl) { _, _ -> }

    override suspend fun getChapterContentWithProgress(
        chapterUrl: String,
        onProgress: suspend (readBytes: Long, totalBytes: Long?) -> Unit
    ): ChapterContent? {
        val allowedUrl = safety.requireAllowed(chapterUrl)
        val html = http.get(allowedUrl, safety)
        val option = parseDownloadOptions(html).firstOrNull { it.allowed } ?: return null
        return ChapterContent(
            title = parseBookTitleAuthor(html).first.ifBlank { "全文TXT" },
            url = option.url,
            content = http.getWithProgress(option.url, referer = allowedUrl, onProgress = onProgress).trim()
                .takeIf(SourceContentGuard::isReadableNovelText)
                ?: return null
        )
    }

    override suspend fun getDownloadOptions(bookUrl: String): List<DownloadOption> {
        val allowedUrl = safety.requireAllowed(bookUrl)
        return parseDownloadOptions(http.get(allowedUrl, safety))
    }

    override suspend fun healthCheck(): SourceHealth {
        return SourceHealth(sourceId, available = true, message = "可解析列表、详情与TXT下载")
    }

    override fun isSafeReadUrl(bookUrl: String): Boolean = safety.isAllowed(bookUrl)

    override suspend fun ranking(period: RankingPeriod): List<SearchResult> {
        return parseListPage(http.get(rankingUrl(period), safety)).take(30)
    }

    override suspend fun homepageFeatured(): CategoryBrowseResult = safeCategoryBrowse(10_000) {
        parseHomepagePage(discoveryHttp.get("$baseUrl/", safety))
    }

    override suspend fun categoryBrowse(category: String, page: Int): CategoryBrowseResult {
        if (category != "全部") return CategoryBrowseResult.Unsupported
        if (page != 1) return CategoryBrowseResult.Success(emptyList(), hasMore = false)
        val url = "$baseUrl/"
        return safeCategoryBrowse(8_000) {
            parseHomepagePage(discoveryHttp.get(url, safety))
        }
    }

    fun discoveryTimeoutConfiguration(): HttpTimeoutConfiguration = discoveryHttp.timeoutConfiguration()

    fun rankingUrl(period: RankingPeriod): String {
        return when (period) {
            RankingPeriod.MONTH -> "$baseUrl/rank/topdownload"
            RankingPeriod.YEAR -> "$baseUrl/rank/postdate"
            RankingPeriod.TOTAL -> "$baseUrl/rank/topdownload"
        }
    }

    fun parseListPage(html: String): List<SearchResult> {
        return HtmlTools.allMatches(html, "<a[^>]+href=\"(/book/\\d+\\.html)\"[\\s\\S]*?</a>")
            .mapNotNull { match ->
                val item = match.value
                val titleLine = HtmlTools.firstMatch(item, "<span[^>]+class=\"[^\"]*link[^\"]*\"[^>]*>([\\s\\S]*?)</span>")
                    ?.let(HtmlTools::stripTags)
                    ?: HtmlTools.stripTags(item).lineSequence().firstOrNull { it.contains("作者：") }
                    ?: return@mapNotNull null
                val (title, author) = parseTitleLine(titleLine)
                if (title.isBlank()) return@mapNotNull null
                val summary = HtmlTools.firstMatch(item, "<p[^>]+class=\"[^\"]*tile-description[^\"]*\"[^>]*>([\\s\\S]*?)</p>")
                    ?.let(HtmlTools::stripTags)
                    .orEmpty()
                val downloads = HtmlTools.firstMatch(item, "<span[^>]+class=\"[^\"]*downloads[^\"]*\"[^>]*>([\\s\\S]*?)</span>")
                    ?.let(HtmlTools::stripTags)
                    .orEmpty()
                SearchResult(
                    sourceId = sourceId,
                    title = title,
                    author = author,
                    bookUrl = HtmlTools.absoluteUrl(baseUrl, match.groupValues[1]),
                    summary = listOf(downloads, summary).filter { it.isNotBlank() }.joinToString(" · "),
                    capabilities = capabilities
                )
            }
            .filter { safety.isAllowed(it.bookUrl) }
            .distinctBy { it.bookUrl }
    }

    /** Parses only homepage tile elements, preserving challenge/structure failures. */
    fun parseHomepagePage(html: String): CategoryBrowseResult {
        if (looksLikeDiscoveryVerificationPage(html)) {
            return CategoryBrowseResult.Failure("首页返回验证页面")
        }
        if (isExplicitlyEmptyDiscoveryPage(html)) {
            return CategoryBrowseResult.Success(emptyList(), hasMore = false)
        }
        val rawTiles = Regex("<mio-tile\\b[^>]*>([\\s\\S]*?)</mio-tile>", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { it.value }
            .toList()
        if (rawTiles.isEmpty()) {
            return CategoryBrowseResult.Failure("首页精选卡片结构已变化")
        }
        val items = parseListPage(rawTiles.joinToString("\n"))
        if (items.isEmpty()) {
            return CategoryBrowseResult.Failure("首页精选条目结构已变化")
        }
        return CategoryBrowseResult.Success(
            items = items.take(30),
            hasMore = false,
            partialFailure = items.size < rawTiles.size
        )
    }

    fun parseBookDetail(url: String, html: String): BookDetail {
        val (title, author) = parseBookTitleAuthor(html)
        val summary = HtmlTools.firstMatch(html, "<meta[^>]+name=\"description\"[^>]+content=\"([^\"]+)\"")
            ?: HtmlTools.firstMatch(html, "<p[^>]+class=\"[^\"]*tile-description[^\"]*\"[^>]*>([\\s\\S]*?)</p>")
                ?.let(HtmlTools::stripTags)
            ?: ""
        val size = HtmlTools.firstMatch(html, "内容大小[：:]\\s*([^<\\n]+)")
            ?.let(HtmlTools::stripTags)
        return BookDetail(
            book = Book(
                id = stableBookId(sourceId, title, author),
                title = title.ifBlank { "未知书名" },
                author = author,
                status = BookStatus.FINISHED,
                summary = summary,
                sourceIds = listOf(sourceId)
            ),
            sourceUrl = url,
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

    private fun parseBookTitleAuthor(html: String): Pair<String, String> {
        val titleText = HtmlTools.firstMatch(html, "<title>([\\s\\S]*?)</title>")
            ?.let(HtmlTools::stripTags)
            ?: HtmlTools.firstMatch(html, "<h1[^>]*>([\\s\\S]*?)</h1>")?.let(HtmlTools::stripTags)
            ?: ""
        return parseTitleLine(titleText)
    }

    private fun parseTitleLine(value: String): Pair<String, String> {
        val text = value.trim()
        val title = HtmlTools.firstMatch(text, "《([^》]+)》")
            ?: text.substringBefore("作者：")
                .substringBefore("txt下载")
                .substringBefore("_")
                .trim('《', '》', ' ', '　')
        val author = text.substringAfter("作者：", "")
            .substringBefore("txt")
            .substringBefore("_")
            .substringBefore(" ")
            .trim()
        return title
            .substringBefore("（")
            .substringBefore("(")
            .trim() to author
    }

    private fun isAllowedDownloadUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        return uri.scheme == "https" &&
            uri.host in downloadHosts &&
            uri.path.endsWith(".txt", ignoreCase = true)
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(" ", "")
}
