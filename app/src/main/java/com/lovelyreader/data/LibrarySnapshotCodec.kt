package com.lovelyreader.data

import com.lovelyreader.domain.AppTheme
import com.lovelyreader.domain.Book
import com.lovelyreader.domain.BookStatus
import com.lovelyreader.domain.Bookmark
import com.lovelyreader.domain.HusbandNote
import com.lovelyreader.domain.ReadingProgress

class LibrarySnapshotCodec {
    fun encode(snapshot: LibrarySnapshot): EncodedLibrarySnapshot {
        return EncodedLibrarySnapshot(
            books = snapshot.books.joinToString("\n") { encodeBook(it) },
            progress = snapshot.progress.joinToString("\n") { encodeProgress(it) },
            bookmarks = snapshot.bookmarks.joinToString("\n") { encodeBookmark(it) },
            notes = snapshot.notes.joinToString("\n") { encodeNote(it) },
            seenTitles = snapshot.seenTitles.joinToString("\n", transform = ::escape),
            seenBookIdentities = snapshot.seenBookIdentities.joinToString("\n", transform = ::escape),
            offlineChapters = snapshot.offlineChapters.joinToString("\n") { encodeOfflineChapter(it) },
            partialChapters = snapshot.partialChapters.joinToString("\n") { encodeOfflineChapter(it) },
            readerFontSize = snapshot.readerFontSize.toString(),
            readerNightMode = snapshot.readerNightMode.toString(),
            appTheme = snapshot.appTheme.name
        )
    }

    fun decode(encoded: EncodedLibrarySnapshot): LibrarySnapshot {
        return LibrarySnapshot(
            books = encoded.books.linesNotBlank().mapNotNull(::decodeBook),
            progress = encoded.progress.linesNotBlank().mapNotNull(::decodeProgress),
            bookmarks = encoded.bookmarks.linesNotBlank().mapNotNull(::decodeBookmark),
            notes = encoded.notes.linesNotBlank().mapNotNull(::decodeNote),
            seenTitles = encoded.seenTitles.linesNotBlank().map { splitEscaped(it).joinToString("|") },
            seenBookIdentities = encoded.seenBookIdentities.linesNotBlank().map { splitEscaped(it).joinToString("|") },
            offlineChapters = encoded.offlineChapters.linesNotBlank().mapNotNull(::decodeOfflineChapter),
            partialChapters = encoded.partialChapters.linesNotBlank().mapNotNull(::decodeOfflineChapter),
            readerFontSize = encoded.readerFontSize.toIntOrNull() ?: defaultReaderFontSize,
            readerNightMode = encoded.readerNightMode.toBooleanStrictOrNull() ?: false,
            appTheme = runCatching { AppTheme.valueOf(encoded.appTheme) }.getOrDefault(AppTheme.Warm)
        )
    }

    private fun encodeBook(book: Book): String {
        return listOf(
            book.id,
            book.title,
            book.author,
            book.status.name,
            book.summary,
            book.coverUrl.orEmpty(),
            book.sourceIds.joinToString(",")
        ).joinToString("|", transform = ::escape)
    }

    private fun decodeBook(line: String): Book? {
        val parts = splitEscaped(line)
        if (parts.size < 7) return null
        return Book(
            id = parts[0],
            title = parts[1],
            author = parts[2],
            status = runCatching { BookStatus.valueOf(parts[3]) }.getOrDefault(BookStatus.UNKNOWN),
            summary = parts[4],
            coverUrl = parts[5].ifBlank { null },
            sourceIds = parts[6].split(",").filter { it.isNotBlank() }
        )
    }

    private fun encodeProgress(progress: ReadingProgress): String {
        return listOf(
            progress.bookId,
            progress.chapterUrl,
            progress.percent.toString(),
            progress.lastReadIndex.toString(),
            progress.lastReadOffset.toString()
        ).joinToString("|", transform = ::escape)
    }

    private fun decodeProgress(line: String): ReadingProgress? {
        val parts = splitEscaped(line)
        if (parts.size < 3) return null
        return ReadingProgress(
            bookId = parts[0],
            chapterUrl = parts[1],
            percent = parts[2].toIntOrNull() ?: 0,
            lastReadIndex = parts.getOrNull(3)?.toIntOrNull() ?: 0,
            lastReadOffset = parts.getOrNull(4)?.toIntOrNull() ?: 0
        )
    }

    private fun encodeBookmark(bookmark: Bookmark): String {
        return listOf(bookmark.bookId, bookmark.chapterUrl, bookmark.label).joinToString("|", transform = ::escape)
    }

    private fun decodeBookmark(line: String): Bookmark? {
        val parts = splitEscaped(line)
        if (parts.size < 3) return null
        return Bookmark(parts[0], parts[1], parts[2])
    }

    private fun encodeNote(note: HusbandNote): String {
        return listOf(note.id, note.message).joinToString("|", transform = ::escape)
    }

    private fun decodeNote(line: String): HusbandNote? {
        val parts = splitEscaped(line)
        if (parts.size < 2) return null
        return HusbandNote(parts[0], parts[1])
    }

    private fun encodeOfflineChapter(chapter: OfflineChapter): String {
        return listOf(chapter.bookId, chapter.title, chapter.url, chapter.content).joinToString("|", transform = ::escape)
    }

    private fun decodeOfflineChapter(line: String): OfflineChapter? {
        val parts = splitEscaped(line)
        if (parts.size < 4) return null
        return OfflineChapter(parts[0], parts[1], parts[2], parts[3])
    }

    private fun String.linesNotBlank(): List<String> = lines().filter { it.isNotBlank() }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("|", "\\p")
            .replace("\n", "\\n")
    }

    private fun splitEscaped(line: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (char in line) {
            when {
                escaped -> {
                    current.append(
                        when (char) {
                            'p' -> '|'
                            'n' -> '\n'
                            else -> char
                        }
                    )
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == '|' -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        parts += current.toString()
        return parts
    }
}

data class EncodedLibrarySnapshot(
    val books: String = "",
    val progress: String = "",
    val bookmarks: String = "",
    val notes: String = "",
    val seenTitles: String = "",
    val seenBookIdentities: String = "",
    val offlineChapters: String = "",
    val partialChapters: String = "",
    val readerFontSize: String = defaultReaderFontSize.toString(),
    val readerNightMode: String = "false",
    val appTheme: String = AppTheme.Warm.name
)
