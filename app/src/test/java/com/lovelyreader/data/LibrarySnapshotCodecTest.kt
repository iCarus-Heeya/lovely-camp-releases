package com.lovelyreader.data

import com.lovelyreader.domain.Book
import com.lovelyreader.domain.BookStatus
import com.lovelyreader.domain.Bookmark
import com.lovelyreader.domain.HusbandNote
import com.lovelyreader.domain.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySnapshotCodecTest {
    private val codec = LibrarySnapshotCodec()

    @Test
    fun roundTripsEscapedLibrarySnapshotFields() {
        val snapshot = LibrarySnapshot(
            books = listOf(
                Book(
                    id = "book|1",
                    title = "老婆的小书架\\珍藏",
                    author = "顾|漫",
                    status = BookStatus.FINISHED,
                    summary = "第一行\n第二行",
                    coverUrl = "https://example.com/a|b.jpg",
                    sourceIds = listOf("ixdzs", "ijjxs")
                )
            ),
            progress = listOf(
                ReadingProgress(
                    bookId = "book|1",
                    chapterUrl = "https://ixdzs8.com/read/183714/p56.html",
                    percent = 57,
                    lastReadIndex = 123,
                    lastReadOffset = 456
                )
            ),
            bookmarks = listOf(
                Bookmark(
                    bookId = "book|1",
                    chapterUrl = "https://ixdzs8.com/read/183714/p56.html",
                    label = "想你的这一页\n晚安"
                )
            ),
            notes = listOf(
                HusbandNote(
                    id = "note|1",
                    message = "今天也陪你读一会儿。\\"
                )
            ),
            seenTitles = listOf("你是我的荣耀", "老婆|爱看的书"),
            offlineChapters = listOf(
                OfflineChapter(
                    bookId = "book|1",
                    title = "全文TXT",
                    url = "https://down.qishu99.cc/book.txt",
                    content = "第一章\n正文|内容"
                )
            ),
            readerFontSize = 22,
            readerLineSpacing = 20,
            readerNightMode = true
        )

        val restored = codec.decode(codec.encode(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun ignoresMalformedRowsInsteadOfFailingWholeRestore() {
        val restored = codec.decode(
            EncodedLibrarySnapshot(
                books = "broken",
                progress = "also|broken",
                bookmarks = "too|short",
                notes = "note-1|还能恢复的小纸条"
            )
        )

        assertEquals(emptyList<Book>(), restored.books)
        assertEquals(emptyList<ReadingProgress>(), restored.progress)
        assertEquals(emptyList<Bookmark>(), restored.bookmarks)
        assertEquals(listOf(HusbandNote("note-1", "还能恢复的小纸条")), restored.notes)
    }
}
