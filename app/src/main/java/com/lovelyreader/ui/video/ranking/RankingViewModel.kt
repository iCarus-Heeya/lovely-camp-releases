package com.lovelyreader.ui.video.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lovelyreader.video.ranking.RankingAvailability
import com.lovelyreader.video.ranking.RankingBoardOption
import com.lovelyreader.video.ranking.RankingCapabilities
import com.lovelyreader.video.ranking.RankingDataSource
import com.lovelyreader.video.ranking.RankingEntry
import com.lovelyreader.video.ranking.RankingError
import com.lovelyreader.video.ranking.RankingFailureKind
import com.lovelyreader.video.ranking.RankingFreshness
import com.lovelyreader.video.ranking.RankingLoadResult
import com.lovelyreader.video.ranking.RankingRepository
import com.lovelyreader.video.ranking.RankingRequest
import com.lovelyreader.video.ranking.RankingSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A one-shot bridge to the existing drama search; it contains no site URL. */
data class RankingSearchIntent(
    val canonicalId: String,
    val displayTitle: String,
    val aliases: List<String>
)

sealed interface RankingUiState {
    val capabilities: RankingCapabilities

    data class Loading(
        override val capabilities: RankingCapabilities = RankingCapabilities()
    ) : RankingUiState

    data class Content(
        override val request: RankingRequest,
        override val snapshot: RankingSnapshot,
        override val items: List<RankingEntry>,
        override val source: RankingDataSource,
        override val nextCursor: String?,
        override val isLoadingNextPage: Boolean = false,
        override val lastError: RankingError? = null,
        override val capabilities: RankingCapabilities = RankingCapabilities()
    ) : RankingSnapshotUiState

    data class Refreshing(
        override val request: RankingRequest,
        override val snapshot: RankingSnapshot,
        override val items: List<RankingEntry>,
        override val source: RankingDataSource,
        override val nextCursor: String?,
        override val isLoadingNextPage: Boolean = false,
        override val lastError: RankingError? = null,
        override val capabilities: RankingCapabilities = RankingCapabilities()
    ) : RankingSnapshotUiState

    data class Stale(
        override val request: RankingRequest,
        override val snapshot: RankingSnapshot,
        override val items: List<RankingEntry>,
        override val source: RankingDataSource,
        override val nextCursor: String?,
        override val isLoadingNextPage: Boolean = false,
        override val lastError: RankingError? = null,
        val canRetry: Boolean = true,
        override val capabilities: RankingCapabilities = RankingCapabilities()
    ) : RankingSnapshotUiState

    data class Partial(
        override val request: RankingRequest,
        override val snapshot: RankingSnapshot,
        override val items: List<RankingEntry>,
        override val source: RankingDataSource,
        override val nextCursor: String?,
        override val isLoadingNextPage: Boolean = false,
        override val lastError: RankingError? = null,
        override val capabilities: RankingCapabilities = RankingCapabilities()
    ) : RankingSnapshotUiState

    data class Empty(
        override val capabilities: RankingCapabilities = RankingCapabilities(),
        val request: RankingRequest? = null,
        val snapshot: RankingSnapshot? = null,
        val message: String = "暂无可展示的排行榜数据",
        val error: RankingError? = null,
        val canRetry: Boolean = false
    ) : RankingUiState

    data class Error(
        val request: RankingRequest,
        val error: RankingError,
        val canRetry: Boolean,
        override val capabilities: RankingCapabilities = RankingCapabilities()
    ) : RankingUiState
}

/** The shared, immutable portion of states that can render ranking entries. */
sealed interface RankingSnapshotUiState : RankingUiState {
    val request: RankingRequest
    val snapshot: RankingSnapshot
    val items: List<RankingEntry>
    val source: RankingDataSource
    val nextCursor: String?
    val isLoadingNextPage: Boolean
    val lastError: RankingError?
}

/**
 * Owns ranking state only. It deliberately emits [RankingSearchIntent] instead
 * of importing the site-specific DramaViewModel, keeping canonical ranking
 * identity separate from the current video-source identity.
 */
