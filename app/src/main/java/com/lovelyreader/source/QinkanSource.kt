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

class QinkanSource(
    private val http: HttpTextClient = HttpTextClient(),
    private val discoveryHttp: HttpTextClient = HttpTextClient(
        connectTimeoutMillis = 6_000,
        readTimeoutMillis = 8_000
    )
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
    private val discoveryAllPaths = listOf("/npyq/", "/xdds/", "/xhqh/", "/wxxx/", "/lsjs/", "/yxjj/", "/khly/", "/mwtr/")
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

    override suspend fun getChapterContent(chapterUrl: String): ChapterContent? =
        getChapterContentWithProgress(chapterUrl) { _, _ -> }

    override suspend fun getChapterContentWithProgress(
        chapterUrl: String,
        onProgress: suspend (readBytes: Long, totalBytes: Long?) -> Unit
    ): ChapterContent? {
        val allowedUrl = safety.requireAllowed(chapterUrl)
        val html = http.get(allowedUrl, safety)
        val options = parseDownloadOptions(html).filter { it.allowed && it.format == "txt" }
        if (options.isEmpty()) return null
        val title = parseTitle(html).ifBlank { "全文TXT" }
        for (option in options) {
            val text = http.getWithProgress(option.url, referer = allowedUrl, onProgress = onProgress).trim()
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
            "言情" -> "/npyq/"
            "现代言情" -> "/npyq/xd/"
            "古代言情" -> "/npyq/yq/"
            "穿越架空" -> "/npyq/cy/"
            "宫闱情仇" -> "/npyq/qc/"
            "浪漫言情" -> "/npyq/lm/"
            "菁菁校园" -> "/npyq/qq/"
            "爱在职场" -> "/npyq/az/"
            "耽美纯爱" -> "/npyq/dm/"
            "现代都市" -> "/xdds/"
            "玄幻奇幻" -> "/xhqh/"
            "武侠仙侠" -> "/wxxx/"
            "历史军事" -> "/lsjs/"
            "游戏竞技" -> "/yxjj/"
            "科幻世界" -> "/khly/sj/"
            "灵异神怪" -> "/khly/ly/"
            "美文同人" -> "/mwtr/"
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
        val container = Regex(
            "<div[^>]+class=[\"'][^\"']*listBox[^\"']*[\"'][^>]*>[\\s\\S]*?" +
                "<ul[^>]*>([\\s\\S]*?)</ul>\\s*" +
                "(<div[^>]+class=[\"'][^\"']*tspage[^\"']*[\"'][^>]*>[\\s\\S]*?</div>)\\s*</div>",
            RegexOption.IGNORE_CASE
        ).find(html) ?: return CategoryBrowseResult.Failure("分类列表或分页结构已变化")
        val listHtml = container.groupValues[1]
        val pageHtml = container.groupValues[2]
        val rawItems = HtmlTools.allMatches(listHtml, "<li[^>]*>([\\s\\S]*?)</li>")
        val items = rawItems
            .mapNotNull { match -> parseCategoryListItem(match.value) }
        if (rawItems.isNotEmpty() && items.isEmpty()) {
            return CategoryBrowseResult.Failure("分类条目结构已变化")
        }
        if (rawItems.isEmpty() && HtmlTools.stripTags(listHtml).isNotBlank()) {
            return CategoryBrowseResult.Failure("分类条目结构已变化")
        }
        val nextPage = page.coerceAtLeast(1) + 1
        val hasMore = Regex("index_${nextPage}\\.html", RegexOption.IGNORE_CASE).containsMatchIn(pageHtml)
        return CategoryBrowseResult.Success(items, hasMore, partialFailure = items.size < rawItems.size)
    }

    /** Parses only the homepage's real listBox, preserving explicit failure states. */
    fun parseHomepagePage(html: String): CategoryBrowseResult {
        if (looksLikeDiscoveryVerificationPage(html)) {
            return CategoryBrowseResult.Failure("首页返回验证页面")
        }
        if (isExplicitlyEmptyDiscoveryPage(html)) {
            return CategoryBrowseResult.Success(emptyList(), hasMore = false)
        }
        val listBox = extractListBoxBody(html)
            ?: return CategoryBrowseResult.Failure("首页精选列表结构已变化")
        val listHtml = Regex("<ul[^>]*>([\\s\\S]*?)</ul>", RegexOption.IGNORE_CASE)
            .find(listBox)?.groupValues?.get(1)
            ?: return CategoryBrowseResult.Failure("首页精选列表结构已变化")
        val rawItems = HtmlTools.allMatches(listHtml, "<li[^>]*>([\\s\\S]*?)</li>")
        val items = rawItems.mapNotNull { match -> parseCategoryListItem(match.value) }
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

    private fun extractListBoxBody(html: String): String? {
        val open = Regex(
            "<div[^>]+class=[\"'][^\"']*listBox[^\"']*[\"'][^>]*>",
            RegexOption.IGNORE_CASE
        ).find(html) ?: return null
        val tag = Regex("</?div\\b[^>]*>", RegexOption.IGNORE_CASE)
        var depth = 1
        for (match in tag.findAll(html, open.range.last + 1)) {
            if (match.value.startsWith("</", ignoreCase = false)) {
                depth -= 1
                if (depth == 0) {
                    return html.substring(open.range.last + 1, match.range.first)
                }
            } else if (!match.value.trimEnd().endsWith("/>")) {
                depth += 1
            }
        }
        return null
    }

    private fun sourcePage(category: String, page: Int): Int = if (category == "全部") 1 else page.coerceAtLeast(1)

    private fun parseCategoryListItem(item: String): SearchResult? {
        val href = HtmlTools.firstMatch(item, "<a[^>]+href=[\"'](/book/\\d+\\.html)[\"']") ?: return null
        val title = HtmlTools.firstMatch(item, "<a[^>]+href=[\"']/book/\\d+\\.html[\"'][^>]*>[\\s\\S]*?《([^》]+)》")
            ?.let(HtmlTools::stripTags)?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val author = HtmlTools.firstMatch(item, "作者[：:]\\s*([^<]+)")?.let(HtmlTools::stripTags).orEmpty()
        val summary = HtmlTools.firstMatch(item, "<div[^>]+class=[\"'][^\"']*u[^\"']*[\"'][^>]*>([\\s\\S]*?)</div>")
            ?.let(HtmlTools::stripTags).orEmpty()
        return SearchResult(
            sourceId = sourceId,
            title = title,
            author = author,
            bookUrl = HtmlTools.absoluteUrl(baseUrl, href),
            summary = summary,
            coverUrl = HtmlTools.firstMatch(item, "<img[^>]+src=[\"']([^\"']+)[\"']")
                ?.let { HtmlTools.absoluteUrl(baseUrl, it) },
            capabilities = capabilities
        ).takeIf { safety.isAllowed(it.bookUrl) }
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
