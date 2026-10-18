package com.lovelyreader.audiobook

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookApiSessionTest {
    @Test
    fun `first metadata request silently bootstraps an app session`() = runBlocking {
        val clientId = "e".repeat(64)
        val factory = SessionConnectionFactory(
            listOf(
                "{\"sessionToken\":\"${SESSION_TOKEN}\",\"clientId\":\"$clientId\",\"expiresAt\":\"2099-01-01T00:00:00.000Z\"}",
                "{\"books\":[]}",
            )
        )
        var saved = ""
        var savedClientId = ""
        val api = AudiobookApi(
            config = AudiobookApiConfig("https://example.test", ""),
            sessionTokenSaver = { saved = it },
            clientIdSaver = { savedClientId = it },
            connectionFactory = factory,
        )

        assertTrue(api.listBooks().isEmpty())
        assertEquals(listOf("/api/v1/session", "/api/v1/books"), factory.paths)
        assertEquals(SESSION_TOKEN, saved)
        assertEquals(clientId, savedClientId)
        assertEquals("android", factory.connections.first().requestHeadersSnapshot()["X-Reader-Client"])
        assertEquals(SESSION_TOKEN, factory.connections[1].requestHeadersSnapshot()["X-Reader-Session"])
    }

    @Test
    fun `existing app session is sent without creating another session`() = runBlocking {
        val factory = SessionConnectionFactory(listOf("{\"books\":[]}"))
        val api = AudiobookApi(
            config = AudiobookApiConfig("https://example.test", SESSION_TOKEN),
            sessionTokenSaver = {},
            connectionFactory = factory,
        )

        api.listBooks()

        assertEquals(listOf("/api/v1/books"), factory.paths)
        assertEquals(SESSION_TOKEN, factory.connections.single().requestHeadersSnapshot()["X-Reader-Session"])
    }

    @Test
    fun `expired app session is rotated and the original request is retried`() = runBlocking {
        val refreshedToken = "d".repeat(64)
        val clientId = "f".repeat(64)
        val factory = SessionConnectionFactory(
            payloads = listOf(
                "{\"error\":\"session expired\"}",
                "{\"sessionToken\":\"$refreshedToken\",\"clientId\":\"$clientId\",\"expiresAt\":\"2099-01-01T00:00:00.000Z\"}",
                "{\"books\":[]}",
            ),
            responseCodes = listOf(401, 200, 200),
        )
        var saved = SESSION_TOKEN
        var savedClientId = ""
        val api = AudiobookApi(
            config = AudiobookApiConfig("https://example.test", SESSION_TOKEN, clientId),
            sessionTokenSaver = { saved = it },
            clientIdSaver = { savedClientId = it },
            connectionFactory = factory,
        )

        assertTrue(api.listBooks().isEmpty())
        assertEquals(listOf("/api/v1/books", "/api/v1/session", "/api/v1/books"), factory.paths)
        assertEquals(refreshedToken, saved)
        assertEquals(clientId, savedClientId)
        assertEquals(SESSION_TOKEN, factory.connections[0].requestHeadersSnapshot()["X-Reader-Session"])
        assertEquals(clientId, factory.connections[0].requestHeadersSnapshot()["X-Reader-Client-Id"])
        assertEquals("android", factory.connections[1].requestHeadersSnapshot()["X-Reader-Client"])
        assertEquals(clientId, factory.connections[1].requestHeadersSnapshot()["X-Reader-Client-Id"])
        assertEquals(refreshedToken, factory.connections[2].requestHeadersSnapshot()["X-Reader-Session"])
        assertEquals(clientId, factory.connections[2].requestHeadersSnapshot()["X-Reader-Client-Id"])
    }

    @Test
    fun `retrying a failed analysis uses the app session without exposing credentials`() = runBlocking {
        val factory = SessionConnectionFactory(
            payloads = listOf(
                """{"book":{"id":"book-1","bookId":"client-book-1","title":"样书","author":"作者","status":"accepted","phase":"queued","sourceStatus":"complete","sourceSha256":"${"a".repeat(64)}","sourceBytes":12,"sourceChunkCount":1,"manifestRevision":0,"chaptersTotal":0,"chaptersReady":0,"chaptersFailed":0}}"""
            )
        )
        val api = AudiobookApi(
            config = AudiobookApiConfig("https://example.test", SESSION_TOKEN, "f".repeat(64)),
            connectionFactory = factory,
        )

        val book = api.retryBookAnalysis("book-1")

        assertEquals("book-1", book.id)
        assertEquals(listOf("/api/v1/books/book-1/retry"), factory.paths)
        assertEquals(SESSION_TOKEN, factory.connections.single().requestHeadersSnapshot()["X-Reader-Session"])
        assertEquals("f".repeat(64), factory.connections.single().requestHeadersSnapshot()["X-Reader-Client-Id"])
    }

    @Test
    fun `retrying a failed chapter uses the app session and returns the updated chapter`() = runBlocking {
        val factory = SessionConnectionFactory(
            payloads = listOf(
                """{"book":{"id":"book-1","bookId":"client-book-1","title":"样书","author":"作者","status":"generating","phase":"generating","sourceStatus":"complete","sourceSha256":"${"a".repeat(64)}","sourceBytes":12,"sourceChunkCount":1,"manifestRevision":1,"chaptersTotal":1,"chaptersReady":0,"chaptersFailed":0},"chapter":{"id":"chapter-1","bookId":"book-1","ordinal":1,"title":"第一章","sourceStart":0,"sourceEnd":12,"sourceSpanSha256":"${"b".repeat(64)}","status":"pending","manifestRevision":1,"progress":0,"partCount":1,"attempt":0}}"""
            )
        )
        val api = AudiobookApi(
            config = AudiobookApiConfig("https://example.test", SESSION_TOKEN, "f".repeat(64)),
            connectionFactory = factory,
        )

        val result = api.retryChapter("book-1", "chapter-1")

        assertEquals("book-1", result.book.id)
        assertEquals("chapter-1", result.chapter.id)
        assertEquals(AudiobookChapterStatus.PENDING, result.chapter.status)
        assertEquals(listOf("/api/v1/books/book-1/chapters/chapter-1/retry"), factory.paths)
        assertEquals(SESSION_TOKEN, factory.connections.single().requestHeadersSnapshot()["X-Reader-Session"])
        assertEquals("f".repeat(64), factory.connections.single().requestHeadersSnapshot()["X-Reader-Client-Id"])
    }

    private class SessionConnectionFactory(
        payloads: List<String>,
        responseCodes: List<Int> = List(payloads.size) { 200 },
    ) : AudiobookHttpConnectionFactory {
        private val payloads = payloads.toMutableList()
        private val responseCodes = responseCodes.toMutableList()
        val paths = mutableListOf<String>()
        val connections = mutableListOf<RecordingConnection>()

        override fun open(
            config: AudiobookApiConfig,
            method: String,
            path: String,
            body: ByteArray?,
            readTimeoutMillis: Int,
        ): HttpURLConnection {
            paths += path
            val connection = RecordingConnection(
                URL("https://example.test$path"),
                payloads.removeAt(0),
                responseCodes.removeAt(0),
            )
            connections += connection
            return connection
        }
    }

    private class RecordingConnection(
        url: URL,
        private val payload: String,
        private val responseCode: Int,
    ) : HttpURLConnection(url) {
        private val output = ByteArrayOutputStream()
        private val recordedHeaders = linkedMapOf<String, String>()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = responseCode
        override fun getInputStream() = ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))
        override fun getOutputStream() = output
        override fun setRequestProperty(key: String, value: String) {
            recordedHeaders[key] = value
        }

        fun requestHeadersSnapshot(): Map<String, String> = recordedHeaders.toMap()
    }

    companion object {
        private val SESSION_TOKEN = "c".repeat(64)
    }
}
