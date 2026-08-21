package com.lovelyreader.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.lovelyreader.data.AndroidLibraryPersistence
import com.lovelyreader.data.LibraryRepository
import com.lovelyreader.domain.Book
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.source.IjjxsSource
import com.lovelyreader.source.IxdzsSource
import com.lovelyreader.source.NovelSource
import com.lovelyreader.source.QinkanSource
import com.lovelyreader.source.QisuwangSource
import com.lovelyreader.source.SourceContentGuard
import com.lovelyreader.source.ZxcsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object BookDownloadProgressKeys {
    const val PERCENT = "percent"
    const val MESSAGE = "message"
    const val DOWNLOADED_CHAPTERS = "downloaded_chapters"
    const val TOTAL_CHAPTERS = "total_chapters"
    const val DOWNLOADED_BYTES = "downloaded_bytes"
    const val TOTAL_BYTES = "total_bytes"
    const val SPEED_BYTES_PER_SECOND = "speed_bytes_per_second"
    const val ETA_SECONDS = "eta_seconds"
}

internal fun DownloadProgressReport.toWorkData(): Data = workDataOf(
    BookDownloadProgressKeys.PERCENT to percent,
    BookDownloadProgressKeys.MESSAGE to message,
    BookDownloadProgressKeys.DOWNLOADED_CHAPTERS to downloadedChapters,
    BookDownloadProgressKeys.TOTAL_CHAPTERS to totalChapters,
    BookDownloadProgressKeys.DOWNLOADED_BYTES to downloadedBytes,
    BookDownloadProgressKeys.TOTAL_BYTES to totalBytes,
    BookDownloadProgressKeys.SPEED_BYTES_PER_SECOND to speedBytesPerSecond,
    BookDownloadProgressKeys.ETA_SECONDS to (etaSeconds ?: -1L)
)

internal data class BookDownloadWorkInput(
    val bookId: String,
    val bookTitle: String,
    val author: String,
    val result: SearchResult
) {
    fun toData(): Data = Data.Builder()
        .putString(KEY_INPUT, JSONObject().apply {
            put("book_id", bookId)
            put("book_title", bookTitle)
            put("author", author)
            put("result", result.toJson())
        }.toString())
        .build()

    companion object {
        private const val KEY_INPUT = "book_download_input"

        fun fromData(data: Data): BookDownloadWorkInput? = runCatching {
            val root = JSONObject(data.getString(KEY_INPUT).orEmpty())
            BookDownloadWorkInput(
                bookId = root.getString("book_id"),
                bookTitle = root.getString("book_title"),
                author = root.getString("author"),
                result = root.getJSONObject("result").toSearchResult()
            )
        }.getOrNull()
    }
}

private fun SearchResult.toJson(): JSONObject = JSONObject().apply {
    put("source_id", sourceId)
    put("title", title)
    put("author", author)
    put("book_url", bookUrl)
    put("summary", summary)
    put("cover_url", coverUrl ?: JSONObject.NULL)
    put("latest_chapter", latestChapter ?: JSONObject.NULL)
    put("capabilities", JSONArray(capabilities.map(SourceCapability::name)))
}

private fun JSONObject.toSearchResult(): SearchResult {
    val capabilities = buildSet {
        val array = optJSONArray("capabilities") ?: JSONArray()
        repeat(array.length()) {
            runCatching { SourceCapability.valueOf(array.getString(it)) }.getOrNull()?.let(::add)
        }
    }
    return SearchResult(
        sourceId = getString("source_id"),
        title = getString("title"),
        author = getString("author"),
        bookUrl = getString("book_url"),
        summary = optString("summary"),
        coverUrl = optString("cover_url").takeIf { it.isNotBlank() && it != JSONObject.NULL.toString() },
        latestChapter = optString("latest_chapter").takeIf { it.isNotBlank() && it != JSONObject.NULL.toString() },
        capabilities = capabilities
    )
}

internal enum class BookDownloadTaskState { ENQUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

internal data class BookDownloadTaskStatus(
    val state: BookDownloadTaskState,
    val progress: BookDownloadStatus
)

internal interface BookDownloadScheduler {
    fun enqueue(input: BookDownloadWorkInput)
    fun observe(bookId: String): Flow<BookDownloadTaskStatus>
    fun cancel(bookId: String)
}

internal class AndroidBookDownloadScheduler(context: Context) : BookDownloadScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun enqueue(input: BookDownloadWorkInput) {
        val request = OneTimeWorkRequestBuilder<BookDownloadWorker>()
            .setInputData(input.toData())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName(input.bookId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun observe(bookId: String): Flow<BookDownloadTaskStatus> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(bookId))
            .mapNotNull { infos -> infos.firstOrNull()?.toTaskStatus() }

    override fun cancel(bookId: String) {
        workManager.cancelUniqueWork(workName(bookId))
    }

