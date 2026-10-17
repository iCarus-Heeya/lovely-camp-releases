package com.lovelyreader.audiobook

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookApiDownloadSessionTest {
    @Test
    fun `expired session during audio download rotates once and resumes the chapter request`() = runBlocking {
        val initialToken = "a".repeat(64)
        val refreshedToken = "b".repeat(64)
        val audio = "hello".toByteArray()
        val factory = DownloadConnectionFactory(
            listOf(
                FakeDownloadHttpConnection(401, "{\"error\":\"session expired\"}".toByteArray()),
                FakeDownloadHttpConnection(200, "{\"sessionToken\":\"$refreshedToken\"}".toByteArray()),
                FakeDownloadHttpConnection(200, audio),
            )
        )
        var savedToken = initialToken
        val api = AudiobookApi(
            configProvider = AudiobookApiConfigProvider { AudiobookApiConfig("https://example.test", savedToken) },
            sessionTokenSaver = { savedToken = it },
            connectionFactory = factory,
        )
        val root = Files.createTempDirectory("audiobook-download-session").toFile()
        val target = root.resolve("chapter.wav")
        val book = AudiobookBook(
            id = "book-1",
            clientBookId = "client-book-1",
            title = "测试书",
            author = "测试作者",
            manifestRevision = 1L,
            chaptersTotal = 1,
        )
        val chapter = AudiobookChapter(
            id = "chapter-1",
            bookId = book.id,
            ordinal = 1,
            title = "第一章",
            manifestRevision = 1L,
            status = AudiobookChapterStatus.READY,
            usable = true,
            audioSha256 = sha256(audio),
            audioBytes = audio.size.toLong(),
            audioContentType = "audio/wav",
        )

        val artifact = api.downloadChapter(book, chapter, target)

        assertEquals(audio.toList(), artifact.file.readBytes().toList())
        assertTrue(artifact.file.exists())
        assertFalse(artifact.partial.exists())
        assertEquals(refreshedToken, savedToken)
        assertEquals(
            listOf(
                "/api/v1/books/book-1/chapters/chapter-1/audio",
                "/api/v1/session",
                "/api/v1/books/book-1/chapters/chapter-1/audio",
            ),
            factory.paths,
        )
        assertEquals(initialToken, factory.connections[0].requestHeadersSnapshot()["X-Reader-Session"])
        assertEquals("android", factory.connections[1].requestHeadersSnapshot()["X-Reader-Client"])
        assertEquals(refreshedToken, factory.connections[2].requestHeadersSnapshot()["X-Reader-Session"])
    }

    private class DownloadConnectionFactory(
        responses: List<FakeDownloadHttpConnection>,
    ) : AudiobookHttpConnectionFactory {
        private val responses = responses.toMutableList()
        val paths = mutableListOf<String>()
        val connections = mutableListOf<FakeDownloadHttpConnection>()

        override fun open(
            config: AudiobookApiConfig,
            method: String,
            path: String,
            body: ByteArray?,
            readTimeoutMillis: Int,
        ): HttpURLConnection {
            paths += path
            return responses.removeAt(0).also { connections += it }
        }
    }

    private class FakeDownloadHttpConnection(
        private val code: Int,
        private val payload: ByteArray,
    ) : HttpURLConnection(URL("https://example.test")) {
        private val output = ByteArrayOutputStream()
        private val headers = linkedMapOf<String, String>()
        private val input = ByteArrayInputStream(payload)
        private val error = ByteArrayInputStream(payload)

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getContentLengthLong(): Long = payload.size.toLong()
        override fun getInputStream() = input
        override fun getErrorStream() = error
        override fun getOutputStream() = output
        override fun setRequestProperty(key: String, value: String) {
            headers[key] = value
        }

        fun requestHeadersSnapshot(): Map<String, String> = headers.toMap()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
