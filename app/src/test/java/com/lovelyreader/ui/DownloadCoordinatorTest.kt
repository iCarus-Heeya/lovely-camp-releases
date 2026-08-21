package com.lovelyreader.ui

import com.lovelyreader.data.LibraryRepository
import com.lovelyreader.domain.BookDetail
import com.lovelyreader.domain.Chapter
import com.lovelyreader.domain.ChapterContent
import com.lovelyreader.domain.DownloadOption
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.domain.SourceHealth
import com.lovelyreader.source.IjjxsSource
import com.lovelyreader.source.HttpTextClient
import com.lovelyreader.source.IxdzsSource
import com.lovelyreader.source.NovelSource
import com.lovelyreader.source.QinkanSource
import com.lovelyreader.source.QisuwangSource
import com.lovelyreader.source.ZxcsSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class DownloadCoordinatorTest {
    @Test
    fun progressReportCarriesTransferMetricsForRealtimeUi() {
        val report = DownloadProgressReport(
            percent = 42,
            message = "正在下载 TXT 全文",
            downloadedChapters = 0,
            totalChapters = 1,
            downloadedBytes = 4_200,
            totalBytes = 10_000,
            speedBytesPerSecond = 1_400,
            etaSeconds = 4
        )

        assertEquals(4_200, report.downloadedBytes)
        assertEquals(10_000, report.totalBytes)
        assertEquals(1_400, report.speedBytesPerSecond)
        assertEquals(4L, report.etaSeconds)
    }

    @Test
    fun txtDownloadEmitsByteProgressWhileContentIsBeingRead() = runTest {
        val progress = mutableListOf<DownloadProgressReport>()
        val source = FakeNovelSource(
            sourceId = "progress-txt",
            capabilities = setOf(SourceCapability.TXT_IMPORT),
            chapters = listOf(Chapter("全文TXT", "txt-file", 0)),
            content = "可读正文。".repeat(200),
            progressReadBytes = 4_200,
            progressTotalBytes = 10_000
        )

        val result = listOf(source).downloadBookWithFallback(
            bookId = "progress-book",
            initialResult = SearchResult(
                sourceId = "progress-txt",
                title = "进度测试",
                author = "测试",
                bookUrl = "progress-book-url",
                capabilities = setOf(SourceCapability.TXT_IMPORT)
            ),
            bookTitle = "进度测试",
            author = "测试",
            repository = LibraryRepository(),
            onProgress = { progress += it }
        )

        assertNotNull(result)
        assertTrue(progress.any { it.downloadedBytes == 4_200L && it.totalBytes == 10_000L })
    }

    @Test
    fun `book download work runs on its injected background dispatcher`() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "book-download-worker")
        }.asCoroutineDispatcher()
        var executionThread = ""
        val source = FakeNovelSource(
            sourceId = "txt",
            capabilities = setOf(SourceCapability.TXT_IMPORT),
            chapters = listOf(Chapter("full text", "txt-file", 0)),
            content = "A readable offline chapter with enough content to be accepted by the source guard. ".repeat(20),
            onOperation = { executionThread = Thread.currentThread().name }
        )

        try {
            val result = listOf(source).downloadBookWithFallback(
                bookId = "background-book",
                initialResult = SearchResult(
                    sourceId = "txt", title = "Background", author = "Tester", bookUrl = "book",
                    capabilities = setOf(SourceCapability.TXT_IMPORT)
                ),
                bookTitle = "Background",
                author = "Tester",
                repository = LibraryRepository(),
                workDispatcher = dispatcher,
                onProgress = { }
            )

            assertNotNull(result)
            assertTrue(executionThread.contains("book-download-worker"))
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun prefersTxtAlternativeBeforeSlowChapterSource() = runTest {
        val chapterSource = FakeNovelSource(
            sourceId = "chapter",
            capabilities = setOf(SourceCapability.READ_CHAPTER),
            chapters = (1..300).map { Chapter("第${it}章", "chapter-$it", it) }
        )
        val txtResult = SearchResult(
            sourceId = "txt",
            title = "香蜜沉沉烬如霜",
            author = "电线",
            bookUrl = "txt-book",
            capabilities = setOf(SourceCapability.TXT_IMPORT)
        )
        val txtSource = FakeNovelSource(
            sourceId = "txt",
            capabilities = setOf(SourceCapability.TXT_IMPORT),
            searchResults = listOf(txtResult),
            chapters = listOf(Chapter("全文TXT", "txt-file", 0)),
            content = "第一章\n\n她慢慢读完这一页，正文已经下载到本地，可以离线阅读。"
        )
        val progress = mutableListOf<DownloadProgressReport>()
        val repository = LibraryRepository()

        val result = listOf(chapterSource, txtSource).downloadBookWithFallback(
            bookId = "test-book",
            initialResult = SearchResult(
                sourceId = "chapter",
                title = "香蜜沉沉烬如霜",
                author = "电线",
                bookUrl = "chapter-book",
                capabilities = setOf(SourceCapability.READ_CHAPTER)
            ),
            bookTitle = "香蜜沉沉烬如霜",
            author = "电线",
            repository = repository,
            config = DownloadCoordinatorConfig(
                totalTimeoutMillis = 2_000,
                sourceTimeoutMillis = 500,
                chapterTimeoutMillis = 500,
                alternativeSearchTimeoutMillis = 500,
                maxChapterSourceChapters = 60
            ),
            onProgress = { progress += it }
        )

        assertEquals("txt", result?.first?.sourceId)
        assertEquals(0, chapterSource.contentAttempts)
        assertTrue(progress.none { it.percent == 2 })
        assertTrue(progress.any { it.message.contains("寻找") })
    }

    @Test
    fun triesSameSourceAlternativeWhenInitialUrlFails() = runTest {
        val badInitial = SearchResult(
            sourceId = "qinkan",
            title = "香蜜沉沉烬如霜",
            author = "电线",
            bookUrl = "bad-url",
            capabilities = setOf(SourceCapability.TXT_IMPORT)
        )
        val goodAlternative = badInitial.copy(bookUrl = "good-url")
        val source = FakeNovelSource(
            sourceId = "qinkan",
            capabilities = setOf(SourceCapability.TXT_IMPORT),
            searchResults = listOf(goodAlternative),
            chaptersByBookUrl = mapOf(
                "bad-url" to emptyList(),
                "good-url" to listOf(Chapter("全文TXT", "txt-file", 0))
            ),
            content = "第一章\n\n她慢慢读完这一页，正文已经下载到本地，可以离线阅读。"
        )
        val repository = LibraryRepository()

        val result = listOf(source).downloadBookWithFallback(
            bookId = "test-book",
            initialResult = badInitial,
            bookTitle = "香蜜沉沉烬如霜",
            author = "电线",
            repository = repository,
            config = DownloadCoordinatorConfig(
                totalTimeoutMillis = 2_000,
                sourceTimeoutMillis = 500,
                chapterTimeoutMillis = 500,
                alternativeSearchTimeoutMillis = 500
            ),
            onProgress = { }
        )

        assertEquals("good-url", result?.first?.bookUrl)
    }

    @Test
    fun triesSelectedSourceBeforeSearchingAlternatives() = runTest {
        val primary = FakeNovelSource(
            sourceId = "primary",
            capabilities = setOf(SourceCapability.TXT_IMPORT),
            chapters = listOf(Chapter("全文TXT", "primary-txt", 0)),
            content = "主来源已经提供完整正文，应该先直接开始下载。".repeat(20)
        )
        val alternative = FakeNovelSource(
            sourceId = "alternative",
            capabilities = setOf(SourceCapability.TXT_IMPORT),
            searchResults = listOf(
                SearchResult(
                    sourceId = "alternative",
                    title = "先试主来源",
                    author = "测试",
                    bookUrl = "alternative-book",
                    capabilities = setOf(SourceCapability.TXT_IMPORT)
                )
            )
        )

        val result = listOf(primary, alternative).downloadBookWithFallback(
            bookId = "primary-first",
            initialResult = SearchResult(
                sourceId = "primary",
                title = "先试主来源",
                author = "测试",
                bookUrl = "primary-book",
                capabilities = setOf(SourceCapability.TXT_IMPORT)
            ),
            bookTitle = "先试主来源",
            author = "测试",
            repository = LibraryRepository(),
            onProgress = { }
        )

        assertNotNull(result)
        assertEquals("primary", result?.first?.sourceId)
        assertEquals(0, alternative.searchCalls)
    }

    @Test
    fun returnsNullWithinTotalTimeoutWhenSourcesHang() = runTest {
        val hangingSource = FakeNovelSource(
            sourceId = "hang",
            capabilities = setOf(SourceCapability.TXT_IMPORT),
            delayMillis = 5_000
        )
        val repository = LibraryRepository()

        val result = listOf(hangingSource).downloadBookWithFallback(
            bookId = "test-book",
            initialResult = SearchResult(
                sourceId = "hang",
                title = "不存在的书",
                author = "无名",
                bookUrl = "hang-book",
                capabilities = setOf(SourceCapability.TXT_IMPORT)
            ),
            bookTitle = "不存在的书",
            author = "无名",
            repository = repository,
            config = DownloadCoordinatorConfig(
                totalTimeoutMillis = 100,
                sourceTimeoutMillis = 80,
                chapterTimeoutMillis = 80,
                alternativeSearchTimeoutMillis = 80
            ),
            onProgress = { }
        )

        assertNull(result)
    }

    @Test
    @Ignore("Live network diagnostic; run manually only.")
    fun downloadRealBook_我花开后百花杀() = runBlocking {
        val sources = listOf(IxdzsSource(), IjjxsSource(), QisuwangSource(), QinkanSource(), ZxcsSource())
        val repository = LibraryRepository()
        val title = "我花开后百花杀"
        val author = "锦凰"
        val progress = mutableListOf<DownloadProgressReport>()

        println("\n=== 开始搜索《$title》===")
        val searchResults = sources.flatMap {
            runCatching { it.search(title) }.getOrDefault(emptyList())
        }.filter { it.title.contains(title) || title.contains(it.title) }

        println("找到 ${searchResults.size} 条相关结果")
        searchResults.forEach {
            println("  [${it.sourceId}] ${it.title} / ${it.author} -> ${it.bookUrl} capabilities=${it.capabilities}")
        }

        assertTrue("没有搜索到《$title》", searchResults.isNotEmpty())

        val initialResult = searchResults.first()
        println("\n=== 使用主源 [${initialResult.sourceId}] 开始下载 ===")

        val result = sources.downloadBookWithFallback(
            bookId = "test-$title",
            initialResult = initialResult,
            bookTitle = title,
            author = author,
            repository = repository,
            config = DownloadCoordinatorConfig(
                totalTimeoutMillis = 600_000,
                sourceTimeoutMillis = 30_000,
                chapterTimeoutMillis = 15_000,
                alternativeSearchTimeoutMillis = 10_000,
                maxChapterSourceChapters = 3000,
                chapterConcurrency = 4,
                chapterRetryCount = 2
            ),
            onProgress = {
                progress += it
                println("  [${it.percent}%] ${it.message}")
            }
        )

        assertNotNull("《$title》下载失败", result)
        val content = result!!.second.content
        println("\n=== 下载成功 ===")
        println("来源：${result.first.sourceId}")
        println("总字数：${content.length}")
        println("前 200 字：${content.take(200)}")
        assertTrue("下载内容过短", content.length > 800)

        val cachedPartial = repository.partialChaptersFor("test-$title")
        val cachedOffline = repository.offlineChapterFor("test-$title")
        println("章节缓存：${cachedPartial.size} 章")
        println("离线全书：${if (cachedOffline != null) "有" else "无"}")
    }

    @Test
    @Ignore("Live network diagnostic; run manually only.")
    fun httpClientFollowsIxdzsJsChallenge() = runTest(timeout = 30.seconds) {
        val url = "https://ixdzs8.com/read/321787/p1.html"
        val client = HttpTextClient()
        val first = client.get(url)
        println("First get: length=${first.length}, hasVerify=${first.contains("正在验证浏览器")}")
        val token = Regex("let token = \"([^\"]+)\"").find(first)?.groupValues?.get(1)
        println("Token extracted: ${token != null}, value=$token")
        if (token != null) {
            val challengeUrl = url.substringBefore("?") + "?challenge=" + java.net.URLEncoder.encode(token, "UTF-8")
            println("Challenge URL: $challengeUrl")
            val second = client.get(challengeUrl)
            println("Second get: length=${second.length}, hasVerify=${second.contains("正在验证浏览器")}, hasContent=${second.contains("page-content")}")
        }
        val html = client.get(url)
        println("After get: length=${html.length}, hasVerify=${html.contains("正在验证浏览器")}, hasContent=${html.contains("page-content")}")
        assertTrue("Chapter content not present", html.contains("page-content"))
        assertFalse("Still on verification page", html.contains("正在验证浏览器"))
    }

    @Test
    @Ignore("Live network diagnostic; run manually only.")
    fun diagnoseIxdzs_我花开后百花杀() = runTest(timeout = 60.seconds) {
        val title = "我花开后百花杀"

        listOf(
            "LovelyReader/0.1 personal Android reader" to "默认 UA",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" to "浏览器 UA"
        ).forEach { (ua, label) ->
            val source = IxdzsSource(http = HttpTextClient(userAgent = ua))
            println("\n=== 诊断 ixdzs《$title》 [$label] ===")
            val searchResults = runCatching { source.search(title) }.getOrDefault(emptyList())
            println("  搜索结果：${searchResults.size}")
            searchResults.forEach {
                println("    ${it.title} / ${it.author} -> ${it.bookUrl}")
            }

            if (searchResults.isEmpty()) return@forEach

            val bookUrl = searchResults.first { it.title.contains(title) || title.contains(it.title) }.bookUrl
            println("\n  获取章节列表：$bookUrl")
            val chapters = source.getChapterList(bookUrl)
            println("  章节数：${chapters.size}")
            chapters.take(5).forEach {
                println("    [${it.order}] ${it.title} -> ${it.url}")
            }

            if (chapters.isNotEmpty()) {
                val firstChapter = chapters.first()
                println("\n  获取第一章内容：${firstChapter.url}")
                val content = source.getChapterContent(firstChapter.url)
                println("  内容长度：${content?.content?.length ?: 0}")
                println("  内容前 200 字：${content?.content?.take(200)}")
            }
        }
    }

    @Test
    @Ignore("Live network diagnostic; run manually only.")
    fun diagnoseEachSource_我花开后百花杀() = runTest(timeout = 120.seconds) {
        val title = "我花开后百花杀"
        val author = "锦凰"
        val sources = listOf(IxdzsSource(), IjjxsSource(), QisuwangSource(), QinkanSource(), ZxcsSource())

        sources.forEach { source ->
            println("\n=== 诊断 ${source.sourceId}《$title》===")
            val searchResults = runCatching {
                withTimeoutOrNull(15_000) { source.search(title) }.orEmpty()
            }.getOrElse { emptyList() }

            println("  搜索结果：${searchResults.size}")
            searchResults.forEach {
                println("    ${it.title} / ${it.author} -> ${it.bookUrl} caps=${it.capabilities}")
            }

            val match = searchResults.firstOrNull {
                it.title.contains(title) || title.contains(it.title) || it.author == author
            } ?: return@forEach

            println("  选中：${match.title} / ${match.author} -> ${match.bookUrl}")

            val chapters = runCatching {
                withTimeoutOrNull(15_000) { source.getChapterList(match.bookUrl) }.orEmpty()
            }.getOrElse { emptyList() }
            println("  章节数：${chapters.size}")

            if (chapters.isNotEmpty()) {
                val firstChapter = chapters.first()
                println("  第一章：${firstChapter.title} -> ${firstChapter.url}")
                val content = runCatching {
                    withTimeoutOrNull(15_000) { source.getChapterContent(firstChapter.url) }
                }.getOrNull()
                println("  内容长度：${content?.content?.length ?: 0}")
                println("  内容前 100 字：${content?.content?.take(100)}")
            }
        }
    }

    @Test
    @Ignore("Live network diagnostic; run manually only.")
    fun diagnoseDownload_全职高手() = runBlocking {
        val title = "全职高手"
        val author = "蝴蝶蓝"
        val sources = listOf(IxdzsSource(), IjjxsSource(), QisuwangSource(), QinkanSource(), ZxcsSource())
        val repository = LibraryRepository()
        val progress = mutableListOf<DownloadProgressReport>()

        println("\n=== 搜索《$title》===")
        val searchResults = sources.flatMap {
            runCatching { it.search(title) }.getOrDefault(emptyList())
        }.filter { it.title.contains(title) || title.contains(it.title) || it.author == author }
        searchResults.forEach {
            println("  [${it.sourceId}] ${it.title} / ${it.author} -> ${it.bookUrl} caps=${it.capabilities}")
        }

        assertTrue("没有搜索到《$title》", searchResults.isNotEmpty())

        val initialResult = searchResults.first()
        println("\n=== 使用主源 [${initialResult.sourceId}] 开始下载 ===")

        val result = sources.downloadBookWithFallback(
            bookId = "test-$title",
            initialResult = initialResult,
            bookTitle = title,
            author = author,
            repository = repository,
            config = DownloadCoordinatorConfig(
                totalTimeoutMillis = 180_000,
                sourceTimeoutMillis = 20_000,
                chapterTimeoutMillis = 10_000,
                alternativeSearchTimeoutMillis = 15_000,
                maxChapterSourceChapters = 3000,
                chapterConcurrency = 8,
                chapterRetryCount = 2
            ),
            onProgress = {
                progress += it
                println("  [${it.percent}%] ${it.message}")
            }
        )

        assertNotNull("《$title》下载失败", result)
        println("\n=== 下载成功 ===")
        println("来源：${result!!.first.sourceId}")
        println("总字数：${result.second.content.length}")
        println("前 200 字：${result.second.content.take(200)}")
        assertTrue("《$title》下载内容过短", result.second.content.length > 800)
    }
}

