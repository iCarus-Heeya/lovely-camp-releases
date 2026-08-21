package com.lovelyreader.ui

import androidx.work.Data
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BookDownloadWorkTest {
    @Test
    fun workInputRoundTripsSelectedBookAndCapabilities() {
        val input = BookDownloadWorkInput(
            bookId = "book-1",
            bookTitle = "测试小说",
            author = "作者",
            result = SearchResult(
                sourceId = "qinkan",
                title = "测试小说",
                author = "作者",
                bookUrl = "https://example.com/book",
                summary = "简介",
                coverUrl = "https://example.com/cover.jpg",
                latestChapter = "第十章",
                capabilities = setOf(SourceCapability.TXT_IMPORT, SourceCapability.READ_CHAPTER)
            )
        )

        val decoded = BookDownloadWorkInput.fromData(input.toData())

        assertNotNull(decoded)
        assertEquals(input, decoded)
    }

    @Test
    fun progressDataPreservesBytesSpeedAndEta() {
        val data = DownloadProgressReport(
            percent = 38,
            message = "正在下载",
            downloadedChapters = 2,
            totalChapters = 10,
            downloadedBytes = 3_800,
            totalBytes = 10_000,
            speedBytesPerSecond = 950,
            etaSeconds = 6
        ).toWorkData()

        assertEquals(38, data.getInt(BookDownloadProgressKeys.PERCENT, 0))
        assertEquals(3_800L, data.getLong(BookDownloadProgressKeys.DOWNLOADED_BYTES, 0L))
        assertEquals(10_000L, data.getLong(BookDownloadProgressKeys.TOTAL_BYTES, 0L))
        assertEquals(950L, data.getLong(BookDownloadProgressKeys.SPEED_BYTES_PER_SECOND, 0L))
        assertEquals(6L, data.getLong(BookDownloadProgressKeys.ETA_SECONDS, 0L))
    }
}
