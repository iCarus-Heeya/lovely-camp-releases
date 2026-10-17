package com.lovelyreader.download

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
                input = ByteArrayInputStream("world".toByteArray()),
                contentRangeStart = 6L,
                contentRangeEnd = 10L,
                contentRangeTotal = 11L
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

    @Test
    fun `retries a transient stream failure by resuming the partial file`() = runTest {
        val root = Files.createTempDirectory("lovely-download").toFile()
        val partial = File(root, "book.part")
        val target = File(root, "book.txt")
        val requests = mutableListOf<Long>()
        val downloader = ResumableFileDownloader(
            open = { _, start ->
                requests += start
                if (start == 0L) {
                    FakeDownloadConnection(
                        responseCode = 200,
                        contentLength = 11L,
                        input = FailingAfterInputStream("hello ".toByteArray())
                    )
                } else {
                    FakeDownloadConnection(
                        responseCode = 206,
                        contentLength = 5L,
                        input = ByteArrayInputStream("world".toByteArray()),
                        contentRangeStart = 6L,
                        contentRangeEnd = 10L,
                        contentRangeTotal = 11L
                    )
                }
            },
            retryDelay = {},
            maxAttempts = 2
        )

        downloader.download("https://example.com/book.txt", partial, target, expectedBytes = 11L)

        assertEquals(listOf(0L, 6L), requests)
        assertEquals("hello world", target.readText())
        assertFalse(partial.exists())
    }

    @Test
    fun `does not commit an incomplete successful stream`() = runTest {
        val root = Files.createTempDirectory("lovely-download").toFile()
        val partial = File(root, "book.part")
        val target = File(root, "book.txt")
        val downloader = ResumableFileDownloader(
            open = { _, start ->
                assertEquals(0L, start)
                FakeDownloadConnection(
                    responseCode = 200,
                    contentLength = 5L,
                    input = ByteArrayInputStream("short".toByteArray())
                )
            },
            retryDelay = {},
            maxAttempts = 1
        )

        var thrown: Throwable? = null
        try {
            downloader.download("https://example.com/book.txt", partial, target, expectedBytes = 10L)
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is IncompleteDownloadException)
        assertFalse(target.exists())
        assertEquals(5L, partial.length())
    }

    @Test
    fun `does not retry a deterministic http response`() = runTest {
        val root = Files.createTempDirectory("lovely-download").toFile()
        val partial = File(root, "book.part")
        val target = File(root, "book.txt")
        var attempts = 0
        val downloader = ResumableFileDownloader(
            open = { _, _ ->
                attempts += 1
                FakeDownloadConnection(
                    responseCode = 403,
                    contentLength = 0L,
                    input = ByteArrayInputStream(ByteArray(0))
                )
            },
            retryDelay = {},
            maxAttempts = 3
        )

        var thrown: Throwable? = null
        try {
            downloader.download("https://example.com/book.txt", partial, target)
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is IllegalArgumentException)
        assertEquals(1, attempts)
        assertFalse(target.exists())
    }

    @Test
    fun `changed partial identity discards stale bytes before resuming`() = runTest {
        val root = Files.createTempDirectory("lovely-download-identity").toFile()
        val partial = File(root, "book.part").apply { writeText("stale") }
        File(root, "book.part.meta").writeText("artifact-old")
        val target = File(root, "book.txt")
        val requests = mutableListOf<Long>()
        val downloader = ResumableFileDownloader(open = { _, start ->
            requests += start
            FakeDownloadConnection(200, 5L, ByteArrayInputStream("fresh".toByteArray()))
        })

        downloader.download("https://example.com/book.txt", partial, target, partialIdentity = "artifact-new")

        assertEquals(listOf(0L), requests)
        assertEquals("fresh", target.readText())
        assertFalse(File(root, "book.part.meta").exists())
    }

    @Test
    fun `rejects a range response whose content range starts elsewhere`() = runTest {
        val root = Files.createTempDirectory("lovely-download-range").toFile()
        val partial = File(root, "book.part").apply { writeText("hello ") }
        val target = File(root, "book.txt")
        val downloader = ResumableFileDownloader(open = { _, start ->
            assertEquals(6L, start)
            FakeDownloadConnection(
                responseCode = 206,
                contentLength = 5L,
                input = ByteArrayInputStream("world".toByteArray()),
                contentRangeStart = 0L,
                contentRangeEnd = 10L,
                contentRangeTotal = 11L
            )
        })

        var thrown: Throwable? = null
        try {
            downloader.download("https://example.com/book.txt", partial, target)
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is IllegalArgumentException)
        assertEquals("hello ", partial.readText())
        assertFalse(target.exists())
    }

    @Test
    fun `rejects a content range whose end precedes its start`() {
        assertEquals(null, parseContentRangeStart("bytes 6-4/11"))
    }

    @Test
    fun `rejects a content range whose total is smaller than its end`() {
        assertEquals(null, parseContentRangeStart("bytes 6-10/5"))
    }

    @Test
    fun `does not commit a range response whose declared length differs from content range`() = runTest {
        val root = Files.createTempDirectory("lovely-download-body-length").toFile()
        val partial = File(root, "book.part").apply { writeText("hello ") }
        val target = File(root, "book.txt")
        val downloader = ResumableFileDownloader(
            open = { _, start ->
                assertEquals(6L, start)
                FakeDownloadConnection(
                    responseCode = 206,
                    contentLength = 4L,
                    input = ByteArrayInputStream("world".toByteArray()),
                    contentRangeStart = 6L,
                    contentRangeEnd = 10L,
                    contentRangeTotal = 11L
                )
            },
            retryDelay = {},
            maxAttempts = 1
        )

        var thrown: Throwable? = null
        try {
            downloader.download("https://example.com/book.txt", partial, target)
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is IllegalArgumentException)
        assertEquals("hello ", partial.readText())
        assertFalse(target.exists())
    }

    @Test
    fun `does not commit a truncated range response`() = runTest {
        val root = Files.createTempDirectory("lovely-download-truncated-range").toFile()
        val partial = File(root, "book.part").apply { writeText("hello ") }
        val target = File(root, "book.txt")
        val downloader = ResumableFileDownloader(
            open = { _, start ->
                assertEquals(6L, start)
                FakeDownloadConnection(
                    responseCode = 206,
                    contentLength = 5L,
                    input = ByteArrayInputStream("worl".toByteArray()),
                    contentRangeStart = 6L,
                    contentRangeEnd = 10L,
                    contentRangeTotal = 11L
                )
            },
            retryDelay = {},
            maxAttempts = 1
        )

        var thrown: Throwable? = null
        try {
            downloader.download("https://example.com/book.txt", partial, target)
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is IncompleteDownloadException)
        assertEquals("hello worl", partial.readText())
        assertFalse(target.exists())
    }

    @Test
    fun `does not retain bytes beyond the declared range`() = runTest {
        val root = Files.createTempDirectory("lovely-download-overlong-range").toFile()
        val partial = File(root, "book.part").apply { writeText("hello ") }
        val target = File(root, "book.txt")
        val downloader = ResumableFileDownloader(
            open = { _, start ->
                assertEquals(6L, start)
                FakeDownloadConnection(
                    responseCode = 206,
                    contentLength = null,
                    input = ByteArrayInputStream("world".toByteArray()),
                    contentRangeStart = 6L,
                    contentRangeEnd = 9L,
                    contentRangeTotal = 11L
                )
            },
            retryDelay = {},
            maxAttempts = 1
        )

        var thrown: Throwable? = null
        try {
            downloader.download("https://example.com/book.txt", partial, target)
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is IncompleteDownloadException)
        assertEquals("hello worl", partial.readText())
        assertFalse(target.exists())
    }

    @Test
    fun `same partial writers are serialized by a file lock`() = runBlocking {
        val root = Files.createTempDirectory("lovely-download-lock").toFile()
        val partial = File(root, "book.part")
        val target = File(root, "book.txt")
        val opens = AtomicInteger(0)
        val firstReadEntered = CountDownLatch(1)
        val releaseFirstRead = CountDownLatch(1)

        fun downloader() = ResumableFileDownloader(open = { _, start ->
            assertEquals(0L, start)
            if (opens.incrementAndGet() == 1) {
                FakeDownloadConnection(
                    responseCode = 200,
                    contentLength = 5L,
                    input = BlockingInputStream("first".toByteArray(), firstReadEntered, releaseFirstRead)
                )
            } else {
                FakeDownloadConnection(200, 5L, ByteArrayInputStream("fresh".toByteArray()))
            }
        })

        val first = async(Dispatchers.IO) {
            downloader().download("https://example.com/book.txt", partial, target, expectedBytes = 5L)
        }
        assertTrue(firstReadEntered.await(2, TimeUnit.SECONDS))
        val second = async(Dispatchers.IO) {
            downloader().download("https://example.com/book.txt", partial, target, expectedBytes = 5L)
        }
        delay(100L)
        assertEquals(1, opens.get())
        releaseFirstRead.countDown()

        first.await()
        second.await()
        assertEquals("fresh", target.readText())
    }

    @Test
    fun `cleanup removes stale partial identity and lock as one entry`() {
        val root = Files.createTempDirectory("lovely-download-cleanup").toFile()
        val partial = File(root, "book.part").apply {
            writeText("stale")
            setLastModified(0L)
        }
        val identity = File(root, "book.part.meta").apply { writeText("artifact-old") }
        val lockFile = File(root, "book.part.lock").apply { writeText("") }

        val cleaned = ResumableFileDownloader.cleanupStalePartials(
            directory = root,
            olderThanMillis = 1L,
            nowMillis = 10_000L
        )

        assertEquals(1, cleaned)
        assertFalse(partial.exists())
        assertFalse(identity.exists())
        assertFalse(lockFile.exists())
    }

    @Test
    fun `cleanup leaves an active locked partial for its writer`() {
        val root = Files.createTempDirectory("lovely-download-active-cleanup").toFile()
        val partial = File(root, "book.part").apply {
            writeText("active")
            setLastModified(0L)
        }
        val identity = File(root, "book.part.meta").apply { writeText("artifact-active") }
        val lockFile = File(root, "book.part.lock")

        FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                val cleaned = ResumableFileDownloader.cleanupStalePartials(
                    directory = root,
                    olderThanMillis = 1L,
                    nowMillis = 10_000L
                )

                assertEquals(0, cleaned)
                assertTrue(partial.exists())
                assertTrue(identity.exists())
                assertTrue(lockFile.exists())
            }
        }

        assertEquals(
            1,
            ResumableFileDownloader.cleanupStalePartials(
                directory = root,
                olderThanMillis = 1L,
                nowMillis = 10_000L
            )
        )
        assertFalse(partial.exists())
        assertFalse(identity.exists())
        assertFalse(lockFile.exists())
    }
}

