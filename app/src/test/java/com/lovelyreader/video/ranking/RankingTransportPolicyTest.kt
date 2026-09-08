package com.lovelyreader.video.ranking

import android.content.SharedPreferences
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingTransportPolicyTest {
    private val request = RankingRequest("weekly/popular", "7 days", RankingMediaType.MOVIE, "中国", 2025, "科幻", "zh-CN", 20, "signed cursor")

    @Test
    fun `endpoint policy accepts only fixed approved https origin`() {
        approvedEndpoint("https://rank.example/api/v1/ranking", setOf("rank.example"))

        listOf(
            "http://rank.example/api/v1/ranking",
            "https://user@rank.example/api/v1/ranking",
            "https://evil.example/api/v1/ranking",
            "https://rank.example/api/v1/ranking?token=secret",
            "https://rank.example/api/v1/ranking#fragment",
            "https://rank.example/api/v1/ranking/../other"
        ).forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) {
                approvedEndpoint(raw, setOf("rank.example"))
            }
        }
    }

    @Test
    fun `endpoint policy rejects local targets even when mistakenly allowlisted`() {
        listOf("localhost", "127.0.0.1", "10.0.0.8", "192.168.1.9", "[::1]").forEach { host ->
            assertThrows(IllegalArgumentException::class.java) {
                approvedEndpoint("https://$host/api/v1/ranking", setOf(host.removePrefix("[").removeSuffix("]")))
            }
        }
    }

    @Test
    fun `request builder percent encodes path and query without changing authority`() {
        val endpoint = approvedEndpoint("https://rank.example/api/v1/ranking", setOf("rank.example"))

        val built = buildRankingRequestUrl(endpoint, request)

        assertEquals("rank.example", built.host)
        assertEquals("/api/v1/ranking/boards/weekly%2Fpopular/entries", built.rawPath)
        assertTrue(built.rawQuery.contains("period=7%20days"))
        assertTrue(built.rawQuery.contains("region=%E4%B8%AD%E5%9B%BD"))
        assertTrue(built.rawQuery.contains("cursor=signed%20cursor"))
        assertFalse(built.toString().contains("evil.example"))
    }

    @Test
    fun `named endpoints never reach an unbound connection factory`() = runTest {
        var opened = false
        val failure = runCatching {
            AndroidRankingTransport("https://rank.example/api/v1/ranking", setOf("rank.example"), connectionFactory = {
                opened = true
                StubConnection(it, 200, "{}", "application/json")
            }, resolveAddresses = { listOf(InetAddress.getByName("8.8.8.8")) }).fetch(request, null)
        }.exceptionOrNull()
        assertTrue(failure is RankingTransportException)
        assertFalse(opened)
    }

    @Test
    fun `dns filter rejects every special use class and selects only public mixed answers`() {
        val unsafe = listOf("127.0.0.1", "::1", "10.0.0.1", "172.16.0.1", "192.168.0.1", "169.254.169.254",
            "fe80::1", "fd00::1", "224.0.0.1", "ff02::1", "0.0.0.0", "::", "100.64.0.1", "192.0.2.1",
            "198.18.0.1", "198.51.100.1", "203.0.113.1", "240.0.0.1", "2001:db8::1", "fec0::1")
            .map(InetAddress::getByName)
        unsafe.forEach { address ->
            assertThrows(RankingTransportException::class.java) {
                resolveRankingPublicAddresses("rank.example") { listOf(address) }
            }
        }
        val publicV4 = InetAddress.getByName("8.8.8.8")
        val publicV6 = InetAddress.getByName("2001:4860:4860::8888")
        assertEquals(listOf(publicV4, publicV6), resolveRankingPublicAddresses("rank.example") {
            unsafe + listOf(publicV4, publicV4, publicV6)
        })
        assertThrows(RankingTransportException::class.java) {
            resolveRankingPublicAddresses("rank.example") { emptyList() }
        }
    }

    @Test
    fun `approved named dns answers and lookup failures never open an unbound connection`() = runTest {
        val answers = listOf(emptyList(), listOf("127.0.0.1"), listOf("8.8.8.8", "::1"), listOf("8.8.8.8"))
        answers.forEach { values ->
            var resolvedHost: String? = null
            var opened = false
            val result = runCatching {
                AndroidRankingTransport("https://RANK.example/api/v1/ranking", setOf("rank.example"),
                    connectionFactory = { opened = true; StubConnection(it, 200, "{}", "application/json") },
                    resolveAddresses = { host -> resolvedHost = host; values.map(InetAddress::getByName) }
                ).fetch(request, null)
            }
            assertTrue(result.exceptionOrNull() is RankingTransportException)
            assertEquals("rank.example", resolvedHost)
            assertFalse(opened)
        }
        val failure = runCatching {
            AndroidRankingTransport("https://rank.example/api/v1/ranking", setOf("rank.example"),
                connectionFactory = { error("must not open") },
                resolveAddresses = { throw java.net.UnknownHostException("sensitive DNS details") }
            ).fetch(request, null)
        }.exceptionOrNull() as RankingTransportException
        assertEquals(RankingTransportFailure.NETWORK, failure.failure)
        assertEquals("Ranking transport failed", failure.message)
    }

    @Test
    fun `dns cancellation propagates`() {
        assertThrows(CancellationException::class.java) {
            runTest {
                AndroidRankingTransport("https://rank.example/api/v1/ranking", setOf("rank.example"),
                    connectionFactory = { error("must not open") },
                    resolveAddresses = { throw CancellationException("cancelled") }
                ).fetch(request, null)
            }
        }
    }

    @Test
    fun `endpoint literals use the shared special use address exclusions`() {
        listOf("192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "240.0.0.1").forEach { host ->
            assertThrows(IllegalArgumentException::class.java) {
                approvedEndpoint("https://$host/api/v1/ranking", setOf(host))
            }
        }
    }

    @Test
    fun `transport disables redirects applies bounds and sends conditional etag`() = runTest {
        val connection = StubConnection(URL("https://rank.example"), 200, "{}", "application/json")
        val transport = AndroidRankingTransport(
            endpoint = "https://8.8.8.8/api/v1/ranking",
            approvedHosts = setOf("8.8.8.8"),
            connectionFactory = { url ->
                assertEquals("https", url.protocol)
                assertEquals("8.8.8.8", url.host)
                connection
            },
            resolveAddresses = { error("literal must not trigger DNS") },
            connectTimeoutMillis = 1_234,
            readTimeoutMillis = 2_345,
            maxResponseBytes = 64
        )

        transport.fetch(request, "etag-1")

        assertFalse(connection.instanceFollowRedirects)
        assertEquals(1_234, connection.connectTimeout)
        assertEquals(2_345, connection.readTimeout)
        assertEquals("etag-1", connection.requestProperties["If-None-Match"]?.single())
        assertEquals("application/json", connection.requestProperties["Accept"]?.single())
    }

    @Test
    fun `transport rejects redirect non json and oversized bodies`() = runTest {
        suspend fun fetch(status: Int, body: String, contentType: String?): Throwable? = runCatching {
            AndroidRankingTransport(
                endpoint = "https://8.8.8.8/api/v1/ranking",
                approvedHosts = setOf("8.8.8.8"),
                connectionFactory = { StubConnection(URL("https://rank.example"), status, body, contentType) },
                maxResponseBytes = 8
            ).fetch(request, null)
        }.exceptionOrNull()

        assertEquals(RankingTransportFailure.REDIRECT, (fetch(302, "", "application/json") as RankingTransportException).failure)
        assertEquals(RankingTransportFailure.CONTENT_TYPE, (fetch(200, "{}", "text/html") as RankingTransportException).failure)
        assertEquals(RankingTransportFailure.BODY_TOO_LARGE, (fetch(200, "0123456789", "application/json") as RankingTransportException).failure)
    }

    @Test
    fun `transport preserves an http error when the server sends no body`() = runTest {
        val transport = AndroidRankingTransport(
            endpoint = "https://8.8.8.8/api/v1/ranking",
            approvedHosts = setOf("8.8.8.8"),
            connectionFactory = { NoBodyErrorConnection(URL("https://rank.example"), 404) }
        )

        val response = transport.fetch(request, null)

        assertEquals(404, response.statusCode)
        assertEquals(null, response.body)
    }

    @Test
    fun `stable cache key separates every request dimension and never stores cursor or url`() {
        val base = rankingCacheKey(request)
        val variants = listOf(
            request.copy(boardId = "other"), request.copy(period = "month"), request.copy(mediaType = RankingMediaType.SERIES),
            request.copy(region = "US"), request.copy(year = 2024), request.copy(genre = "喜剧"),
            request.copy(language = "en"), request.copy(limit = 10), request.copy(cursor = "another-secret")
        )

        assertTrue(base.startsWith("videoRanking.v1."))
        assertFalse(base.contains("signed cursor"))
        assertFalse(base.contains("https://"))
        assertEquals(variants.size, variants.map(::rankingCacheKey).toSet().size)
        assertTrue(variants.none { rankingCacheKey(it) == base })
    }

    @Test
    fun `android cache atomically round trips only a namespaced snapshot envelope`() = runTest {
        val preferences = MemoryPreferences()
        val callerThread = Thread.currentThread()
        val cache = AndroidRankingCache(preferences, clockMillis = { 1_000 })
        val key = rankingCacheKey(request)

        cache.write(key, cacheSnapshot(), "etag-1", 900)
        val restored = cache.read(key)

        assertEquals(1, preferences.commitCount)
        assertNotEquals(callerThread, preferences.commitThread)
        assertEquals("snapshot-cache", restored?.snapshot?.snapshotId)
        assertEquals("etag-1", restored?.etag)
        assertEquals(setOf(key), preferences.all.keys)
        assertFalse(preferences.all.toString().contains("signed cursor"))
        assertFalse(preferences.all.toString().contains("next-page-secret"))
        assertEquals(null, restored?.snapshot?.filters?.cursor)
        assertEquals(null, restored?.snapshot?.nextCursor)
    }

    @Test
    fun `android cache removes corrupt and oversized entries instead of surfacing content`() = runTest {
        val key = rankingCacheKey(request)
        val corrupt = MemoryPreferences(mutableMapOf(key to "not-json"))
        val oversized = MemoryPreferences(mutableMapOf(key to "x".repeat(64)))

        assertEquals(null, AndroidRankingCache(corrupt, clockMillis = { 1_000 }).read(key))
        assertFalse(corrupt.contains(key))
        assertEquals(null, AndroidRankingCache(oversized, clockMillis = { 1_000 }, maxEntryBytes = 16).read(key))
        assertFalse(oversized.contains(key))
    }

    @Test
    fun `android cache read propagates cancellation instead of deleting the entry`() {
        assertThrows(CancellationException::class.java) {
            runTest {
                AndroidRankingCache(
                    MemoryPreferences(readFailure = CancellationException("cancelled")),
                    clockMillis = { 1_000 }
                ).read(rankingCacheKey(request))
            }
        }
    }

    private fun cacheSnapshot() = RankingSnapshot(
        schemaVersion = 1,
        requestId = "request-cache",
        boardId = request.boardId,
        filters = RankingFilters(request.period, request.mediaType, request.region, request.year, request.genre, request.language, request.limit, request.cursor),
        snapshotId = "snapshot-cache",
        generatedAt = InstantLike(500),
        dataAsOf = InstantLike(400),
        expiresAt = InstantLike(2_000),
        staleUntil = InstantLike(3_000),
        freshness = RankingFreshness.FRESH,
        availability = RankingAvailability.AVAILABLE,
        coverage = RankingCoverage(1, listOf("source"), true),
        sourceStatuses = listOf(RankingSourceStatus("source", RankingSourceHealth.AVAILABLE)),
        items = emptyList(),
        nextCursor = "next-page-secret"
    )

    private class StubConnection(
        url: URL,
        private val status: Int,
        body: String,
        private val type: String?
    ) : HttpURLConnection(url) {
        private val bytes = body.toByteArray()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = type
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getErrorStream() = ByteArrayInputStream(bytes)
        override fun getHeaderField(name: String?): String? = null
    }

    private class NoBodyErrorConnection(url: URL, private val status: Int) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getErrorStream() = null
        override fun getInputStream() = throw java.io.FileNotFoundException("no body")
    }

    private class MemoryPreferences(
        private val values: MutableMap<String, Any?> = mutableMapOf(),
        private val readFailure: Throwable? = null
    ) : SharedPreferences {
        var commitCount = 0
        var commitThread: Thread? = null
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? {
            readFailure?.let { throw it }
            return values[key] as? String ?: defValue
        }
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun contains(key: String?) = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val updates = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clear = false
            override fun putString(key: String, value: String?) = apply { updates[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { updates[key] = values?.toSet() }
            override fun putInt(key: String, value: Int) = apply { updates[key] = value }
            override fun putLong(key: String, value: Long) = apply { updates[key] = value }
            override fun putFloat(key: String, value: Float) = apply { updates[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { updates[key] = value }
            override fun remove(key: String) = apply { removals += key }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean {
                commitThread = Thread.currentThread()
                if (clear) values.clear()
                removals.forEach(values::remove)
                updates.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                commitCount++
                return true
            }
            override fun apply() { commit() }
        }
    }
}
