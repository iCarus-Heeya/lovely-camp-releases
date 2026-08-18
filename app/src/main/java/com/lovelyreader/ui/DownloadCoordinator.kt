package com.lovelyreader.ui

import com.lovelyreader.data.LibraryRepository
import com.lovelyreader.domain.Chapter
import com.lovelyreader.domain.ChapterContent
import com.lovelyreader.domain.PartialChapter
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.source.NovelSource
import com.lovelyreader.source.SourceContentGuard
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

internal data class DownloadCoordinatorConfig(
    val totalTimeoutMillis: Long = 600_000,
    val sourceTimeoutMillis: Long = 30_000,
    val txtDownloadTimeoutMillis: Long = 180_000,
    val chapterTimeoutMillis: Long = 15_000,
    val alternativeSearchTimeoutMillis: Long = 10_000,
    val maxChapterSourceChapters: Int = 3000,
    val chapterConcurrency: Int = 4,
    val chapterRetryCount: Int = 2,
    val minAcceptableTotalChars: Int = 800,
    val maxAcceptableFailureRatio: Float = 0.65f
)

internal data class DownloadProgressReport(
    val percent: Int,
    val message: String,
    val downloadedChapters: Int = 0,
    val totalChapters: Int = 0
)

internal suspend fun List<NovelSource>.downloadBookWithFallback(
    bookId: String,
    initialResult: SearchResult,
    bookTitle: String,
    author: String,
    repository: LibraryRepository,
    config: DownloadCoordinatorConfig = DownloadCoordinatorConfig(),
    workDispatcher: CoroutineDispatcher = Dispatchers.IO,
    onProgress: (DownloadProgressReport) -> Unit
): Pair<SearchResult, ChapterContent>? = withContext(workDispatcher) {
    withTimeoutOrNull(config.totalTimeoutMillis) {
    val title = bookTitle.ifBlank { initialResult.title }
    val bookAuthor = author.ifBlank { initialResult.author }

    emitProgress(onProgress, 1, "准备下载《$title》")

    val candidateSources = prioritizeSources(initialResult)
    val normalizedTitle = title.normalizeForDownloadMatch()
    val normalizedAuthor = bookAuthor.normalizeForDownloadMatch()

    emitProgress(onProgress, 4, "正在寻找最佳下载源")
    val alternatives = searchAlternativesForDownload(
        title = title,
        author = bookAuthor,
        excludedKey = initialResult.downloadKey(),
        searchTimeoutMillis = config.alternativeSearchTimeoutMillis
    )

    val allCandidates = mutableListOf<SearchResult>()
    allCandidates += initialResult
    allCandidates += alternatives
    val sortedCandidates = allCandidates
        .distinctBy { it.downloadKey() }
        .filter {
            SourceCapability.TXT_IMPORT in it.capabilities ||
                SourceCapability.READ_CHAPTER in it.capabilities
        }
        .sortedWith(
            compareByDescending<SearchResult> { it.isExactBookMatch(normalizedTitle, normalizedAuthor) }
                .thenByDescending { SourceCapability.TXT_IMPORT in it.capabilities }
                .thenByDescending { it.sourceReliabilityScore() }
        )

    sortedCandidates.forEachIndexed { index, candidate ->
        val sourceName = sourceDisplayName(candidate.sourceId)
        val label = if (SourceCapability.TXT_IMPORT in candidate.capabilities) {
            "TXT 全本 ($sourceName)"
        } else {
            "章节来源 ($sourceName)"
        }
        val basePercent = (8 + index * 4).coerceAtMost(78)
        emitProgress(onProgress, basePercent, "尝试 $sourceName")
        attemptWholeDownload(candidateSources, candidate, label, repository, bookId, config, onProgress)
            ?.let { return@withTimeoutOrNull it }
    }

    val cached = repository.partialChaptersFor(bookId)
    if (cached.isNotEmpty() && isAcceptablePartialBook(cached, config)) {
        emitProgress(onProgress, 100, "使用已缓存 ${cached.size} 章", cached.size, cached.size)
        return@withTimeoutOrNull mergePartialChapters(initialResult, cached)
    }

    emitProgress(onProgress, 0, "下载失败：已尝试换源，公开来源仍返回验证页或暂时不可读")
        null
    }
}

