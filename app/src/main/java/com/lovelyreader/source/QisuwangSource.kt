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

class QisuwangSource(
    private val http: HttpTextClient = HttpTextClient(),
    private val discoveryHttp: HttpTextClient = HttpTextClient(
        connectTimeoutMillis = 6_000,
        readTimeoutMillis = 8_000
    )
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
    private val discoveryAllPaths = listOf("/yanqing/", "/dushi/", "/xuanhuan/", "/xiuzhen/", "/lishi/", "/wangyou/", "/tongren/")
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

    override suspend fun getChapterContent(chapterUrl: String): ChapterContent? =
        getChapterContentWithProgress(chapterUrl) { _, _ -> }

    override suspend fun getChapterContentWithProgress(
        chapterUrl: String,
        onProgress: suspend (readBytes: Long, totalBytes: Long?) -> Unit
    ): ChapterContent? {
        val downloadPage = safety.requireAllowed(chapterUrl)
        val html = http.get(downloadPage, safety)
        val options = parseDownloadOptions(html).filter { it.allowed }
        if (options.isEmpty()) return null
        val title = HtmlTools.firstMatch(html, "<h1[^>]*class=\"[^\"]*title[^\"]*\"[^>]*>([\\s\\S]*?)</h1>")
            ?.let(HtmlTools::stripTags)
            ?: HtmlTools.firstMatch(html, "<meta[^>]+property=\"og:title\"[^>]+content=\"([^\"]+)\"")
            ?: "全文TXT"
        for (option in options) {
            val text = http.getWithProgress(option.url, referer = downloadPage, onProgress = onProgress).trim()
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
        return SourceHealth(sourceId, available = true, message = "可解析分类、公开列表、详情与TXT下载")
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

    override suspend fun homepageFeatured(): CategoryBrowseResult = safeCategoryBrowse(10_000) {
        parseHomepagePage(discoveryHttp.get("$baseUrl/", safety))
    }

    override suspend fun categoryBrowse(category: String, page: Int): CategoryBrowseResult {
        val url = categoryPageUrl(category, page) ?: return CategoryBrowseResult.Unsupported
        val result = fetchCategoryPage(url, sourcePage(category, page))
        return if (category == "全部" && result is CategoryBrowseResult.Success) {
            result.copy(hasMore = allCategoryHasMore(page))
        } else result
    }

    fun categoryPageUrl(category: String, page: Int): String? {
        if (category == "全部") {
            val safePage = page.coerceAtLeast(1)
            val path = discoveryAllPaths.getOrNull(safePage - 1) ?: return null
            return pageUrl(path, 1)
        }
        val path = when (category) {
            "言情" -> "/yanqing/"
            "现代都市" -> "/dushi/"
            "玄幻奇幻" -> "/xuanhuan/"
            "历史军事" -> "/lishi/"
            "游戏竞技" -> "/wangyou/"
            // /kehuan/ mixes science fiction and supernatural fiction, so it is not exact.
            else -> return null
        }
        return pageUrl(path, page)
    }

    fun allCategoryHasMore(page: Int): Boolean = page.coerceAtLeast(1) < discoveryAllPaths.size

    fun discoveryTimeoutConfiguration(): HttpTimeoutConfiguration = discoveryHttp.timeoutConfiguration()

    private fun pageUrl(path: String, page: Int): String {
        val safePage = page.coerceAtLeast(1)
        return if (safePage == 1) "$baseUrl$path" else "$baseUrl${path}index_${safePage}.html"
    }

    fun parseCategoryListPage(html: String, page: Int): CategoryBrowseResult {
        if (looksLikeDiscoveryVerificationPage(html)) {
            return CategoryBrowseResult.Failure("分类页返回验证页面")
        }
        val listMatch = Regex("<ul[^>]+class=[\"'][^\"']*imgtextlist[^\"']*[\"'][^>]*>([\\s\\S]*?)</ul>", RegexOption.IGNORE_CASE)
            .find(html) ?: return CategoryBrowseResult.Failure("分类列表结构已变化")
        val adjacentTail = html.substring(listMatch.range.last + 1)
        val pagesMatch = Regex("^\\s*<div[^>]+class=[\"'][^\"']*pages[^\"']*[\"'][^>]*>([\\s\\S]*?)</div>", RegexOption.IGNORE_CASE)
            .find(adjacentTail) ?: return CategoryBrowseResult.Failure("分类分页结构已变化")
        val listHtml = listMatch.groupValues[1]
        val rawItems = HtmlTools.allMatches(listHtml, "<li[^>]*>([\\s\\S]*?)</li>")
        val items = parseListPage("<ul class=\"imgtextlist\">$listHtml</ul>")
        if (rawItems.isNotEmpty() && items.isEmpty()) {
            return CategoryBrowseResult.Failure("分类条目结构已变化")
        }
        if (rawItems.isEmpty() && HtmlTools.stripTags(listHtml).isNotBlank()) {
            return CategoryBrowseResult.Failure("分类条目结构已变化")
        }
        val nextPage = page.coerceAtLeast(1) + 1
        return CategoryBrowseResult.Success(
            items = items,
            hasMore = Regex("index_${nextPage}\\.html", RegexOption.IGNORE_CASE).containsMatchIn(pagesMatch.value),
            partialFailure = items.size < rawItems.size
        )
    }

    /** Parses only the homepage's verified image-text list, never a challenge page. */
    fun parseHomepagePage(html: String): CategoryBrowseResult {
        if (looksLikeDiscoveryVerificationPage(html)) {
            return CategoryBrowseResult.Failure("首页返回验证页面")
        }
        if (isExplicitlyEmptyDiscoveryPage(html)) {
            return CategoryBrowseResult.Success(emptyList(), hasMore = false)
        }
        val listMatch = Regex(
            "<ul[^>]+class=[\"'][^\"']*imgtextlist[^\"']*[\"'][^>]*>([\\s\\S]*?)</ul>",
            RegexOption.IGNORE_CASE
        ).find(html) ?: return CategoryBrowseResult.Failure("首页精选列表结构已变化")
        val listHtml = listMatch.groupValues[1]
        val rawItems = HtmlTools.allMatches(listHtml, "<li[^>]*>([\\s\\S]*?)</li>")
        val items = parseListPage("<ul class=\"imgtextlist\">$listHtml</ul>")
        if (rawItems.isNotEmpty() && items.isEmpty()) {
            return CategoryBrowseResult.Failure("首页精选条目结构已变化")
        }
        if (rawItems.isEmpty() && HtmlTools.stripTags(listHtml).isNotBlank()) {
            return CategoryBrowseResult.Failure("首页精选条目结构已变化")
        }
        return CategoryBrowseResult.Success(
            items = items.take(30),
            hasMore = false,
            partialFailure = items.size < rawItems.size
        )
    }

    private suspend fun fetchCategoryPage(url: String, page: Int): CategoryBrowseResult =
        safeCategoryBrowse(10_000) { parseCategoryListPage(discoveryHttp.get(url, safety), page) }

    private fun sourcePage(category: String, page: Int): Int = if (category == "全部") 1 else page.coerceAtLeast(1)

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

}