private class FakeNovelSource(
    override val sourceId: String,
    override val capabilities: Set<SourceCapability>,
    private val searchResults: List<SearchResult> = emptyList(),
    private val chapters: List<Chapter> = emptyList(),
    private val chaptersByBookUrl: Map<String, List<Chapter>> = emptyMap(),
    private val content: String = "第一章\n\n这是可以离线阅读的正文，下载以后不会卡在固定进度。",
    private val delayMillis: Long = 0,
    private val progressReadBytes: Long = 0L,
    private val progressTotalBytes: Long = 0L,
    private val onOperation: () -> Unit = {}
) : NovelSource {
    var contentAttempts: Int = 0
    var searchCalls: Int = 0

    override val displayName: String = sourceId
    override val baseUrl: String = "https://example.com/$sourceId"

    override suspend fun search(query: String): List<SearchResult> {
        searchCalls++
        onOperation()
        if (delayMillis > 0) delay(delayMillis)
        return searchResults
    }

    override suspend fun getBookDetail(bookUrl: String): BookDetail? = null

    override suspend fun getChapterList(bookUrl: String): List<Chapter> {
        onOperation()
        if (delayMillis > 0) delay(delayMillis)
        return chaptersByBookUrl[bookUrl] ?: chapters
    }

    override suspend fun getChapterContent(chapterUrl: String): ChapterContent? {
        onOperation()
        contentAttempts += 1
        if (delayMillis > 0) delay(delayMillis)
        return ChapterContent("正文", chapterUrl, content)
    }

    override suspend fun getChapterContentWithProgress(
        chapterUrl: String,
        onProgress: suspend (readBytes: Long, totalBytes: Long?) -> Unit
    ): ChapterContent? {
        if (progressTotalBytes > 0L) {
            onProgress(progressReadBytes, progressTotalBytes)
        }
        return getChapterContent(chapterUrl)
    }

    override suspend fun getDownloadOptions(bookUrl: String): List<DownloadOption> = emptyList()

    override suspend fun healthCheck(): SourceHealth = SourceHealth(sourceId, true, "ok")
}