private suspend fun attemptWholeDownload(
    allSources: List<NovelSource>,
    result: SearchResult,
    label: String,
    repository: LibraryRepository,
    bookId: String,
    config: DownloadCoordinatorConfig,
    onProgress: (DownloadProgressReport) -> Unit
): Pair<SearchResult, ChapterContent>? {
    val source = allSources.firstOrNull { it.sourceId == result.sourceId } ?: return null
    val chapters = withTimeoutOrNull(config.sourceTimeoutMillis) {
        runCatching { source.getChapterList(result.bookUrl) }.getOrDefault(emptyList())
    }.orEmpty()

    if (chapters.isEmpty()) return null

    val isTxtLike = chapters.size == 1 || SourceCapability.TXT_IMPORT in source.capabilities
    if (!isTxtLike && chapters.size > config.maxChapterSourceChapters) {
        return null
    }

    return if (isTxtLike) {
        downloadTxtLike(source, result, chapters.first().url, repository, bookId, config, onProgress)
    } else {
        downloadChaptersConcurrently(
            primarySource = source,
            primaryResult = result,
            chapters = chapters,
            repository = repository,
            bookId = bookId,
            config = config,
            label = label,
            onProgress = onProgress
        )
    }
}

private suspend fun downloadTxtLike(
    source: NovelSource,
    result: SearchResult,
    txtChapterUrl: String,
    repository: LibraryRepository,
    bookId: String,
    config: DownloadCoordinatorConfig,
    onProgress: (DownloadProgressReport) -> Unit
): Pair<SearchResult, ChapterContent>? {
    emitProgress(onProgress, 10, "正在下载 TXT 全文")
    repeat(2) { attempt ->
        val content = withTimeoutOrNull(config.txtDownloadTimeoutMillis) {
            runCatching {
                source.getChapterContent(txtChapterUrl)
            }.getOrNull()
        }?.takeIf { SourceContentGuard.isReadableNovelText(it.content) }
        if (content != null) {
            repository.cacheOfflineChapter(bookId, content)
            repository.clearPartialChapters(bookId)
            emitProgress(onProgress, 100, "已下载 TXT", 1, 1)
            return result to content
        }
        if (attempt == 0) delay(1_500)
    }
    return null
}

private suspend fun downloadChaptersConcurrently(
    primarySource: NovelSource,
    primaryResult: SearchResult,
    chapters: List<Chapter>,
    repository: LibraryRepository,
    bookId: String,
    config: DownloadCoordinatorConfig,
    label: String,
    onProgress: (DownloadProgressReport) -> Unit
): Pair<SearchResult, ChapterContent>? {
    val total = chapters.size
    val existing = repository.partialChaptersFor(bookId)
    val existingByUrl = existing.associateBy { it.url }
    val tracker = DownloadProgressTracker(total)

    emitProgress(onProgress, 10, "$label：共 $total 章，已缓存 ${existing.size} 章", existing.size, total)

    val results = MutableList<ChapterContent?>(total) { index ->
        chapters[index].let { existingByUrl[it.url] }
    }

    val semaphore = Semaphore(config.chapterConcurrency)

    coroutineScope {
        val heartbeat = launch {
            while (isActive) {
                delay(800)
                tracker.emitHeartbeat(onProgress)
            }
        }

        try {
            chapters.forEachIndexed { index, chapter ->
                if (results[index] != null) {
                    tracker.reportSuccess()
                    return@forEachIndexed
                }
                launch {
                    semaphore.withPermit {
                        val content = downloadChapterWithRetry(
                            chapter = chapter,
                            source = primarySource,
                            config = config
                        )
                        if (content != null) {
                            results[index] = content
                            repository.cachePartialChapter(
                                bookId,
                                PartialChapter(
                                    bookId = bookId,
                                    title = content.title,
                                    url = content.url,
                                    content = content.content,
                                    sourceId = primarySource.sourceId
                                )
                            )
                            tracker.reportSuccess()
                        } else {
                            tracker.reportFailure()
                        }
                    }
                }
            }
        } finally {
            heartbeat.cancel()
        }
    }

    val downloaded = results.filterNotNull()
    return assembleBook(primaryResult, chapters, downloaded, repository, bookId, config, onProgress)
}