class RankingViewModel(
    private val repository: RankingRepository,
    private val scope: CoroutineScope? = null,
    initialCapabilities: RankingCapabilities = RankingCapabilities()
) : ViewModel() {
    private var currentCapabilities = initialCapabilities
    val capabilities: RankingCapabilities
        get() = currentCapabilities

    private val _state = MutableStateFlow<RankingUiState>(RankingUiState.Empty(capabilities = currentCapabilities))
    val state: StateFlow<RankingUiState> = _state.asStateFlow()
    val uiState: StateFlow<RankingUiState> = state

    private val _searchIntent = MutableStateFlow<RankingSearchIntent?>(null)
    private var activeRequest: RankingRequest? = null
    private var generation = 0L
    private var refreshJob: Job? = null
    private var nextPageJob: Job? = null
    private var capabilitiesJob: Job? = null
    private var capabilitiesReady = initialCapabilities.hasVerifiedFilters
    private val executionScope: CoroutineScope
        get() = scope ?: viewModelScope

    fun refresh(request: RankingRequest, forceRefresh: Boolean = false) {
        // Once the handshake has completed, reject requests that contain a
        // filter the service did not declare. Before the first handshake we
        // keep direct unit-test/fake-repository callers usable; the production
        // host enters through refreshInitial() below.
        if (capabilitiesReady && !isSupportedRequest(request, capabilities)) {
            activeRequest = null
            refreshJob?.cancel()
            nextPageJob?.cancel()
            _state.value = RankingUiState.Empty(
                capabilities = capabilities,
                message = "当前筛选能力已更新，请重新选择榜单条件。"
            )
            return
        }
        val requestGeneration = ++generation
        activeRequest = request
        refreshJob?.cancel()
        nextPageJob?.cancel()
        _state.value = refreshPresentation(request)
        refreshJob = executionScope.launch {
            val result = try {
                repository.load(request, forceRefresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                RankingLoadResult(
                    snapshot = null,
                    source = RankingDataSource.EMPTY,
                    error = RankingError(RankingFailureKind.NETWORK, "Ranking request failed", retryable = true)
                )
            }
            if (requestGeneration != generation || activeRequest != request) return@launch
            _state.value = result.toInitialState(request, capabilities)
        }
    }

    fun retry() {
        activeRequest?.let { refresh(it, forceRefresh = true) }
    }

    /** Fetches the versioned service capabilities before filters become visible. */
    fun refreshCapabilities() {
        capabilitiesJob?.cancel()
        capabilitiesJob = executionScope.launch {
            val loaded = loadCapabilitiesSafely()
            capabilitiesReady = true
            applyCapabilities(loaded)
        }
    }

    /**
     * Performs the handshake and only then creates a request from declared
     * capabilities. No fixed board/media combination is sent while the
     * server contract is unknown or unavailable.
     */
    fun refreshInitial() {
        capabilitiesJob?.cancel()
        _state.value = RankingUiState.Loading(capabilities)
        capabilitiesJob = executionScope.launch {
            val loaded = loadCapabilitiesSafely()
            capabilitiesReady = true
            applyCapabilities(loaded)
            val initialRequest = initialRequest(loaded)
            if (initialRequest != null) {
                refresh(initialRequest)
            } else {
                activeRequest = null
                _state.value = RankingUiState.Empty(
                    capabilities = loaded,
                    message = if (loaded.hasVerifiedFilters) {
                        "排行榜暂未返回完整的榜单能力。"
                    } else {
                        "排行榜数据服务暂未接入。"
                    }
                )
            }
        }
    }

    fun loadNextPage() {
        val current = _state.value as? RankingSnapshotUiState ?: return
        val cursor = current.nextCursor ?: return
        if (current.isLoadingNextPage || nextPageJob?.isActive == true) return

        val pageGeneration = generation
        val parentRequest = current.request
        val parentSnapshot = current.snapshot
        val requestContext = requestContext(parentRequest)
        val pageRequest = parentRequest.copy(cursor = cursor)
        _state.value = current.withPageLoading(isLoading = true)
        nextPageJob = executionScope.launch {
            val result = try {
                repository.load(pageRequest)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                RankingLoadResult(
                    snapshot = null,
                    source = RankingDataSource.EMPTY,
                    error = RankingError(RankingFailureKind.NETWORK, "Ranking request failed", retryable = true)
                )
            }
            val active = _state.value as? RankingSnapshotUiState ?: return@launch
            if (pageGeneration != generation || activeRequest != parentRequest ||
                requestContext(active.request) != requestContext || active.snapshot.snapshotId != parentSnapshot.snapshotId
            ) return@launch

            val page = result.snapshot
            if (page == null || !page.isCompatiblePage(parentRequest, pageRequest, parentSnapshot, result.source)) {
                _state.value = active.withPageLoading(isLoading = false, error = result.error)
                return@launch
            }
            val merged = mergeEntries(active.items, page.items)
            val mergedSnapshot = page.copy(
                filters = parentSnapshot.filters,
                items = merged,
                nextCursor = page.nextCursor
            )
            _state.value = mergedSnapshot.toPagedState(
                request = parentRequest,
                items = merged,
                source = result.source,
                error = result.error,
                capabilities = capabilities
            )
        }
    }

    /** Queues a one-shot search handoff for a visible entry and never opens a site detail. */
    fun requestSearch(canonicalId: String): Boolean {
        val visible = (_state.value as? RankingSnapshotUiState)?.items ?: return false
        val entry = visible.firstOrNull { it.canonicalId == canonicalId } ?: return false
        _searchIntent.value = RankingSearchIntent(
            canonicalId = entry.canonicalId,
            displayTitle = entry.searchTitle,
            aliases = entry.aliases.toList()
        )
        return true
    }

    fun consumeSearchIntent(): RankingSearchIntent? {
        val intent = _searchIntent.value
        _searchIntent.value = null
        return intent
    }

    override fun onCleared() {
        refreshJob?.cancel()
        nextPageJob?.cancel()
        capabilitiesJob?.cancel()
        super.onCleared()
    }

    private fun applyCapabilities(value: RankingCapabilities) {
        if (value == capabilities) return
        currentCapabilities = value
        _state.value = _state.value.withCapabilities(value)
    }

    private suspend fun loadCapabilitiesSafely(): RankingCapabilities = try {
        repository.loadCapabilities().getOrElse { RankingCapabilities() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        RankingCapabilities()
    }

    private fun initialRequest(value: RankingCapabilities): RankingRequest? {
        val board = value.normalizedBoards().firstOrNull() ?: return null
        val media = value.normalizedMediaTypes().firstOrNull() ?: return null
        return RankingRequest(
            boardId = board.id,
            period = value.normalizedPeriods().firstOrNull()?.id,
            mediaType = media,
            region = null,
            year = null,
            genre = null,
            language = null,
            limit = 20
        )
    }

    private fun isSupportedRequest(request: RankingRequest, value: RankingCapabilities): Boolean {
        if (!value.canLoadRanking) return false
        if (request.boardId !in value.normalizedBoards().map { it.id }) return false
        if (request.mediaType !in value.normalizedMediaTypes()) return false
        if (request.period != null && request.period !in value.normalizedPeriods().map { it.id }) return false
        if (request.region != null && request.region !in value.normalizedRegions().map { it.id }) return false
        if (request.year != null && request.year !in value.normalizedYears()) return false
        if (request.genre != null && request.genre !in value.normalizedGenres().map { it.id }) return false
        if (request.language != null && request.language !in value.normalizedLanguages().map { it.id }) return false
        return true
    }

    private fun refreshPresentation(request: RankingRequest): RankingUiState {
        val current = _state.value as? RankingSnapshotUiState
        return if (current != null && requestContext(current.request) == requestContext(request)) {
            RankingUiState.Refreshing(
                request = current.request,
                snapshot = current.snapshot,
                items = current.items,
                source = current.source,
                nextCursor = current.nextCursor,
                lastError = current.lastError,
                capabilities = capabilities
            )
        } else {
            RankingUiState.Loading(capabilities)
        }
    }
}

private fun RankingLoadResult.toInitialState(
    request: RankingRequest,
    capabilities: RankingCapabilities
): RankingUiState {
    val rankingSnapshot = snapshot
    if (rankingSnapshot == null) {
        val failure = error
        return if (failure == null) {
            RankingUiState.Empty(capabilities = capabilities, request = request)
        } else {
            RankingUiState.Error(request, failure, failure.retryable, capabilities)
        }
    }
    val data = rankingSnapshot.items
    if (source == RankingDataSource.CACHE_STALE || rankingSnapshot.freshness == RankingFreshness.STALE) {
        return RankingUiState.Stale(
            request, rankingSnapshot, data, source, rankingSnapshot.nextCursor,
            lastError = error, canRetry = error?.retryable ?: true, capabilities = capabilities
        )
    }
    if (rankingSnapshot.availability == RankingAvailability.UNAVAILABLE) {
        return RankingUiState.Error(
            request,
            error ?: RankingError(RankingFailureKind.SERVICE_UNAVAILABLE, "Ranking service is unavailable", retryable = true),
            error?.retryable ?: true,
            capabilities
        )
    }
    if (rankingSnapshot.availability == RankingAvailability.EMPTY || data.isEmpty()) {
        return RankingUiState.Empty(
            capabilities = capabilities,
            request = request,
            snapshot = rankingSnapshot,
            error = error,
            canRetry = error?.retryable == true
        )
    }
    return if (rankingSnapshot.availability == RankingAvailability.PARTIAL) {
        RankingUiState.Partial(request, rankingSnapshot, data, source, rankingSnapshot.nextCursor, lastError = error, capabilities = capabilities)
    } else {
        RankingUiState.Content(request, rankingSnapshot, data, source, rankingSnapshot.nextCursor, lastError = error, capabilities = capabilities)
    }
}

private fun RankingSnapshot.isCompatiblePage(
    parentRequest: RankingRequest,
    pageRequest: RankingRequest,
    parentSnapshot: RankingSnapshot,
    source: RankingDataSource
): Boolean {
    if (snapshotId != parentSnapshot.snapshotId || boardId != parentRequest.boardId) return false
    if (requestContext(filters.toRequest(boardId)) != requestContext(parentRequest)) return false
    return filters.cursor == pageRequest.cursor || (source != RankingDataSource.NETWORK && filters.cursor == null)
}

private fun com.lovelyreader.video.ranking.RankingFilters.toRequest(boardId: String) = RankingRequest(
    boardId = boardId,
    period = period,
    mediaType = mediaType,
    region = region,
    year = year,
    genre = genre,
    language = language,
    limit = limit,
    cursor = cursor
)

private fun requestContext(request: RankingRequest): List<Any?> = listOf(
    request.boardId, request.period, request.mediaType, request.region, request.year,
    request.genre, request.language, request.limit
)

private fun mergeEntries(current: List<RankingEntry>, page: List<RankingEntry>): List<RankingEntry> {
    val ids = current.mapTo(mutableSetOf()) { it.canonicalId }
    return current + page.filter { ids.add(it.canonicalId) }
}

private fun RankingSnapshot.toPagedState(
    request: RankingRequest,
    items: List<RankingEntry>,
    source: RankingDataSource,
    error: RankingError?,
    capabilities: RankingCapabilities
): RankingSnapshotUiState = when {
    source == RankingDataSource.CACHE_STALE || freshness == RankingFreshness.STALE -> RankingUiState.Stale(
        request, this, items, source, nextCursor, lastError = error, canRetry = error?.retryable ?: true, capabilities = capabilities
    )
    availability == RankingAvailability.PARTIAL -> RankingUiState.Partial(
        request, this, items, source, nextCursor, lastError = error, capabilities = capabilities
    )
    else -> RankingUiState.Content(request, this, items, source, nextCursor, lastError = error, capabilities = capabilities)
}

private fun RankingUiState.withCapabilities(value: RankingCapabilities): RankingUiState = when (this) {
    is RankingUiState.Loading -> copy(capabilities = value)
    is RankingUiState.Content -> copy(capabilities = value)
    is RankingUiState.Refreshing -> copy(capabilities = value)
    is RankingUiState.Stale -> copy(capabilities = value)
    is RankingUiState.Partial -> copy(capabilities = value)
    is RankingUiState.Empty -> copy(capabilities = value)
    is RankingUiState.Error -> copy(capabilities = value)
}

private fun RankingSnapshotUiState.withPageLoading(isLoading: Boolean, error: RankingError? = lastError): RankingSnapshotUiState = when (this) {
    is RankingUiState.Content -> copy(isLoadingNextPage = isLoading, lastError = error)
    is RankingUiState.Refreshing -> copy(isLoadingNextPage = isLoading, lastError = error)
    is RankingUiState.Stale -> copy(isLoadingNextPage = isLoading, lastError = error)
    is RankingUiState.Partial -> copy(isLoadingNextPage = isLoading, lastError = error)
}
