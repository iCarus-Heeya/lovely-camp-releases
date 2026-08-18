package com.lovelyreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lovelyreader.data.LibraryPersistence
import com.lovelyreader.data.LibraryRepository
import com.lovelyreader.data.LibrarySnapshot
import com.lovelyreader.domain.AppTheme
import com.lovelyreader.domain.Book
import com.lovelyreader.domain.BookDetail
import com.lovelyreader.domain.BookStatus
import com.lovelyreader.domain.ChapterContent
import com.lovelyreader.domain.RankingPeriod
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SizeBand
import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.source.AggregatedNovelCatalog
import com.lovelyreader.source.BrowsableNovelSource
import com.lovelyreader.source.IjjxsSource
import com.lovelyreader.source.IxdzsSource
import com.lovelyreader.source.NovelSource
import com.lovelyreader.source.QinkanSource
import com.lovelyreader.source.QisuwangSource
import com.lovelyreader.source.SearchResultMerger
import com.lovelyreader.source.SourceContentGuard
import com.lovelyreader.source.ZxcsSource
import com.lovelyreader.source.stableBookId
import com.lovelyreader.sync.ReadingEvent
import com.lovelyreader.sync.ReadingEventType
import com.lovelyreader.sync.ReadingLogSync
import com.lovelyreader.ui.downloadBookWithFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

enum class ShelfSortMode {
    Default, ByProgress, ByTitle
}