private suspend fun downloadChapterWithRetry(
    chapter: Chapter,
    source: NovelSource,
    config: DownloadCoordinatorConfig
): ChapterContent? {
    repeat(config.chapterRetryCount) { attempt ->
        val content = withTimeoutOrNull(config.chapterTimeoutMillis) {
            runCatching { source.getChapterContent(chapter.url) }.getOrNull()
        }
        if (content != null && isReadableChapterContent(content.content)) {
            return content
        }
        if (attempt < config.chapterRetryCount - 1) {
            delay((500L * (attempt + 1) + Random.nextLong(300)).coerceAtMost(2000))
        }
    }
    return null
}

private fun isReadableChapterContent(text: String): Boolean {
    val normalized = text.replace('\u00A0', ' ').trim()
    if (normalized.length < 8) return false
    val lower = normalized.lowercase()
    val signals = listOf(
        "正在验证浏览器", "正在进行安全驗證", "正在进行安全验证", "正在進行安全驗證",
        "checking your browser", "just a moment", "cloudflare",
        "enable javascript", "access denied", "forbidden", "captcha"
    )
    if (signals.any { lower.contains(it.lowercase()) }) return false
    return true
}

private suspend fun assembleBook(
    result: SearchResult,
    chapters: List<Chapter>,
    downloaded: List<ChapterContent>,
    repository: LibraryRepository,
    bookId: String,
    config: DownloadCoordinatorConfig,
    onProgress: (DownloadProgressReport) -> Unit
): Pair<SearchResult, ChapterContent>? {
    emitProgress(onProgress, 96, "合并章节 ${downloaded.size}/${chapters.size}", downloaded.size, chapters.size)

    if (!isAcceptablePartialBook(downloaded, config)) {
        if (downloaded.isNotEmpty()) {
            emitProgress(onProgress, 0, "仅缓存 ${downloaded.size}/${chapters.size} 章，内容不足", downloaded.size, chapters.size)
        }
        return null
    }

    val failureRatio = 1f - downloaded.size.toFloat() / chapters.size.coerceAtLeast(1)
    if (failureRatio > config.maxAcceptableFailureRatio) {
        emitProgress(onProgress, 0, "失败章节过多（${(failureRatio * 100).toInt()}%），尝试换源", downloaded.size, chapters.size)
        return null
    }

    val ordered = chapters.mapNotNull { chapter ->
        downloaded.find { it.url == chapter.url || normalizeChapterTitle(it.title) == normalizeChapterTitle(chapter.title) }
    }

    val fullText = ordered.joinToString("\n\n") { chapter ->
        "${chapter.title}\n\n${chapter.content}"
    }.trim()

    val chapterContent = ChapterContent(
        title = "${result.title} 全文",
        url = result.bookUrl,
        content = fullText
    )

    repository.cacheOfflineChapter(bookId, chapterContent)
    repository.clearPartialChapters(bookId)
    emitProgress(onProgress, 100, "已下载 ${ordered.size}/${chapters.size} 章", ordered.size, chapters.size)
    return result to chapterContent
}

private fun isAcceptablePartialBook(chapters: List<ChapterContent>, config: DownloadCoordinatorConfig): Boolean {
    if (chapters.isEmpty()) return false
    val totalChars = chapters.sumOf { it.content.length }
    if (totalChars < config.minAcceptableTotalChars) return false
    return true
}

private fun normalizeChapterTitle(title: String): String {
    return title.trim()
        .replace(Regex("[第\\s\\d一二三四五六七八九十百千零]+章"), "")
        .replace(Regex("[\\s\\p{P}]"), "")
}

private fun List<NovelSource>.prioritizeSources(initialResult: SearchResult): List<NovelSource> {
    val primary = firstOrNull { it.sourceId == initialResult.sourceId }
    val others = filter { it.sourceId != initialResult.sourceId }
        .sortedByDescending { it.sourceReliabilityScore() }
    return if (primary != null) listOf(primary) + others else others
}

