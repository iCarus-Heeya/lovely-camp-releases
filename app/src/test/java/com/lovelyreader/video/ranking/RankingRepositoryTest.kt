package com.lovelyreader.video.ranking

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class RankingRepositoryTest {
    private val request = RankingRequest("popular", "week", RankingMediaType.SERIES, "CN", 2026, "drama", "zh-CN", 20)

    @Test
    fun `fresh cache short circuits network unless refresh is forced`() = runTest {
        val cached = snapshot(expiresAt = 2_000, staleUntil = 3_000)
        val cache = FakeCache(CachedRanking(cached, 900, "etag-1"))
        val transport = QueueTransport(response(200, validJson()))
        val repository = DefaultRankingRepository(transport, cache, clockMillis = { 1_000 })

        val cachedResult = repository.load(request)
        val refreshedResult = repository.load(request, forceRefresh = true)

        assertSame(cached, cachedResult.snapshot)
        assertEquals(RankingDataSource.CACHE_FRESH, cachedResult.source)
        assertEquals(1, transport.calls)
        assertEquals(RankingDataSource.NETWORK, refreshedResult.source)
    }

    @Test
    fun `network success writes snapshot and etag to cache`() = runTest {
        val cache = FakeCache()
        val repository = DefaultRankingRepository(
            QueueTransport(response(200, validJson(), etag = "etag-2", contentType = "application/json; charset=utf-8")),
            cache,
            clockMillis = { 1_000 }
        )

        val result = repository.load(request)

        assertEquals(RankingDataSource.NETWORK, result.source)
        assertEquals("snapshot-1", result.snapshot?.snapshotId)
        assertEquals("etag-2", cache.value?.etag)
        assertEquals(1_000L, cache.value?.cachedAtMillis)
    }

    @Test
    fun `not modified reuses matching cache and fails clearly without cache`() = runTest {
        val cached = snapshot(expiresAt = 900, staleUntil = 2_000)
        val withCache = DefaultRankingRepository(
            QueueTransport(response(304, null, etag = "etag-1", contentType = null)),
            FakeCache(CachedRanking(cached, 800, "etag-1")),
            clockMillis = { 1_000 }
        ).load(request, forceRefresh = true)
        val withoutCache = DefaultRankingRepository(
            QueueTransport(response(304, null, contentType = null)),
            FakeCache(),
            clockMillis = { 1_000 }
        ).load(request, forceRefresh = true)

        assertSame(cached, withCache.snapshot)
        assertEquals(RankingDataSource.CACHE_STALE, withCache.source)
        assertNull(withoutCache.snapshot)
        assertEquals(RankingDataSource.EMPTY, withoutCache.source)
        assertEquals(RankingFailureKind.NOT_MODIFIED_WITHOUT_CACHE, withoutCache.error?.kind)
    }

    @Test
    fun `http content and parse failures are typed and never empty success`() = runTest {
        val cases = listOf(
            response(400, "{}") to RankingFailureKind.BAD_REQUEST,
            response(404, "{}") to RankingFailureKind.NOT_FOUND,
            response(409, "{}") to RankingFailureKind.SNAPSHOT_EXPIRED,
            response(429, "{}", retryAfter = 30) to RankingFailureKind.RATE_LIMITED,
            response(503, "{}") to RankingFailureKind.SERVICE_UNAVAILABLE,
            response(200, "<html/>", contentType = "text/html") to RankingFailureKind.CONTENT_TYPE,
            response(200, "not-json") to RankingFailureKind.MALFORMED_RESPONSE
        )

        cases.forEach { (http, expected) ->
            val result = DefaultRankingRepository(QueueTransport(http), FakeCache(), clockMillis = { 1_000 })
                .load(request, forceRefresh = true)
            assertNull(result.snapshot)
            assertEquals(RankingDataSource.EMPTY, result.source)
            assertEquals(expected, result.error?.kind)
        }
    }

    @Test
    fun `network failure may fall back to stale but never expired cache`() = runTest {
        val stale = snapshot(expiresAt = 900, staleUntil = 1_100)
        val staleResult = DefaultRankingRepository(
            ThrowingTransport(IOException("query=secret-token")),
            FakeCache(CachedRanking(stale, 800, "etag-1")),
            clockMillis = { 1_000 }
        ).load(request, forceRefresh = true)
        val expiredResult = DefaultRankingRepository(
            ThrowingTransport(IOException("query=secret-token")),
            FakeCache(CachedRanking(snapshot(800, 900), 700, "etag-1")),
            clockMillis = { 1_000 }
        ).load(request, forceRefresh = true)

        assertSame(stale, staleResult.snapshot)
        assertEquals(RankingDataSource.CACHE_STALE, staleResult.source)
        assertEquals(RankingFailureKind.NETWORK, staleResult.error?.kind)
        assertEquals("Ranking request failed", staleResult.error?.message)
        assertNull(expiredResult.snapshot)
        assertEquals(RankingDataSource.EMPTY, expiredResult.source)
    }

    @Test
    fun `cancellation propagates instead of becoming a ranking error`() {
        assertThrows(CancellationException::class.java) {
            runTest {
                DefaultRankingRepository(
                    ThrowingTransport(CancellationException("cancelled")),
                    FakeCache(),
                    clockMillis = { 1_000 }
                ).load(request, forceRefresh = true)
            }
        }
    }

    private fun snapshot(expiresAt: Long, staleUntil: Long) = RankingSnapshot(
        schemaVersion = 1,
        requestId = "request-1",
        boardId = "popular",
        filters = RankingFilters("week", RankingMediaType.SERIES, "CN", 2026, "drama", "zh-CN", 20, null),
        snapshotId = "snapshot-1",
        generatedAt = InstantLike(500),
        dataAsOf = InstantLike(400),
        expiresAt = InstantLike(expiresAt),
        staleUntil = InstantLike(staleUntil),
        freshness = RankingFreshness.FRESH,
        availability = RankingAvailability.AVAILABLE,
        coverage = RankingCoverage(1, listOf("source"), true),
        sourceStatuses = listOf(RankingSourceStatus("source", RankingSourceHealth.AVAILABLE)),
        items = emptyList(),
        nextCursor = null
    )

    private fun validJson() = """
        {"schemaVersion":1,"requestId":"request-1","boardId":"popular",
        "filters":{"period":"week","mediaType":"SERIES","region":"CN","year":2026,"genre":"drama","language":"zh-CN","limit":20,"cursor":null},
        "snapshotId":"snapshot-1","generatedAt":"1970-01-01T00:00:00.500Z","dataAsOf":"1970-01-01T00:00:00.400Z",
        "expiresAt":"1970-01-01T00:00:02Z","staleUntil":"1970-01-01T00:00:03Z","freshness":"FRESH","availability":"AVAILABLE",
        "coverage":{"sourceCount":1,"coveredSourceIds":["source"],"complete":true},
        "sourceStatuses":[{"sourceId":"source","status":"AVAILABLE"}],"items":[],"nextCursor":null}
    """.trimIndent()

    private fun response(
        status: Int,
        body: String?,
        etag: String? = null,
        retryAfter: Long? = null,
        contentType: String? = "application/json"
    ) = RankingHttpResponse(status, body, etag, retryAfter, contentType)

    private class QueueTransport(private val response: RankingHttpResponse) : RankingTransport {
        var calls = 0
        override suspend fun fetch(request: RankingRequest, etag: String?): RankingHttpResponse {
            calls++
            return response
        }
    }

    private class ThrowingTransport(private val failure: Throwable) : RankingTransport {
        override suspend fun fetch(request: RankingRequest, etag: String?): RankingHttpResponse = throw failure
    }

    private class FakeCache(initial: CachedRanking? = null) : RankingEtagCache {
        var value = initial
        override suspend fun read(key: String) = value
        override suspend fun write(key: String, snapshot: RankingSnapshot) {
            value = CachedRanking(snapshot, 0)
        }
        override suspend fun write(key: String, snapshot: RankingSnapshot, etag: String?, cachedAtMillis: Long) {
            value = CachedRanking(snapshot, cachedAtMillis, etag)
        }
        override suspend fun remove(key: String) { value = null }
    }
}
