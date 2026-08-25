package com.lovelyreader.data

import com.lovelyreader.domain.Book
import com.lovelyreader.domain.BookStatus
import com.lovelyreader.domain.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBackupCodecTest {
    @Test
    fun `backup round trip preserves reader data and validates checksum`() {
        val snapshot = LibrarySnapshot(
            books = listOf(Book("b1", "老婆的书", "作者", BookStatus.FINISHED)),
            progress = listOf(ReadingProgress("b1", "https://example.com/p1", 42)),
            bookmarks = emptyList(),
            notes = emptyList(),
            readerFontSize = 22,
            readerLineSpacing = 20,
            readerNightMode = true
        )

        val encoded = LibraryBackupCodec.encode(snapshot)
        val restored = LibraryBackupCodec.decode(encoded).getOrThrow()

        assertEquals(snapshot, restored)
        assertTrue(LibraryBackupCodec.decode(encoded.replace("老婆的书", "被篡改")).isFailure)
    }
}
