package com.lovelyreader.audiobook

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AudiobookApiPaginationTest {
    @Test
    fun `getBook merges every page and preserves the revision`() = runBlocking {
        val factory = StubConnectionFactory(
            listOf(
                page(nextCursor = "2", pageCursor = 0, chapters = listOf(chapter(1), chapter(2))),
                page(nextCursor = null, pageCursor = 2, chapters = listOf(chapter(3))),
            )
        )

        val result = AudiobookApi(testConfig(), factory).getBook("book-1")

        assertEquals(listOf(1, 2, 3), result.second.map { it.ordinal })
        assertEquals(2L, result.first.manifestRevision)
        assertEquals(listOf(
            "/api/v1/books/book-1?limit=100",
            "/api/v1/books/book-1?limit=100&cursor=2&manifestRevision=2",
        ), factory.paths)
    }

    @Test
    fun `getBook rejects a top level manifest revision that disagrees with the book`() {
        val factory = StubConnectionFactory(
            listOf(
                page(nextCursor = null, pageCursor = 0, topLevelRevision = 3, chapters = listOf(chapter(1))),
            )
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { AudiobookApi(testConfig(), factory).getBook("book-1") }
        }
    }

    @Test
    fun `getBook rejects a repeated cursor instead of looping`() {
        val factory = StubConnectionFactory(
            listOf(
                page(nextCursor = "1", pageCursor = 0, chapters = listOf(chapter(1))),
                page(nextCursor = "1", pageCursor = 1, chapters = listOf(chapter(2))),
            )
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { AudiobookApi(testConfig(), factory).getBook("book-1") }
        }
    }

    @Test
    fun `getBook rejects a page that loses the known source identity`() {
        val factory = StubConnectionFactory(
            listOf(
                page(nextCursor = "1", pageCursor = 0, chapters = listOf(chapter(1))),
                page(nextCursor = null, pageCursor = 1, sourceSha256 = "", chapters = listOf(chapter(2))),
            )
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { AudiobookApi(testConfig(), factory).getBook("book-1") }
        }
    }

    @Test
    fun `getBook rejects a page cursor that does not match the requested cursor`() {
        val factory = StubConnectionFactory(
            listOf(
                page(nextCursor = "1", pageCursor = 0, chapters = listOf(chapter(1))),
                page(nextCursor = null, pageCursor = 0, chapters = listOf(chapter(2))),
            )
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { AudiobookApi(testConfig(), factory).getBook("book-1") }
        }
    }

    @Test
    fun `getBook rejects a chapter row with a different manifest revision`() {
        val factory = StubConnectionFactory(
            listOf(
                page(nextCursor = null, pageCursor = 0, chapters = listOf(chapter(1, manifestRevision = 3))),
            )
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { AudiobookApi(testConfig(), factory).getBook("book-1") }
        }
    }

    private fun testConfig() = AudiobookApiConfig("https://example.test", "t".repeat(64))

    private fun page(
        nextCursor: String?,
        pageCursor: Int,
        sourceSha256: String = SOURCE_SHA,
        topLevelRevision: Int = 2,
        chapters: List<JSONObject>,
    ): String = JSONObject().apply {
        put("book", book(sourceSha256))
        put("chapters", JSONArray(chapters))
        put("chaptersComplete", true)
        put("manifestRevision", topLevelRevision)
        put("pageCursor", pageCursor)
        if (nextCursor == null) put("nextCursor", JSONObject.NULL) else put("nextCursor", nextCursor)
    }.toString()

    private fun book(sourceSha256: String) = JSONObject().apply {
        put("id", "book-1")
        put("bookId", "client-book-1")
        put("title", "测试书")
        put("author", "测试作者")
        put("status", "ready")
        put("phase", "ready")
        put("usable", true)
        put("progress", 100)
        put("sourceSha256", sourceSha256)
        put("sourceBytes", 300)
        put("sourceChunkCount", 1)
        put("sourceStatus", "complete")
        put("manifestRevision", 2)
        put("chaptersTotal", 3)
        put("chaptersReady", 3)
    }

    private fun chapter(ordinal: Int, manifestRevision: Long = 2) = JSONObject().apply {
        put("id", "chapter-$ordinal")
        put("bookId", "book-1")
        put("ordinal", ordinal)
        put("title", "第${ordinal}章")
        put("sourceStart", (ordinal - 1) * 100)
        put("sourceEnd", ordinal * 100)
        put("sourceSpanSha256", ordinal.toString().repeat(64).take(64))
        put("manifestRevision", manifestRevision)
        put("status", "ready")
        put("usable", true)
        put("progress", 100)
    }

    private class StubConnectionFactory(payloads: List<String>) : AudiobookHttpConnectionFactory {
        val paths = mutableListOf<String>()
        private val remaining = payloads.toMutableList()

        override fun open(
            config: AudiobookApiConfig,
            method: String,
            path: String,
            body: ByteArray?,
            readTimeoutMillis: Int,
        ): HttpURLConnection {
            paths += path
            val payload = remaining.removeFirstOrNull() ?: error("unexpected request: $path")
            return StubConnection(URL("https://example.test$path"), payload)
        }
    }

    private class StubConnection(url: URL, private val payload: String) : HttpURLConnection(url) {
        private val output = ByteArrayOutputStream()

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = 200

        override fun getInputStream() = ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))

        override fun getOutputStream() = output
    }

    companion object {
        private val SOURCE_SHA = "a".repeat(64)
    }
}
