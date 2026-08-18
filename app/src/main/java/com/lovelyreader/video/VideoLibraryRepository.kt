package com.lovelyreader.video

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.net.URI
import java.security.MessageDigest

/**
 * Durable, metadata-only state for the drama experience. Media bytes remain
 * owned by Android's download collection and are never part of this snapshot.
 */
class VideoLibraryRepository(
    private val persistence: VideoLibraryPersistence,
    private val scope: CoroutineScope
) {
    private var recentViewing: VideoRecentViewing? = null
    private val selectedSources = linkedMapOf<String, String>()
    private val sourceScores = linkedMapOf<String, Int>()
    private val downloads = linkedMapOf<String, VideoDownloadTask>()

    init {
        restore(persistence.load())
    }

    fun recordViewing(
        title: VideoTitle,
        source: VideoSource,
        episode: VideoEpisode,
        positionMillis: Long,
        viewedAtMillis: Long
    ) {
        require(source.titleId == title.id) { "Source must belong to the title" }
        require(episode.sourceId == source.id) { "Episode must belong to the source" }
        selectSource(title.id, source.id, persist = false)
        recentViewing = VideoRecentViewing(
            titleId = title.id,
            titleName = title.name,
            sourceId = source.id,
            episodeId = episode.id,
            positionMillis = positionMillis.coerceAtLeast(0L),
            viewedAtMillis = viewedAtMillis.coerceAtLeast(0L),
            titleDetailUrl = title.detailUrl
        )
        persist()
    }

    fun recentViewing(): VideoRecentViewing? = recentViewing

    fun selectSource(titleId: String, sourceId: String) {
        selectSource(titleId, sourceId, persist = true)
    }

    fun selectedSourceId(titleId: String): String? = selectedSources[titleId]

    /** In-process health memory used only to prefer a working source in the current session. */
    fun recordSourceResult(titleId: String, sourceId: String, success: Boolean) {
        require(titleId.isNotBlank() && sourceId.isNotBlank())
        val key = "$titleId|$sourceId"
        sourceScores[key] = (sourceScores[key] ?: 0) + if (success) 1 else -1
    }

    fun orderSources(titleId: String, sourceIds: List<String>): List<String> =
        sourceIds.withIndex().sortedWith(compareByDescending<IndexedValue<String>> { sourceScores["$titleId|${it.value}"] ?: 0 }
            .thenBy { it.index }).map { it.value }

    /**
     * Adds only one task for a direct URL. The URL itself is intentionally not
     * persisted; its SHA-256 hash is the queue identity.
     */
    fun enqueueDownload(
        titleId: String,
        sourceId: String,
        episodeId: String,
        directUrl: String,
        systemDownloadId: Long? = null
    ): VideoDownloadTask {
        require(titleId.isNotBlank()) { "Title id is required" }
        require(sourceId.isNotBlank()) { "Source id is required" }
        require(episodeId.isNotBlank()) { "Episode id is required" }
        val directUrlHash = stableUrlHash(directUrl)
        downloads[directUrlHash]?.let { return it }

        return VideoDownloadTask(
            id = "video-$directUrlHash",
            titleId = titleId,
            sourceId = sourceId,
            episodeId = episodeId,
            directUrlHash = directUrlHash,
            systemDownloadId = systemDownloadId
        ).also { task ->
            downloads[directUrlHash] = task
            persist()
        }
    }

    fun downloadQueue(): List<VideoDownloadTask> = downloads.values.toList()

    fun markDownloading(directUrlHash: String, systemDownloadId: Long? = null): VideoDownloadTask? =
        transition(directUrlHash) { task ->
            task.copy(
                status = VideoDownloadStatus.DOWNLOADING,
                systemDownloadId = systemDownloadId ?: task.systemDownloadId,
                localUri = null
            )
        }

    fun markCompleted(directUrlHash: String, localUri: String): VideoDownloadTask? {
        require(localUri.isNotBlank()) { "Completed downloads require a local URI" }
        return transition(directUrlHash) { task ->
            task.copy(status = VideoDownloadStatus.COMPLETED, localUri = localUri)
        }
    }

    fun markFailed(directUrlHash: String): VideoDownloadTask? = transition(directUrlHash) { task ->
        task.copy(status = VideoDownloadStatus.FAILED, localUri = null)
    }

    fun stableUrlHash(directUrl: String): String {
        val normalized = normalizeUrl(directUrl)
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun selectSource(titleId: String, sourceId: String, persist: Boolean) {
        require(titleId.isNotBlank()) { "Title id is required" }
        require(sourceId.isNotBlank()) { "Source id is required" }
        selectedSources[titleId] = sourceId
        if (persist) persist()
    }

    private fun transition(
        directUrlHash: String,
        update: (VideoDownloadTask) -> VideoDownloadTask
    ): VideoDownloadTask? {
        val current = downloads[directUrlHash] ?: return null
        return update(current).also { updated ->
            downloads[directUrlHash] = updated
            persist()
        }
    }

    private fun restore(snapshot: VideoLibrarySnapshot?) {
        if (snapshot == null) return
        recentViewing = snapshot.recentViewing
        selectedSources.putAll(snapshot.selectedSources.filter { (titleId, sourceId) ->
            titleId.isNotBlank() && sourceId.isNotBlank()
        })
        snapshot.downloads.forEach { task ->
            if (task.directUrlHash.isNotBlank() && task.id.isNotBlank()) {
                downloads.putIfAbsent(task.directUrlHash, task)
            }
        }
    }

    private fun persist() {
        scope.launch {
            persistence.save(
                VideoLibrarySnapshot(
                    recentViewing = recentViewing,
                    selectedSources = selectedSources.toMap(),
                    downloads = downloads.values.toList()
                )
            )
        }
    }

    private fun normalizeUrl(url: String): String {
        val raw = url.trim().takeIf(String::isNotEmpty) ?: throw IllegalArgumentException("Direct URL is required")
        return runCatching { URI(raw).normalize().toASCIIString() }.getOrDefault(raw)
    }
}

data class VideoRecentViewing(
    val titleId: String,
    val titleName: String,
    val sourceId: String,
    val episodeId: String,
    val positionMillis: Long,
    val viewedAtMillis: Long,
    val titleDetailUrl: String = ""
)

data class VideoLibrarySnapshot(
    val recentViewing: VideoRecentViewing? = null,
    val selectedSources: Map<String, String> = emptyMap(),
    val downloads: List<VideoDownloadTask> = emptyList()
)

interface VideoLibraryPersistence {
    fun load(): VideoLibrarySnapshot?

    suspend fun save(snapshot: VideoLibrarySnapshot)
}
