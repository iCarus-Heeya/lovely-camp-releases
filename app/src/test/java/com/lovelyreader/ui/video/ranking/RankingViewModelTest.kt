package com.lovelyreader.ui.video.ranking

import com.lovelyreader.video.ranking.InstantLike
import com.lovelyreader.video.ranking.RankingAvailability
import com.lovelyreader.video.ranking.RankingBoardOption
import com.lovelyreader.video.ranking.RankingCapabilities
import com.lovelyreader.video.ranking.RankingCoverage
import com.lovelyreader.video.ranking.RankingDataSource
import com.lovelyreader.video.ranking.RankingEntry
import com.lovelyreader.video.ranking.RankingError
import com.lovelyreader.video.ranking.RankingFailureKind
import com.lovelyreader.video.ranking.RankingFilterOption
import com.lovelyreader.video.ranking.RankingFilters
import com.lovelyreader.video.ranking.RankingFreshness
import com.lovelyreader.video.ranking.RankingLoadResult
import com.lovelyreader.video.ranking.RankingMediaType
import com.lovelyreader.video.ranking.RankingRepository
import com.lovelyreader.video.ranking.RankingRequest
import com.lovelyreader.video.ranking.RankingSnapshot
import com.lovelyreader.video.ranking.RankingSourceHealth
import com.lovelyreader.video.ranking.RankingSourceStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingViewModelTest {
    @Test
    fun `capability handshake updates the state before filters are rendered`() = runTest {
        val capabilities = RankingCapabilities(
            supportedBoards = listOf(RankingBoardOption("aggregate", "综合参考")),
            supportedMediaTypes = listOf(RankingMediaType.SERIES)
        )
        val repository = object : RankingRepository {
            override suspend fun load(request: RankingRequest, forceRefresh: Boolean): RankingLoadResult =
                RankingLoadResult(null, RankingDataSource.EMPTY)

            override suspend fun loadCapabilities(): Result<RankingCapabilities> = Result.success(capabilities)
        }
        val viewModel = RankingViewModel(repository, this)

        viewModel.refreshCapabilities()
        advanceUntilIdle()

        assertEquals(capabilities, viewModel.capabilities)
        assertEquals(capabilities, viewModel.state.value.capabilities)
        assertTrue(viewModel.state.value.capabilities.hasVerifiedFilters)
    }

    @Test
    fun `initial refresh waits for handshake and uses only declared dimensions`() = runTest {
        val capabilities = RankingCapabilities(
            supportedBoards = listOf(RankingBoardOption("popular", "热度")),
            supportedMediaTypes = listOf(RankingMediaType.SERIES),
            supportedPeriods = listOf(RankingFilterOption("month", "本月")),
            supportedRegions = listOf(RankingFilterOption("CN", "中国")),
            supportedYears = listOf(2026),
            supportedGenres = listOf(RankingFilterOption("drama", "剧情")),
            supportedLanguages = listOf(RankingFilterOption("zh-CN", "中文"))
        )
        val repository = object : RankingRepository {
            val requests = mutableListOf<RankingRequest>()
            override suspend fun loadCapabilities(): Result<RankingCapabilities> = Result.success(capabilities)
            override suspend fun load(request: RankingRequest, forceRefresh: Boolean): RankingLoadResult {
                requests += request
                return RankingLoadResult(
                    snapshot(
                        mediaType = RankingMediaType.SERIES,
                        items = listOf(entry("declared")),
                    ).copy(
                        filters = RankingFilters("month", RankingMediaType.SERIES, null, null, null, null, 20, null)
                    ),
                    RankingDataSource.NETWORK
                )
            }
        }
        val viewModel = RankingViewModel(repository, this)

        viewModel.refreshInitial()
        advanceUntilIdle()

        assertEquals(1, repository.requests.size)
        assertEquals(
            RankingRequest("popular", "month", RankingMediaType.SERIES, null, null, null, null, 20, null),
            repository.requests.single()
        )
        assertTrue(viewModel.state.value is RankingUiState.Content)
    }

    @Test
    fun `initial refresh does not call ranking endpoint when capability handshake is empty`() = runTest {
        var loads = 0
        val repository = object : RankingRepository {
            override suspend fun loadCapabilities(): Result<RankingCapabilities> = Result.success(RankingCapabilities())
            override suspend fun load(request: RankingRequest, forceRefresh: Boolean): RankingLoadResult {
                loads++
                return RankingLoadResult(null, RankingDataSource.EMPTY)
            }
        }
        val viewModel = RankingViewModel(repository, this)

        viewModel.refreshInitial()
        advanceUntilIdle()

        assertEquals(0, loads)
        val state = viewModel.state.value as RankingUiState.Empty
        assertTrue(state.message.contains("未接入"))
    }

    @Test
    fun `configured capabilities are retained across loading and result states`() = runTest {
        val capabilities = RankingCapabilities(
            supportedBoards = listOf(RankingBoardOption("aggregate", "综合")),
            supportedMediaTypes = listOf(RankingMediaType.SERIES)
        )
        val viewModel = RankingViewModel(
            QueueRepository(RankingLoadResult(snapshot(items = listOf(entry("one"))), RankingDataSource.NETWORK)),
            this,
            capabilities
        )

        assertEquals(capabilities, viewModel.capabilities)
        assertEquals(capabilities, viewModel.state.value.capabilities)
        viewModel.refresh(request())
        assertEquals(capabilities, viewModel.state.value.capabilities)
        advanceUntilIdle()
        assertEquals(capabilities, viewModel.state.value.capabilities)
    }

    @Test
    fun `initial request maps available partial empty and no-cache error truthfully`() = runTest {
        val request = request()
        val repository = QueueRepository(
            RankingLoadResult(snapshot(items = listOf(entry("one"))), RankingDataSource.NETWORK),
            RankingLoadResult(snapshot(availability = RankingAvailability.PARTIAL, items = listOf(entry("two"))), RankingDataSource.NETWORK),
            RankingLoadResult(snapshot(availability = RankingAvailability.EMPTY), RankingDataSource.NETWORK),
            RankingLoadResult(null, RankingDataSource.EMPTY, RankingError(RankingFailureKind.NETWORK, "offline", retryable = true))
        )
        val viewModel = RankingViewModel(repository, this)

        viewModel.refresh(request)
        assertTrue(viewModel.state.value is RankingUiState.Loading)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is RankingUiState.Content)

        viewModel.refresh(request.copy(mediaType = RankingMediaType.MOVIE))
        advanceUntilIdle()
        assertTrue(viewModel.state.value is RankingUiState.Partial)

        viewModel.refresh(request.copy(mediaType = RankingMediaType.SERIES))
        advanceUntilIdle()
        assertTrue(viewModel.state.value is RankingUiState.Empty)

        viewModel.refresh(request.copy(mediaType = RankingMediaType.VARIETY))
        advanceUntilIdle()
        val error = viewModel.state.value as RankingUiState.Error
        assertEquals(RankingFailureKind.NETWORK, error.error.kind)
        assertTrue(error.canRetry)
    }

    @Test
    fun `stale cache and retry preserve items while exposing freshness and refresh force`() = runTest {
        val request = request()
        val stale = snapshot(freshness = RankingFreshness.STALE, items = listOf(entry("one")))
        val repository = QueueRepository(
            RankingLoadResult(stale, RankingDataSource.CACHE_STALE, RankingError(RankingFailureKind.NETWORK, "offline", retryable = true)),
            RankingLoadResult(snapshot(items = listOf(entry("one"), entry("two"))), RankingDataSource.NETWORK)
        )
        val viewModel = RankingViewModel(repository, this)

        viewModel.refresh(request)
        advanceUntilIdle()
        val staleState = viewModel.state.value as RankingUiState.Stale
        assertEquals(listOf("one"), staleState.items.map(RankingEntry::canonicalId))
        assertEquals(RankingFailureKind.NETWORK, staleState.lastError?.kind)
        assertTrue(staleState.canRetry)

        viewModel.retry()
        assertTrue(viewModel.state.value is RankingUiState.Refreshing)
        advanceUntilIdle()
        val content = viewModel.state.value as RankingUiState.Content
        assertEquals(listOf("one", "two"), content.items.map(RankingEntry::canonicalId))
        assertEquals(listOf(false, true), repository.forceRefreshes)
    }

    @Test
    fun `rapid filter change ignores non cooperative old response`() = runTest {
        val oldResult = CompletableDeferred<RankingLoadResult>()
        val newResult = CompletableDeferred<RankingLoadResult>()
        val repository = object : RankingRepository {
            override suspend fun load(request: RankingRequest, forceRefresh: Boolean): RankingLoadResult =
                withContext(NonCancellable) {
                    if (request.mediaType == RankingMediaType.MOVIE) oldResult.await() else newResult.await()
                }
        }
        val viewModel = RankingViewModel(repository, this)

        viewModel.refresh(request().copy(mediaType = RankingMediaType.MOVIE))
        runCurrent()
        viewModel.refresh(request().copy(mediaType = RankingMediaType.SERIES))
        newResult.complete(RankingLoadResult(snapshot(mediaType = RankingMediaType.SERIES, items = listOf(entry("new"))), RankingDataSource.NETWORK))
        advanceUntilIdle()
        oldResult.complete(RankingLoadResult(snapshot(mediaType = RankingMediaType.MOVIE, items = listOf(entry("old"))), RankingDataSource.NETWORK))
        advanceUntilIdle()

        val state = viewModel.state.value as RankingUiState.Content
        assertEquals(RankingMediaType.SERIES, state.request.mediaType)
        assertEquals(listOf("new"), state.items.map(RankingEntry::canonicalId))
    }

    @Test
    fun `repository cancellation is never rendered as a ranking error`() = runTest {
        var calls = 0
        val viewModel = RankingViewModel(object : RankingRepository {
            override suspend fun load(request: RankingRequest, forceRefresh: Boolean): RankingLoadResult {
                if (calls++ == 0) throw CancellationException("cancelled")
                return RankingLoadResult(snapshot(items = listOf(entry("recovered"))), RankingDataSource.NETWORK)
            }
        }, this)

        viewModel.refresh(request())
        advanceUntilIdle()
        assertTrue(viewModel.state.value is RankingUiState.Loading)
        viewModel.refresh(request(), forceRefresh = true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is RankingUiState.Content)
    }

    @Test
    fun `pagination appends current snapshot only and drops duplicate canonical ids`() = runTest {
        val initial = snapshot(snapshotId = "snapshot-a", items = listOf(entry("one")), nextCursor = "page-2")
        val page = snapshot(
            snapshotId = "snapshot-a",
            cursor = "page-2",
            items = listOf(entry("one"), entry("two")),
            nextCursor = null
        )
        val repository = QueueRepository(
            RankingLoadResult(initial, RankingDataSource.NETWORK),
            RankingLoadResult(page, RankingDataSource.NETWORK)
        )
        val viewModel = RankingViewModel(repository, this)

        viewModel.refresh(request())
        advanceUntilIdle()
        viewModel.loadNextPage()
        viewModel.loadNextPage()
        advanceUntilIdle()

        val state = viewModel.state.value as RankingUiState.Content
        assertEquals(listOf("one", "two"), state.items.map(RankingEntry::canonicalId))
        assertNull(state.nextCursor)
        assertEquals(2, repository.requests.size)
        assertEquals("page-2", repository.requests.last().cursor)
    }

    @Test
    fun `pagination rejects a page bound to another snapshot`() = runTest {
        val initial = snapshot(snapshotId = "snapshot-a", items = listOf(entry("one")), nextCursor = "page-2")
        val wrongPage = snapshot(snapshotId = "snapshot-b", cursor = "page-2", items = listOf(entry("two")), nextCursor = null)
        val viewModel = RankingViewModel(
            QueueRepository(
                RankingLoadResult(initial, RankingDataSource.NETWORK),
                RankingLoadResult(wrongPage, RankingDataSource.NETWORK)
            ),
            this
        )

        viewModel.refresh(request())
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        val state = viewModel.state.value as RankingUiState.Content
        assertEquals(listOf("one"), state.items.map(RankingEntry::canonicalId))
        assertEquals("page-2", state.nextCursor)
        assertFalse(state.isLoadingNextPage)
    }

    @Test
    fun `search intent is immutable and consumed exactly once`() = runTest {
        val title = entry("one", displayTitle = "展示标题", searchTitle = "搜索标题", aliases = listOf("别名一", "别名二"))
        val viewModel = RankingViewModel(QueueRepository(RankingLoadResult(snapshot(items = listOf(title)), RankingDataSource.NETWORK)), this)

        viewModel.refresh(request())
        advanceUntilIdle()
        assertTrue(viewModel.requestSearch(title.canonicalId))
        assertEquals(
            RankingSearchIntent(title.canonicalId, title.searchTitle, title.aliases),
            viewModel.consumeSearchIntent()
        )
        assertNull(viewModel.consumeSearchIntent())
        assertFalse(viewModel.requestSearch("missing"))
    }

    private fun request() = RankingRequest(
        boardId = "popular", period = "week", mediaType = RankingMediaType.ALL,
        region = "CN", year = 2026, genre = "drama", language = "zh-CN", limit = 20
    )

    private fun entry(
        id: String,
        displayTitle: String = id,
        searchTitle: String = displayTitle,
        aliases: List<String> = emptyList()
    ) = RankingEntry(
        canonicalId = id, rank = 1, displayTitle = displayTitle, originalTitle = null, aliases = aliases,
        mediaType = RankingMediaType.SERIES, releaseYear = 2026, countries = listOf("CN"), genres = listOf("drama"),
        posterUrl = null, summary = null, aggregateScore = null, sourceRatings = emptyList(), sourceRanks = emptyList(),
        coverage = RankingCoverage(1, listOf("source"), true), searchTitle = searchTitle
    )

    private fun snapshot(
        snapshotId: String = "snapshot-a",
        mediaType: RankingMediaType = RankingMediaType.ALL,
        cursor: String? = null,
        freshness: RankingFreshness = RankingFreshness.FRESH,
        availability: RankingAvailability = RankingAvailability.AVAILABLE,
        items: List<RankingEntry> = emptyList(),
        nextCursor: String? = null
    ) = RankingSnapshot(
        schemaVersion = 1, requestId = "request-$snapshotId-$cursor", boardId = "popular",
        filters = RankingFilters("week", mediaType, "CN", 2026, "drama", "zh-CN", 20, cursor), snapshotId = snapshotId,
        generatedAt = InstantLike(1), dataAsOf = InstantLike(1), expiresAt = InstantLike(2), staleUntil = InstantLike(3),
        freshness = freshness, availability = availability, coverage = RankingCoverage(1, listOf("source"), true),
        sourceStatuses = listOf(RankingSourceStatus("source", RankingSourceHealth.AVAILABLE)), items = items, nextCursor = nextCursor
    )

    private class QueueRepository(vararg results: RankingLoadResult) : RankingRepository {
        private val results = ArrayDeque(results.toList())
        val requests = mutableListOf<RankingRequest>()
        val forceRefreshes = mutableListOf<Boolean>()

        override suspend fun load(request: RankingRequest, forceRefresh: Boolean): RankingLoadResult {
            requests += request
            forceRefreshes += forceRefresh
            return results.removeFirst()
        }
    }
}
