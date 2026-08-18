package com.lovelyreader.video

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoLibraryRepositoryTest {
    @Test
    fun `successful source is preferred over a source with recent failures`() = runTest {
        val repository = VideoLibraryRepository(InMemoryVideoLibraryPersistence(), this)
        repository.recordSourceResult("drama", "slow", success = false)
        repository.recordSourceResult("drama", "slow", success = false)
        repository.recordSourceResult("drama", "steady", success = true)

        assertEquals(listOf("steady", "slow"), repository.orderSources("drama", listOf("slow", "steady")))
    }

    @Test
    fun `selected source and recent viewing survive repository recreation`() = runTest {
        val persistence = InMemoryVideoLibraryPersistence()
        val title = VideoTitle("title-1", "A drama", "https://site.example/title-1")
        val source = VideoSource("source-1", title.id, "Line one")
        val episode = VideoEpisode("episode-1", source.id, "Episode 1", "https://site.example/episode-1", 0)

        VideoLibraryRepository(persistence, this).apply {
            selectSource(title.id, source.id)
            recordViewing(title, source, episode, positionMillis = 42_000L, viewedAtMillis = 99L)
        }
        advanceUntilIdle()

        val restored = VideoLibraryRepository(persistence, this)

        assertEquals(source.id, restored.selectedSourceId(title.id))
        assertEquals(
            VideoRecentViewing(title.id, title.name, source.id, episode.id, 42_000L, 99L, title.detailUrl),
            restored.recentViewing()
        )
    }

    @Test
    fun `queue deduplicates repeated direct URL and keeps the original task`() = runTest {
        val repository = VideoLibraryRepository(InMemoryVideoLibraryPersistence(), this)

        val first = repository.enqueueDownload(
            titleId = "title-1",
            sourceId = "source-1",
            episodeId = "episode-1",
            directUrl = "https://cdn.example/episode-1.mp4",
            systemDownloadId = 7L
        )
        val repeated = repository.enqueueDownload(
            titleId = "title-1",
            sourceId = "source-1",
            episodeId = "episode-1",
            directUrl = "https://cdn.example/episode-1.mp4",
            systemDownloadId = 8L
        )
        advanceUntilIdle()

        assertEquals(first, repeated)
        assertEquals(1, repository.downloadQueue().size)
        assertEquals(7L, repository.downloadQueue().single().systemDownloadId)
    }

    @Test
    fun `completion transition persists local URI for its matching queue task`() = runTest {
        val persistence = InMemoryVideoLibraryPersistence()
        val repository = VideoLibraryRepository(persistence, this)
        val queued = repository.enqueueDownload(
            titleId = "title-1",
            sourceId = "source-1",
            episodeId = "episode-1",
            directUrl = "https://cdn.example/episode-1.mp4"
        )
        advanceUntilIdle()

        val completed = repository.markCompleted(queued.directUrlHash, "content://downloads/17")
        advanceUntilIdle()

        val restored = VideoLibraryRepository(persistence, this).downloadQueue().single()

        assertEquals(VideoDownloadStatus.COMPLETED, completed?.status)
        assertEquals("content://downloads/17", completed?.localUri)
        assertEquals(completed, restored)
    }

    @Test
    fun `failure transition only changes the matching queue task`() = runTest {
        val repository = VideoLibraryRepository(InMemoryVideoLibraryPersistence(), this)
        val first = repository.enqueueDownload("title-1", "source-1", "episode-1", "https://cdn.example/episode-1.mp4")
        val second = repository.enqueueDownload("title-1", "source-1", "episode-2", "https://cdn.example/episode-2.mp4")
        advanceUntilIdle()

        val failed = repository.markFailed(second.directUrlHash)
        advanceUntilIdle()

        assertEquals(VideoDownloadStatus.FAILED, failed?.status)
        assertEquals(VideoDownloadStatus.QUEUED, repository.downloadQueue().single { it.id == first.id }.status)
        assertEquals(VideoDownloadStatus.FAILED, repository.downloadQueue().single { it.id == second.id }.status)
        assertNull(repository.markFailed("not-a-task"))
        assertNotNull(repository.downloadQueue().singleOrNull { it.id == second.id })
    }

    private class InMemoryVideoLibraryPersistence : VideoLibraryPersistence {
        private var snapshot: VideoLibrarySnapshot? = null

        override fun load(): VideoLibrarySnapshot? = snapshot

        override suspend fun save(snapshot: VideoLibrarySnapshot) {
            this.snapshot = snapshot
        }
    }
}