/** Schedules persistence exactly once; file I/O remains inside [LibraryPersistence]. */
internal fun persistLibrarySnapshot(
    scope: CoroutineScope,
    persistence: LibraryPersistence,
    snapshot: () -> LibrarySnapshot
): Job = scope.launch {
    persistence.save(snapshot())
}

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val persistence: LibraryPersistence,
    private val sync: ReadingLogSync
) : ViewModel() {

    private val sources = listOf(IxdzsSource(), IjjxsSource(), QisuwangSource(), QinkanSource(), ZxcsSource())
    private val catalog = AggregatedNovelCatalog

    private val _screen = MutableStateFlow<Screen>(Screen.Shelf)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _shelfBooks = MutableStateFlow<List<Book>>(emptyList())
    val shelfBooks: StateFlow<List<Book>> = _shelfBooks.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _rankingResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val rankingResults: StateFlow<List<SearchResult>> = _rankingResults.asStateFlow()

    private val _randomResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val randomResults: StateFlow<List<SearchResult>> = _randomResults.asStateFlow()

    private val _searchMessage = MutableStateFlow(WarmPhrases.searchIdle.random())
    val searchMessage: StateFlow<String> = _searchMessage.asStateFlow()

    private val _rankingMessage = MutableStateFlow("打开排行榜时会刷新真实来源。")
    val rankingMessage: StateFlow<String> = _rankingMessage.asStateFlow()

    private val _randomMessage = MutableStateFlow("选择类型后会从真实来源随机换一批，默认全部。")
    val randomMessage: StateFlow<String> = _randomMessage.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingRanking = MutableStateFlow(false)
    val isLoadingRanking: StateFlow<Boolean> = _isLoadingRanking.asStateFlow()

    private val _isLoadingRandom = MutableStateFlow(false)
    val isLoadingRandom: StateFlow<Boolean> = _isLoadingRandom.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private var searchJob: Job? = null
    private var searchRunId = 0
    private var rankingRunId = 0
    private var randomRunId = 0

    private val _selectedDetail = MutableStateFlow<BookDetail?>(null)
    val selectedDetail: StateFlow<BookDetail?> = _selectedDetail.asStateFlow()

    private val _readerChapter = MutableStateFlow<ChapterContent?>(null)
    val readerChapter: StateFlow<ChapterContent?> = _readerChapter.asStateFlow()

    private val _isLoadingChapter = MutableStateFlow(false)
    val isLoadingChapter: StateFlow<Boolean> = _isLoadingChapter.asStateFlow()

    private val _chapterLoadAttempted = MutableStateFlow(false)
    val chapterLoadAttempted: StateFlow<Boolean> = _chapterLoadAttempted.asStateFlow()

    private val _lastReaderBookId = MutableStateFlow<String?>(null)
    val lastReaderBookId: StateFlow<String?> = _lastReaderBookId.asStateFlow()

    private val _downloadStatuses = MutableStateFlow<Map<String, BookDownloadStatus>>(emptyMap())
    val downloadStatuses: StateFlow<Map<String, BookDownloadStatus>> = _downloadStatuses.asStateFlow()

    private val _downloadingBookIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingBookIds: StateFlow<Set<String>> = _downloadingBookIds.asStateFlow()

    private val _syncConfigured = MutableStateFlow(sync.isConfigured)
    val syncConfigured: StateFlow<Boolean> = _syncConfigured.asStateFlow()

    private val _syncMessage = MutableStateFlow("")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    private val _appTheme = MutableStateFlow(AppTheme.Warm)
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    init {
        viewModelScope.launch {
            persistence.load()?.let(repository::restore)
            _appTheme.value = repository.appTheme
            _shelfBooks.value = repository.bookshelf()
            _downloadStatuses.value = repository.bookshelf().associate { book ->
                val offline = repository.offlineChapterFor(book.id)
                book.id to when {
                    offline?.let { SourceContentGuard.isReadableNovelText(it.content) } == true -> {
                        BookDownloadStatus(DownloadState.Ready, 100)
                    }
                    repository.partialChaptersFor(book.id).isNotEmpty() -> {
                        val cached = repository.partialChaptersFor(book.id)
                        BookDownloadStatus(
                            DownloadState.Downloading,
                            0,
                            "已缓存 ${cached.size} 章，点击继续"
                        )
                    }
                    else -> BookDownloadStatus()
                }
            }
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        sync.setEnabled(enabled)
    }

    fun updateSyncCredentials(token: String, gistId: String) {
        sync.githubToken = token.trim()
        sync.gistId = gistId.trim()
        _syncConfigured.value = sync.isConfigured
        _syncMessage.value = if (sync.isConfigured) "已保存 GitHub 凭证" else "Token 或 Gist ID 为空"
    }

    fun clearSyncMessage() {
        _syncMessage.value = ""
    }

    fun clearSyncAuth() {
        sync.clearAuth()
        _syncConfigured.value = false
    }

    fun isBookReady(bookId: String): Boolean {
        if (bookId in _downloadingBookIds.value) return false
        val content = repository.offlineChapterFor(bookId)?.content ?: return false
        if (!SourceContentGuard.isReadableNovelText(content)) return false
        return content.length >= 500
    }

    fun openShelf() {
        recordReaderExitProgress()
        _screen.value = Screen.Shelf
    }

    fun openSearch() {
        recordReaderExitProgress()
        _screen.value = Screen.Search
    }

    fun openReader() {
        recordReaderExitProgress()
        val bookId = _lastReaderBookId.value?.takeIf(::isBookReady)
            ?: repository.bookshelf().firstOrNull { isBookReady(it.id) }?.id
        if (bookId == null) return
        val current = _screen.value
        if (current is Screen.Reader && current.bookId == bookId) return
        _lastReaderBookId.value = bookId
        _readerChapter.value = null
        _chapterLoadAttempted.value = false
        _isLoadingChapter.value = true
        _screen.value = Screen.Reader(bookId)
    }

    fun openNotes() {
        recordReaderExitProgress()
        _screen.value = Screen.Settings
    }

    fun navigateBack() {
        readerBackDestination(_screen.value)?.let { destination ->
            _screen.value = destination
        }
    }

    private fun recordReaderExitProgress() {
        val current = _screen.value
        if (current is Screen.Reader) {
            val bookId = current.bookId
            val percent = progressFor(bookId)
            repository.bookById(bookId)?.let { book ->
                recordEvent(ReadingEventType.READ_PROGRESS, book = book, progressPercent = percent)
            }
        }
    }

    fun navigateToDetail(result: SearchResult) {
        repository.markSeenTitle(result.title)
        persist()
        _selectedDetail.value = null
        _readerChapter.value = null
        _chapterLoadAttempted.value = false
        _screen.value = Screen.Detail(result)
    }

    fun navigateToReader(bookId: String) {
        _lastReaderBookId.value = bookId
        _readerChapter.value = null
        _chapterLoadAttempted.value = false
        _isLoadingChapter.value = true
        _screen.value = Screen.Reader(bookId)
        repository.bookById(bookId)?.let { book ->
            val percent = progressFor(bookId)
            recordEvent(ReadingEventType.OPEN_BOOK, book = book, progressPercent = percent)
        }
    }

    fun clearReaderState() {
        _readerChapter.value = null
        _chapterLoadAttempted.value = false
    }

    fun clearLastReaderBookId() {
        _lastReaderBookId.value = null
    }

    fun refreshRanking(period: RankingPeriod) {
        val runId = rankingRunId + 1
        rankingRunId = runId
        _isLoadingRanking.value = true
        _rankingResults.value = emptyList()
        _rankingMessage.value = "正在从真实来源刷新${period.label}。"
        viewModelScope.launch {
            val realResults = sources
                .filterIsInstance<BrowsableNovelSource>()
                .map { source ->
                    async {
                        withContext(Dispatchers.Default) {
                            withTimeoutOrNull(18_000) {
                                runCatching { source.ranking(period) }.getOrDefault(emptyList())
                            }.orEmpty()
                        }
                    }
                }
                .awaitAll()
                .flatten()
                .filterNot { it.title in repository.seenTitles() }
            if (rankingRunId == runId) {
                _rankingResults.value = SearchResultMerger.merge(realResults)
                _rankingMessage.value = if (_rankingResults.value.isEmpty()) {
                    "这次没有拿到真实排行榜，可能是来源站点超时或拦截。"
                } else {
                    "已刷新 ${_rankingResults.value.size} 条真实来源排行榜结果。"
                }
                _isLoadingRanking.value = false
            }
        }
    }

    fun refreshRandomBrowse(category: String) {
        val runId = randomRunId + 1
        randomRunId = runId
        _isLoadingRandom.value = true
        _randomResults.value = emptyList()
        _randomMessage.value = "正在从真实来源按「$category」随机拉取。"
        viewModelScope.launch {
            val realResults = sources
                .filterIsInstance<BrowsableNovelSource>()
                .map { source ->
                    async {
                        withContext(Dispatchers.Default) {
                            withTimeoutOrNull(18_000) {
                                runCatching { source.randomBrowse(category, false, SizeBand("all", 0, 999_999)) }.getOrDefault(emptyList())
                            }.orEmpty()
                        }
                    }
                }
                .awaitAll()
                .flatten()
                .filterNot { it.title in repository.seenTitles() }
            if (randomRunId == runId) {
                val merged = SearchResultMerger.merge(realResults)
                _randomResults.value = merged.shuffled(Random(runId)).take(12)
                _randomMessage.value = if (_randomResults.value.isEmpty()) {
                    "这次没有拿到「$category」的真实推荐，可能是来源站点超时或该分类暂未适配。"
                } else {
                    "已从真实来源按「$category」换来 ${_randomResults.value.size} 本。"
                }
                _isLoadingRandom.value = false
            }
        }
    }

    fun performSearch(query: String) {
        searchJob?.cancel()
        val runId = searchRunId + 1
        searchRunId = runId
        if (query.isBlank()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            _searchMessage.value = WarmPhrases.searchIdle.random()
            return
        }
        _searchHistory.value = (listOf(query) + _searchHistory.value.filter { it != query }).take(8)
        recordEvent(ReadingEventType.SEARCH, keyword = query)
        _isSearching.value = true
        _searchMessage.value = WarmPhrases.searchLoading.random()
        _searchResults.value = emptyList()
        searchJob = viewModelScope.launch {
            try {
                val all = sources.map { source ->
                    async {
                        withContext(Dispatchers.Default) {
                            withTimeoutOrNull(12_000) {
                                runCatching { source.search(query) }.getOrDefault(emptyList())
                            }.orEmpty()
                        }
                    }
                }.awaitAll()
                val merged = SearchResultMerger.merge(all.flatten())
                if (searchRunId == runId) {
                    _searchResults.value = merged
                    _searchMessage.value = if (merged.isEmpty()) {
                        WarmPhrases.searchEmpty.random()
                    } else {
                        "已经从优先来源找到 ${merged.size} 条结果。"
                    }
                }
            } finally {
                if (searchRunId == runId) {
                    _isSearching.value = false
                }
            }
        }
    }

    fun startDownloadToShelf(result: SearchResult, preferredBook: Book? = null) {
        val book = preferredBook ?: result.toBook()
        repository.addToShelf(book)
        _shelfBooks.value = repository.bookshelf()
        repository.markSeenTitle(book.title)
        repository.updateProgress(book.id, result.bookUrl, 0)
        persist()
        recordEvent(ReadingEventType.ADD_SHELF, book = book, sourceId = result.sourceId)
        _selectedDetail.value = null
        _readerChapter.value = null
        _chapterLoadAttempted.value = false
        _lastReaderBookId.value = book.id
        updateDownloadStatus(book.id, BookDownloadStatus(DownloadState.Downloading, 1, "准备下载"))
        _downloadingBookIds.value = _downloadingBookIds.value + book.id
        _screen.value = Screen.Shelf

        viewModelScope.launch {
            try {
                val downloaded = sources.downloadBookWithFallback(
                bookId = book.id,
                initialResult = result,
                bookTitle = book.title,
                author = book.author,
                repository = repository,
                onProgress = { report ->
                    updateDownloadStatus(book.id, BookDownloadStatus(DownloadState.Downloading, report.percent, report.message))
                }
            )
            if (downloaded != null) {
                val (downloadedResult, chapter) = downloaded
                val storedBook = book.copy(
                    sourceIds = listOf(downloadedResult.sourceId),
                    summary = downloadedResult.summary.ifBlank { book.summary },
                    coverUrl = downloadedResult.coverUrl ?: book.coverUrl
                )
                repository.addToShelf(storedBook)
                repository.cacheOfflineChapter(storedBook.id, chapter)
                repository.updateProgress(storedBook.id, chapter.url, 0)
                _shelfBooks.value = repository.bookshelf()
                persist()
                val readyMessage = if (downloadedResult.sourceId == result.sourceId) {
                    "已下载"
                } else {
                    "已换源下载"
                }
                updateDownloadStatus(storedBook.id, BookDownloadStatus(DownloadState.Ready, 100, readyMessage))
            } else {
                updateDownloadStatus(
                    book.id,
                    BookDownloadStatus(DownloadState.Failed, 0, "下载失败：已尝试换源，公开来源仍返回验证页或暂时不可读")
                )
                persist()
            }
            } finally {
                _downloadingBookIds.value = _downloadingBookIds.value - book.id
            }
        }
    }

    fun retryDownload(book: Book) {
        val sourceUrl = repository.progressFor(book.id)?.chapterUrl.orEmpty()
        if (sourceUrl.isNotBlank()) {
            startDownloadToShelf(book.toSearchResult(sourceUrl), book)
        }
    }

    fun deleteBook(bookId: String) {
        val book = repository.bookById(bookId)
        repository.deleteBook(bookId)
        _shelfBooks.value = repository.bookshelf()
        _downloadStatuses.value = _downloadStatuses.value - bookId
        if (_lastReaderBookId.value == bookId) {
            _lastReaderBookId.value = null
            _readerChapter.value = null
            _chapterLoadAttempted.value = false
        }
        persist()
        book?.let { recordEvent(ReadingEventType.DELETE_BOOK, book = it) }
    }

    fun loadDetail(result: SearchResult) {
        viewModelScope.launch {
            val source = sources.firstOrNull { it.sourceId == result.sourceId }
            _selectedDetail.value = source?.loadDetailOrFallback(result)
        }
    }

    fun loadChapter(bookId: String) {
        viewModelScope.launch {
            _isLoadingChapter.value = true
            _chapterLoadAttempted.value = false
            _readerChapter.value = repository.offlineChapterFor(bookId)
                ?.takeIf { SourceContentGuard.isReadableNovelText(it.content) }
            _chapterLoadAttempted.value = true
            _isLoadingChapter.value = false
        }
    }

    fun updateProgress(bookId: String, percent: Int, lastReadIndex: Int = 0, lastReadOffset: Int = 0) {
        val existing = repository.progressFor(bookId)
        repository.updateProgress(
            bookId = bookId,
            chapterUrl = existing?.chapterUrl ?: _readerChapter.value?.url.orEmpty(),
            percent = percent,
            lastReadIndex = lastReadIndex,
            lastReadOffset = lastReadOffset
        )
        persist()
        // 阅读进度不再每次滚动都上报，只在打开书和退出阅读器时各记录一次。
    }

    fun addBookmark(bookId: String) {
        repository.addBookmark(
            com.lovelyreader.domain.Bookmark(
                bookId = bookId,
                chapterUrl = _readerChapter.value?.url ?: repository.progressFor(bookId)?.chapterUrl.orEmpty()
            )
        )
        persist()
        repository.bookById(bookId)?.let { book ->
            recordEvent(ReadingEventType.ADD_BOOKMARK, book = book)
        }
    }

    private val _shelfSortMode = MutableStateFlow(ShelfSortMode.Default)
    val shelfSortMode: StateFlow<ShelfSortMode> = _shelfSortMode.asStateFlow()

    fun setShelfSortMode(mode: ShelfSortMode) {
        _shelfSortMode.value = mode
    }

    fun bookshelf(): List<Book> = repository.bookshelf()

    fun sortBooks(books: List<Book>, mode: ShelfSortMode): List<Book> {
        return when (mode) {
            ShelfSortMode.ByProgress -> books.sortedByDescending { progressFor(it.id) }
            ShelfSortMode.ByTitle -> books.sortedBy { it.title }
            // 默认把后加入的书放顶部，方便找到刚下载/添加的书
            ShelfSortMode.Default -> books.reversed()
        }
    }

    fun sortedBookshelf(): List<Book> = sortBooks(_shelfBooks.value, _shelfSortMode.value)
    fun progressFor(bookId: String): Int = repository.progressFor(bookId)?.percent ?: 0
    fun lastReadPositionFor(bookId: String): Pair<Int, Int> = repository.lastReadPositionFor(bookId)
    fun readerPreferences(): Pair<Int, Boolean> = repository.readerPreferences()
    fun updateReaderFontSize(fontSize: Int) {
        repository.updateReaderPreferences(fontSize = fontSize)
        persist()
    }
    fun updateReaderNightMode(nightMode: Boolean) {
        repository.updateReaderPreferences(nightMode = nightMode)
        persist()
    }

    fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
        repository.updateReaderPreferences(theme = theme)
        persist()
    }

    fun bookById(bookId: String): Book? = repository.bookById(bookId)
    fun notes(): List<String> = repository.husbandNotes().map { it.message }

    fun downloadStatusFor(bookId: String): BookDownloadStatus {
        return _downloadStatuses.value[bookId] ?: if (isBookReady(bookId)) {
            BookDownloadStatus(DownloadState.Ready, 100)
        } else {
            BookDownloadStatus()
        }
    }

    private fun updateDownloadStatus(bookId: String, status: BookDownloadStatus) {
        _downloadStatuses.value = _downloadStatuses.value + (bookId to status)
    }

    private fun recordEvent(
        type: ReadingEventType,
        book: Book? = null,
        progressPercent: Int = 0,
        keyword: String = "",
        sourceId: String = "",
        note: String = ""
    ) {
        if (!sync.isEnabled) return
        val event = ReadingEvent(
            type = type,
            deviceName = sync.currentDeviceName(),
            bookTitle = book?.title ?: "",
            bookAuthor = book?.author ?: "",
            progressPercent = progressPercent,
            keyword = keyword,
            sourceId = sourceId,
            note = note
        )
        viewModelScope.launch {
            runCatching { sync.syncEvent(event) }
        }
    }

    private fun persist() {
        persistLibrarySnapshot(viewModelScope, persistence, repository::snapshot)
    }

    private fun SearchResult.toBook(): Book {
        return Book(
            id = stableBookId(sourceId, title, author),
            title = title,
            author = author,
            status = BookStatus.UNKNOWN,
            summary = summary,
            coverUrl = coverUrl,
            sourceIds = listOf(sourceId)
        )
    }

    private fun Book.toSearchResult(bookUrl: String): SearchResult {
        return SearchResult(
            sourceId = sourceIds.firstOrNull().orEmpty(),
            title = title,
            author = author,
            bookUrl = bookUrl,
            summary = summary,
            coverUrl = coverUrl,
            capabilities = setOf(SourceCapability.READ_CHAPTER)
        )
    }

    private suspend fun NovelSource.loadDetailOrFallback(result: SearchResult): BookDetail {
        return withTimeoutOrNull(12_000) {
            runCatching { getBookDetail(result.bookUrl) }.getOrNull()
        } ?: BookDetail(
            book = result.toBook(),
            sourceUrl = result.bookUrl,
            latestChapter = result.latestChapter,
            offlineLabel = "仅打开原站"
        )
    }
}
