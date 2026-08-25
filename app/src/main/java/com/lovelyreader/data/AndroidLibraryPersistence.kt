package com.lovelyreader.data

import android.content.Context
import com.lovelyreader.domain.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidLibraryPersistence(context: Context) : LibraryPersistence {
    private val preferences = context.getSharedPreferences("lovely_reader_library", Context.MODE_PRIVATE)
    private val codec = LibrarySnapshotCodec()
    private val contentPersistence = ChapterContentFilePersistence(context)
    private val persistenceMutex = Mutex()

    override suspend fun save(snapshot: LibrarySnapshot) = persistenceMutex.withLock {
        saveInternal(snapshot)
    }

    private suspend fun saveInternal(snapshot: LibrarySnapshot) = withContext(Dispatchers.IO) {
        // 大段章节文本单独写文件，SharedPreferences 只存元数据，避免 OOM。
        contentPersistence.saveOfflineChapters(snapshot.offlineChapters)
        contentPersistence.savePartialChapters(snapshot.partialChapters)

        val encoded = codec.encode(
            snapshot.copy(
                offlineChapters = snapshot.offlineChapters.map { it.copy(content = "") },
                partialChapters = snapshot.partialChapters.map { it.copy(content = "") }
            )
        )
        preferences.edit()
            .putString("books", encoded.books)
            .putString("progress", encoded.progress)
            .putString("bookmarks", encoded.bookmarks)
            .putString("notes", encoded.notes)
            .putString("seenTitles", encoded.seenTitles)
            .putString("seenBookIdentities", encoded.seenBookIdentities)
            .putString("offlineChapters", encoded.offlineChapters)
            .putString("partialChapters", encoded.partialChapters)
            .putString("readerFontSize", encoded.readerFontSize)
            .putString("readerLineSpacing", encoded.readerLineSpacing)
            .putString("readerNightMode", encoded.readerNightMode)
            .putString("appTheme", encoded.appTheme)
            .apply()
    }

    override suspend fun load(): LibrarySnapshot? = loadInternal()

    private suspend fun loadInternal(): LibrarySnapshot? = withContext(Dispatchers.IO) {
        if (!preferences.contains("books")) return@withContext null
        val meta = codec.decode(
            EncodedLibrarySnapshot(
                books = preferences.getString("books", "").orEmpty(),
                progress = preferences.getString("progress", "").orEmpty(),
                bookmarks = preferences.getString("bookmarks", "").orEmpty(),
                notes = preferences.getString("notes", "").orEmpty(),
                seenTitles = preferences.getString("seenTitles", "").orEmpty(),
                seenBookIdentities = preferences.getString("seenBookIdentities", "").orEmpty(),
                offlineChapters = preferences.getString("offlineChapters", "").orEmpty(),
                partialChapters = preferences.getString("partialChapters", "").orEmpty(),
                readerFontSize = preferences.getString("readerFontSize", null) ?: defaultReaderFontSize.toString(),
                readerLineSpacing = preferences.getString("readerLineSpacing", null) ?: "16",
                readerNightMode = preferences.getString("readerNightMode", null) ?: "false",
                appTheme = preferences.getString("appTheme", null) ?: AppTheme.Warm.name
            )
        )
        meta.copy(
            offlineChapters = contentPersistence.loadOfflineChapters(meta.offlineChapters),
            partialChapters = contentPersistence.loadPartialChapters(meta.partialChapters)
        )
    }

    /**
     * Persists only the download-owned state for [bookId] on top of the latest
     * snapshot. This prevents a long-running background worker from replacing
     * newer shelf, reading-position, or settings changes made by the user.
     */
    suspend fun mergeDownloadSnapshot(bookId: String, downloadSnapshot: LibrarySnapshot) {
        persistenceMutex.withLock {
            val latest = loadInternal()
            if (latest == null) {
                saveInternal(downloadSnapshot)
                return@withLock
            }
            // A user may delete the book while the worker is still finishing
            // a network request. Do not resurrect a deleted shelf entry.
            if (latest.books.none { it.id == bookId }) return@withLock
            val downloadedBook = downloadSnapshot.books.firstOrNull { it.id == bookId }
            val merged = latest.copy(
                books = latest.books.filterNot { it.id == bookId } + listOfNotNull(downloadedBook),
                progress = latest.progress.filterNot { it.bookId == bookId } +
                    downloadSnapshot.progress.filter { it.bookId == bookId },
                offlineChapters = latest.offlineChapters.filterNot { it.bookId == bookId } +
                    downloadSnapshot.offlineChapters.filter { it.bookId == bookId },
                partialChapters = latest.partialChapters.filterNot { it.bookId == bookId } +
                    downloadSnapshot.partialChapters.filter { it.bookId == bookId }
            )
            saveInternal(merged)
        }
    }
}
