package com.lovelyreader.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lovelyreader.video.DownloadResult
import com.lovelyreader.video.VideoDownloadTask
import com.lovelyreader.video.VideoLibraryRepository
import com.lovelyreader.video.VideoRootResolution
import com.lovelyreader.video.VideoRootResolutionStatus
import com.lovelyreader.video.VideoSiteAdapter
import com.lovelyreader.video.VideoSiteResolver
import com.lovelyreader.video.VideoSiteRoot
import com.lovelyreader.video.VideoSource
import com.lovelyreader.video.VideoTitle
import com.lovelyreader.video.VideoTitleDetail
import com.lovelyreader.video.mergeVideoTitleMetadata
import com.lovelyreader.video.VideoEpisode
import com.lovelyreader.video.VideoMediaLink
import com.lovelyreader.video.VideoRecentViewing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import kotlinx.coroutines.launch

/** Injectable boundary around [VideoSiteResolver] so the screen never depends on fetching details. */
fun interface DramaRootResolver {
    suspend fun resolve(): VideoRootResolution
}

fun interface DramaDownloadEnqueuer {
    fun enqueue(url: String): DownloadResult
}

/** Metadata-only library operations required by the drama UI. */
interface DramaLibrary {
    fun selectedSourceId(titleId: String): String?
    fun selectSource(titleId: String, sourceId: String)
    fun recordViewing(title: VideoTitle, source: VideoSource, episode: VideoEpisode)
    fun recentViewing(): VideoRecentViewing?
    fun recordSourceResult(titleId: String, sourceId: String, success: Boolean)
    fun orderSources(titleId: String, sources: List<VideoSource>): List<VideoSource>
    fun enqueueDownload(titleId: String, sourceId: String, episodeId: String, directUrl: String): VideoDownloadTask
    fun downloadQueue(): List<VideoDownloadTask>
}

class VideoLibraryDramaStore(
    private val repository: VideoLibraryRepository
) : DramaLibrary {
    override fun selectedSourceId(titleId: String): String? = repository.selectedSourceId(titleId)

    override fun selectSource(titleId: String, sourceId: String) {
        repository.selectSource(titleId, sourceId)
    }

    override fun recordViewing(title: VideoTitle, source: VideoSource, episode: VideoEpisode) {
        repository.recordViewing(title, source, episode, positionMillis = 0L, viewedAtMillis = System.currentTimeMillis())
    }

    override fun recentViewing(): VideoRecentViewing? = repository.recentViewing()

    override fun recordSourceResult(titleId: String, sourceId: String, success: Boolean) {
        repository.recordSourceResult(titleId, sourceId, success)
    }

    override fun orderSources(titleId: String, sources: List<VideoSource>): List<VideoSource> {
        val ordered = repository.orderSources(titleId, sources.map(VideoSource::id))
        return ordered.mapNotNull { id -> sources.firstOrNull { it.id == id } }
    }

    override fun enqueueDownload(
        titleId: String,
        sourceId: String,
        episodeId: String,
        directUrl: String
    ): VideoDownloadTask = repository.enqueueDownload(titleId, sourceId, episodeId, directUrl)

    override fun downloadQueue(): List<VideoDownloadTask> = repository.downloadQueue()
}

data class DramaRootUiState(
    val root: VideoSiteRoot? = null,
    val isRefreshing: Boolean = false,
    val isUsingCachedRoot: Boolean = false,
    val message: String = "片源地址尚未检查"
)

data class DramaSearchUiState(
    val isLoading: Boolean = false,
    val results: List<VideoTitle> = emptyList(),
    val message: String = "输入剧名后开始找剧"
)

data class DramaDetailUiState(
    val detail: VideoTitleDetail? = null,
    val isLoading: Boolean = false,
    val message: String = "请选择一部剧查看播放源"
)

data class DramaPlaybackUiState(
    val titleName: String? = null,
    val episode: VideoEpisode? = null,
    val media: VideoMediaLink? = null,
    val isLoading: Boolean = false,
    val message: String = ""
)

/**
 * Owns only drama state. The host app can opt into it later without changing the
 * novel shelf's navigation or state model.
 */
