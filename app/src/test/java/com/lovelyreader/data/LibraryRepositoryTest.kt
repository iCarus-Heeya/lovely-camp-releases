package com.lovelyreader.data

import com.lovelyreader.domain.Book
import com.lovelyreader.domain.Bookmark
import com.lovelyreader.domain.BookStatus
import com.lovelyreader.domain.ChapterContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.lovelyreader.source.NormalizedBookIdentity

class LibraryRepositoryTest {
    @Test
    fun startsWithEmptyBookshelf() {
        val repository = LibraryRepository()

        assertTrue(repository.bookshelf().isEmpty())
    }

    @Test
    fun addsBookAndUpdatesReadingProgress() {
        val repository = LibraryRepository()
        val book = Book(
            id = "ixdzs-183714",
            title = "你是我的荣耀",
            author = "顾漫",
            status = BookStatus.FINISHED,
            sourceIds = listOf("ixdzs")
        )

        repository.addToShelf(book)
        repository.updateProgress(book.id, chapterUrl = "https://ixdzs8.com/read/183714/p56.html", percent = 37)

        assertTrue(repository.bookshelf().any { it.id == book.id })
        assertEquals(37, repository.progressFor(book.id)?.percent)
    }

    @Test
    fun storesHusbandNotesLocally() {
        val repository = LibraryRepository()

        repository.saveHusbandNote("今天也陪你读一会儿。")

        assertTrue(repository.husbandNotes().any { it.message.contains("陪你读") })
    }

    @Test
    fun remembersSeenTitlesLocally() {
        val repository = LibraryRepository()

        repository.markSeenTitle("偷偷藏不住")

        assertTrue("偷偷藏不住" in repository.seenTitles())
    }

    @Test
    fun persistsSeenBookIdentityWithoutCollapsingDifferentAuthors() {
        val original = LibraryRepository()
        original.markSeenBook("同名书", "甲")
        original.markSeenBook("同名书", "乙")

        val restored = LibraryRepository().also { it.restore(original.snapshot()) }

        assertEquals(
            setOf(NormalizedBookIdentity("同名书", "甲"), NormalizedBookIdentity("同名书", "乙")),
            restored.seenBookIdentities().toSet()
        )
    }

    @Test
    fun cachesOfflineChapterForLaterReading() {
        val repository = LibraryRepository()

        repository.cacheOfflineChapter(
            bookId = "book-1",
            chapter = ChapterContent(
                title = "全文TXT",
                url = "https://down.qishu99.cc/book.txt",
                content = "可以离线看的正文"
            )
        )

        assertEquals("可以离线看的正文", repository.offlineChapterFor("book-1")?.content)
    }

    @Test
    fun deleteBookRemovesShelfProgressBookmarksAndOfflineCache() {
        val repository = LibraryRepository()
        val book = Book(id = "book-1", title = "香蜜沉沉烬如霜", author = "电线")

        repository.addToShelf(book)
        repository.updateProgress(book.id, chapterUrl = "https://example.com/book.txt", percent = 22)
        repository.addBookmark(Bookmark(bookId = book.id, chapterUrl = "https://example.com/book.txt"))
        repository.cacheOfflineChapter(
            bookId = book.id,
            chapter = ChapterContent(
                title = "全文TXT",
                url = "https://example.com/book.txt",
                content = "可以离线看的正文"
            )
        )

        repository.deleteBook(book.id)

        assertNull(repository.bookById(book.id))
        assertNull(repository.progressFor(book.id))
        assertNull(repository.offlineChapterFor(book.id))
        assertTrue(repository.bookmarksFor(book.id).isEmpty())
        assertTrue("香蜜沉沉烬如霜" in repository.seenTitles())
    }

    @Test
    fun restoresLibrarySnapshot() {
        val original = LibraryRepository()
        val book = Book(
            id = "ijjxs-56501",
            title = "七零年代之省城媳妇",
            author = "末笙",
            status = BookStatus.UNKNOWN,
            sourceIds = listOf("ijjxs")
        )
        original.addToShelf(book)
        original.updateProgress(book.id, chapterUrl = "https://m.ijjxs.com/txt/56501.html", percent = 12)
        original.saveHusbandNote("已经替你收好啦，随时可以看。")
        original.markSeenTitle("七零年代之省城媳妇")

        val restored = LibraryRepository()
        restored.restore(original.snapshot())

        assertEquals("七零年代之省城媳妇", restored.bookById(book.id)?.title)
        assertEquals(12, restored.progressFor(book.id)?.percent)
        assertTrue(restored.husbandNotes().any { it.message.contains("随时可以看") })
        assertTrue("七零年代之省城媳妇" in restored.seenTitles())
    }
    @Test
    fun restoreDropsOldDemoBookFromPreviousBuilds() {
        val restored = LibraryRepository()

        restored.restore(
            LibrarySnapshot(
                books = listOf(
                    Book(id = "demo-glory", title = "你是我的荣耀", author = "顾漫"),
                    Book(id = "real-book", title = "香蜜沉沉烬如霜", author = "电线")
                ),
                progress = emptyList(),
                bookmarks = emptyList(),
                notes = emptyList()
            )
        )

        assertNull(restored.bookById("demo-glory"))
        assertEquals("香蜜沉沉烬如霜", restored.bookById("real-book")?.title)
    }

    @Test
    fun storesLastReadPositionAndGlobalReaderPreferences() {
        val repository = LibraryRepository()
        val book = Book(id = "book-1", title = "test", author = "author")
        repository.addToShelf(book)

        repository.updateProgress(
            book.id,
            chapterUrl = "https://example.com/1.txt",
            percent = 42,
            lastReadIndex = 128,
            lastReadOffset = 312
        )
        repository.updateReaderPreferences(fontSize = 22, nightMode = true)

        assertEquals(42, repository.progressFor(book.id)?.percent)
        assertEquals(128 to 312, repository.lastReadPositionFor(book.id))
        assertEquals(22 to true, repository.readerPreferences())
    }

    @Test
    fun restoreDropsUnreadableOfflineChapterCache() {
        val restored = LibraryRepository()

        restored.restore(
            LibrarySnapshot(
                books = listOf(Book(id = "real-book", title = "香蜜沉沉烬如霜", author = "电线")),
                progress = emptyList(),
                bookmarks = emptyList(),
                notes = emptyList(),
                offlineChapters = listOf(
                    OfflineChapter(
                        bookId = "real-book",
                        title = "全文TXT",
                        url = "https://example.com/book.txt",
                        content = "锟斤拷锟斤拷锟斤拷脴蔚锟斤拷锟斤拷N锟斤拷锟斤拷"
                    )
                )
            )
        )

        assertNull(restored.offlineChapterFor("real-book"))
    }
}