private fun NovelSource.sourceReliabilityScore(): Int {
    return when (sourceId) {
        "qinkan" -> 100
        "qisuwang" -> 95
        "zxcs" -> 90
        "ixdzs" -> 45
        "ijjxs" -> 20
        "yqxz" -> 0
        else -> 10
    }
}

private fun mergePartialChapters(
    result: SearchResult,
    cached: List<ChapterContent>
): Pair<SearchResult, ChapterContent> {
    val fullText = cached.joinToString("\n\n") { "${it.title}\n\n${it.content}" }.trim()
    return result to ChapterContent(
        title = "${result.title} 全文",
        url = result.bookUrl,
        content = fullText
    )
}

private suspend fun List<NovelSource>.searchAlternativesForDownload(
    title: String,
    author: String,
    excludedKey: String,
    searchTimeoutMillis: Long
): List<SearchResult> = coroutineScope {
    val normalizedTitle = title.normalizeForDownloadMatch()
    val normalizedAuthor = author.normalizeForDownloadMatch()
    map { source ->
        async {
            withTimeoutOrNull(searchTimeoutMillis) {
                runCatching { source.search(title) }.getOrDefault(emptyList())
            }.orEmpty()
        }
    }.awaitAll()
        .flatten()
        .filterNot { it.downloadKey() == excludedKey }
        .filter { result ->
            val candidateTitle = result.title.normalizeForDownloadMatch()
            val candidateAuthor = result.author.normalizeForDownloadMatch()
            candidateTitle == normalizedTitle ||
                candidateTitle.contains(normalizedTitle) ||
                normalizedTitle.contains(candidateTitle) ||
                (normalizedAuthor.isNotBlank() && candidateAuthor == normalizedAuthor)
        }
        .distinctBy { "${it.sourceId}::${it.bookUrl}" }
        .sortedWith(
            compareByDescending<SearchResult> { it.isExactBookMatch(normalizedTitle, normalizedAuthor) }
                .thenByDescending { SourceCapability.TXT_IMPORT in it.capabilities }
                .thenByDescending { SourceCapability.READ_CHAPTER in it.capabilities }
                .thenByDescending { it.sourceReliabilityScore() }
        )
}

private fun List<NovelSource>.sourceDisplayName(sourceId: String): String {
    return firstOrNull { it.sourceId == sourceId }?.displayName ?: sourceId
}

private fun String.normalizeForDownloadMatch(): String {
    return trim()
        .lowercase()
        .replace(Regex("[\\s　《》<>「」『』【】\\[\\]（）()]"), "")
}

private fun SearchResult.downloadKey(): String = "${sourceId}::${bookUrl}"

private fun SearchResult.isExactBookMatch(normalizedTitle: String, normalizedAuthor: String): Boolean {
    val candidateTitle = title.normalizeForDownloadMatch()
    val candidateAuthor = author.normalizeForDownloadMatch()
    return candidateTitle == normalizedTitle &&
        (normalizedAuthor.isBlank() || candidateAuthor == normalizedAuthor)
}

private fun SearchResult.sourceReliabilityScore(): Int {
    return when (sourceId) {
        "qinkan" -> 100
        "qisuwang" -> 95
        "zxcs" -> 90
        "ixdzs" -> 45
        "ijjxs" -> 20
        "yqxz" -> 0
        else -> 10
    }
}

private fun emitProgress(
    onProgress: (DownloadProgressReport) -> Unit,
    percent: Int,
    message: String,
    downloaded: Int = 0,
    total: Int = 0
) {
    onProgress(DownloadProgressReport(percent.coerceIn(0, 100), message, downloaded, total))
}

private class DownloadProgressTracker(private val total: Int) {
    private var successCount = 0
    private var failureCount = 0

    fun reportSuccess() {
        synchronized(this) { successCount++ }
    }

    fun reportFailure() {
        synchronized(this) { failureCount++ }
    }

    fun emitHeartbeat(onProgress: (DownloadProgressReport) -> Unit) {
        val (success, failure) = synchronized(this) { successCount to failureCount }
        val processed = success + failure
        val percent = if (total > 0) (processed * 94 / total).coerceIn(0, 94) else 0
        val message = "正在下载第 $processed/$total 章，成功 $success 章"
        onProgress(DownloadProgressReport(percent, message, success, total))
    }
}