private class FakeDownloadConnection(
    override val responseCode: Int,
    override val contentLength: Long?,
    override val input: InputStream,
    override val contentRangeStart: Long? = null,
    override val contentRangeEnd: Long? = null,
    override val contentRangeTotal: Long? = null
) : DownloadConnection {
    override fun close() = Unit
}

private class BlockingInputStream(
    private val bytes: ByteArray,
    private val entered: CountDownLatch,
    private val release: CountDownLatch
) : InputStream() {
    private var read = false

    override fun read(): Int {
        val buffer = ByteArray(1)
        return if (read(buffer, 0, 1) < 0) -1 else buffer[0].toInt() and 0xff
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (read) return -1
        read = true
        entered.countDown()
        check(release.await(2, TimeUnit.SECONDS)) { "test stream was not released" }
        bytes.copyInto(target, offset, 0, minOf(length, bytes.size))
        return minOf(length, bytes.size)
    }
}

private class FailingAfterInputStream(
    private val bytes: ByteArray
) : InputStream() {
    private var index = 0

    override fun read(): Int {
        if (index >= bytes.size) throw IOException("unexpected end of stream")
        return bytes[index++].toInt() and 0xff
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (index >= bytes.size) throw IOException("unexpected end of stream")
        val count = minOf(length, bytes.size - index)
        bytes.copyInto(target, offset, index, index + count)
        index += count
        return count
    }
}