class DramaViewModel(
    private val rootResolver: DramaRootResolver?,
    private val siteAdapter: VideoSiteAdapter?,
    private val library: DramaLibrary,
    private val downloadEnqueuer: DramaDownloadEnqueuer?,
    private val coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val _rootStatus = MutableStateFlow(DramaRootUiState())
    val rootStatus: StateFlow<DramaRootUiState> = _rootStatus.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow(DramaSearchUiState())
    val searchResults: StateFlow<DramaSearchUiState> = _searchResults.asStateFlow()

    private val _selectedTitle = MutableStateFlow(DramaDetailUiState())
    val selectedTitle: StateFlow<DramaDetailUiState> = _selectedTitle.asStateFlow()

    private val _selectedSource = MutableStateFlow<VideoSource?>(null)
    val selectedSource: StateFlow<VideoSource?> = _selectedSource.asStateFlow()

    private val _sourceEpisodes = MutableStateFlow<List<VideoEpisode>>(emptyList())
    val sourceEpisodes: StateFlow<List<VideoEpisode>> = _sourceEpisodes.asStateFlow()

    private val _selectedEpisodeIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedEpisodeIds: StateFlow<Set<String>> = _selectedEpisodeIds.asStateFlow()

    private val _downloadTasks = MutableStateFlow(library.downloadQueue())
    val downloadTasks: StateFlow<List<VideoDownloadTask>> = _downloadTasks.asStateFlow()

    private val _playback = MutableStateFlow(DramaPlaybackUiState())
    val playback: StateFlow<DramaPlaybackUiState> = _playback.asStateFlow()

    private val _recentViewing = MutableStateFlow(library.recentViewing())
    val recentViewing: StateFlow<VideoRecentViewing?> = _recentViewing.asStateFlow()

    private var rootRequest = 0
    private var searchRequest = 0
    private var detailRequest = 0
    private var episodesRequest = 0
    private var playbackRequest = 0
    private var searchJob: Job? = null
    private var pendingResumeRequest = false
    private var pendingSearchQuery: String? = null
    private val scope: CoroutineScope
        get() = coroutineScope ?: viewModelScope

    init {
        refreshRoot()
    }

    fun refreshRoot() {
        val request = ++rootRequest
        val resolver = rootResolver
        if (resolver == null) {
            _rootStatus.value = DramaRootUiState(message = "片源地址解析服务暂不可用。")
            return
        }
        _rootStatus.value = _rootStatus.value.copy(isRefreshing = true, message = "正在更新片源地址…")
        scope.launch {
            val resolution = runCatching { resolver.resolve() }.getOrElse {
                VideoRootResolution(null, VideoRootResolutionStatus.UNAVAILABLE, "片源地址刷新失败。")
            }
            if (request == rootRequest) {
                _rootStatus.value = resolution.toUiState()
                if (pendingResumeRequest) {
                    pendingResumeRequest = false
                    resumeRecentViewing()
                }
                pendingSearchQuery?.let { query ->
                    pendingSearchQuery = null
                    search(query)
                }
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        val request = ++searchRequest
        val root = rootStatus.value.root
        val adapter = siteAdapter
        if (query.isBlank()) {
            _searchResults.value = DramaSearchUiState(message = "输入剧名后开始找剧")
            return
        }
        if (root == null && rootStatus.value.isRefreshing) {
            pendingSearchQuery = query.trim()
            _searchResults.value = DramaSearchUiState(isLoading = true, message = "正在连接片源后搜索…")
            return
        }
        if (root == null || adapter == null) {
            _searchResults.value = DramaSearchUiState(
                message = dramaHomeAvailabilityMessage(rootStatus.value) ?: "片源地址尚未就绪，请稍后再试。"
            )
            return
        }
        _searchResults.value = DramaSearchUiState(isLoading = true, message = dramaStatusCopy(DramaStatus.Searching))
        searchJob = scope.launch {
            val results = runCatching { adapter.search(root, query.trim()) }.getOrElse { emptyList() }
            if (request == searchRequest) {
                _searchResults.value = DramaSearchUiState(
                    results = results,
                    message = if (results.isEmpty()) dramaStatusCopy(DramaStatus.NoSearchResults) else "找到 ${results.size} 部剧集"
                )
            }
        }
    }

    fun openTitle(title: VideoTitle) {
        val root = rootStatus.value.root
        val adapter = siteAdapter
        val request = ++detailRequest
        _selectedSource.value = null
        _sourceEpisodes.value = emptyList()
        _selectedEpisodeIds.value = emptySet()
        closePlayback()
        if (root == null || adapter == null) {
            _selectedTitle.value = DramaDetailUiState(message = "片源尚未就绪，暂时无法加载剧集详情")
            return
        }
        _selectedTitle.value = DramaDetailUiState(isLoading = true, message = "正在加载播放源…")
        scope.launch {
            val detail = runCatching { adapter.loadDetail(root, title.detailUrl) }.getOrNull()
            if (request != detailRequest) return@launch
            if (detail == null) {
                _selectedTitle.value = DramaDetailUiState(message = "暂时无法取得这部剧的播放源")
                return@launch
            }
            val searchTitle = _searchResults.value.results.firstOrNull { candidate ->
                candidate.id == title.id || candidate.detailUrl == title.detailUrl
            } ?: title
            val mergedTitle = mergeVideoTitleMetadata(searchTitle, detail.title)
            val orderedSources = library.orderSources(mergedTitle.id, detail.sources.map { it.copy(titleId = mergedTitle.id) })
            val orderedDetail = detail.copy(title = mergedTitle, sources = orderedSources)
            _selectedTitle.value = DramaDetailUiState(detail = orderedDetail, message = "请选择播放源")
            val selected = orderedSources.firstOrNull { it.id == library.selectedSourceId(mergedTitle.id) }
                ?: orderedSources.firstOrNull()
            if (selected == null) {
                _selectedTitle.value = DramaDetailUiState(detail = orderedDetail, message = "这部剧暂未提供可用播放源")
            } else {
                selectSource(selected)
            }
        }
    }

    fun selectSource(source: VideoSource) {
        val detail = selectedTitle.value.detail ?: return
        if (source.titleId != detail.title.id || detail.sources.none { it.id == source.id }) return
        val root = rootStatus.value.root
        val adapter = siteAdapter
        val request = ++episodesRequest
        library.selectSource(detail.title.id, source.id)
        _selectedSource.value = source
        _sourceEpisodes.value = emptyList()
        _selectedEpisodeIds.value = emptySet()
        closePlayback()
        if (root == null || adapter == null) {
            _selectedTitle.value = _selectedTitle.value.copy(message = "片源尚未就绪，暂时无法加载选集")
            return
        }
        _selectedTitle.value = _selectedTitle.value.copy(message = "正在加载 ${source.label} 的选集…")
        scope.launch {
            val episodes = runCatching { adapter.loadEpisodes(root, source) }.getOrElse { emptyList() }
            if (request == episodesRequest && _selectedSource.value?.id == source.id) {
                _sourceEpisodes.value = episodes.filter { it.sourceId == source.id }.sortedBy { it.position }
                _selectedTitle.value = _selectedTitle.value.copy(
                    message = if (episodes.isEmpty()) "该播放源暂未提供选集" else "请选择一集或多集"
                )
            }
        }
    }

    fun toggleEpisode(episodeId: String) {
        if (_sourceEpisodes.value.none { it.id == episodeId }) return
        _selectedEpisodeIds.value = _selectedEpisodeIds.value.let { selected ->
            if (episodeId in selected) selected - episodeId else selected + episodeId
        }
    }

    fun openEpisode(episode: VideoEpisode) {
        val root = rootStatus.value.root
        val adapter = siteAdapter
        val source = selectedSource.value
        if (root == null || adapter == null || source == null || episode.sourceId != source.id ||
            _sourceEpisodes.value.none { it.id == episode.id }
        ) {
            _playback.value = DramaPlaybackUiState(message = "这一集暂时无法播放")
            return
        }
        val titleName = selectedTitle.value.detail?.title?.name
        val request = ++playbackRequest
        _playback.value = DramaPlaybackUiState(
            titleName = titleName,
            episode = episode,
            isLoading = true,
            message = "正在准备播放…"
        )
        scope.launch {
            val media = runCatching { adapter.loadMedia(root, episode) }
                .getOrNull()
                ?.takeUnless(VideoMediaLink::isEncrypted)
            if (request != playbackRequest) return@launch
            _playback.value = if (media == null) {
                library.recordSourceResult(selectedTitle.value.detail?.title?.id ?: return@launch, source.id, success = false)
                DramaPlaybackUiState(
                    titleName = titleName,
                    episode = episode,
                    message = "该播放源没有提供可公开播放的媒体地址。请返回选集后尝试其他播放源。"
                )
            } else {
                library.recordSourceResult(selectedTitle.value.detail?.title?.id ?: return@launch, source.id, success = true)
                library.recordViewing(selectedTitle.value.detail?.title ?: return@launch, source, episode)
                _recentViewing.value = library.recentViewing()
                DramaPlaybackUiState(titleName = titleName, episode = episode, media = media)
            }
        }
    }

    fun closePlayback() {
        playbackRequest++
        _playback.value = DramaPlaybackUiState()
    }

    /** Re-enters the normal title/source/episode pipeline without exposing a stored raw media URL. */
    fun resumeRecentViewing() {
        val recent = recentViewing.value ?: return
        val root = rootStatus.value.root
        val adapter = siteAdapter
        if (root == null && rootStatus.value.isRefreshing) {
            pendingResumeRequest = true
            _playback.value = DramaPlaybackUiState(isLoading = true, message = "正在连接片源后继续观看…")
            return
        }
        if (root == null || adapter == null || recent.titleDetailUrl.isBlank()) {
            _playback.value = DramaPlaybackUiState(message = "暂时无法继续观看，请从搜索结果重新打开这部剧。")
            return
        }
        val request = ++detailRequest
        _playback.value = DramaPlaybackUiState(isLoading = true, message = "正在恢复上次观看…")
        scope.launch {
            val detail = runCatching {
                adapter.loadDetail(root, rebindSavedTitleUrl(root, recent.titleDetailUrl))
            }.getOrNull()
            if (request != detailRequest || detail == null) {
                if (request == detailRequest) _playback.value = DramaPlaybackUiState(message = "暂时无法恢复上次观看，请重新选择片源。")
                return@launch
            }
            val sources = library.orderSources(detail.title.id, detail.sources)
            val source = sources.firstOrNull { source -> matchesSavedSource(source.id, recent.sourceId) }
            _selectedTitle.value = DramaDetailUiState(detail = detail.copy(sources = sources), message = "请选择播放源")
            if (source == null) {
                _playback.value = DramaPlaybackUiState(message = "上次使用的播放源已不可用，请重新选择播放源。")
                return@launch
            }
            library.selectSource(detail.title.id, source.id)
            _selectedSource.value = source
            val episodes = runCatching { adapter.loadEpisodes(root, source) }.getOrDefault(emptyList())
                .filter { it.sourceId == source.id }.sortedBy { it.position }
            _sourceEpisodes.value = episodes
            val episode = episodes.firstOrNull { candidate -> matchesSavedEpisode(candidate, recent.episodeId) }
            if (episode == null) {
                _playback.value = DramaPlaybackUiState(message = "上次观看的剧集已不可用，请重新选择。")
                return@launch
            }
            openEpisode(episode)
        }
    }

    /** Provider domains can rotate; retain only the title path when restoring stored metadata. */
    private fun rebindSavedTitleUrl(root: VideoSiteRoot, savedUrl: String): String {
        val saved = runCatching { URI(savedUrl) }.getOrNull()
            ?.takeIf { it.isAbsolute && it.host != null }
            ?: return savedUrl
        val rootUri = runCatching { URI(root.url) }.getOrNull() ?: return savedUrl
        val rootRelative = URI(null, null, saved.rawPath ?: "/", saved.rawQuery, null)
        return rootUri.resolve(rootRelative).toString()
    }

    private fun matchesSavedSource(candidateId: String, savedId: String): Boolean =
        candidateId == savedId || candidateId.substringAfter("#source-", "") == savedId.substringAfter("#source-", "")

    private fun matchesSavedEpisode(candidate: VideoEpisode, savedId: String): Boolean {
        if (candidate.id == savedId) return true
        val savedUrl = savedId.substringAfter("#episode-", "")
        return resumePathKey(candidate.url) == resumePathKey(savedUrl)
    }

    private fun resumePathKey(url: String): String? = runCatching { URI(url) }.getOrNull()
        ?.takeIf { it.isAbsolute && it.host != null }
        ?.let { uri -> uri.rawPath.orEmpty() + "?" + uri.rawQuery.orEmpty() }

    fun enqueueSelectedEpisodes() {
        val root = rootStatus.value.root
        val adapter = siteAdapter
        val detail = selectedTitle.value.detail
        val source = selectedSource.value
        val enqueuer = downloadEnqueuer
        if (root == null || adapter == null || detail == null || source == null || enqueuer == null) {
            _selectedTitle.value = _selectedTitle.value.copy(message = "下载服务暂未就绪")
            return
        }
        val requested = _sourceEpisodes.value.filter { it.id in _selectedEpisodeIds.value }
        if (requested.isEmpty()) {
            _selectedTitle.value = _selectedTitle.value.copy(message = "请先选择至少一集")
            return
        }
        scope.launch {
            var queued = 0
            requested.forEach { episode ->
                val media = runCatching { adapter.loadMedia(root, episode) }.getOrNull()
                val directUrl = media?.directMp4Url
                if (directUrl != null && adapter.isDirectMp4(directUrl) && enqueuer.enqueue(directUrl) is DownloadResult.Accepted) {
                    library.enqueueDownload(detail.title.id, source.id, episode.id, directUrl)
                    queued++
                }
            }
            refreshDownloads()
            _selectedTitle.value = _selectedTitle.value.copy(
                message = if (queued == 0) noPublicVideoDownloadMessage() else "已将 $queued 集加入下载列表"
            )
        }
    }

    fun refreshDownloads() {
        _downloadTasks.value = runCatching { library.downloadQueue() }.getOrDefault(emptyList())
    }

    private fun VideoRootResolution.toUiState(): DramaRootUiState = when (status) {
        VideoRootResolutionStatus.RESOLVED -> DramaRootUiState(root = root, message = detail)
        VideoRootResolutionStatus.USING_CACHED_ROOT -> DramaRootUiState(root = root, isUsingCachedRoot = true, message = detail)
        VideoRootResolutionStatus.USING_BOOTSTRAP_ROOT -> DramaRootUiState(root = root, message = detail)
        VideoRootResolutionStatus.UNAVAILABLE -> DramaRootUiState(message = detail)
    }
}
