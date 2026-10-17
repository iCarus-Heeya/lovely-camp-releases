package com.lovelyreader.audiobook

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lovelyreader.data.LibraryRepository
import com.lovelyreader.domain.Book
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

enum class AudiobookTransferKind {
    QUERY,
    DOWNLOAD,
    OTHER
}

data class AudiobookTransferState(
    val bookId: String? = null,
    val message: String = "",
    val percent: Int = 0,
    val active: Boolean = false,
    val kind: AudiobookTransferKind = AudiobookTransferKind.OTHER
)

fun audiobookDownloadResultState(
    bookId: String,
    chapterTitle: String,
    error: Throwable?
): AudiobookTransferState = if (error == null) {
    AudiobookTransferState(bookId, "《$chapterTitle》下载完成", 100, false)
} else {
    AudiobookTransferState(bookId, "《$chapterTitle》下载失败：${error.message.orEmpty()}", 0, false)
}

private data class RefreshDetailResult(
    val remoteBook: AudiobookBook,
    val result: Result<Pair<AudiobookBook, List<AudiobookChapter>>>
)

private data class ReadyChapterDownload(
    val book: AudiobookBook,
    val chapter: AudiobookChapter
)

class AudiobookViewModel(
    context: Context,
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    private val appContext = context.applicationContext
    private val repository = AudiobookRepository(appContext)
    private val configStore = AudiobookConfigStore(appContext)
    private val api = AudiobookApi(configStore)
    private val player = AudiobookPlayer(appContext)
    private val _books = MutableStateFlow(repository.snapshot().books)
    val books: StateFlow<List<AudiobookBook>> = _books.asStateFlow()
    private val _uiSnapshot = MutableStateFlow(AudiobookUiSnapshot(repository.snapshot()))
    val uiSnapshot: StateFlow<AudiobookUiSnapshot> = _uiSnapshot.asStateFlow()
    private val _selectedBookId = MutableStateFlow<String?>(null)
    val selectedBookId: StateFlow<String?> = _selectedBookId.asStateFlow()
    private val _transfer = MutableStateFlow(AudiobookTransferState())
    val transfer: StateFlow<AudiobookTransferState> = _transfer.asStateFlow()
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()
    private val _connectionReady = MutableStateFlow(false)
    val connectionReady: StateFlow<Boolean> = _connectionReady.asStateFlow()
    private val _playerState = MutableStateFlow(AudiobookPlaybackState())
    val playerState: StateFlow<AudiobookPlaybackState> = _playerState.asStateFlow()
    private val downloadJobs = mutableMapOf<String, Job>()
    private var refreshJob: Job? = null
    private var readyDownloadJob: Job? = null
    private var retryJob: Job? = null
    @Volatile
    private var operationGeneration = 0L

    init {
        viewModelScope.launch {
            while (isActive) {
                val state = player.snapshot()
                _playerState.value = state
                if (state.bookId != null && state.chapterId != null && state.durationMs > 0L) {
                    repository.book(state.bookId)?.let { book ->
                        repository.chapters(state.bookId).firstOrNull { it.id == state.chapterId }?.let { chapter ->
                            repository.saveHistory(
                                AudiobookHistoryEntry(
                                    bookId = book.id,
                                    bookTitle = book.title,
                                    chapterId = chapter.id,
                                    chapterTitle = chapter.title,
                                    positionMs = state.positionMs,
                                    durationMs = state.durationMs,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
                delay(1_000)
            }
        }
    }

    fun selectBook(bookId: String?) {
        _selectedBookId.value = bookId
    }

    fun chapters(bookId: String): List<AudiobookChapter> = repository.chapters(bookId)

    fun hasAudio(chapter: AudiobookChapter): Boolean = repository.hasAudio(chapter)

    fun historyFor(bookId: String, chapterId: String): AudiobookHistoryEntry? = repository.history()
        .firstOrNull { it.bookId == bookId && it.chapterId == chapterId }

    private fun beginRefreshGeneration(): Long {
        operationGeneration += 1
        readyDownloadJob?.cancel()
        readyDownloadJob = null
        return operationGeneration
    }

    private fun beginStandaloneDownloadGeneration(): Long {
        operationGeneration += 1
        readyDownloadJob?.cancel()
        readyDownloadJob = null
        return operationGeneration
    }

    private fun isCurrentGeneration(generation: Long): Boolean =
        audiobookOperationIsCurrent(generation, operationGeneration)

    private fun emitTransfer(generation: Long?, state: AudiobookTransferState) {
        if (generation == null || isCurrentGeneration(generation)) _transfer.value = state
    }

    private fun emitMessage(generation: Long?, value: String) {
        if (generation == null || isCurrentGeneration(generation)) _message.value = value
    }

    private fun publishBooks(generation: Long?) {
        if (generation == null || isCurrentGeneration(generation)) {
            val snapshot = repository.snapshot()
            _books.value = snapshot.books
            _uiSnapshot.value = publishAudiobookUiSnapshot(_uiSnapshot.value, snapshot)
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        val generation = beginRefreshGeneration()
        refreshJob = viewModelScope.launch {
            emitTransfer(generation, AudiobookTransferState(
                message = "正在查询后台章节进度…",
                active = true,
                kind = AudiobookTransferKind.QUERY
            ))
            val failedBookTitles = mutableListOf<String>()
            val pendingDownloads = mutableListOf<ReadyChapterDownload>()
            var remoteListLoaded = false
            var detailChannel: Channel<RefreshDetailResult>? = null
            try {
                withTimeout(REFRESH_TIMEOUT_MILLIS) {
                    val remoteBooks = api.listBooks()
                    remoteListLoaded = true
                    val currentBooks = remoteBooks.map { remoteBook ->
                        remoteBook.copy(sourceUri = repository.book(remoteBook.id)?.sourceUri)
                    }
                    repository.replaceBooks(currentBooks)
                    publishBooks(generation)
                    emitTransfer(generation, AudiobookTransferState(
                        message = audiobookRefreshProgressMessage(0, currentBooks.size),
                        percent = 0,
                        active = true,
                        kind = AudiobookTransferKind.QUERY
                    ))

                    val detailSemaphore = Semaphore(REFRESH_DETAIL_CONCURRENCY)
                    val channel = Channel<RefreshDetailResult>(
                        audiobookRefreshResultBufferCapacity(currentBooks.size)
                    )
                    detailChannel = channel
                    fun applyDetail(detail: RefreshDetailResult) {
                        detail.result
                            .onSuccess { (book, chapters) ->
                                val localBook = book.copy(sourceUri = detail.remoteBook.sourceUri)
                                repository.replaceBookAndChapters(localBook, chapters)
                                chapters
                                    .filter { shouldAutoDownloadChapter(it.status, repository.hasAudio(it)) }
                                    .forEach { pendingDownloads += ReadyChapterDownload(localBook, it) }
                            }
                            .onFailure { failedBookTitles += detail.remoteBook.title }
                    }
                    coroutineScope {
                        currentBooks.forEach { remoteBook ->
                            launch(Dispatchers.IO) {
                                val result = detailSemaphore.withPermit {
                                    fetchRefreshDetail(remoteBook.id)
                                }
                                channel.send(RefreshDetailResult(remoteBook, result))
                            }
                        }
                        repeat(currentBooks.size) { index ->
                            val detail = channel.receive()
                            applyDetail(detail)
                            val completed = index + 1
                            publishBooks(generation)
                            emitTransfer(generation, AudiobookTransferState(
                                message = audiobookRefreshProgressMessage(completed, currentBooks.size),
                                percent = completed * 100 / currentBooks.size,
                                active = true,
                                kind = AudiobookTransferKind.QUERY
                            ))
                        }
                        channel.close()
                        detailChannel = null
                    }
                }
                if (!audiobookOperationIsCurrent(generation, operationGeneration)) return@launch
                _connectionReady.value = remoteListLoaded
                val resultMessage = audiobookRefreshResultMessage(failedBookTitles, emptyList())
                emitTransfer(generation, AudiobookTransferState(
                    message = resultMessage,
                    active = false,
                    percent = 100,
                    kind = AudiobookTransferKind.QUERY
                ))
                emitMessage(generation, if (failedBookTitles.isEmpty()) "" else resultMessage)
                startReadyChapterSync(pendingDownloads, failedBookTitles, generation)
            } catch (error: TimeoutCancellationException) {
                detailChannel?.let { channel ->
                    while (true) {
                        val detail = channel.tryReceive().getOrNull() ?: break
                        detail.result
                            .onSuccess { (book, chapters) ->
                                val localBook = book.copy(sourceUri = detail.remoteBook.sourceUri)
                                repository.replaceBookAndChapters(localBook, chapters)
                                chapters
                                    .filter { shouldAutoDownloadChapter(it.status, repository.hasAudio(it)) }
                                    .forEach { pendingDownloads += ReadyChapterDownload(localBook, it) }
                            }
                            .onFailure { failedBookTitles += detail.remoteBook.title }
                    }
                    channel.close()
                    detailChannel = null
                    publishBooks(generation)
                }
                if (!audiobookOperationIsCurrent(generation, operationGeneration)) return@launch
                _connectionReady.value = remoteListLoaded
                val timeoutMessage = audiobookRefreshTimeoutMessage(REFRESH_TIMEOUT_SECONDS)
                emitTransfer(generation, AudiobookTransferState(
                    message = timeoutMessage,
                    active = false,
                    percent = 0,
                    kind = AudiobookTransferKind.QUERY
                ))
                emitMessage(generation, timeoutMessage)
                startReadyChapterSync(pendingDownloads, failedBookTitles, generation)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                detailChannel?.close()
                detailChannel = null
                if (!audiobookOperationIsCurrent(generation, operationGeneration)) return@launch
                _connectionReady.value = remoteListLoaded
                val failureMessage = "刷新失败：${error.message.orEmpty()}"
                emitTransfer(generation, AudiobookTransferState(
                    message = failureMessage,
                    active = false,
                    kind = AudiobookTransferKind.QUERY
                ))
                emitMessage(generation, error.message ?: "听书状态刷新失败")
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (refreshJob === job) refreshJob = null
            }
        }
    }

    /** Requeue a failed pre-manifest analysis without asking the user for any token or URL. */
    fun retryBookAnalysis(bookId: String) {
        if (_transfer.value.active || retryJob?.isActive == true) return
        val book = repository.book(bookId)
        if (book == null || !audiobookCanRetryAnalysis(book)) {
            _message.value = "当前任务不能重新提交分析，请先刷新后台状态"
            return
        }
        val generation = beginRefreshGeneration()
        var refreshAfterRetry = false
        retryJob = viewModelScope.launch {
            emitTransfer(generation, AudiobookTransferState(
                bookId = bookId,
                message = "正在重新提交《${book.title}》…",
                active = true,
                kind = AudiobookTransferKind.QUERY
            ))
            try {
                val updated = api.retryBookAnalysis(bookId)
                repository.replaceBook(updated.copy(sourceUri = book.sourceUri))
                publishBooks(generation)
                emitTransfer(generation, AudiobookTransferState(
                    bookId = bookId,
                    message = "已重新提交《${book.title}》，后台会重新分析整本书",
                    percent = 100,
                    active = false,
                    kind = AudiobookTransferKind.QUERY
                ))
                emitMessage(generation, "")
                refreshAfterRetry = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emitTransfer(generation, AudiobookTransferState(
                    bookId = bookId,
                    message = "重新提交失败：${error.message.orEmpty()}",
                    active = false,
                    kind = AudiobookTransferKind.QUERY
                ))
                emitMessage(generation, error.message ?: "重新提交整本书任务失败")
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (retryJob === job) retryJob = null
                if (refreshAfterRetry) refresh()
            }
        }
    }

    /** Requeue one failed published chapter without asking the user for credentials. */
    fun retryChapter(bookId: String, chapterId: String) {
        if (_transfer.value.active || retryJob?.isActive == true) return
        val book = repository.book(bookId)
        val chapter = repository.chapters(bookId).firstOrNull { it.id == chapterId }
        if (book == null || chapter == null || !canRetryAudiobookChapterGeneration(chapter)) {
            _message.value = "当前章节不能重新生成，请先刷新后台状态"
            return
        }
        val generation = beginRefreshGeneration()
        var refreshAfterRetry = false
        retryJob = viewModelScope.launch {
            emitTransfer(generation, AudiobookTransferState(
                bookId = bookId,
                message = "正在重新生成《${chapter.title}》…",
                active = true,
                kind = AudiobookTransferKind.OTHER
            ))
            try {
                val updated = api.retryChapter(bookId, chapterId)
                repository.replaceBook(updated.book.copy(sourceUri = book.sourceUri))
                repository.updateChapter(updated.chapter)
                publishBooks(generation)
                emitTransfer(generation, AudiobookTransferState(
                    bookId = bookId,
                    message = "已提交《${chapter.title}》，后台开始重新生成",
                    percent = 100,
                    active = false,
                    kind = AudiobookTransferKind.OTHER
                ))
                emitMessage(generation, "")
                refreshAfterRetry = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emitTransfer(generation, AudiobookTransferState(
                    bookId = bookId,
                    message = "重新生成失败：${error.message.orEmpty()}",
                    active = false,
                    kind = AudiobookTransferKind.OTHER
                ))
                emitMessage(generation, error.message ?: "重新生成章节失败")
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (retryJob === job) retryJob = null
                if (refreshAfterRetry) refresh()
            }
        }
    }

    fun importTxt(uri: Uri) {
        if (_transfer.value.active) return
        viewModelScope.launch {
            val temp = File.createTempFile("audiobook-source-", ".txt", appContext.cacheDir)
            try {
                _transfer.value = AudiobookTransferState(message = "正在读取小说文件…", active = true)
                copyUriToFile(appContext.contentResolver, uri, temp)
                val title = displayName(appContext.contentResolver, uri)
                    .substringBeforeLast('.', displayName(appContext.contentResolver, uri))
                    .ifBlank { "未命名有声书" }
                uploadSourceFile(temp, title, "", uri.toString())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _transfer.value = AudiobookTransferState(message = "上传失败：${error.message.orEmpty()}", active = false)
                _message.value = error.message ?: "小说上传失败"
            } finally {
                temp.delete()
            }
        }
    }

    /** Submit the complete text already downloaded into the app's offline library. */
    fun importDownloadedNovel(book: Book) {
        if (_transfer.value.active) return
        viewModelScope.launch {
            val temp = File.createTempFile("audiobook-offline-source-", ".txt", appContext.cacheDir)
            try {
                val imported = prepareDownloadedNovelImport(
                    book = book,
                    offline = libraryRepository.offlineChapterFor(book.id)
                ).getOrThrow()
                _transfer.value = AudiobookTransferState(
                    message = "正在准备《${imported.title}》的已下载正文…",
                    active = true
                )
                withContext(Dispatchers.IO) {
                    temp.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(imported.sourceText)
                    }
                }
                uploadSourceFile(temp, imported.title, imported.author, imported.sourceUri)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _transfer.value = AudiobookTransferState(message = "上传失败：${error.message.orEmpty()}", active = false)
                _message.value = error.message ?: "已下载小说导入失败"
            } finally {
                temp.delete()
            }
        }
    }

    fun downloadChapter(bookId: String, chapter: AudiobookChapter) {
        if (repository.hasAudio(chapter) || chapter.status != AudiobookChapterStatus.READY) return
        if (_transfer.value.active) return
        if (downloadJobs[chapter.id]?.isActive == true) return
        val generation = beginStandaloneDownloadGeneration()
        downloadJobs[chapter.id] = viewModelScope.launch {
            try {
                val committed = downloadChapterInternal(repository.book(bookId), chapter, generation)
                if (committed) {
                    emitTransfer(generation, audiobookDownloadResultState(bookId, chapter.title, null))
                    emitMessage(generation, "")
                } else {
                    emitTransfer(generation, AudiobookTransferState(
                        bookId = bookId,
                        message = "后台音频版本已更新，已丢弃旧下载结果",
                        percent = 100,
                        active = false,
                        kind = AudiobookTransferKind.DOWNLOAD
                    ))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emitTransfer(generation, audiobookDownloadResultState(bookId, chapter.title, error))
                emitMessage(generation, "《${chapter.title}》下载失败：${error.message.orEmpty()}")
            } finally {
                publishBooks(generation)
                downloadJobs.remove(chapter.id)
            }
        }
    }

    fun playChapter(bookId: String, chapter: AudiobookChapter) {
        if (!canPlayAudiobookChapter(chapter.status, repository.hasAudio(chapter))) {
            _message.value = "本章音频尚未下载完成"
            return
        }
        val existing = playbackResumePosition(historyFor(bookId, chapter.id)?.positionMs, chapter.durationMs)
        val started = player.play(
            repository.audioFile(bookId, chapter.id, chapter.audioContentType),
            bookId,
            chapter.id,
            chapter.title,
            existing
        )
        if (!started) _message.value = "后台播放器仍在连接，请稍后再点一次"
    }

    fun retryPlayback() {
        val state = player.snapshot()
        val bookId = state.bookId
        val chapterId = state.chapterId
        if (bookId.isNullOrBlank() || chapterId.isNullOrBlank()) {
            _message.value = "没有可重试的章节"
            return
        }
        val chapter = repository.chapters(bookId).firstOrNull { it.id == chapterId }
        if (chapter == null) {
            _message.value = "本章记录已不存在，请重新打开书籍"
            return
        }
        playChapter(bookId, chapter)
    }

    fun replayPlayback() {
        val state = player.snapshot()
        val bookId = state.bookId
        val chapterId = state.chapterId
        if (bookId.isNullOrBlank() || chapterId.isNullOrBlank()) {
            _message.value = "没有可重播的章节"
            return
        }
        val chapter = repository.chapters(bookId).firstOrNull { it.id == chapterId }
        if (chapter == null) {
            _message.value = "本章记录已不存在，请重新打开书籍"
            return
        }
        playChapter(bookId, chapter)
    }

    fun pausePlayback() = player.pause()
    fun resumePlayback() = player.resume()
    fun seekPlayback(positionMs: Long) = player.seekTo(positionMs)

    private suspend fun syncReadyChapters(book: AudiobookBook, chapters: List<AudiobookChapter>): List<String> {
        val failures = mutableListOf<String>()
        chapters.filter { shouldAutoDownloadChapter(it.status, repository.hasAudio(it)) }.forEachIndexed { index, chapter ->
            _transfer.value = AudiobookTransferState(
                bookId = book.id,
                message = "正在下载已生成章节 ${index + 1}/${chapters.count { it.status == AudiobookChapterStatus.READY }}…",
                percent = 0,
                active = true,
                kind = AudiobookTransferKind.DOWNLOAD
            )
            runCatching { downloadChapterInternal(book, chapter) }
                .onFailure {
                    failures += chapter.title
                    _message.value = "《${chapter.title}》下载失败：${it.message.orEmpty()}"
                }
        }
        return failures
    }

    private suspend fun fetchRefreshDetail(bookId: String): Result<Pair<AudiobookBook, List<AudiobookChapter>>> =
        try {
            Result.success(api.getBook(bookId))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun startReadyChapterSync(
        plans: List<ReadyChapterDownload>,
        failedBookTitles: List<String>,
        generation: Long
    ) {
        if (plans.isEmpty()) return
        readyDownloadJob?.cancel()
        readyDownloadJob = viewModelScope.launch {
            val failedChapterTitles = mutableListOf<String>()
            plans.forEachIndexed { index, plan ->
                if (!isCurrentGeneration(generation)) return@launch
                emitTransfer(generation, AudiobookTransferState(
                    bookId = plan.book.id,
                    message = "正在下载已生成章节 ${index + 1}/${plans.size}…",
                    percent = 0,
                    active = true,
                    kind = AudiobookTransferKind.DOWNLOAD
                ))
                try {
                    downloadChapterInternal(plan.book, plan.chapter, generation)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failedChapterTitles += plan.chapter.title
                    emitMessage(generation, "《${plan.chapter.title}》下载失败：${error.message.orEmpty()}")
                }
            }
            if (!isCurrentGeneration(generation)) return@launch
            publishBooks(generation)
            val resultMessage = audiobookRefreshResultMessage(failedBookTitles, failedChapterTitles)
            emitTransfer(generation, AudiobookTransferState(
                message = resultMessage,
                active = false,
                percent = 100,
                kind = AudiobookTransferKind.DOWNLOAD
            ))
            emitMessage(generation, if (failedChapterTitles.isEmpty() && failedBookTitles.isEmpty()) "" else resultMessage)
        }.also { job ->
            job.invokeOnCompletion {
                if (readyDownloadJob === job) readyDownloadJob = null
            }
        }
    }

    private suspend fun downloadChapterInternal(
        book: AudiobookBook?,
        chapter: AudiobookChapter,
        generation: Long? = null
    ): Boolean {
        require(book != null) { "有声书不存在" }
        val target = repository.audioFile(book.id, chapter.id, chapter.audioContentType)
        target.parentFile?.mkdirs()
        var artifact: AudiobookDownloadArtifact? = null
        try {
            artifact = api.downloadChapter(book, chapter, target) { read, total ->
                val percent = total?.takeIf { it > 0L }?.let { (read * 100L / it).toInt() } ?: 0
                emitTransfer(generation, AudiobookTransferState(
                    bookId = book.id,
                    message = "正在下载《${chapter.title}》…",
                    percent = percent,
                    active = true,
                    kind = AudiobookTransferKind.DOWNLOAD
                ))
            }
            val committed = repository.commitChapterAudio(chapter, checkNotNull(artifact).file)
            if (!committed) {
                checkNotNull(artifact).cleanup()
            }
            return committed
        } catch (error: CancellationException) {
            if (!shouldRetainAudiobookPartial(error)) artifact?.cleanup()
            throw error
        } catch (error: Exception) {
            if (!shouldRetainAudiobookPartial(error)) artifact?.cleanup()
            repository.markChapterDownloadFailed(chapter, error.message ?: "下载失败")
            throw error
        }
    }

    private suspend fun uploadSourceFile(
        temp: File,
        title: String,
        author: String,
        sourceUri: String
    ) {
        val size = temp.length()
        require(size in 1..(500L * 1024L * 1024L)) { "TXT 文件必须在 1 B～500 MB 之间" }
        val sourceSha256 = sha256(temp)
        val identity = audiobookImportIdentity(sourceSha256, title, author)
        val chunkCount = ((size + AudiobookPipelineContract.SOURCE_CHUNK_BYTES - 1L) /
            AudiobookPipelineContract.SOURCE_CHUNK_BYTES).toInt()
        var book = api.createBook(
            clientRequestId = identity.clientRequestId,
            bookId = identity.bookId,
            title = title,
            author = author,
            sourceSha256 = sourceSha256,
            sourceBytes = size,
            sourceChunkCount = chunkCount
        )
        repository.replaceBook(book.copy(sourceUri = sourceUri))
        publishBooks(null)
        if (audiobookSourceUploadAction(book.sourceStatus) == AudiobookSourceUploadAction.REUSE_COMMITTED) {
            val (existingBook, chapters) = api.getBook(book.id)
            val localBook = existingBook.copy(sourceUri = sourceUri)
            repository.replaceBookAndChapters(localBook, chapters)
            val downloadFailures = syncReadyChapters(localBook, chapters)
            publishBooks(null)
            _transfer.value = AudiobookTransferState(
                bookId = localBook.id,
                message = audiobookExistingTaskMessage(localBook.title, downloadFailures),
                percent = 100,
                active = false
            )
            _message.value = if (downloadFailures.isEmpty()) {
                "已恢复《${localBook.title}》的有声书任务"
            } else {
                audiobookRefreshMessage(downloadFailures)
            }
            return
        }
        RandomAccessFile(temp, "r").use { input ->
            val buffer = ByteArray(AudiobookPipelineContract.SOURCE_CHUNK_BYTES)
            for (index in 0 until chunkCount) {
                val expected = minOf(buffer.size.toLong(), size - index.toLong() * buffer.size).toInt()
                input.seek(index.toLong() * buffer.size)
                input.readFully(buffer, 0, expected)
                api.uploadSourceChunk(book.id, index, buffer.copyOf(expected))
                _transfer.value = AudiobookTransferState(
                    bookId = book.id,
                    message = "正在上传原文分片 ${index + 1}/$chunkCount…",
                    percent = ((index + 1) * 100 / chunkCount).coerceIn(1, 99),
                    active = true
                )
            }
        }
        book = api.completeSource(book.id)
        repository.replaceBook(book.copy(sourceUri = sourceUri))
        publishBooks(null)
        _transfer.value = AudiobookTransferState(book.id, "原文已上传，等待后台分析整本书", 100, false)
        _message.value = "已提交《${book.title}》，后台会先拆分章节再生成音频"
    }

    private suspend fun copyUriToFile(resolver: ContentResolver, uri: Uri, target: File) = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { input -> target.outputStream().buffered().use { output -> input.copyTo(output) } }
            ?: error("无法读取小说文件")
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        val queried = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return queried ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
    }

    private suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun onCleared() {
        operationGeneration += 1
        refreshJob?.cancel()
        retryJob?.cancel()
        downloadJobs.values.forEach(Job::cancel)
        readyDownloadJob?.cancel()
        player.release()
        super.onCleared()
    }

    private companion object {
        const val REFRESH_TIMEOUT_MILLIS = 45_000L
        const val REFRESH_TIMEOUT_SECONDS = 45
        const val REFRESH_DETAIL_CONCURRENCY = 4
    }
}
