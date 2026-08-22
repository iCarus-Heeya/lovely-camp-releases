package com.lovelyreader.video

import com.lovelyreader.ui.video.DramaDownloadEnqueuer
import com.lovelyreader.ui.video.DramaLibrary
import com.lovelyreader.ui.video.DramaRootResolver
import com.lovelyreader.ui.video.DramaViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DramaViewModelTest {
    @Test
    fun `resume matches saved source and episode after a provider host changes`() = runTest {
        val currentTitle = VideoTitle("https://new-provider.example/drama", "Drama", "https://new-provider.example/drama")
        val currentSource = VideoSource("${currentTitle.id}#source-stab1-0", currentTitle.id, "Source")
        val currentEpisode = VideoEpisode(
            "${currentSource.id}#episode-https://new-provider.example/play/one",
            currentSource.id,
            "Episode 1",
            "https://new-provider.example/play/one",
            1,
            currentTitle.id
        )
        val media = VideoMediaLink("https://media.example/one.m3u8")
        val savedTitleId = "https://old-provider.example/drama"
        val savedSourceId = "$savedTitleId#source-stab1-0"
        val savedEpisodeId = "$savedSourceId#episode-https://old-provider.example/play/one"
        val library = FakeLibrary().apply {
            recent = VideoRecentViewing(savedTitleId, "Drama", savedSourceId, savedEpisodeId, 0L, 0L, "$savedTitleId")
        }
        val viewModel = DramaViewModel(
            rootResolver = FakeResolver(VideoRootResolution(VideoSiteRoot("https://new-provider.example", 1L), VideoRootResolutionStatus.RESOLVED, "ok")),
            siteAdapter = FakeAdapter(VideoTitleDetail(currentTitle, listOf(currentSource)), mapOf(currentSource.id to listOf(currentEpisode)), mapOf(currentEpisode.id to media)),
            library = library,
            downloadEnqueuer = FakeDownloadEnqueuer(),
            coroutineScope = this
        )

        advanceUntilIdle()
        viewModel.resumeRecentViewing()
        advanceUntilIdle()

        assertEquals(currentSource, viewModel.selectedSource.value)
        assertEquals(currentEpisode, viewModel.playback.value.episode)
        assertEquals(media, viewModel.playback.value.media)
    }

    @Test
    fun `search requested during root refresh runs after the root is ready`() = runTest {
        val title = VideoTitle("drama", "Drama", "https://video.example/drama")
        val resolution = CompletableDeferred<VideoRootResolution>()
        val viewModel = DramaViewModel(
            rootResolver = DramaRootResolver { resolution.await() },
            siteAdapter = FakeAdapter(VideoTitleDetail(title, emptyList()), emptyMap()),
            library = FakeLibrary(),
            downloadEnqueuer = FakeDownloadEnqueuer(),
            coroutineScope = this
        )

        viewModel.search("Drama")
        resolution.complete(VideoRootResolution(VideoSiteRoot("https://video.example", 1L), VideoRootResolutionStatus.RESOLVED, "ok"))
        advanceUntilIdle()

        assertEquals(listOf(title), viewModel.searchResults.value.results)
    }

    @Test
    fun `resume requested during root refresh continues after the root is ready`() = runTest {
        val title = VideoTitle("drama", "Drama", "https://video.example/drama")
        val source = VideoSource("source", title.id, "Source")
        val episode = VideoEpisode("episode-1", source.id, "Episode 1", "https://video.example/episode/1", 1, title.id)
        val media = VideoMediaLink("https://video.example/media/1.m3u8")
        val resolution = CompletableDeferred<VideoRootResolution>()
        val library = FakeLibrary().apply {
            recent = VideoRecentViewing(title.id, title.name, source.id, episode.id, 0L, 0L, title.detailUrl)
        }
        val viewModel = DramaViewModel(
            rootResolver = DramaRootResolver { resolution.await() },
            siteAdapter = FakeAdapter(VideoTitleDetail(title, listOf(source)), mapOf(source.id to listOf(episode)), mapOf(episode.id to media)),
            library = library,
            downloadEnqueuer = FakeDownloadEnqueuer(),
            coroutineScope = this
        )

        viewModel.resumeRecentViewing()
        resolution.complete(VideoRootResolution(VideoSiteRoot("https://video.example", 1L), VideoRootResolutionStatus.RESOLVED, "ok"))
        advanceUntilIdle()

        assertEquals(episode, viewModel.playback.value.episode)
        assertEquals(media, viewModel.playback.value.media)
    }

    @Test
    fun `recent viewing resumes its remembered source and episode`() = runTest {
        val title = VideoTitle("drama", "Drama", "https://video.example/drama")
        val source = VideoSource("source", title.id, "Source")
        val episode = VideoEpisode("episode-1", source.id, "Episode 1", "https://video.example/episode/1", 1, title.id)
        val media = VideoMediaLink("https://video.example/media/1.m3u8")
        val library = FakeLibrary().apply {
            recent = VideoRecentViewing(title.id, title.name, source.id, episode.id, 0L, 0L, title.detailUrl)
        }
        val viewModel = DramaViewModel(
            rootResolver = FakeResolver(VideoRootResolution(VideoSiteRoot("https://video.example", 1L), VideoRootResolutionStatus.RESOLVED, "ok")),
            siteAdapter = FakeAdapter(VideoTitleDetail(title, listOf(source)), mapOf(source.id to listOf(episode)), mapOf(episode.id to media)),
            library = library,
            downloadEnqueuer = FakeDownloadEnqueuer(),
            coroutineScope = this
        )

        advanceUntilIdle()
        viewModel.resumeRecentViewing()
        advanceUntilIdle()

        assertEquals(source, viewModel.selectedSource.value)
        assertEquals(episode, viewModel.playback.value.episode)
        assertEquals(media, viewModel.playback.value.media)
    }

    @Test
    fun `creating the view model refreshes the video root immediately`() = runTest {
        var resolveCalls = 0
        val library = FakeLibrary()
        val viewModel = DramaViewModel(
            rootResolver = DramaRootResolver {
                resolveCalls++
                VideoRootResolution(
                    VideoSiteRoot("https://video.example", 1L),
                    VideoRootResolutionStatus.RESOLVED,
                    "ok"
                )
            },
            siteAdapter = null,
            library = library,
            downloadEnqueuer = null,
            coroutineScope = this
        )

        advanceUntilIdle()

        assertEquals(1, resolveCalls)
        assertEquals("https://video.example", viewModel.rootStatus.value.root?.url)
    }

    @Test
    fun `selecting a source exposes only that source episodes and clears previous multi-selection`() = runTest {
        val title = VideoTitle("drama", "Drama", "https://video.example/drama")
        val first = VideoSource("first", title.id, "First")
        val second = VideoSource("second", title.id, "Second")
        val adapter = FakeAdapter(
            detail = VideoTitleDetail(title, listOf(first, second)),
            episodes = mapOf(
                first.id to listOf(VideoEpisode("first-1", first.id, "1", "https://video.example/first/1", 1, title.id)),
                second.id to listOf(VideoEpisode("second-1", second.id, "1", "https://video.example/second/1", 1, title.id))
            )
        )
        val library = FakeLibrary()
        val viewModel = DramaViewModel(
            rootResolver = FakeResolver(VideoRootResolution(VideoSiteRoot("https://video.example", 1L), VideoRootResolutionStatus.RESOLVED, "ok")),
            siteAdapter = adapter,
            library = library,
            downloadEnqueuer = FakeDownloadEnqueuer(),
            coroutineScope = this
        )

        viewModel.refreshRoot()
        advanceUntilIdle()
        viewModel.openTitle(title)
        advanceUntilIdle()
        viewModel.toggleEpisode("first-1")
        viewModel.selectSource(second)
        advanceUntilIdle()

        assertEquals(second, viewModel.selectedSource.value)
        assertEquals(listOf("second-1"), viewModel.sourceEpisodes.value.map(VideoEpisode::id))
        assertTrue(viewModel.selectedEpisodeIds.value.isEmpty())
    }

    @Test
    fun `missing resolver and adapter leave the drama flow unavailable instead of throwing`() = runTest {
        val viewModel = DramaViewModel(
            rootResolver = null,
            siteAdapter = null,
            library = FakeLibrary(),
            downloadEnqueuer = FakeDownloadEnqueuer(),
            coroutineScope = this
        )

        viewModel.refreshRoot()
        advanceUntilIdle()
        viewModel.search("anything")
        advanceUntilIdle()

        assertEquals(null, viewModel.rootStatus.value.root)
        assertEquals("片源地址解析服务暂不可用。", viewModel.rootStatus.value.message)
        assertEquals("片源暂时连不上，请稍后再试", viewModel.searchResults.value.message)
    }

    @Test
    fun `opening an episode resolves its real media link for playback`() = runTest {
        val title = VideoTitle("drama", "Drama", "https://video.example/drama")
        val source = VideoSource("source", title.id, "Source")
        val episode = VideoEpisode("episode-1", source.id, "Episode 1", "https://video.example/episode/1", 1, title.id)
        val media = VideoMediaLink(playbackUrl = "https://video.example/media/1.m3u8")
        val adapter = FakeAdapter(
            detail = VideoTitleDetail(title, listOf(source)),
            episodes = mapOf(source.id to listOf(episode)),
            media = mapOf(episode.id to media)
        )
        val library = FakeLibrary()
        val viewModel = DramaViewModel(
            rootResolver = FakeResolver(VideoRootResolution(VideoSiteRoot("https://video.example", 1L), VideoRootResolutionStatus.RESOLVED, "ok")),
            siteAdapter = adapter,
            library = library,
            downloadEnqueuer = FakeDownloadEnqueuer(),
            coroutineScope = this
        )

        viewModel.refreshRoot()
        advanceUntilIdle()
        viewModel.openTitle(title)
        advanceUntilIdle()
        viewModel.openEpisode(episode)
        advanceUntilIdle()

        assertEquals(media, viewModel.playback.value.media)
        assertEquals(episode, viewModel.playback.value.episode)
        assertEquals(title.name, viewModel.playback.value.titleName)
        assertTrue(viewModel.playback.value.message.isEmpty())
        assertEquals(episode.id, library.recentEpisodeId)
    }

    @Test
    fun `opening an episode without a public media link never creates playback`() = runTest {
        val title = VideoTitle("drama", "Drama", "https://video.example/drama")
        val source = VideoSource("source", title.id, "Source")
        val episode = VideoEpisode("episode-1", source.id, "Episode 1", "https://video.example/episode/1", 1, title.id)
        val adapter = FakeAdapter(VideoTitleDetail(title, listOf(source)), mapOf(source.id to listOf(episode)))
        val viewModel = DramaViewModel(
            rootResolver = FakeResolver(VideoRootResolution(VideoSiteRoot("https://video.example", 1L), VideoRootResolutionStatus.RESOLVED, "ok")),
            siteAdapter = adapter, library = FakeLibrary(), downloadEnqueuer = FakeDownloadEnqueuer(), coroutineScope = this
        )

        advanceUntilIdle()
        viewModel.openTitle(title)
        advanceUntilIdle()
        viewModel.openEpisode(episode)
        advanceUntilIdle()

        assertEquals(null, viewModel.playback.value.media)
        assertTrue(viewModel.playback.value.message.contains("公开播放"))
    }

    private class FakeResolver(private val resolution: VideoRootResolution) : DramaRootResolver {
        override suspend fun resolve(): VideoRootResolution = resolution
    }

    private class FakeAdapter(
        private val detail: VideoTitleDetail,
        private val episodes: Map<String, List<VideoEpisode>>,
        private val media: Map<String, VideoMediaLink> = emptyMap()
    ) : VideoSiteAdapter {
        override suspend fun search(root: VideoSiteRoot, query: String): List<VideoTitle> = listOf(detail.title)
        override suspend fun loadDetail(root: VideoSiteRoot, titleUrl: String): VideoTitleDetail = detail
        override suspend fun loadEpisodes(root: VideoSiteRoot, source: VideoSource): List<VideoEpisode> = episodes[source.id].orEmpty()
        override suspend fun loadMedia(root: VideoSiteRoot, episode: VideoEpisode): VideoMediaLink? = media[episode.id]
        override fun isDirectMp4(url: String): Boolean = url.endsWith(".mp4")
    }

    private class FakeLibrary : DramaLibrary {
        private val selectedSources = mutableMapOf<String, String>()
        private val downloads = mutableListOf<VideoDownloadTask>()
        var recentEpisodeId: String? = null
        var recent: VideoRecentViewing? = null

        override fun selectedSourceId(titleId: String): String? = selectedSources[titleId]
        override fun selectSource(titleId: String, sourceId: String) { selectedSources[titleId] = sourceId }
        override fun recordViewing(title: VideoTitle, source: VideoSource, episode: VideoEpisode) {
            recentEpisodeId = episode.id
        }
        override fun recentViewing(): VideoRecentViewing? = recent ?: recentEpisodeId?.let { episodeId ->
            VideoRecentViewing("drama", "Drama", "source", episodeId, 0L, 0L)
        }
        override fun recordSourceResult(titleId: String, sourceId: String, success: Boolean) = Unit
        override fun orderSources(titleId: String, sources: List<VideoSource>): List<VideoSource> = sources
        override fun enqueueDownload(titleId: String, sourceId: String, episodeId: String, directUrl: String): VideoDownloadTask {
            return VideoDownloadTask("task-$episodeId", titleId, sourceId, episodeId, directUrl, status = VideoDownloadStatus.QUEUED)
                .also(downloads::add)
        }
        override fun downloadQueue(): List<VideoDownloadTask> = downloads.toList()
    }

    private class FakeDownloadEnqueuer : DramaDownloadEnqueuer {
        override fun enqueue(url: String): DownloadResult = DownloadResult.Accepted
    }
}
