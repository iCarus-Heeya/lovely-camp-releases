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
import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.source.BrowsableNovelSource
import com.lovelyreader.source.CategoryBrowseResult
import com.lovelyreader.source.DiscoveryCoordinator
import com.lovelyreader.source.DiscoveryEndpoint
import com.lovelyreader.source.DiscoveryLoadStatus
import com.lovelyreader.source.DiscoveryRequestGate
import com.lovelyreader.source.DiscoveryRotation
import com.lovelyreader.source.IjjxsSource
import com.lovelyreader.source.IxdzsSource
import com.lovelyreader.source.NovelSource
import com.lovelyreader.source.QinkanSource
import com.lovelyreader.source.QisuwangSource
import com.lovelyreader.source.SearchResultMerger
import com.lovelyreader.source.SourceContentGuard
import com.lovelyreader.source.ZxcsSource
import com.lovelyreader.source.normalizedBookIdentity
import com.lovelyreader.source.compatibleWithAny
import com.lovelyreader.source.safeCategoryBrowse
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

internal class LibraryViewModel(
    private val repository: LibraryRepository,
    private val persistence: LibraryPersistence,
    private val sync: ReadingLogSync,
    private val downloadScheduler: BookDownloadScheduler
) : ViewModel() {

    private val sources = listOf(IxdzsSource(), IjjxsSource(), QisuwangSource(), QinkanSource(), ZxcsSource())
    private val discoveryRotation = DiscoveryRotation()
    private val discoveryCoordinator = DiscoveryCoordinator(discoveryRotation)

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

    private val _rankingMessage = MutableStateFlow("打开首页精选时会刷新真实来源。")
    val rankingMessage: StateFlow<String> = _rankingMessage.asStateFlow()

    private val _randomMessage = MutableStateFlow("选择类型后会从真实来源随机换一批，默认全部。")
    val randomMessage: StateFlow<String> = _randomMessage.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingRanking = MutableStateFlow(false)
    val isLoadingRanking: StateFlow<Boolean> = _isLoadingRanking.asStateFlow()

    private val _isLoadingRandom = MutableStateFlow(false)
    val isLoadingRandom: StateFlow<Boolean> = _isLoadingRandom.asStateFlow()

    private val _isRandomExhausted = MutableStateFlow(false)
    val isRandomExhausted: StateFlow<Boolean> = _isRandomExhausted.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private var searchJob: Job? = null
    private var rankingJob: Job? = null
    private var randomJob: Job? = null
    private var searchRunId = 0
    private val rankingGate = DiscoveryRequestGate()
    private val randomGate = DiscoveryRequestGate()

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
    private val downloadWatchJobs = mutableMapOf<String, Job>()

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
            _shelfBooks.value.forEach { watchDownload(it.id) }
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
        repository.markSeenBook(result.title, result.author)
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

    @Suppress("UNUSED_PARAMETER")
    fun refreshRanking(period: RankingPeriod, category: String = "全部") {
        cancelDiscoveryJobs()
        val requestId = rankingGate.begin()
        _isLoadingRanking.value = true
        _rankingResults.value = emptyList()
        _rankingMessage.value = if (category == "全部") {
            "正在从各来源首页刷新精选。"
        } else {
            "正在从真实分类页刷新「$category」精选。"
        }
        rankingJob = viewModelScope.launch {
            try {
                val sourceResults = sources.mapNotNull { source ->
                    (source as? BrowsableNovelSource)?.let { source to it }
                }.map { (source, browse) ->
                    async {
                        source.sourceId to safeCategoryBrowse(10_000) {
                            if (category == "全部") browse.homepageFeatured()
                            else browse.categoryBrowse(category, 1)
                        }
                    }
                }
                .awaitAll()
                if (rankingGate.isCurrent(requestId)) {
                    val successes = sourceResults.mapNotNull { (_, result) -> result as? CategoryBrowseResult.Success }
                    val failures = sourceResults.count { it.second is CategoryBrowseResult.Failure }
                    val seenBooks = repository.seenBookIdentities()
                    _rankingResults.value = SearchResultMerger.merge(successes.flatMap { it.items })
                        .filterNot { compatibleWithAny(normalizedBookIdentity(it), seenBooks) }
                    _rankingMessage.value = when {
                        successes.isEmpty() && failures > 0 -> "来源网络请求失败，请稍后重试；现有内容没有被当作页尾。"
                        successes.isEmpty() -> "没有来源能精确提供「$category」分类，不会回退到近似栏目。"
                        _rankingResults.value.isEmpty() -> if (category == "全部") {
                            "来源首页当前没有未读的精选内容。"
                        } else {
                            "「$category」当前没有未读的分类精选。"
                        }
                        failures > 0 || successes.any { it.partialFailure } -> {
                            "已保留可用来源的 ${_rankingResults.value.size} 条精选，部分来源暂时连接失败。"
                        }
                        category == "全部" -> "已刷新 ${_rankingResults.value.size} 条来源首页精选。"
                        else -> "已从真实分类页刷新 ${_rankingResults.value.size} 条「$category」精选。"
                    }
                }
            } finally {
                if (rankingGate.isCurrent(requestId)) _isLoadingRanking.value = false
            }
        }
    }

    fun refreshRandomBrowse(category: String) {
        cancelDiscoveryJobs()
        val requestId = randomGate.begin()
        _isLoadingRandom.value = true
        _isRandomExhausted.value = false
        _randomResults.value = emptyList()
        _randomMessage.value = "正在从真实来源按「$category」随机拉取。"
        randomJob = viewModelScope.launch {
            val endpoints = sources.mapNotNull { source ->
                val browse = source as? BrowsableNovelSource ?: return@mapNotNull null
                DiscoveryEndpoint(source.sourceId) { requestedCategory, page ->
                    safeCategoryBrowse(8_000) { browse.categoryBrowse(requestedCategory, page) }
                }
            }
            try {
                val outcome = discoveryCoordinator.load(
                    category = category,
                    sources = endpoints,
                    seenTitles = repository.seenTitles(),
                    seenBooks = repository.seenBookIdentities(),
                    seed = requestId,
                    isCurrent = { randomGate.isCurrent(requestId) }
                )
                if (randomGate.isCurrent(requestId)) {
                    _randomResults.value = outcome.items
                    _isRandomExhausted.value = outcome.status == DiscoveryLoadStatus.EXHAUSTED
                    _randomMessage.value = when (outcome.status) {
                        DiscoveryLoadStatus.SUCCESS -> "已从「$category」真实分类页换来 ${outcome.items.size} 本；本轮不会重复。"
                        DiscoveryLoadStatus.PARTIAL_SUCCESS -> {
                            if (outcome.items.isEmpty()) "部分来源暂时连接失败，浏览进度和历史已保留。"
                            else "已保留可用来源的 ${outcome.items.size} 本，部分来源暂时连接失败。"
                        }
                        DiscoveryLoadStatus.FAILURE -> "来源网络请求失败，请稍后重试；浏览进度和去重历史均已保留。"
                        DiscoveryLoadStatus.UNSUPPORTED -> "没有来源能精确提供「$category」分类，不会回退到近似栏目。"
                        DiscoveryLoadStatus.EXHAUSTED -> "「$category」本轮已经看完，不会自动重复；可点“重新开始”重置。"
                        DiscoveryLoadStatus.NO_NEW_ITEMS -> "这一页没有未展示过的新书，页码已继续向后；再点一次换一批。"
                        DiscoveryLoadStatus.STALE -> return@launch
                    }
                }
            } finally {
                if (randomGate.isCurrent(requestId)) _isLoadingRandom.value = false
            }
        }
    }

    fun restartRandomBrowse(category: String) {
        cancelDiscoveryJobs()
        discoveryRotation.reset(category)
        refreshRandomBrowse(category)
    }

    fun cancelDiscoveryLoads() {
        cancelDiscoveryJobs()
    }

    private fun cancelDiscoveryJobs() {
        searchJob?.cancel()
        rankingJob?.cancel()
        randomJob?.cancel()
        searchRunId += 1
        rankingGate.begin()
        randomGate.begin()
        _isSearching.value = false
        _isLoadingRanking.value = false
        _isLoadingRandom.value = false
    }

    fun performSearch(query: String) {
        cancelDiscoveryJobs()
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
        repository.markSeenBook(book.title, book.author)
        repository.updateProgress(book.id, result.bookUrl, 0)
        recordEvent(ReadingEventType.ADD_SHELF, book = book, sourceId = result.sourceId)
        _selectedDetail.value = null
        _readerChapter.value = null
        _chapterLoadAttempted.value = false
        _lastReaderBookId.value = book.id
        updateDownloadStatus(book.id, BookDownloadStatus(DownloadState.Downloading, 1, "准备下载"))
        _downloadingBookIds.value = _downloadingBookIds.value + book.id
        _screen.value = Screen.Shelf

        // Save the shelf snapshot before WorkManager starts. A worker may be
        // launched immediately, so an asynchronous persist after enqueue can
        // otherwise race with the worker's initial restore.
        viewModelScope.launch {
            runCatching {
                persistence.save(repository.snapshot())
                downloadScheduler.enqueue(
                    BookDownloadWorkInput(
                        bookId = book.id,
                        bookTitle = book.title,
                        author = book.author,
                        result = result
                    )
                )
                watchDownload(book.id)
            }.onFailure { error ->
                _downloadingBookIds.value = _downloadingBookIds.value - book.id
                updateDownloadStatus(
                    book.id,
                    BookDownloadStatus(
                        state = DownloadState.Failed,
                        percent = 0,
                        message = "无法开始下载：${error.message.orEmpty()}"
                    )
                )
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
        downloadScheduler.cancel(bookId)
        downloadWatchJobs.remove(bookId)?.cancel()
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

    private fun watchDownload(bookId: String) {
        downloadWatchJobs.remove(bookId)?.cancel()
        downloadWatchJobs[bookId] = viewModelScope.launch {
            var handledFinal = false
            downloadScheduler.observe(bookId).collect { task ->
                updateDownloadStatus(bookId, task.progress)
                if (task.state == BookDownloadTaskState.ENQUEUED ||
                    task.state == BookDownloadTaskState.RUNNING
                ) {
                    _downloadingBookIds.value = _downloadingBookIds.value + bookId
                }
                if (task.state == BookDownloadTaskState.SUCCEEDED ||
                    task.state == BookDownloadTaskState.FAILED ||
                    task.state == BookDownloadTaskState.CANCELLED
                ) {
                    _downloadingBookIds.value = _downloadingBookIds.value - bookId
                    if (!handledFinal) {
                        handledFinal = true
                        persistence.load()?.let(repository::restore)
                        _shelfBooks.value = repository.bookshelf()
                    }
                }
            }
        }
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
            offlineLabel = "暂不支持站内下载"
        )
    }
}
