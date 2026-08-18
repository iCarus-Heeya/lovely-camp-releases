package com.lovelyreader.data

import com.lovelyreader.domain.AppTheme
import com.lovelyreader.domain.Book
import com.lovelyreader.domain.Bookmark
import com.lovelyreader.domain.HusbandNote
import com.lovelyreader.domain.ReadingProgress
import com.lovelyreader.domain.ChapterContent
import com.lovelyreader.domain.PartialChapter
import com.lovelyreader.source.SourceContentGuard

const val defaultReaderFontSize = 20

class LibraryRepository {
    private val books = linkedMapOf<String, Book>()
    private val progress = linkedMapOf<String, ReadingProgress>()
    private val bookmarks = mutableListOf<Bookmark>()
    private val notes = mutableListOf<HusbandNote>()
    private val seenTitles = linkedSetOf<String>()
    private val offlineChapters = linkedMapOf<String, OfflineChapter>()
    private val partialChapters = linkedMapOf<String, MutableList<OfflineChapter>>()
    private val blockedLegacyBookIds = setOf("demo-glory")

    var readerFontSize: Int = defaultReaderFontSize
        private set
    var readerNightMode: Boolean = false
        private set
    var appTheme: AppTheme = AppTheme.Warm
        private set

    fun addToShelf(book: Book) {
        books[book.id] = book
        markSeenTitle(book.title)
    }

    fun bookshelf(): List<Book> = books.values.toList()

    fun bookById(bookId: String): Book? = books[bookId]

    fun deleteBook(bookId: String) {
        books.remove(bookId)
        progress.remove(bookId)
        bookmarks.removeAll { it.bookId == bookId }
        offlineChapters.remove(bookId)
    }

    fun updateProgress(
        bookId: String,
        chapterUrl: String,
        percent: Int,
        lastReadIndex: Int = 0,
        lastReadOffset: Int = 0
    ) {
        progress[bookId] = ReadingProgress(
            bookId = bookId,
            chapterUrl = chapterUrl,
            percent = percent.coerceIn(0, 100),
            lastReadIndex = lastReadIndex.coerceAtLeast(0),
            lastReadOffset = lastReadOffset.coerceAtLeast(0)
        )
    }

    fun progressFor(bookId: String): ReadingProgress? = progress[bookId]

    fun lastReadPositionFor(bookId: String): Pair<Int, Int> {
        val p = progress[bookId]
        return (p?.lastReadIndex ?: 0) to (p?.lastReadOffset ?: 0)
    }

    fun updateReaderPreferences(fontSize: Int? = null, nightMode: Boolean? = null, theme: AppTheme? = null) {
        fontSize?.let { readerFontSize = it.coerceIn(14, 24) }
        nightMode?.let { readerNightMode = it }
        theme?.let { appTheme = it }
    }

    fun readerPreferences(): Pair<Int, Boolean> = readerFontSize to readerNightMode

    fun cacheOfflineChapter(bookId: String, chapter: ChapterContent) {
        offlineChapters[bookId] = OfflineChapter(
            bookId = bookId,
            title = chapter.title,
            url = chapter.url,
            content = chapter.content
        )
    }

    fun cachePartialChapter(bookId: String, chapter: PartialChapter) {
        if (!SourceContentGuard.isReadableNovelText(chapter.content)) return
        val list = partialChapters.getOrPut(bookId) { mutableListOf() }
        val index = list.indexOfFirst { it.url == chapter.url }
        val offline = OfflineChapter(
            bookId = bookId,
            title = chapter.title,
            url = chapter.url,
            content = chapter.content
        )
        if (index >= 0) {
            list[index] = offline
        } else {
            list += offline
        }
    }

    fun cachePartialChapters(bookId: String, chapters: List<PartialChapter>) {
        chapters.forEach { cachePartialChapter(bookId, it) }
    }

    fun partialChaptersFor(bookId: String): List<ChapterContent> {
        return partialChapters[bookId].orEmpty().map {
            ChapterContent(title = it.title, url = it.url, content = it.content)
        }
    }

    fun clearPartialChapters(bookId: String) {
        partialChapters.remove(bookId)
    }

    fun offlineChapterFor(bookId: String): ChapterContent? {
        return offlineChapters[bookId]?.let {
            ChapterContent(title = it.title, url = it.url, content = it.content)
        }
    }

    fun addBookmark(bookmark: Bookmark) {
        bookmarks += bookmark
    }

    fun bookmarksFor(bookId: String): List<Bookmark> = bookmarks.filter { it.bookId == bookId }

    fun saveHusbandNote(message: String) {
        notes += HusbandNote(id = "note-${notes.size + 1}", message = message)
    }

    fun husbandNotes(): List<HusbandNote> = notes.toList()

    fun markSeenTitle(title: String) {
        title.trim().takeIf { it.isNotBlank() }?.let(seenTitles::add)
    }

    fun seenTitles(): Set<String> = seenTitles.toSet()

    fun snapshot(): LibrarySnapshot {
        return LibrarySnapshot(
            books = bookshelf(),
            progress = progress.values.toList(),
            bookmarks = bookmarks.toList(),
            notes = notes.toList(),
            seenTitles = seenTitles.toList(),
            offlineChapters = offlineChapters.values.toList(),
            partialChapters = partialChapters.values.flatten(),
            readerFontSize = readerFontSize,
            readerNightMode = readerNightMode,
            appTheme = appTheme
        )
    }

    fun restore(snapshot: LibrarySnapshot) {
        books.clear()
        progress.clear()
        bookmarks.clear()
        notes.clear()
        seenTitles.clear()
        offlineChapters.clear()
        partialChapters.clear()
        readerFontSize = snapshot.readerFontSize.coerceIn(14, 24)
        readerNightMode = snapshot.readerNightMode
        appTheme = snapshot.appTheme
        val restoredBooks = snapshot.books.filterNot { it.id in blockedLegacyBookIds }
        val restoredBookIds = restoredBooks.mapTo(mutableSetOf()) { it.id }
        restoredBooks.forEach { books[it.id] = it }
        snapshot.progress
            .filter { it.bookId in restoredBookIds }
            .forEach { progress[it.bookId] = it }
        bookmarks += snapshot.bookmarks.filter { it.bookId in restoredBookIds }
        notes += snapshot.notes
        seenTitles += snapshot.seenTitles.filterNot { it == "你是我的荣耀" }
        snapshot.offlineChapters
            .filter { it.bookId in restoredBookIds }
            .filter { SourceContentGuard.isReadableNovelText(it.content) }
            .forEach { offlineChapters[it.bookId] = it }
        snapshot.partialChapters
            .filter { it.bookId in restoredBookIds }
            .filter { SourceContentGuard.isReadableNovelText(it.content) }
            .groupBy { it.bookId }
            .forEach { (bookId, chapters) ->
                partialChapters[bookId] = chapters.toMutableList()
            }
    }
}

data class OfflineChapter(
    val bookId: String,
    val title: String,
    val url: String,
    val content: String
)

data class LibrarySnapshot(
    val books: List<Book>,
    val progress: List<ReadingProgress>,
    val bookmarks: List<Bookmark>,
    val notes: List<HusbandNote>,
    val seenTitles: List<String> = emptyList(),
    val offlineChapters: List<OfflineChapter> = emptyList(),
    val partialChapters: List<OfflineChapter> = emptyList(),
    val readerFontSize: Int = defaultReaderFontSize,
    val readerNightMode: Boolean = false,
    val appTheme: AppTheme = AppTheme.Warm
)
