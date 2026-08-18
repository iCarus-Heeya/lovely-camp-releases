package com.lovelyreader.ui

import com.lovelyreader.data.LibraryRepository
import com.lovelyreader.domain.RankingPeriod
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SizeBand
import com.lovelyreader.source.BrowsableNovelSource
import com.lovelyreader.source.HttpTextClient
import com.lovelyreader.source.IjjxsSource
import com.lovelyreader.source.IxdzsSource
import com.lovelyreader.source.QinkanSource
import com.lovelyreader.source.QisuwangSource
import com.lovelyreader.source.SearchResultMerger
import com.lovelyreader.source.ZxcsSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class SamplingDownloadTest {

    private val sources = listOf(IxdzsSource(), IjjxsSource(), QisuwangSource(), QinkanSource(), ZxcsSource())

    @Test(timeout = 3_600_000)
    @Ignore("Live network diagnostic; run manually only.")
    fun rankingTotalBooksCanBeDownloaded() = runBlocking {
        val period = RankingPeriod.TOTAL
        println("\n=== 获取排行榜总榜 ===")
        val rankingResults = sources
            .filterIsInstance<BrowsableNovelSource>()
            .flatMap { source ->
                runCatching { source.ranking(period) }.getOrDefault(emptyList())
            }
            .let { SearchResultMerger.merge(it) }

        println("排行榜总榜共 ${rankingResults.size} 本")
        rankingResults.forEachIndexed { index, result ->
            println("  [${index + 1}] ${result.title} / ${result.author} [${result.sourceId}]")
        }

        val results = rankingResults.mapIndexed { index, result ->
            println("\n--- 测试下载 [${index + 1}/${rankingResults.size}] ${result.title} ---")
            testDownload(result)
        }

        val successCount = results.count { it.success }
        val failureCount = results.size - successCount
        println("\n=== 排行榜总榜下载抽查结果 ===")
        println("总数：${results.size}")
        println("成功：$successCount")
        println("失败：$failureCount")
        results.filter { !it.success }.forEach {
            println("  失败：${it.title} / ${it.author} [${it.sourceId}] - ${it.error}")
        }

        assertTrue("排行榜总榜有 $failureCount 本下载失败", failureCount == 0)
    }

    @Test(timeout = 1_800_000)
    @Ignore("Live network diagnostic; run manually only.")
    fun randomTwentyUniqueBooksCanBeDownloaded() = runBlocking {
        println("\n=== 随机选取 20 本不重复的书 ===")
        val randomBooks = mutableListOf<SearchResult>()
        val categories = listOf(
            "全部", "现言甜宠", "都市职场", "青春校园", "古言宫斗",
            "穿越重生", "年代种田", "仙侠奇缘", "悬疑推理"
        )
        val maxAttempts = 30
        var attempts = 0
        while (randomBooks.size < 20 && attempts < maxAttempts) {
            attempts++
            val category = categories.random()
            val results = sources
                .filterIsInstance<BrowsableNovelSource>()
                .flatMap { source ->
                    runCatching {
                        source.randomBrowse(category, false, SizeBand("all", 0, 999_999))
                    }.getOrDefault(emptyList())
                }
                .let { SearchResultMerger.merge(it) }
            results.forEach { result ->
                if (randomBooks.none { it.title == result.title && it.author == result.author } && randomBooks.size < 20) {
                    randomBooks.add(result)
                    println("  加入 [${randomBooks.size}] ${result.title} / ${result.author} [${result.sourceId}]")
                }
            }
        }

        println("\n共选取 ${randomBooks.size} 本")
        val results = randomBooks.mapIndexed { index, result ->
            println("\n--- 测试下载 [${index + 1}/${randomBooks.size}] ${result.title} ---")
            testDownload(result)
        }

        val successCount = results.count { it.success }
        val failureCount = results.size - successCount
        println("\n=== 随便看看随机 20 本下载抽查结果 ===")
        println("总数：${results.size}")
        println("成功：$successCount")
        println("失败：$failureCount")
        results.filter { !it.success }.forEach {
            println("  失败：${it.title} / ${it.author} [${it.sourceId}] - ${it.error}")
        }

        assertTrue("随机 20 本中有 $failureCount 本下载失败", failureCount == 0)
    }

    @Test(timeout = 120_000)
    @Ignore("Live network diagnostic; run manually only.")
    fun diagnoseZxcs_凡人修仙传() = runBlocking {
        val source = ZxcsSource()
        val title = "凡人修仙传"
        println("\n=== 诊断 zxcs《$title》===")
        val ranking = runCatching { source.ranking(RankingPeriod.TOTAL) }.getOrDefault(emptyList())
        println("  排行榜数量：${ranking.size}")
        val match = ranking.firstOrNull { it.title.contains(title) || title.contains(it.title) }
            ?: return@runBlocking println("  未在排行榜找到")
        println("  选中：${match.title} / ${match.author} -> ${match.bookUrl}")
        val chapters = runCatching { source.getChapterList(match.bookUrl) }.getOrDefault(emptyList())
        println("  章节：${chapters.size}")
        chapters.forEach { println("    [${it.order}] ${it.title} -> ${it.url}") }
        if (chapters.isNotEmpty()) {
            val chapterUrl = chapters.first().url
            val options = runCatching { source.getDownloadOptions(chapterUrl) }.getOrNull()
            println("  下载选项：${options?.size ?: 0}")
            options?.forEach { println("    [${it.allowed}] ${it.label} -> ${it.url}") }
            val option = options?.firstOrNull { it.allowed }
            if (option != null) {
                val rawResult = runCatching { HttpTextClient().get(option.url) }
                val rawText = rawResult.getOrNull()
                println("  原始文本长度：${rawText?.length ?: 0}")
                println("  原始文本前 200 字：${rawText?.take(200)}")
                rawResult.exceptionOrNull()?.printStackTrace()
            }
            val content = runCatching { source.getChapterContent(chapterUrl) }.getOrNull()
            println("  内容长度：${content?.content?.length ?: 0}")
            println("  前 200 字：${content?.content?.take(200)}")
        }
    }

    private data class DownloadTestResult(
        val title: String,
        val author: String,
        val sourceId: String,
        val success: Boolean,
        val contentLength: Int = 0,
        val finalSourceId: String = "",
        val error: String = ""
    )

    private suspend fun testDownload(result: SearchResult): DownloadTestResult {
        val repository = LibraryRepository()
        val bookId = "sample-${result.title.hashCode()}"
        var lastMessage = ""
        val downloadResult = runCatching {
            sources.downloadBookWithFallback(
                bookId = bookId,
                initialResult = result,
                bookTitle = result.title,
                author = result.author,
                repository = repository,
                config = DownloadCoordinatorConfig(
                    totalTimeoutMillis = 180_000,
                    sourceTimeoutMillis = 30_000,
                    chapterTimeoutMillis = 15_000,
                    alternativeSearchTimeoutMillis = 10_000,
                    maxChapterSourceChapters = 3000,
                    chapterConcurrency = 4,
                    chapterRetryCount = 2
                ),
                onProgress = { report ->
                    lastMessage = "[${report.percent}%] ${report.message}"
                    println("    $lastMessage")
                }
            )
        }.getOrNull()

        return if (downloadResult != null) {
            println("    成功，来源：${downloadResult.first.sourceId}，字数：${downloadResult.second.content.length}")
            DownloadTestResult(
                title = result.title,
                author = result.author,
                sourceId = result.sourceId,
                success = true,
                contentLength = downloadResult.second.content.length,
                finalSourceId = downloadResult.first.sourceId
            )
        } else {
            println("    失败：$lastMessage")
            DownloadTestResult(
                title = result.title,
                author = result.author,
                sourceId = result.sourceId,
                success = false,
                error = lastMessage
            )
        }
    }
}