    private fun workName(bookId: String): String = "book-download:$bookId"
}

private fun WorkInfo.toTaskStatus(): BookDownloadTaskStatus {
    val state = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> BookDownloadTaskState.ENQUEUED
        WorkInfo.State.RUNNING -> BookDownloadTaskState.RUNNING
        WorkInfo.State.SUCCEEDED -> BookDownloadTaskState.SUCCEEDED
        WorkInfo.State.FAILED -> BookDownloadTaskState.FAILED
        WorkInfo.State.CANCELLED -> BookDownloadTaskState.CANCELLED
    }
    val progress = this.progress
    val eta = progress.getLong(BookDownloadProgressKeys.ETA_SECONDS, -1L).takeIf { it >= 0L }
    val downloadState = when (state) {
        BookDownloadTaskState.SUCCEEDED -> DownloadState.Ready
        BookDownloadTaskState.FAILED, BookDownloadTaskState.CANCELLED -> DownloadState.Failed
        else -> DownloadState.Downloading
    }
    return BookDownloadTaskStatus(
        state = state,
        progress = BookDownloadStatus(
            state = downloadState,
            percent = progress.getInt(BookDownloadProgressKeys.PERCENT, 0),
            message = progress.getString(BookDownloadProgressKeys.MESSAGE).orEmpty(),
            downloadedChapters = progress.getInt(BookDownloadProgressKeys.DOWNLOADED_CHAPTERS, 0),
            totalChapters = progress.getInt(BookDownloadProgressKeys.TOTAL_CHAPTERS, 0),
            downloadedBytes = progress.getLong(BookDownloadProgressKeys.DOWNLOADED_BYTES, 0L),
            totalBytes = progress.getLong(BookDownloadProgressKeys.TOTAL_BYTES, 0L),
            speedBytesPerSecond = progress.getLong(BookDownloadProgressKeys.SPEED_BYTES_PER_SECOND, 0L),
            etaSeconds = eta
        )
    )
}

internal class BookDownloadWorker(
    appContext: Context,
    params: androidx.work.WorkerParameters
) : CoroutineWorker(appContext, params) {
    private val persistence = AndroidLibraryPersistence(appContext)

    override suspend fun doWork(): Result {
        val input = BookDownloadWorkInput.fromData(inputData) ?: return Result.failure()
        val repository = LibraryRepository()
        persistence.load()?.let(repository::restore)
        val book = repository.bookById(input.bookId) ?: Book(
            id = input.bookId,
            title = input.bookTitle,
            author = input.author,
            summary = input.result.summary,
            coverUrl = input.result.coverUrl,
            sourceIds = listOf(input.result.sourceId)
        )
        repository.addToShelf(book)

        setForeground(createForegroundInfo(input.bookId, input.bookTitle, 1, "准备下载"))
        setProgress(DownloadProgressReport(1, "准备下载").toWorkData())

        val sources: List<NovelSource> = listOf(
            IxdzsSource(),
            IjjxsSource(),
            QisuwangSource(),
            QinkanSource(),
            ZxcsSource()
        )
        var lastPersistedChapterCount = -1
        var lastPublishedAt = 0L
        var lastPublishedPercent = -1
        suspend fun publishProgress(report: DownloadProgressReport, force: Boolean = false) {
            val now = System.currentTimeMillis()
            val shouldPublish = force ||
                now - lastPublishedAt >= PROGRESS_PUBLISH_INTERVAL_MILLIS ||
                report.percent != lastPublishedPercent ||
                report.downloadedChapters >= report.totalChapters && report.totalChapters > 0
            if (!shouldPublish) return
            lastPublishedAt = now
            lastPublishedPercent = report.percent
            setProgress(report.toWorkData())
            setForeground(createForegroundInfo(input.bookId, input.bookTitle, report.percent, report.message))
        }
        val downloaded = sources.downloadBookWithFallback(
            bookId = input.bookId,
            initialResult = input.result,
            bookTitle = input.bookTitle,
            author = input.author,
            repository = repository,
            onProgress = { report ->
                publishProgress(report)
                if (report.downloadedChapters > lastPersistedChapterCount) {
                    lastPersistedChapterCount = report.downloadedChapters
                    persistence.mergeDownloadSnapshot(input.bookId, repository.snapshot())
                }
            }
        )

        if (downloaded == null) {
            persistence.mergeDownloadSnapshot(input.bookId, repository.snapshot())
            publishProgress(
                DownloadProgressReport(percent = 0, message = "下载失败"),
                force = true
            )
            return Result.failure(workDataOf(BookDownloadProgressKeys.MESSAGE to "下载失败"))
        }

        val (downloadedResult, chapter) = downloaded
        repository.addToShelf(
            book.copy(
                sourceIds = listOf(downloadedResult.sourceId),
                summary = downloadedResult.summary.ifBlank { book.summary },
                coverUrl = downloadedResult.coverUrl ?: book.coverUrl
            )
        )
        repository.cacheOfflineChapter(input.bookId, chapter)
        repository.updateProgress(input.bookId, chapter.url, 0)
        persistence.mergeDownloadSnapshot(input.bookId, repository.snapshot())
        val completedReport = DownloadProgressReport(
            percent = 100,
            message = "已下载",
            downloadedChapters = 1,
            totalChapters = 1
        )
        publishProgress(completedReport, force = true)
        return Result.success()
    }

    private fun createForegroundInfo(bookId: String, title: String, percent: Int, message: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "书籍下载",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("下载《$title》")
            .setContentText(message)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        val notificationId = NOTIFICATION_ID_BASE + (bookId.hashCode() and 0x0FFF)
        return ForegroundInfo(notificationId, notification)
    }

    private companion object {
        const val CHANNEL_ID = "book-downloads"
        const val NOTIFICATION_ID_BASE = 8814
        const val PROGRESS_PUBLISH_INTERVAL_MILLIS = 350L
    }
}
