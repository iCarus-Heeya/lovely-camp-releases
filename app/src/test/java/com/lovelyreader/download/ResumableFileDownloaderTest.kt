package com.lovelyreader.download

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumableFileDownloaderTest {
    @Test
    fun `resumes an existing partial file with a range response`() = runTest {
        val root = Files.createTempDirectory("lovely-download").toFile()
        val partial = File(root, "book.part").apply { writeText("hello ") }
        val target = File(root, "book.txt")
        val requests = mutableListOf<Long>()
        val downloader = ResumableFileDownloader(open = { _, start ->
            requests += start
            FakeDownloadConnection(
                responseCode = 206,
                contentLength = 5L,
                input = ByteArrayInputStream("world".toByteArray())
            )
        })

        downloader.download("https://example.com/book.txt", partial, target)

        assertEquals(listOf(6L), requests)
        assertEquals("hello world", target.readText())
        assertFalse(partial.exists())
    }

    @Test
    fun `server ignoring range restarts from zero instead of corrupting output`() = runTest {
        val root = Files.createTempDirectory("lovely-download").toFile()
        val partial = File(root, "book.part").apply { writeText("stale") }
        val target = File(root, "book.txt")
        val downloader = ResumableFileDownloader(open = { _, start ->
            assertEquals(5L, start)
            FakeDownloadConnection(200, 5L, ByteArrayInputStream("fresh".toByteArray()))
        })

        downloader.download("https://example.com/book.txt", partial, target)

        assertEquals("fresh", target.readText())
        assertTrue(target.exists())
    }
}

private class FakeDownloadConnection(
    override val responseCode: Int,
    override val contentLength: Long?,
    override val input: ByteArrayInputStream
) : DownloadConnection {
    override fun close() = Unit
}
